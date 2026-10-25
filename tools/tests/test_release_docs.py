"""Published-source document generation, atomic recovery and release-baseline review."""
import base64
import copy
import hashlib
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from tools import release_docs as docs, signed_release as release

SHA = "a" * 40
VERSION = "r14.25.3"


class Server:
    def __init__(self):
        self.metadata = {"version_name": VERSION, "version_code": 248, "source_sha": SHA,
            "apk": "CustoMIUIzer-A14-r14.25.3-eae252.apk", "apk_sha256": "b" * 64, "apk_size": 42,
            "repository": release.REPO, "workflow": release.WORKFLOW, "source_ref": "refs/heads/main",
            "certificate_sha256": release.CERT, "application_id": "tv.withaibuild.customiuizer.r14",
            "min_sdk": 34, "target_sdk": 34, "abi": "arm64-v8a", "debuggable": False}
        self.raw = json.dumps(self.metadata) + "\n"
        assets = [{"name": self.metadata["apk"], "digest": "sha256:" + "b" * 64, "size": 42},
                  {"id": 9, "name": "build-metadata.json", "size": len(self.raw.encode()),
                   "digest": "sha256:" + hashlib.sha256(self.raw.encode()).hexdigest()}]
        self.source = {"id": 1, "tag_name": VERSION, "target_commitish": SHA, "draft": False,
                       "prerelease": False, "assets": assets, "html_url": "https://github.com/source/release"}
        self.download = dict(copy.deepcopy(self.source), id=2, tag_name="248-" + VERSION)
        self.head = "c" * 40
        self.old_tree = {"LICENSE": {"mode": "100644", "sha": "preserve"}}
        self.new_tree = None
        self.writes = []
        self.reads = []
        self.fail_after_patch = False
        self.race = False
        self.head_reads = 0

    def text(self, filename):
        if filename.endswith("scope.list"):
            return "system\nandroid\ncom.android.systemui\n"
        if filename.endswith("module.prop"):
            return "minApiVersion=102\ntargetApiVersion=102\nstaticScope=true\n"
        user, engineering = ("用户变化", "工程说明") if filename == "CHANGELOG.md" else ("User changes", "Engineering notes")
        return ("## Unreleased\n\n- NEVER_PUBLISH\n\n## r14.25.3\n\nversionCode 248. Changes since r14.25.1:\n\n"
                f"### {user}\n\nOverview.\n\n" + "\n".join(f"- User change {i}." for i in range(4)) +
                f"\n\n### {engineering}\n\n- INTERNAL_ONLY\n\n## r14.25.1\n\nversionCode 246.\n\n- Prior user change.\n")

    def api(self, endpoint, *, env=None, **fields):
        self.reads.append(endpoint)
        if endpoint == f"repos/{release.REPO}/releases/latest": return copy.deepcopy(self.source)
        if endpoint == f"repos/{docs.DOWNLOAD}/releases/latest": return copy.deepcopy(self.download)
        if "/git/ref/tags/" in endpoint: return {"object": {"type": "commit", "sha": SHA}}
        if endpoint.endswith("/contents?ref=" + SHA):
            return [{"name": name, "type": "file"} for name, _ in release.CURRENT_CHANGELOGS]
        if "/contents/" in endpoint:
            if not endpoint.endswith("?ref=" + SHA): raise AssertionError("Source must use the exact published SHA")
            filename = endpoint.split("/contents/", 1)[1].split("?", 1)[0]
            return {"content": base64.b64encode(self.text(filename).encode()).decode()}
        if "/releases?per_page=100&page=1" in endpoint:
            return [self.source, dict(self.source, tag_name="r14.25.1"), dict(self.source, tag_name="r14.99.0", prerelease=True)]
        if endpoint.endswith("/git/ref/heads/main"):
            self.head_reads += 1
            if self.race and self.head_reads == 2: self.head = "e" * 40
            return {"object": {"sha": self.head}}
        if "/git/commits/" in endpoint:
            return {"tree": {"sha": "old-tree" if self.head == "c" * 40 else "new-tree"}}
        if "/git/trees/" in endpoint:
            tree = self.old_tree if endpoint.endswith("old-tree") else self.new_tree
            return {"tree": [dict(value, path=key, type="blob") for key, value in tree.items()], "truncated": False}
        raise AssertionError(endpoint)

    def write(self, endpoint, payload, env, method="POST"):
        self.writes.append((endpoint, copy.deepcopy(payload), method))
        if endpoint.endswith("/git/trees"):
            self.new_tree = copy.deepcopy(self.old_tree)
            for item in payload["tree"]:
                assert item["path"] in docs.FILES and item["mode"] == "100644"
                self.new_tree[item["path"]] = {"mode": "100644", "sha": docs.blob_sha(item["content"])}
            return {"sha": "new-tree"}
        if endpoint.endswith("/git/commits"):
            assert payload["parents"] == ["c" * 40]
            return {"sha": "d" * 40}
        assert endpoint.endswith("/git/refs/heads/main") and method == "PATCH" and payload["force"] is False
        self.head = payload["sha"]
        self.old_tree = self.new_tree
        if self.fail_after_patch:
            self.fail_after_patch = False
            raise RuntimeError("Connection lost after successful patch")
        return {"object": {"sha": self.head}}


