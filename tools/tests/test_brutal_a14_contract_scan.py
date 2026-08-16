import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


SCANNER = Path(__file__).resolve().parent.parent / "brutal_a14_contract_scan.py"


class ContractScanExitProtocolTest(unittest.TestCase):
    """Verify the apply-check scanner exit protocol in a real git worktree."""

    def _create_repo(self, mutators_source: str) -> Path:
        td = Path(tempfile.mkdtemp())
        (td / "app/src/main/java").mkdir(parents=True)
        (td / "tools").mkdir()
        (td / "app/src/main/java/Foo.java").write_text(
            "public class Foo {\n    public void foo() {}\n}\n", encoding="utf-8"
        )
        (td / "tools/brutal_a14_mutators.py").write_text(mutators_source, encoding="utf-8")
        self._run(td, ["git", "init"])
        self._run(td, ["git", "config", "user.email", "devin@local"])
        self._run(td, ["git", "config", "user.name", "Devin"])
        self._run(td, ["git", "add", "."])
        self._run(td, ["git", "commit", "-m", "baseline"])
        return td

    def _run(self, cwd: Path, argv: list[str]) -> int:
        result = subprocess.run(argv, cwd=cwd, capture_output=True, text=True)
        return result.returncode

    def _apply(self, repo: Path, mutator: str) -> int:
        env = os.environ.copy()
        # The temp repo's tools package must shadow the real repo.
        env["PYTHONPATH"] = str(repo)
        result = subprocess.run(
            [sys.executable, "-c", f"import tools.brutal_a14_mutators as m; m.MUTATORS['{mutator}'](__import__('pathlib').Path({str(repo)!r}), {{}})"],
            cwd=repo,
            env=env,
            capture_output=True,
            text=True,
        )
        if result.returncode != 0:
            print(result.stderr, file=sys.stderr)
        return result.returncode

    def _scan(self, repo: Path, mutator: str) -> int:
        result = subprocess.run(
            [sys.executable, str(SCANNER), "--mutation", mutator],
            cwd=repo,
            capture_output=True,
            text=True,
        )
        return result.returncode

    def test_simple_mutation_returns_applied(self):
        mutators = '''
import re
from pathlib import Path

def _read(root, rel):
    return (root/rel).read_text(encoding="utf-8")

def _write(root, rel, text):
    (root/rel).write_text(text, encoding="utf-8")

def _replace_first(root, rel, pattern, repl, flags=0):
    text = _read(root, rel)
    changed, count = re.subn(pattern, repl, text, count=1, flags=flags)
    if count != 1:
        raise RuntimeError("pattern not found")
    _write(root, rel, changed)

def _java_file(rel):
    return f"app/src/main/java/{rel}"

def simple_apply(root, cfg):
    _replace_first(root, _java_file("Foo.java"), r"foo\\(\\)", "bar()")

MUTATORS = {"simple_apply": simple_apply}
'''
        repo = self._create_repo(mutators)
        try:
            self.assertEqual(0, self._apply(repo, "simple_apply"))
            self.assertEqual(0, self._scan(repo, "simple_apply"))
        finally:
            shutil.rmtree(repo, ignore_errors=True)

    def test_pattern_count_preserved_returns_not_applied(self):
        """A mutation that keeps the target pattern count must not report applied."""
        mutators = '''
import re
from pathlib import Path

def _read(root, rel):
    return (root/rel).read_text(encoding="utf-8")

def _write(root, rel, text):
    (root/rel).write_text(text, encoding="utf-8")

def _replace_first(root, rel, pattern, repl, flags=0):
    text = _read(root, rel)
    changed, count = re.subn(pattern, repl, text, count=1, flags=flags)
    if count != 1:
        raise RuntimeError("pattern not found")
    _write(root, rel, changed)

def _java_file(rel):
    return f"app/src/main/java/{rel}"

def fake_green(root, cfg):
    _replace_first(root, _java_file("Foo.java"), r"foo\\(\\)", "bar()")
    path = root / "app/src/main/java/Foo.java"
    path.write_text(path.read_text(encoding="utf-8") + "\\n    public void dummy() { foo(); }\\n", encoding="utf-8")

MUTATORS = {"fake_green": fake_green}
'''
        repo = self._create_repo(mutators)
        try:
            self.assertEqual(0, self._apply(repo, "fake_green"))
            # The scanner only knows the target pattern; count is unchanged.
            self.assertEqual(4, self._scan(repo, "fake_green"))
        finally:
            shutil.rmtree(repo, ignore_errors=True)

    def test_no_extractable_pattern_returns_cannot_verify(self):
        mutators = '''
from pathlib import Path

def no_pattern(root, cfg):
    path = root / "app/src/main/java/Foo.java"
    path.write_text("public class Foo {\\n    public void foo() {\\n        throw new RuntimeException(\\"broken\\");\\n    }\\n}\\n", encoding="utf-8")

MUTATORS = {"no_pattern": no_pattern}
'''
        repo = self._create_repo(mutators)
        try:
            self.assertEqual(0, self._apply(repo, "no_pattern"))
            self.assertEqual(2, self._scan(repo, "no_pattern"))
        finally:
            shutil.rmtree(repo, ignore_errors=True)

    def test_inject_hazard_two_arg_form_supported(self):
        mutators = '''
from pathlib import Path

def _inject_hazard(root, body):
    path = root / "app/src/main/java/brutal_mutation/InjectedHazard.kt"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(f"package brutal_mutation\\n\\n{body}\\n", encoding="utf-8")

def inject_hazard(root, cfg):
    _inject_hazard(root, 'object InjectedHazard { fun run() { error("x") } }')

MUTATORS = {"inject_hazard": inject_hazard}
'''
        repo = self._create_repo(mutators)
        try:
            self.assertEqual(0, self._apply(repo, "inject_hazard"))
            self.assertEqual(0, self._scan(repo, "inject_hazard"))
        finally:
            shutil.rmtree(repo, ignore_errors=True)


if __name__ == "__main__":
    unittest.main()
