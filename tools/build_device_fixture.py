#!/usr/bin/env python3
"""Build a disposable offline device fixture using the existing SDK, no Gradle module."""
from __future__ import annotations

import argparse
from pathlib import Path
import subprocess
import sys
import tempfile
import zipfile

try:
    from tools.verify import check_java_runtime, command_environment
except ModuleNotFoundError:
    from verify import check_java_runtime, command_environment

ROOT = Path(__file__).resolve().parent.parent


def build(sdk: Path, output: Path) -> Path:
    check_java_runtime()
    env = command_environment()
    windows = sys.platform == "win32"
    java_bin = Path(env["JAVA_HOME"]) / "bin" if env.get("JAVA_HOME") else Path()
    tools = sdk / "build-tools/36.0.0"
    platform = sdk / "platforms/android-37.0/android.jar"
    def run(args):
        result = subprocess.run([str(a) for a in args], capture_output=True, text=True, env=env, timeout=90)
        if result.returncode:
            raise RuntimeError(f"Fixture tool failed: {Path(args[0]).name}\n{result.stderr[-2000:]}")
    output.mkdir(parents=True, exist_ok=False)
    with tempfile.TemporaryDirectory(prefix="a14-device-build-") as tmp:
        work = Path(tmp)
        source = ROOT / "tools/device/fixture"
        run([java_bin / ("javac.exe" if windows else "javac"), "--release", "8", "-classpath", platform,
             "-d", work, source / "FixtureActivity.java"])
        run([tools / ("d8.bat" if windows else "d8"), "--min-api", "34", "--lib", platform,
             "--output", work, *sorted(work.rglob("*.class"))])
        unsigned = work / "unsigned.apk"
        run([tools / ("aapt2.exe" if windows else "aapt2"), "link", "-I", platform,
             "--manifest", source / "AndroidManifest.xml", "-o", unsigned])
        with zipfile.ZipFile(unsigned, "a") as archive:
            archive.write(work / "classes.dex", "classes.dex")
        key = work / "fixture.p12"
        # Disposable test-only key. Never reads the module's signing configuration.
        run([java_bin / ("keytool.exe" if windows else "keytool"), "-genkeypair", "-keystore", key,
             "-storepass", "a14fixture", "-keypass", "a14fixture", "-alias", "fixture", "-dname", "CN=A14 Fixture",
             "-keyalg", "RSA", "-validity", "2"])
        apk = output / "fixture.apk"
        run([tools / ("apksigner.bat" if windows else "apksigner"), "sign", "--ks", key,
             "--ks-pass", "pass:a14fixture", "--out", apk, unsigned])
    return apk


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sdk", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    print(build(args.sdk, args.output))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