class DownloadDocumentsTest(unittest.TestCase):
    def context(self, server):
        from contextlib import ExitStack
        stack = ExitStack()
        stack.enter_context(patch.object(release, "api", side_effect=server.api))
        stack.enter_context(patch.object(release, "optional_api", side_effect=server.api))
        stack.enter_context(patch.object(docs, "metadata_bytes", side_effect=lambda _: server.raw.encode("utf-8")))
        stack.enter_context(patch.object(docs, "write_api", side_effect=server.write))
        return stack

    def test_exact_published_source_generates_concise_bilingual_documents_and_native_scope(self):
        server = Server()
        with self.context(server):
            result = docs.documents(docs.published_version(VERSION))
        self.assertEqual(docs.FILES, set(result))
        self.assertEqual(["system", "android", "com.android.systemui"], json.loads(result["SCOPE"]))
        for filename in ("README.md", "README_EN.md", "CHANGELOG.md", "CHANGELOG_CN.md"):
            self.assertIn(VERSION, result[filename])
            self.assertNotIn("NEVER_PUBLISH", result[filename])
            self.assertNotIn("INTERNAL_ONLY", result[filename])
        self.assertIn(server.metadata["apk"], result["README.md"])
        self.assertIn("/commit/" + SHA, result["README_EN.md"])

    def test_stable_identity_asset_and_platform_mismatches_fail_without_any_write(self):
        for key, value in (("draft", True), ("prerelease", True), ("tag_name", "r14.99.0"), ("target_commitish", "f" * 40)):
            server = Server(); server.source[key] = value
            with self.subTest(key=key), self.context(server), self.assertRaises(ValueError):
                docs.sync_published(VERSION, apply=True)
            self.assertEqual([], server.writes)
        server = Server(); server.download["assets"][0]["digest"] = "sha256:" + "f" * 64
        with self.context(server), self.assertRaisesRegex(ValueError, "assets differ"):
            docs.sync_published(VERSION, apply=True)
        self.assertEqual([], server.writes)

    def test_metadata_digest_mismatch_and_wrong_scope_do_not_write(self):
        server = Server(); server.raw += " "
        with self.context(server), self.assertRaisesRegex(ValueError, "metadata bytes"):
            docs.sync_published(VERSION, apply=True)
        self.assertEqual([], server.writes)
        server = Server()
        with self.context(server), patch.object(docs, "source_text", return_value="invalid"), self.assertRaises(ValueError):
            docs.sync_published(VERSION, apply=True)
        self.assertEqual([], server.writes)

    def test_source_only_release_remains_a_valid_review_baseline_while_download_is_older(self):
        server = Server(); server.download["tag_name"] = "246-r14.25.1"
        with self.context(server):
            baseline = docs.published_version(require_download=False)
            self.assertEqual(VERSION, baseline["metadata"]["version_name"])
            self.assertIsNone(baseline["download"])
            with self.assertRaisesRegex(ValueError, "same latest stable"):
                docs.published_version(VERSION)

    def test_original_crlf_metadata_bytes_are_hashed_before_json_decoding(self):
        server = Server()
        server.raw = json.dumps(server.metadata, indent=2).replace("\n", "\r\n") + "\r\n"
        for record in (server.source, server.download):
            asset = record["assets"][1]
            asset["size"] = len(server.raw.encode())
            asset["digest"] = "sha256:" + hashlib.sha256(server.raw.encode()).hexdigest()
        with self.context(server):
            self.assertEqual(VERSION, docs.published_version(VERSION)["metadata"]["version_name"])

    def test_preview_is_read_only_and_repeated_sync_has_no_commit(self):
        server = Server()
        with self.context(server):
            docs.sync_published(VERSION, apply=False)
            self.assertEqual([], server.writes)
            docs.sync_published(VERSION, apply=True)
            writes = len(server.writes)
            docs.sync_published(VERSION, apply=True)
            self.assertEqual(writes, len(server.writes))
        self.assertEqual("preserve", server.new_tree["LICENSE"]["sha"])
        self.assertEqual(1, sum(method == "PATCH" for _, _, method in server.writes))

    def test_unknown_patch_outcome_recovers_by_readback_without_a_second_commit(self):
        server = Server(); server.fail_after_patch = True
        with self.context(server):
            with self.assertRaises(RuntimeError): docs.sync_published(VERSION, apply=True)
            writes = len(server.writes)
            docs.sync_published(VERSION, apply=True)
            self.assertEqual(writes, len(server.writes))

    def test_concurrent_main_advance_stops_before_the_public_mutation(self):
        server = Server(); server.race = True
        with self.context(server), self.assertRaisesRegex(ValueError, "main advanced"):
            docs.sync_published(VERSION, apply=True)
        self.assertFalse(any(method == "PATCH" for _, _, method in server.writes))

    def test_newer_latest_before_commit_prevents_rollback(self):
        server = Server()
        with self.context(server), patch.object(docs, "published_version", side_effect=[
                {"metadata": server.metadata, "source": server.source}, ValueError("latest changed")]):
            with self.assertRaisesRegex(ValueError, "latest changed"): docs.sync_published(VERSION, apply=True)
        self.assertFalse(any(method == "PATCH" for _, _, method in server.writes))


class BaselineReviewTest(unittest.TestCase):
    def test_legacy_notes_baseline_uses_the_published_tag_without_modern_assets(self):
        record = {"tag_name":"r14.24.1", "draft":False, "prerelease":False, "assets":[]}
        with patch.object(release,"api",side_effect=[record,{"object":{"type":"tag","sha":"b"*40}},{"object":{"type":"commit","sha":SHA}}]), \
             patch.object(docs,"source_text",return_value='val lastVersion = 245\nval lastVersionName = "r14.24.1"\n') as source:
            result=docs.notes_baseline("r14.24.1")
            self.assertEqual({"version_name":"r14.24.1","version_code":245,"source_sha":SHA},result["metadata"])
            source.assert_called_once_with("app/build.gradle.kts",SHA)

    def test_notes_baseline_rejects_unpublished_or_mismatched_source(self):
        record={"tag_name":"r14.24.1","draft":False,"prerelease":False}
        for changed in ({"draft":True},{"prerelease":True},{"tag_name":"r14.24.2"}):
            with self.subTest(changed=changed),patch.object(release,"api",return_value=dict(record,**changed)),self.assertRaisesRegex(ValueError,"actually published"):
                docs.notes_baseline("r14.24.1")
        with patch.object(release,"api",side_effect=[record,{"object":{"type":"commit","sha":SHA}}]), \
             patch.object(docs,"source_text",return_value='val lastVersion = 246\nval lastVersionName = "r14.25.1"\n'),self.assertRaisesRegex(ValueError,"disagree"):
            docs.notes_baseline("r14.24.1")

    def test_cumulative_notes_and_published_safety_baselines_coexist_without_removing_history(self):
        latest={"metadata":{"version_name":"r14.25.3","version_code":248,"source_sha":"b"*40},"source":{"html_url":"https://example.test/stable"}}
        notes={"metadata":{"version_name":"r14.24.1","version_code":245,"source_sha":"a"*40},"source":{"html_url":"https://example.test/notes-base"}}
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)
            for filename,language in release.CURRENT_CHANGELOGS:
                marker="自 r14.24.1 起的变更：" if language=="简体中文" else "Changes since r14.24.1:"
                (root/filename).write_text(f"## r14.25.5\n\nversionCode 254. {marker}\n\n- Reviewed cumulative change.\n\n## r14.25.3\n\nversionCode 248.\n\n## r14.25.1\n\nversionCode 246.\n\n## r14.24.1\n\nversionCode 245.\n",encoding="utf-8")
            with patch.object(release,"ROOT",root),patch.dict(release.POLICY,cumulative_releases={"r14.25.5":{"notes_base":"r14.24.1"}}),patch.object(docs,"published_version",return_value=latest) as stable,patch.object(docs,"notes_baseline",return_value=notes) as historical,patch.object(release,"run",return_value="M\tapp/Actual.kt") as command:
                result=docs.review_candidate("r14.25.5",254,"f"*40)
                self.assertIn("Published safety baseline: [r14.25.3]",result)
                self.assertIn("Cumulative notes baseline: [r14.24.1]",result)
                stable.assert_called_once_with(require_download=False);historical.assert_called_once_with("r14.24.1")
                self.assertIn((("git","merge-base","--is-ancestor","a"*40,"b"*40),),command.call_args_list)
                self.assertIn((("git","merge-base","--is-ancestor","b"*40,"f"*40),),command.call_args_list)
                self.assertEqual(("git","diff","--name-status","a"*40,"f"*40,"--"),command.call_args.args)
                for path in root.glob("*.md"):
                    self.assertIn("## r14.25.1",path.read_text(encoding="utf-8"));self.assertIn("## r14.25.3",path.read_text(encoding="utf-8"))
                with self.assertRaisesRegex(ValueError,"versionCode"):docs.review_candidate("r14.25.5",248,"f"*40)
                path=root/"CHANGELOG_EN.md";path.write_text(path.read_text(encoding="utf-8").replace("Changes since r14.24.1:","Changes since r14.25.3:"))
                with self.assertRaisesRegex(ValueError,"designated notes baseline"):docs.review_candidate("r14.25.5",254,"f"*40)

    def test_historical_baseline_is_source_only_and_does_not_change_latest_document_gate(self):
        server = Server()
        def endpoint(path, **kwargs):
            if path == f"repos/{release.REPO}/releases/tags/{VERSION}": return server.source
            return server.api(path, **kwargs)
        with DownloadDocumentsTest().context(server), patch.object(release, "api", side_effect=endpoint):
            self.assertEqual(VERSION, docs.published_version(VERSION, require_download=False, latest_only=False)["metadata"]["version_name"])
            with self.assertRaisesRegex(ValueError, "explicit source"):
                docs.published_version(VERSION, latest_only=False)
            with self.assertRaisesRegex(ValueError, "latest published"):
                docs.published_version("r14.25.1")

    def test_complete_diff_uses_the_actual_published_sha_and_rejects_stale_wording(self):
        server = Server()
        published = {"metadata": server.metadata, "source": server.source}
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            for filename, language in release.CURRENT_CHANGELOGS:
                marker = "自 r14.25.3 起的变更：" if language == "简体中文" else "Changes since r14.25.3:"
                (root / filename).write_text(f"## r14.25.4\n\nversionCode 249. {marker}\n\n- Reviewed user change.\n", encoding="utf-8")
            with patch.object(release, "ROOT", root), patch.object(docs, "published_version", return_value=published), patch.object(release, "run", return_value="M\tapp/Actual.kt\nD\tapp/Removed.kt") as command:
                summary = docs.review_candidate("r14.25.4", 249, "f" * 40)
                self.assertIn(SHA, summary); self.assertIn("Removed.kt", summary)
                self.assertEqual(("git", "diff", "--name-status", SHA, "f" * 40, "--"), command.call_args.args)
                for filename, _ in release.CURRENT_CHANGELOGS:
                    path = root / filename
                    path.write_text(path.read_text(encoding="utf-8").replace("r14.25.3", "r14.25.1"), encoding="utf-8")
                with self.assertRaisesRegex(ValueError, "last successfully published"):
                    docs.review_candidate("r14.25.4", 249, "f" * 40)

    def test_document_retry_rejects_an_untrusted_native_workflow_without_an_approval_token(self):
        for ref in ("refs/heads/untrusted","refs/heads/main"):
            env={"GITHUB_ACTIONS":"true","GITHUB_REPOSITORY":release.REPO,"GITHUB_EVENT_NAME":"workflow_dispatch",
                 "GITHUB_REF":ref,"GITHUB_SHA":SHA,"GITHUB_WORKFLOW_REF":"attacker/untrusted.yml@refs/heads/main"}
            with patch.dict(os.environ,env),patch("sys.argv",["signed_release.py","sync-docs","--version",VERSION,"--apply"]),patch.object(docs,"sync_published") as sync:
                self.assertEqual(1,release.main());sync.assert_not_called()
