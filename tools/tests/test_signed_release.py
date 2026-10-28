"""Release trust boundaries, APK validation and workflow regression tests."""
import base64
import copy
import hashlib
import json
import io
from types import SimpleNamespace
import os
import re
import tempfile
import unittest
import zipfile
from contextlib import ExitStack
from pathlib import Path
from unittest.mock import patch

from tools import ci_contract_scan, signed_release as release

REPO_ROOT = Path(__file__).resolve().parents[2]
COMMIT = "12345678" + "a" * 32
REF = "refs/heads/main"
ENV = {"GITHUB_REPOSITORY": release.REPO, "GITHUB_EVENT_NAME": "workflow_dispatch",
       "GITHUB_REF": REF, "GITHUB_SHA": COMMIT, "GITHUB_RUN_ID": "42", "GITHUB_RUN_ATTEMPT": "1",
       "GITHUB_EVENT_PATH": ""}
BADGING = """package: name='tv.withaibuild.customiuizer.r14' versionCode='245' versionName='r14.24.1' platformBuildVersionName='x'
sdkVersion:'34'
targetSdkVersion:'34'
native-code: 'arm64-v8a'
"""
SIGNATURE = f"Verified using v2 scheme (APK Signature Scheme v2): true\nSigner #1 certificate SHA-256 digest: {release.CERT}\n"


def setUpModule():
    # These API/asset fixtures model the published r14.25.5 release. Keep that
    # identity explicit so advancing the repository target does not rewrite them.
    global historical_target
    historical_target = patch.dict(release.POLICY, release_target={
        "version_name": "r14.25.5", "minimum_version_code": 254})
    historical_target.start()


def tearDownModule():
    historical_target.stop()


def build_run():
    return {"id": 42, "run_attempt": 1, "workflow_id": 7, "path": release.WORKFLOW,
            "head_repository": {"full_name": release.REPO}, "head_sha": COMMIT,
            "head_branch": "main", "status": "completed", "conclusion": "success", "event": "workflow_dispatch"}


def ci_run():
    return dict(build_run(), id=100, workflow_id=8, name="A14 CI", path=".github/workflows/a14-ci.yml", event="push")


class AutomaticReleaseTest(unittest.TestCase):
    def setUp(self):
        source = patch.object(release, "source_version", return_value=("r14.25.5", 254))
        source.start()
        self.addCleanup(source.stop)

    def environment(self, path, record=None):
        path.write_text(json.dumps({"action": "completed", "workflow_run": record or ci_run()}))
        return dict(ENV, GITHUB_EVENT_NAME="workflow_run", GITHUB_EVENT_PATH=str(path),
                    GITHUB_WORKFLOW_REF=f"{release.REPO}/{release.WORKFLOW}@{REF}", GITHUB_WORKFLOW_SHA=COMMIT)

    def test_main_ci_event_has_native_source_and_default_dual_publication(self):
        with tempfile.TemporaryDirectory() as temp:
            env = self.environment(Path(temp) / "event.json", dict(ci_run(), name="A14 CI / main"))
            self.assertEqual((REF, COMMIT), release.event_source(env))
            with patch.dict(os.environ, env):
                self.assertEqual(("both", False), release.dispatch_options())
                with patch.object(release, "api", side_effect=[{"id": 8}, ci_run()]):
                    self.assertEqual(100, release.automatic_ci()["id"])

    def test_failed_pr_fork_branch_and_stale_ci_cannot_reach_signing(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "event.json"
            for key, value in (("event", "pull_request"), ("head_branch", "other"), ("head_sha", "b" * 40),
                               ("head_repository", {"full_name": "attacker/fork"}), ("conclusion", "failure"), ("path", ".github/workflows/untrusted.yml")):
                env = self.environment(path, dict(ci_run(), **{key: value}))
                with self.subTest(key=key), self.assertRaisesRegex(ValueError, "upstream main CI"):
                    release.event_source(env)

    def test_upstream_ci_rerun_after_trigger_requires_a_fresh_source_check(self):
        with tempfile.TemporaryDirectory() as temp:
            env = self.environment(Path(temp) / "event.json")
            with patch.dict(os.environ, env), patch.object(release, "api", side_effect=[{"id": 8}, dict(ci_run(), run_attempt=2)]):
                with self.assertRaisesRegex(ValueError, "attempt/source changed"):
                    release.automatic_ci()

    def test_zero_approval_environments_still_require_exact_main_branch_restrictions(self):
        valid = {"protection_rules": [{"type": "branch_policy"}], "deployment_branch_policy": {"custom_branch_policies": True, "protected_branches": False}}
        branches = {"total_count": 1, "branch_policies": [{"name": "main", "type": "branch"}]}
        def read(path):
            return branches if "deployment-branch-policies" in path else valid
        with patch.object(release, "api", side_effect=read):
            release.environment_policy()
        for broken in ({}, dict(valid, protection_rules=[{"type": "required_reviewers", "reviewers": [1]}]),
                       dict(valid, protection_rules=[{"type": "wait_timer", "wait_timer": 1}]),
                       dict(valid, deployment_branch_policy=None)):
            with self.subTest(broken=broken), patch.object(release, "api", return_value=broken), self.assertRaises(ValueError):
                release.environment_policy()
        for entry in ({"name": "*", "type": "branch"}, {"name": "main", "type": "tag"}, {"name": "other", "type": "branch"}):
            with self.subTest(entry=entry), patch.object(release, "api", side_effect=[valid, {"total_count": 1, "branch_policies": [entry]}]), self.assertRaises(ValueError):
                release.environment_policy()

    def test_already_published_main_only_skips_without_signing_or_requesting_approval(self):
        with tempfile.TemporaryDirectory() as temp:
            env = self.environment(Path(temp) / "event.json")
            with patch.dict(os.environ, env), patch.object(release, "automatic_ci"), patch.object(release, "require_current_main"):
                with patch.object(release, "optional_api", return_value={"draft": False}), patch.object(release, "output") as output:
                    with patch.object(release, "environment_policy") as approval, patch.object(release, "check_new_release") as version:
                        release.release_plan()
                        self.assertEqual("false", output.call_args.kwargs["ready"])
                        approval.assert_not_called()
                        version.assert_not_called()

    def test_new_release_checks_environment_only_after_version_and_notes_pass(self):
        with tempfile.TemporaryDirectory() as temp:
            env = self.environment(Path(temp) / "event.json")
            env["GITHUB_STEP_SUMMARY"] = str(Path(temp) / "summary.md")
            with ExitStack() as stack:
                stack.enter_context(patch.dict(os.environ, env))
                for name in ("automatic_ci", "require_current_main", "check_new_release"):
                    stack.enter_context(patch.object(release, name))
                review = stack.enter_context(patch.object(release, "release_review", return_value="Published baseline and complete source comparison\n\n"))
                stack.enter_context(patch.object(release, "optional_api", return_value=None))
                notes = stack.enter_context(patch.object(release, "local_release_notes", return_value=["Reviewed changes"]))
                approval = stack.enter_context(patch.object(release, "environment_policy"))
                output = stack.enter_context(patch.object(release, "output"))
                release.release_plan()
                summary = Path(env["GITHUB_STEP_SUMMARY"]).read_text(encoding="utf-8")
                self.assertIn("Published baseline and complete source comparison", summary)
                self.assertIn("Reviewed changes", summary)
                review.assert_called_once()
                self.assertEqual(("true", "both"), (output.call_args.kwargs["ready"], output.call_args.kwargs["destination"]))
                approval.assert_called_once()
                approval.reset_mock()
                notes.side_effect = ValueError("Missing translation")
                with self.assertRaisesRegex(ValueError, "Missing translation"):
                    release.release_plan()
                approval.assert_not_called()

    def test_moved_main_and_pr_only_ci_are_rejected(self):
        with patch.object(release, "api", return_value={"object": {"sha": "b" * 40}}):
            with self.assertRaisesRegex(ValueError, "main moved"):
                release.require_current_main(COMMIT)
        with patch.object(release, "api", side_effect=[{"id": 8}, {"workflow_runs": [dict(ci_run(), event="pull_request")]}]):
            with self.assertRaisesRegex(ValueError, "successful A14 CI"):
                release.require_main_ci(COMMIT, current=False)

    def test_manual_publication_also_requires_exact_branch_environment_policy(self):
        with patch.dict(os.environ, ENV), ExitStack() as stack:
            stack.enter_context(patch.object(release, "dispatch_options", return_value=("both", False)))
            stack.enter_context(patch.object(release, "require_main_ci"))
            stack.enter_context(patch.object(release, "check_new_release"))
            stack.enter_context(patch.object(release, "local_release_notes", return_value=["Reviewed"]))
            stack.enter_context(patch.object(release, "release_review", return_value="Baseline review"))
            stack.enter_context(patch.object(release, "optional_api", return_value=None))
            approval = stack.enter_context(patch.object(release, "environment_policy", side_effect=ValueError("Invalid environment restrictions")))
            output = stack.enter_context(patch.object(release, "output"))
            with self.assertRaisesRegex(ValueError,"Invalid environment restrictions"):
                release.release_plan()
            output.assert_not_called()
            with self.assertRaisesRegex(ValueError,"Invalid environment restrictions"):
                release.signing_preflight()
            self.assertEqual(2, approval.call_count)

    def test_manual_already_published_version_skips_before_ci_or_approval(self):
        with patch.dict(os.environ, ENV), ExitStack() as stack:
            stack.enter_context(patch.object(release, "dispatch_options", return_value=("both", False)))
            stack.enter_context(patch.object(release, "optional_api", return_value={"draft": False}))
            ci = stack.enter_context(patch.object(release, "require_main_ci"))
            approval = stack.enter_context(patch.object(release, "environment_policy"))
            output = stack.enter_context(patch.object(release, "output"))
            release.release_plan()
            self.assertEqual("false", output.call_args.kwargs["ready"])
            ci.assert_not_called()
            approval.assert_not_called()

    def test_candidate_only_cannot_use_an_unprotected_environment(self):
        with patch.dict(os.environ, ENV), patch.object(release, "dispatch_options", return_value=("actions-only", False)):
            with patch.object(release, "environment_policy", side_effect=ValueError("Invalid environment restrictions")):
                with self.assertRaisesRegex(ValueError, "Invalid environment restrictions"):
                    release.release_plan()
                with self.assertRaisesRegex(ValueError, "Invalid environment restrictions"):
                    release.signing_preflight()

    def test_public_notes_hide_engineering_without_losing_the_cumulative_baseline(self):
        for filename, user, engineering in (("CHANGELOG.md", "用户变化", "工程说明"),
                                             ("CHANGELOG_EN.md", "User changes", "Engineering notes")):
            text = (f"## r14.25.3\n\nversionCode 248. Changes since r14.25.1:\n\n### {user}\n\nA concise overview.\n\n"
                    + "\n".join(f"- User change {i}." for i in range(5))
                    + f"\n\n### {engineering}\n\n- InternalHook details.\n")
            notes = release.public_release_notes_section(text, "r14.25.3", 248, filename)
            self.assertIn("Changes since r14.25.1:", notes)
            self.assertIn("A concise overview.", notes)
            self.assertEqual(5, notes.count("- User change"))
            self.assertNotIn("InternalHook", notes)
            self.assertNotIn("versionCode", notes)
            self.assertNotIn("r14.25.2", notes)
            with self.assertRaisesRegex(ValueError, "section pair"):
                release.public_release_notes_section(text.replace(f"### {engineering}", f"### {user}"), "r14.25.3", 248, filename)
            with self.assertRaisesRegex(ValueError, "reviewed user changes"):
                release.public_release_notes_section(text.replace("- User change", "User change"), "r14.25.3", 248, filename)
        legacy = "## r14.25.2\n\nversionCode 247.\n\n- Original release note.\n"
        self.assertEqual(release.release_notes_section(legacy, "r14.25.2", 247, "CHANGELOG_EN.md"),
                         release.public_release_notes_section(legacy, "r14.25.2", 247, "CHANGELOG_EN.md"))

    def test_changelog_release_notes_cannot_be_empty_ambiguous_or_wrong_version(self):
        for text in ("## r14.26.1\n\nversionCode 247.\n", "## r14.26.1\n\nversionCode 246.\n- Change\n",
                     "## r14.26.1\n\nversionCode 247.\n- Change\n\n## r14.26.1\n- Duplicate\n"):
            with self.subTest(text=text), self.assertRaises(ValueError):
                release.release_notes_section(text, "r14.26.1", 247, "CHANGELOG_EN.md")


class SourceTrustTest(unittest.TestCase):
    def test_native_branch_and_exact_sha_are_accepted(self):
        self.assertEqual((REF, COMMIT), release.event_source(ENV))

    def test_source_selector_inputs_are_rejected_even_if_they_match_the_native_commit(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "event.json"
            for key,value in (("ref", "main"), ("source_sha", COMMIT), ("display_sha", COMMIT[:8])):
                path.write_text(json.dumps({"inputs": {key:value}}))
                with self.subTest(key=key), self.assertRaisesRegex(ValueError, "Source selectors"):
                    release.event_source(dict(ENV, GITHUB_EVENT_PATH=str(path)))

    def test_native_release_options_cannot_change_checkout_source(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "event.json"
            for destination in ("both", "source", "actions-only"):
                path.write_text(json.dumps({"inputs":{"destination":destination,"prerelease":"true"}}))
                env=dict(ENV,GITHUB_EVENT_PATH=str(path),GITHUB_WORKFLOW_REF=f"{release.REPO}/{release.WORKFLOW}@{REF}",GITHUB_WORKFLOW_SHA=COMMIT)
                with patch.dict(os.environ,env):
                    self.assertEqual((REF,COMMIT),release.event_source())
                    self.assertEqual((destination,True),release.dispatch_options())

    def test_signing_rechecks_the_original_main_ci_without_a_second_dispatch(self):
        with patch.dict(os.environ,dict(ENV,GITHUB_EVENT_NAME="workflow_run")), ExitStack() as stack:
            stack.enter_context(patch.object(release,"dispatch_options",return_value=("both",False)))
            stack.enter_context(patch.object(release,"event_source",return_value=(REF,COMMIT)))
            stack.enter_context(patch.object(release,"environment_policy"))
            ci=stack.enter_context(patch.object(release,"automatic_ci"))
            main=stack.enter_context(patch.object(release,"require_main_ci"))
            stack.enter_context(patch.object(release,"source_version",return_value=("r14.25.5",254)))
            for name in ("check_new_release","local_release_notes","release_review"):
                stack.enter_context(patch.object(release,name))
            command=stack.enter_context(patch.object(release,"run"))
            release.signing_preflight()
            ci.assert_called_once(); main.assert_called_once_with(COMMIT); command.assert_not_called()

    def test_pr_fork_tag_and_partial_sha_are_rejected(self):
        for field, value in (("GITHUB_EVENT_NAME", "pull_request"), ("GITHUB_REPOSITORY", "attacker/fork"),
                             ("GITHUB_REF", "refs/tags/r14.24.1"), ("GITHUB_SHA", COMMIT[:8])):
            with self.subTest(field=field), self.assertRaises(ValueError):
                release.event_source(dict(ENV, **{field: value}))

    def test_untrusted_branch_never_prepares_keys(self):
        with self.assertRaisesRegex(ValueError, "not authorized"):
            release.prepare_signing(dict(ENV, GITHUB_REF="refs/heads/untrusted"))

    def test_missing_secrets_fail_before_writing_files(self):
        with tempfile.TemporaryDirectory() as temp, patch.dict(os.environ, RUNNER_TEMP=temp):
            with self.assertRaisesRegex(ValueError, "Missing release-signing secrets"):
                release.prepare_signing(ENV)
            self.assertFalse((Path(temp) / "a14-signing").exists())

    def test_wrong_certificate_fails_without_properties(self):
        env = dict(ENV, A14_KEYSTORE_BASE64=base64.b64encode(b"fixture").decode(), A14_KEY_ALIAS="fixture",
                   A14_STORE_PASSWORD="fixture", A14_KEY_PASSWORD="fixture")
        with tempfile.TemporaryDirectory() as temp, patch.dict(os.environ, RUNNER_TEMP=temp):
            with patch.object(release.subprocess, "run") as command:
                command.return_value.returncode = 0
                command.return_value.stdout = b"wrong certificate"
                with self.assertRaisesRegex(ValueError, "historical"):
                    release.prepare_signing(env)
                self.assertFalse((Path(temp) / "a14-signing/keystore.properties").exists())

    def test_signing_properties_are_escaped_and_password_is_not_an_argument(self):
        env = dict(ENV, A14_KEYSTORE_BASE64=base64.b64encode(b"fixture").decode(), A14_KEY_ALIAS="fixture",
                   A14_STORE_PASSWORD=" secret:=\\\n中", A14_KEY_PASSWORD="fixture")
        with tempfile.TemporaryDirectory() as temp, patch.dict(os.environ, RUNNER_TEMP=temp):
            with patch.object(release.subprocess, "run") as command, patch.object(release, "CERT", hashlib.sha256(b"certificate").hexdigest()):
                command.return_value.returncode = 0
                command.return_value.stdout = b"certificate"
                release.prepare_signing(env)
                self.assertNotIn(env["A14_STORE_PASSWORD"], command.call_args.args[0])
                props = (Path(temp) / "a14-signing/keystore.properties").read_text(encoding="ascii")
                self.assertIn("storePassword=\\ secret\\:\\=\\\\\\n\\u4e2d\n", props)

    def test_only_successful_same_sha_upstream_ci_can_be_reused(self):
        record = dict(build_run(), path=".github/workflows/a14-ci.yml")
        self.assertTrue(release.trusted_ci(record, COMMIT, 7))
        for key, value in (("head_sha", "b" * 40), ("conclusion", "failure"), ("event", "pull_request"),
                           ("head_branch", "untrusted"), ("workflow_id", 8), ("head_repository", {"full_name": "attacker/fork"})):
            self.assertFalse(release.trusted_ci(dict(record, **{key: value}), COMMIT, 7))


class ApkTrustTest(unittest.TestCase):
    def test_candidate_uses_final_version_filename_without_online_proof_or_altering_binary(self):
        with tempfile.TemporaryDirectory() as temp, patch.dict(os.environ, ENV):
            root = Path(temp)
            output = root / "app/build/outputs"
            apk = output / "apk/release/original.apk"
            apk.parent.mkdir(parents=True)
            apk.write_bytes(b"original signed bytes")
            mapping = output / "mapping/release"
            mapping.mkdir(parents=True)
            for filename in ("mapping.txt", "configuration.txt", "usage.txt"):
                (mapping / filename).write_text("R8 output")
            (root / "app/build.gradle.kts").write_text('val lastVersionName = "r14.25.2"\nval lastVersion = 300\n')
            folder = root / "candidate"
            def verify(staged, sha, name, code):
                self.assertEqual((COMMIT, "r14.25.2", 300), (sha, name, code))
                return {"apk": staged.name, "apk_sha256": release.digest(staged)}
            with patch.object(release, "ROOT", root), patch.object(release, "verify_apk", side_effect=verify):
                release.candidate(folder)
            name = "CustoMIUIzer-A14-r14.25.2.apk"
            self.assertEqual([folder / name], list(folder.glob("*.apk")))
            self.assertEqual(apk.read_bytes(), (folder / name).read_bytes())
            metadata = json.loads((folder / "build-metadata.json").read_text())
            self.assertEqual(name, metadata["apk"])
            self.assertEqual(COMMIT, metadata["source_sha"])
            self.assertEqual([name, "build-metadata.json"], metadata["release_assets"])
            self.assertEqual(f"https://github.com/{release.REPO}/actions/runs/42/attempts/1", metadata["run_url"])
            self.assertIn(COMMIT, metadata["workflow_url"])
            self.assertNotIn("attestation", metadata)
            self.assertEqual({name, "build-metadata.json"}, {item.name for item in folder.iterdir()})

    def test_correct_manifest_and_historical_signature(self):
        release.check_badging(BADGING, "r14.24.1", 245)
        release.check_signature(SIGNATURE)

    def test_debuggable_wrong_package_sdk_abi_or_version_fail(self):
        for original, changed in (("r14.24.1", "r14.24.2"), (".r14'", ".r15'"), ("sdkVersion:'34'", "sdkVersion:'33'"),
                                  ("targetSdkVersion:'34'", "targetSdkVersion:'35'"), ("'arm64-v8a'", "'arm64-v8a' 'x86_64'")):
            with self.subTest(changed=changed), self.assertRaises(ValueError):
                release.check_badging(BADGING.replace(original, changed), "r14.24.1", 245)
        with self.assertRaises(ValueError):
            release.check_badging(BADGING + "application-debuggable\n", "r14.24.1", 245)

    def test_wrong_certificate_missing_v2_and_multiple_signers_fail(self):
        for signature in (SIGNATURE.replace(release.CERT, "a" * 64), SIGNATURE.replace(": true", ": false"),
                          SIGNATURE + f"Signer #2 certificate SHA-256 digest: {release.CERT}\n"):
            with self.assertRaises(ValueError):
                release.check_signature(signature)

    def test_embedded_provenance_cannot_claim_another_commit(self):
        with tempfile.TemporaryDirectory() as temp, patch.dict(os.environ, ANDROID_HOME=temp):
            apk = Path(temp) / "candidate.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("assets/build-provenance.properties", "revision=bbbbbbbb\nversionName=r14.24.1\nversionCode=245\nbuildType=release\n")
            with patch.object(release, "run", side_effect=[BADGING, "", SIGNATURE]), self.assertRaisesRegex(RuntimeError, "revision mismatch"):
                release.verify_apk(apk, COMMIT, "r14.24.1", 245)

    def test_short_revision_alone_is_insufficient_for_a_signed_candidate(self):
        with tempfile.TemporaryDirectory() as temp, patch.dict(os.environ, ANDROID_HOME=temp):
            apk = Path(temp) / "candidate.apk"
            props = f"revision={COMMIT[:8]}\nversionName=r14.24.1\nversionCode=245\nbuildType=release\n"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("assets/build-provenance.properties", props)
            with patch.object(release, "run", side_effect=[BADGING, "", SIGNATURE]), self.assertRaisesRegex(RuntimeError, "sourceCommit"):
                release.verify_apk(apk, COMMIT, "r14.24.1", 245)
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("assets/build-provenance.properties", props + f"sourceCommit={COMMIT}\n")
            with patch.object(release, "run", side_effect=[BADGING, "", SIGNATURE]):
                metadata = release.verify_apk(apk, COMMIT, "r14.24.1", 245)
                self.assertEqual(hashlib.sha256(apk.read_bytes()).hexdigest(), metadata["apk_sha256"])

    def test_candidate_tampering_is_rejected_before_android_verification(self):
        record=build_run()
        metadata={"repository":release.REPO,"workflow":release.WORKFLOW,"source_sha":COMMIT,"source_ref":REF,
                  "run_id":42,"run_attempt":1,"version_name":"r14.24.1","apk":"CustoMIUIzer-A14-r14.24.1.apk","apk_sha256":"a"*64}
        with tempfile.TemporaryDirectory() as temp:
            folder=Path(temp)/"download"
            def download(artifact,target):
                target.mkdir();(target/metadata["apk"]).write_bytes(b"tampered")
                release.write_json(target/"build-metadata.json",metadata)
            with patch.object(release,"api",side_effect=[{"id":7},record,{"artifacts":[{"id":123,"name":"a14-signed-42-1","expired":False}]}]),patch.object(release,"download_candidate",side_effect=download),patch.object(release,"verify_apk") as verify:
                with self.assertRaisesRegex(ValueError,"digest differs"):release.fetch_candidate("42",folder)
                verify.assert_not_called()


class ArtifactIntegrityTest(unittest.TestCase):
    def fixture(self, folder, legacy=None):
        name="CustoMIUIzer-A14-r14.24.1.apk" if legacy!="sha" else f"CustoMIUIzer-A14-r14.24.1-{COMMIT[:6]}.apk"
        (folder/name).write_bytes(b"original signed APK bytes")
        metadata={"repository":release.REPO,"workflow":release.WORKFLOW,"source_sha":COMMIT,"source_ref":REF,
                  "run_id":42,"run_attempt":1,"version_name":"r14.24.1","version_code":245,
                  "apk":name,"apk_sha256":release.digest(folder/name)}
        if legacy is None: metadata["release_assets"]=[name,"build-metadata.json"]
        release.write_json(folder/"build-metadata.json",metadata)
        if legacy:
            (folder/"SHA256SUMS").write_text(f"{metadata['apk_sha256']}  {name}\n",encoding="ascii")
            (folder/"attestation.sigstore.json").write_text("legacy evidence retained verbatim")
        return metadata

    def archive(self, entries):
        stream=io.BytesIO()
        with zipfile.ZipFile(stream,"w") as archive:
            for name,value in entries: archive.writestr(name,value)
        return stream.getvalue()

    def download(self, payload, *, digest=None):
        artifact={"id":123,"size_in_bytes":len(payload),"digest":digest or "sha256:"+hashlib.sha256(payload).hexdigest()}
        def command(args,**kwargs):
            self.assertEqual(["gh","api",f"repos/{release.REPO}/actions/artifacts/123/zip"],args)
            kwargs["stdout"].write(payload)
            return SimpleNamespace(returncode=0)
        return artifact,patch.object(release.subprocess,"run",side_effect=command)

    def test_download_is_bound_to_exact_artifact_id_and_archive_digest(self):
        payload=self.archive([("build-metadata.json",b"{}")])
        artifact,command=self.download(payload)
        with tempfile.TemporaryDirectory() as temp,command:
            target=Path(temp)/"candidate"
            release.download_candidate(artifact,target)
            self.assertEqual(b"{}",(target/"build-metadata.json").read_bytes())
        artifact,command=self.download(payload,digest="sha256:"+"a"*64)
        with tempfile.TemporaryDirectory() as temp,command,self.assertRaisesRegex(ValueError,"archive bytes differ"):
            release.download_candidate(artifact,Path(temp)/"candidate")

    def test_missing_digest_or_oversize_archive_never_downloads(self):
        for artifact in ({"id":123,"size_in_bytes":1}, {"id":123,"digest":"sha256:"+"a"*64,"size_in_bytes":33*1024*1024}):
            with self.subTest(artifact=artifact),patch.object(release.subprocess,"run") as command,self.assertRaises(ValueError):
                release.download_candidate(artifact,Path("unused"))
            command.assert_not_called()

    def test_zip_traversal_symlinks_and_entry_growth_are_rejected_before_extraction(self):
        link=zipfile.ZipInfo("link");link.create_system=3;link.external_attr=0o120777<<16
        for entries in ([('../escape',b'x')],[(link,b'target')],[(f'{i}.txt',b'x') for i in range(17)]):
            payload=self.archive(entries);artifact,command=self.download(payload)
            with tempfile.TemporaryDirectory() as temp,command,self.assertRaisesRegex(ValueError,"archive"):
                target=Path(temp)/"candidate"
                try: release.download_candidate(artifact,target)
                finally: self.assertFalse(target.exists())

    def test_original_and_new_candidates_keep_metadata_unchanged_without_online_proof(self):
        for legacy in (None,"sha","plain"):
            with self.subTest(legacy=legacy),tempfile.TemporaryDirectory() as temp:
                root=Path(temp);original=root/"original";original.mkdir()
                metadata=self.fixture(original,legacy)
                expected={item.name:item.read_bytes() for item in original.iterdir()}
                payload=self.archive(list(expected.items()));artifact,command=self.download(payload)
                artifact.update(name="a14-signed-42-1",expired=False)
                source={"content":base64.b64encode(b'val lastVersionName = "r14.24.1"\nval lastVersion = 245\n').decode()}
                records=[{"id":7},build_run(),{"artifacts":[artifact]},source,build_run()]
                checked={"apk":metadata["apk"],"apk_sha256":metadata["apk_sha256"]}
                with patch.object(release,"api",side_effect=records),command,patch.object(release,"verify_apk",return_value=checked) as verify:
                    fetched=release.fetch_candidate("42",root/"download")
                    verify.assert_called_once_with(root/"download"/metadata["apk"],COMMIT,"r14.24.1",245)
                self.assertEqual(123,fetched["artifact_id"])
                for name,data in expected.items(): self.assertEqual(data,(root/"download"/name).read_bytes())
                self.assertNotIn("artifact_id",json.loads((root/"download/build-metadata.json").read_text()))


class PromotionTrustTest(unittest.TestCase):
    def test_only_a_failed_publisher_after_successful_signing_may_recover_its_original_artifact(self):
        record = dict(build_run(), conclusion="failure")
        valid = {"total_count": 3, "jobs": [{"name": name, "status": "completed", "conclusion": conclusion}
                 for name, conclusion in (("Validate exact source", "success"), ("Build, sign and verify Release", "success"),
                                          ("Publish verified APK to Release", "failure"))]}
        with tempfile.TemporaryDirectory() as temp:
            with_request = copy.deepcopy(valid)
            with_request["total_count"] += 1
            with_request["jobs"].append({"name": "Resolve short SHA and request signing", "status": "completed", "conclusion": "skipped"})
            for jobs in (valid, with_request):
                with patch.object(release, "api", side_effect=[{"id": 7}, record, jobs, {"artifacts": []}]):
                    with self.assertRaisesRegex(ValueError, "missing, expired"):
                        release.fetch_candidate("42", Path(temp) / "candidate")
            with_request["jobs"][-1]["conclusion"] = "success"
            with patch.object(release, "api", side_effect=[{"id": 7}, record, with_request]):
                with self.assertRaisesRegex(ValueError, "did not finish successfully"):
                    release.fetch_candidate("42", Path(temp) / "candidate")
            for index in (0, 1, 2):
                jobs = copy.deepcopy(valid)
                jobs["jobs"][index]["conclusion"] = "failure" if index < 2 else "cancelled"
                with patch.object(release, "api", side_effect=[{"id": 7}, record, jobs]) as api:
                    with self.subTest(index=index), self.assertRaisesRegex(ValueError, "did not finish successfully"):
                        release.fetch_candidate("42", Path(temp) / "candidate")
                    self.assertEqual(3, api.call_count)

    def test_completed_automatic_candidates_are_allowed_only_from_main(self):
        record = dict(build_run(), event="workflow_run")
        release.check_run(record, 7)
        with self.assertRaisesRegex(ValueError, "main CI release"):
            release.check_run(dict(record, head_branch="codex/a14-feature-refinement-20261019"), 7)

    def test_current_run_is_allowed_only_with_exact_native_run_attempt_and_source(self):
        record = dict(build_run(), status="in_progress", conclusion=None)
        with patch.dict(os.environ, ENV):
            release.check_run(record, 7, current_run=True)
            with self.assertRaisesRegex(ValueError, "did not finish"):
                release.check_run(record, 7)
            for field, value in (("id", 43), ("run_attempt", 2), ("head_sha", "b" * 40), ("head_branch", "other")):
                with self.subTest(field=field), self.assertRaisesRegex(ValueError, "another run"):
                    release.check_run(dict(record, **{field: value}), 7, current_run=True)

    def test_native_dispatch_options_cannot_come_from_another_workflow(self):
        with tempfile.TemporaryDirectory() as temp:
            event = Path(temp) / "event.json"
            event.write_text(json.dumps({"inputs": {"destination": "source", "prerelease": "false"}}))
            env = dict(ENV, GITHUB_EVENT_PATH=str(event), GITHUB_WORKFLOW_REF=f"{release.REPO}/{release.WORKFLOW}@{REF}", GITHUB_WORKFLOW_SHA=COMMIT)
            with patch.dict(os.environ, env):
                self.assertEqual(("source", False), release.dispatch_options())
                with patch.dict(os.environ, GITHUB_WORKFLOW_SHA="b" * 40), self.assertRaisesRegex(ValueError, "native signed-build"):
                    release.dispatch_options()
                event.write_text(json.dumps({"inputs": {"destination": "source", "prerelease": "invalid"}}))
                with self.assertRaisesRegex(ValueError, "pre-release"):
                    release.dispatch_options()

    def test_actions_only_never_fetches_or_publishes(self):
        with patch.object(release, "dispatch_options", return_value=("actions-only", False)):
            with patch.object(release, "fetch_candidate") as fetch, patch.object(release, "publish_verified") as publish:
                with self.assertRaisesRegex(ValueError, "did not authorize"):
                    release.publish_current(Path("candidate"))
                fetch.assert_not_called()
                publish.assert_not_called()

    def test_automatic_publication_binds_sign_receipt_without_selecting_another_run(self):
        metadata = {"version_name": "r14.25.5", "version_code": 254, "source_sha": COMMIT, "apk_sha256": "c" * 64, "run_attempt": 1, "artifact_id": 123}
        env = dict(ENV, GITHUB_JOB="publish", **{"VERIFIED_" + key.upper(): str(value) for key, value in metadata.items()})
        for prerelease in (False, True):
            with patch.dict(os.environ, env), patch.object(release, "dispatch_options", return_value=("source", prerelease)):
                with patch.object(release, "fetch_candidate", return_value=metadata) as fetch, patch.object(release, "publish_verified") as publish:
                    release.publish_current(Path("candidate"))
                    fetch.assert_called_once_with("42", Path("candidate"), current_run=True)
                    publish.assert_called_once_with(Path("candidate"), "42", "source", metadata, prerelease=prerelease)
                    publish.reset_mock()
                    with patch.dict(os.environ, VERIFIED_ARTIFACT_ID="124"), self.assertRaisesRegex(ValueError, "artifact changed"):
                        release.publish_current(Path("candidate"))
                    publish.assert_not_called()

    def test_source_workflow_and_failed_build_are_rejected(self):
        release.check_run(build_run(), 7)
        for field, value in (("path", ".github/workflows/a14-ci.yml"), ("workflow_id", 8), ("status", "in_progress"),
                             ("conclusion", "failure"), ("event", "push"), ("head_branch", "untrusted")):
            with self.subTest(field=field), self.assertRaises(ValueError):
                release.check_run(dict(build_run(), **{field: value}), 7)

    def test_wrong_run_or_missing_artifact_is_not_rebuilt(self):
        with tempfile.TemporaryDirectory() as temp, patch.object(release, "api", side_effect=[{"id": 7}, build_run(), {"artifacts": []}]):
            with patch.object(release, "run") as command, self.assertRaisesRegex(ValueError, "missing, expired"):
                release.fetch_candidate("42", Path(temp) / "candidate")
            command.assert_not_called()

    def test_metadata_must_bind_every_original_source_and_run_identity(self):
        meta={"repository":release.REPO,"workflow":release.WORKFLOW,"source_sha":COMMIT,"source_ref":REF,
              "run_id":42,"run_attempt":1,"version_name":"r14.24.1","apk":"CustoMIUIzer-A14-r14.24.1.apk"}
        release.check_metadata(meta,build_run())
        for key,value in (("repository","attacker/fork"),("workflow","untrusted.yml"),("source_sha","b"*40),
                          ("source_ref","refs/heads/other"),("run_id",43),("run_attempt",2)):
            with self.subTest(key=key),self.assertRaisesRegex(ValueError,"selected workflow run"):
                release.check_metadata(dict(meta,**{key:value}),build_run())

    def test_metadata_must_match_original_run_and_safe_filename(self):
        meta = {"repository": release.REPO, "workflow": release.WORKFLOW, "source_sha": COMMIT, "source_ref": REF,
                "run_id": 42, "run_attempt": 1, "version_name": "r14.24.1", "apk": "CustoMIUIzer-A14-r14.24.1.apk"}
        release.check_metadata(meta, build_run())
        release.check_metadata(dict(meta, apk=f"CustoMIUIzer-A14-r14.24.1-{COMMIT[:6]}.apk"), build_run())
        new = dict(meta, release_assets=[meta["apk"], "build-metadata.json"])
        release.check_metadata(new, build_run())
        for changed in (dict(new, apk=f"CustoMIUIzer-A14-r14.24.1-{COMMIT[:6]}.apk"),
                        dict(new, release_assets=[meta["apk"], "build-metadata.json", "SHA256SUMS"]),
                        dict(new, release_assets=["other.apk", "build-metadata.json"])):
            with self.assertRaisesRegex(ValueError, "public release asset contract"):
                release.check_metadata(changed, build_run())
        for field, value in (("apk", "../unexpected.apk"), ("run_id", 43), ("source_sha", "b" * 40)):
            with self.assertRaises(ValueError):
                release.check_metadata(dict(meta, **{field: value}), build_run())
        for filename in ("CustoMIUIzer-A14-r14.24.1-aaaaaa.apk", f"CustoMIUIzer-A14-r14.24.2-{COMMIT[:6]}.apk"):
            with self.assertRaisesRegex(ValueError, "filename"):
                release.check_metadata(dict(meta, apk=filename), build_run())

    def test_conflicting_tag_cannot_be_moved(self):
        with patch.object(release, "optional_api", return_value={"object": {"type": "commit", "sha": "b" * 40}}):
            with self.assertRaisesRegex(ValueError, "another source commit"):
                release.check_tag(release.REPO, "r14.24.1", COMMIT)

    def test_promotion_cannot_switch_to_a_rerun_or_replacement_artifact(self):
        metadata = {"source_sha": COMMIT, "apk_sha256": "c" * 64, "run_attempt": 1, "artifact_id": 123}
        receipt = {"VERIFIED_" + key.upper(): str(value) for key, value in metadata.items()}
        release.check_verification_receipt(metadata, receipt)
        for key, value in (("source_sha", "b" * 40), ("apk_sha256", "b" * 64), ("run_attempt", 2), ("artifact_id", 124)):
            with self.assertRaisesRegex(ValueError, "artifact changed"):
                release.check_verification_receipt(dict(metadata, **{key: value}), receipt)
        with self.assertRaisesRegex(ValueError, "receipt is missing"):
            release.check_verification_receipt(metadata, {})

    def test_recovery_uses_native_inputs_and_no_human_authorization_field(self):
        metadata={"version_name":"r14.25.5","version_code":254,"source_sha":COMMIT,"apk_sha256":"c"*64,"run_attempt":1,"artifact_id":123}
        with tempfile.TemporaryDirectory() as temp:
            path=Path(temp)/"event.json"
            env=dict(ENV,GITHUB_EVENT_PATH=str(path),GITHUB_WORKFLOW_REF=f"{release.REPO}/.github/workflows/a14-promote-release.yml@{REF}",GITHUB_WORKFLOW_SHA=COMMIT,
                     **{"VERIFIED_"+key.upper():str(metadata[key]) for key in ("source_sha","apk_sha256","run_attempt","artifact_id")})
            original={"build_run_id":"42","destination":"source","prerelease":"false"}
            for changed in (original,dict(original,build_run_id="43"),dict(original,destination="both"),dict(original,prerelease="true")):
                path.write_text(json.dumps({"inputs":changed}))
                with patch.dict(os.environ,env),patch.object(release,"fetch_candidate",return_value=metadata),patch.object(release,"publish_verified") as publish:
                    if changed==original:
                        release.publish(Path("candidate"),"42","source");publish.assert_called_once()
                    else:
                        with self.assertRaisesRegex(ValueError,"Recovery options"):release.publish(Path("candidate"),"42","source")
                        publish.assert_not_called()


class PublicationTest(unittest.TestCase):
    def test_version_code_and_name_must_exceed_published_prereleases_too(self):
        content = {"content": base64.b64encode(b'val lastVersionName = "r14.25.2"\nval lastVersion = 247\n').decode()}
        with patch.object(release, "check_tag"), patch.object(release, "optional_api", return_value=None):
            for name, code in (("r14.25.3", 247), ("r14.25.1", 248)):
                with patch.object(release, "api", side_effect=[[{"draft": False, "prerelease": True, "tag_name": "r14.25.2"}], content]):
                    with self.assertRaisesRegex(ValueError, "must exceed"):
                        release.check_new_release(name, code, COMMIT)

    def test_version_check_pages_through_releases_and_rejects_existing_drafts(self):
        older = [{"draft": False, "tag_name": "r14.24.1"}] * 100
        draft = {"draft": True, "tag_name": "r14.25.2"}
        with patch.object(release, "check_tag"), patch.object(release, "optional_api", return_value=None):
            with patch.object(release, "api", side_effect=[older, [draft]]) as api:
                with self.assertRaisesRegex(ValueError, "draft.*already exists"):
                    release.check_new_release("r14.25.2", 247, COMMIT)
                self.assertIn("page=2", api.call_args.args[0])

    def test_draft_assets_are_verified_before_publication_and_prereleases_never_become_latest(self):
        for prerelease, tamper in ((False, False), (True, False), (False, True)):
            with self.subTest(prerelease=prerelease, tamper=tamper), tempfile.TemporaryDirectory() as temp:
                folder = Path(temp)
                apk = release.candidate_filename("r14.25.2", COMMIT)
                filenames = (apk, "build-metadata.json")
                for filename in filenames:
                    (folder / filename).write_bytes(filename.encode())
                metadata = {"version_name": "r14.25.2", "version_code": 247, "source_sha": COMMIT,
                            "source_ref": REF, "apk": apk, "apk_sha256": release.digest(folder / apk)}
                assets = [{"name": filename, "digest": "sha256:" + release.digest(folder / filename), "size": (folder / filename).stat().st_size} for filename in filenames]
                if tamper:
                    assets[-1]["digest"] = "sha256:" + "a" * 64
                records = {"id": 99, "tag_name": "r14.25.2", "target_commitish": COMMIT, "draft": True, "prerelease": prerelease, "assets": assets,
                           "html_url": "https://github.com/example/release", "name": "r14.25.2"}
                writes = []
                def command(*args, **kwargs):
                    if args[:3] == ("gh", "release", "upload"):
                        writes.append("upload")
                        return ""
                    if args[:3] == ("gh", "release", "edit"):
                        self.assertEqual(["create", "upload", "check-draft"], writes)
                        self.assertIn(f"--latest={str(not prerelease).lower()}", args)
                        writes.append("publish")
                        return ""
                    self.assertEqual(("gh", "api"), args[:2])
                    if "POST" in args:
                        request = json.loads((folder / "release-request.json").read_text())
                        self.assertEqual((True, prerelease, COMMIT), (request["draft"], request["prerelease"], request["target_commitish"]))
                        self.assertEqual("r14.25.2", request["name"])
                        self.assertIn("## English", request["body"])
                        self.assertNotIn("gh attestation", request["body"]); self.assertIn("APK SHA-256:", request["body"]); self.assertIn(release.CERT, request["body"])
                        self.assertIn(metadata["apk_sha256"], request["body"])
                        records["body"] = request["body"]
                        writes.append("create")
                        return json.dumps(dict(records, assets=[]))
                    if args[2].endswith("/99"):
                        writes.append("check-draft")
                        return json.dumps(records)
                    if args[2].endswith("/releases/latest"):
                        self.assertFalse(prerelease)
                        return json.dumps({"id": 99})
                    self.assertIn("publish", writes)
                    return json.dumps(dict(records, draft=False))
                notes = {"content": base64.b64encode(b"## r14.25.2\n\nversionCode 247.\n\n- Changes.\n").decode()}
                with patch.object(release, "check_new_release"), patch.object(release, "check_tag") as tag:
                    with patch.object(release, "find_release", return_value=None), patch.object(release, "api", side_effect=lambda endpoint, **_: [{"name": filename, "type": "file"} for filename, _ in release.CURRENT_CHANGELOGS] if "/contents?ref=" in endpoint else notes):
                        with patch.object(release, "run", side_effect=command), patch.object(release, "require_main_ci"), patch.object(release, "require_current_main"), patch.object(release, "release_review"):
                            if tamper:
                                with self.assertRaisesRegex(ValueError, "digest/size differs"):
                                    release.publish_verified(folder, "42", "source", metadata)
                                self.assertNotIn("publish", writes)
                            else:
                                release.publish_verified(folder, "42", "source", metadata, prerelease=prerelease)
                                self.assertEqual(["create", "upload", "check-draft", "publish"], writes)
                                self.assertTrue(tag.call_args.kwargs["must_exist"])

    def test_release_assets_must_include_exactly_the_declared_files_without_duplicates(self):
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp)
            (folder / "test.apk").write_bytes(b"original")
            asset = {"name": "test.apk", "size": 8, "digest": "sha256:" + release.digest(folder / "test.apk")}
            record = {"draft": True, "prerelease": False, "assets": [asset]}
            release.check_release_assets(record, folder, ("test.apk",), draft=True, prerelease=False)
            with self.assertRaisesRegex(ValueError, "exactly"):
                release.check_release_assets(dict(record, assets=[asset, asset]), folder, ("test.apk",), draft=True, prerelease=False)
            with self.assertRaisesRegex(ValueError, "missing"):
                with patch.object(release, "optional_api", return_value=None):
                    release.check_tag(release.REPO, "r14.25.2", COMMIT, must_exist=True)

    def test_draft_source_cannot_be_changed_before_publication(self):
        record = {"id": 99, "tag_name": "r14.25.2", "target_commitish": COMMIT}
        release.check_release_identity(record, 99, "r14.25.2", COMMIT)
        for key, value in (("id", 100), ("tag_name", "r14.25.3"), ("target_commitish", "b" * 40)):
            with self.subTest(key=key), self.assertRaisesRegex(ValueError, "source identity changed"):
                release.check_release_identity(dict(record, **{key: value}), 99, "r14.25.2", COMMIT)


class ReleaseServer:
    """Small stateful GitHub double: release writes, partial upload and readback."""
    download = release.POLICY["download_repository"]

    def __init__(self, folder):
        self.folder = folder
        self.records = {}
        self.events = []
        self.fail_download_upload = False
        self.tamper_source_readback = False
        self.tamper_content_at = None
        self.fail_event = None
        self.notes = {"content": base64.b64encode(b"## r14.26.1\n\nversionCode 247.\n\n- Reviewed change.\n").decode()}
        self.source_files = ["CHANGELOG.md", "CHANGELOG_EN.md"]
        self.source_notes = {}
        self.source_reads = []

    def readback(self, record, phase, repository):
        record = copy.deepcopy(record)
        if self.tamper_content_at and self.tamper_content_at[:2] == (phase, repository):
            record[self.tamper_content_at[2]] += " Unexpected edit"
        return json.dumps(record)

    def event(self, *event):
        self.events.append(event)
        if event[:2] == self.fail_event:
            self.fail_event = None
            raise RuntimeError("Simulated publication interruption")

    def token(self, repository, env):
        if repository == self.download:
            if not env or env.get("GH_TOKEN") != "test-xposed-token":
                raise AssertionError("Every Xposed read/write must use the separate token")

    def api(self, endpoint, *, env=None, **fields):
        if "/contents?ref=" in endpoint:
            self.source_reads.append(endpoint)
            return [{"name": filename, "type": "file"} for filename in self.source_files]
        if "/contents/" in endpoint:
            self.source_reads.append(endpoint)
            filename = endpoint.split("/contents/", 1)[1].split("?", 1)[0]
            return self.source_notes.get(filename, self.notes)
        if "/releases?" in endpoint:
            repository = endpoint.split("/releases?", 1)[0].removeprefix("repos/")
            self.token(repository, env)
            if repository in self.records and not self.records[repository]["draft"]:
                self.event("existing-readback", repository)
            return [copy.deepcopy(self.records[repository])] if repository in self.records else []
        if endpoint.endswith("/releases/latest"):
            repository = endpoint.removesuffix("/releases/latest").removeprefix("repos/")
            self.token(repository, env)
            self.event("latest", repository)
            return copy.deepcopy(self.records[repository])
        raise AssertionError(endpoint)

    def command(self, *args, env=None):
        if args[:3] == ("gh", "api", f"repos/{self.download}"):
            self.token(self.download, env)
            return json.dumps({"permissions": {"push": True}})
        if args[0] == "git":
            self.event("git", self.download)
            if not any((kind, release.REPO) in self.events for kind in ("published-readback", "existing-readback")):
                raise AssertionError("Xposed tag write occurred before source verification")
            return ""
        if args[:2] == ("gh", "api"):
            endpoint = args[2]
            repository = endpoint.split("/releases", 1)[0].removeprefix("repos/")
            self.token(repository, env)
            if "POST" in args:
                record = json.loads((self.folder / "release-request.json").read_text())
                record.update(id=100 if repository == self.download else 99, assets=[], html_url=f"https://github.com/{repository}/releases/tag/{record['tag_name']}")
                self.records[repository] = record
                self.event("create", repository)
                return self.readback(record, "create", repository)
            record = copy.deepcopy(self.records[repository])
            if endpoint.endswith("/latest"):
                self.event("latest", repository)
            elif "/tags/" in endpoint:
                self.event("published-readback", repository)
            else:
                self.event("draft-readback", repository)
                if self.tamper_source_readback and repository == release.REPO:
                    record["assets"][0]["digest"] = "sha256:" + "b" * 64
            phase = "published-readback" if "/tags/" in endpoint else "draft-readback"
            return self.readback(record, phase, repository)
        if args[:2] == ("gh", "release"):
            repository = args[args.index("--repo") + 1]
            self.token(repository, env)
            record = self.records[repository]
            if args[2] == "upload":
                paths = args[4:args.index("--repo")]
                self.event("upload", repository, *(Path(path).name for path in paths))
                for path in paths:
                    path = Path(path)
                    record["assets"].append({"name": path.name, "digest": "sha256:" + release.digest(path), "size": path.stat().st_size})
                    if repository == self.download and self.fail_download_upload:
                        raise RuntimeError("Simulated Xposed network failure")
                return ""
            if args[2] == "edit":
                if ("draft-readback", repository) not in self.events:
                    raise AssertionError("Draft was published before readback")
                record["draft"] = False
                record["name"] = args[args.index("--title") + 1]
                self.event("publish", repository)
                return ""
        raise AssertionError(args)


class DualPublicationTest(unittest.TestCase):
    def fixture(self, root, *, legacy=False):
        apk = f"CustoMIUIzer-A14-r14.26.1-{COMMIT[:6]}.apk" if legacy else release.candidate_filename("r14.26.1", COMMIT)
        for filename in (apk, "SHA256SUMS", "build-metadata.json", "attestation.sigstore.json"):
            (root / filename).write_bytes(filename.encode())
        metadata = {"version_name": "r14.26.1", "version_code": 247, "source_sha": COMMIT,
                    "source_ref": REF, "apk": apk, "apk_sha256": release.digest(root / apk)}
        if not legacy:
            metadata["release_assets"] = [apk, "build-metadata.json"]
        return metadata

    def context(self, server):
        stack = ExitStack()
        stack.enter_context(patch.dict(os.environ, A14_XPOSED_RELEASE_TOKEN="test-xposed-token"))
        stack.enter_context(patch.object(release, "api", side_effect=server.api))
        stack.enter_context(patch.object(release, "run", side_effect=server.command))
        for name in ("check_tag", "check_new_release", "require_main_ci", "require_current_main", "release_review", "sync_release_documents"):
            stack.enter_context(patch.object(release, name))
        return stack

    def test_document_failure_is_retried_after_both_public_releases_without_asset_writes(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            metadata = self.fixture(root)
            server = ReleaseServer(root)
            with self.context(server), patch.object(release, "sync_release_documents", side_effect=[RuntimeError("docs only"), None]) as sync:
                with self.assertRaisesRegex(RuntimeError, "docs only"):
                    release.publish_verified(root, "42", "both", metadata)
                self.assertFalse(server.records[release.REPO]["draft"])
                self.assertFalse(server.records[server.download]["draft"])
                server.events.clear()
                release.publish_verified(root, "42", "both", metadata)
                self.assertEqual(2, sync.call_count)
                self.assertFalse(any(event[0] in {"git", "create", "upload", "publish"} for event in server.events))

    def test_source_only_and_prereleases_cannot_update_stable_download_documents(self):
        for destination, prerelease in (("source", False), ("both", True)):
            with self.subTest(destination=destination), tempfile.TemporaryDirectory() as temp:
                root = Path(temp)
                metadata = self.fixture(root)
                server = ReleaseServer(root)
                with self.context(server), patch.object(release, "sync_release_documents") as sync:
                    release.publish_verified(root, "42", destination, metadata, prerelease=prerelease)
                    sync.assert_not_called()

    def test_current_and_historical_changelogs_render_identical_language_roles_at_exact_sha(self):
        for layout in ((("CHANGELOG.md", "简体中文"), ("CHANGELOG_EN.md", "English")),
                       (("CHANGELOG_CN.md", "简体中文"), ("CHANGELOG.md", "English"))):
            with self.subTest(layout=layout), tempfile.TemporaryDirectory() as temp:
                root = Path(temp)
                metadata = self.fixture(root)
                server = ReleaseServer(root)
                server.source_files = [filename for filename, _ in layout]
                for filename, language in layout:
                    user, engineering = ("用户变化", "工程说明") if language == "简体中文" else ("User changes", "Engineering notes")
                    text = f"## r14.26.1\n\nversionCode 247. Changes since r14.25.1:\n\n### {user}\n\n{language} overview.\n\n"
                    text += "\n".join(f"- {language} change {i}." for i in range(4))
                    text += f"\n\n### {engineering}\n\n- Internal details.\n"
                    server.source_notes[filename] = {"content": base64.b64encode(text.encode()).decode()}
                with self.context(server):
                    release.publish_verified(root, "42", "both", metadata)
                notes = server.records[release.REPO]["body"]
                self.assertIn("简体中文 overview.", notes)
                self.assertIn("English overview.", notes)
                self.assertLess(notes.index("## 简体中文"), notes.index("## English"))
                self.assertNotIn("Internal details", notes)
                self.assertEqual(notes, server.records[server.download]["body"])
                expected = [f"repos/{release.REPO}/contents?ref={COMMIT}"]
                expected += [f"repos/{release.REPO}/contents/{filename}?ref={COMMIT}" for filename, _ in layout]
                self.assertEqual(expected, server.source_reads)

    def test_missing_ambiguous_or_unavailable_source_layout_stops_before_publication(self):
        for files in (["CHANGELOG.md"], ["CHANGELOG.md", "CHANGELOG_EN.md", "CHANGELOG_CN.md"]):
            with self.subTest(files=files), tempfile.TemporaryDirectory() as temp:
                root = Path(temp)
                metadata = self.fixture(root)
                server = ReleaseServer(root)
                server.source_files = files
                with self.context(server), self.assertRaisesRegex(ValueError, "missing or ambiguous"):
                    release.publish_verified(root, "42", "both", metadata)
                self.assertEqual({}, server.records)
                self.assertEqual([], server.events)
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            metadata = self.fixture(root)
            server = ReleaseServer(root)
            with self.context(server), patch.object(release, "api", side_effect=RuntimeError("Network unavailable")):
                with self.assertRaisesRegex(RuntimeError, "Network unavailable"):
                    release.publish_verified(root, "42", "both", metadata)
            self.assertEqual({}, server.records)

    def test_source_is_published_and_read_back_before_any_xposed_write(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            metadata = self.fixture(root)
            server = ReleaseServer(root)
            with self.context(server):
                release.publish_verified(root, "42", "both", metadata)
            self.assertEqual([release.REPO, server.download], [event[1] for event in server.events if event[0] == "publish"])
            self.assertLess(server.events.index(("latest", release.REPO)), server.events.index(("git", server.download)))
            source, download = server.records[release.REPO], server.records[server.download]
            self.assertEqual("r14.26.1", source["tag_name"])
            self.assertEqual("247-r14.26.1", download["tag_name"])
            self.assertEqual(source["assets"], download["assets"])
            self.assertEqual(source["body"], download["body"])
            self.assertEqual("r14.26.1", source["name"])
            self.assertEqual(source["name"], download["name"])
            self.assertEqual({metadata["apk"], "build-metadata.json"}, {asset["name"] for asset in source["assets"]})

    def test_community_footer_is_published_once_and_legacy_recovery_is_read_only(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            metadata = self.fixture(root)
            server = ReleaseServer(root)
            with self.context(server):
                release.publish_verified(root, "42", "both", metadata)
                for record in server.records.values():
                    self.assertTrue(record["body"].endswith(release.community_notes() + "\n"))
                    self.assertEqual(1, record["body"].count(release.POLICY["community_url"]))
                for legacy in (False, True):
                    if legacy:
                        for record in server.records.values():
                            record["body"] = record["body"].removesuffix("\n\n" + release.community_notes() + "\n") + "\n"
                    original = copy.deepcopy(server.records)
                    server.events.clear()
                    release.publish_verified(root, "42", "both", metadata)
                    self.assertEqual(original, server.records)
                    self.assertFalse(any(event[0] in {"create", "upload", "publish", "git"} for event in server.events))

    def test_changed_community_url_cannot_bypass_existing_candidate_validation(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            metadata = self.fixture(root)
            server = ReleaseServer(root)
            with self.context(server):
                release.publish_verified(root, "42", "both", metadata)
                server.records[release.REPO]["body"] = server.records[release.REPO]["body"].replace(
                    release.POLICY["community_url"], "https://t.me/wrong")
                server.events.clear()
                with self.assertRaisesRegex(ValueError, "notes/title"):
                    release.publish_verified(root, "42", "both", metadata)
                self.assertFalse(any(event[0] in {"create", "upload", "publish", "git"} for event in server.events))

    def test_changed_title_or_notes_stop_publication_and_cross_repo_sync(self):
        for phase in ("create", "draft-readback", "published-readback"):
            for repository in (release.REPO, ReleaseServer.download):
                for field in ("name", "body"):
                    with self.subTest(phase=phase, repository=repository, field=field), tempfile.TemporaryDirectory() as temp:
                        root = Path(temp)
                        metadata = self.fixture(root)
                        server = ReleaseServer(root)
                        server.tamper_content_at = (phase, repository, field)
                        with self.context(server), self.assertRaisesRegex(ValueError, "notes/title"):
                            release.publish_verified(root, "42", "both", metadata)
                        if phase != "published-readback":
                            self.assertTrue(server.records[repository]["draft"])
                            self.assertNotIn(("publish", repository), server.events)
                        if repository == release.REPO:
                            self.assertNotIn(server.download, server.records)
                            self.assertFalse(any(event[0] == "git" for event in server.events))

    def test_recovery_keeps_published_legacy_title_and_normalizes_the_remaining_draft(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            metadata = self.fixture(root, legacy=True)
            server = ReleaseServer(root)
            server.fail_download_upload = True
            with self.context(server):
                with self.assertRaisesRegex(RuntimeError, "Xposed network failure"):
                    release.publish_verified(root, "42", "both", metadata)
                for record in server.records.values():
                    record["name"] = "CustoMIUIzer A14 · r14.26.1"
                original_source = copy.deepcopy(server.records[release.REPO])
                server.fail_download_upload = False
                server.events.clear()
                release.publish_verified(root, "42", "both", metadata)
            self.assertEqual(original_source, server.records[release.REPO])
            self.assertEqual("r14.26.1", server.records[server.download]["name"])
            self.assertFalse(server.records[server.download]["draft"])

    def test_failed_source_verification_prevents_xposed_tags_or_releases(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            metadata = self.fixture(root)
            server = ReleaseServer(root)
            server.tamper_source_readback = True
            with self.context(server), self.assertRaisesRegex(ValueError, "digest/size differs"):
                release.publish_verified(root, "42", "both", metadata)
            self.assertTrue(server.records[release.REPO]["draft"])
            self.assertNotIn(server.download, server.records)
            self.assertFalse(any(event[0] == "git" for event in server.events))

    def test_main_may_advance_after_source_verification_without_invalidating_xposed_sync(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            metadata = self.fixture(root)
            server = ReleaseServer(root)
            def current_main(sha):
                self.assertEqual(COMMIT, sha)
                if release.REPO in server.records and not server.records[release.REPO]["draft"]:
                    raise ValueError("main moved after source publication")
            with self.context(server), patch.object(release, "require_current_main", side_effect=current_main):
                release.publish_verified(root, "42", "both", metadata)
            self.assertFalse(server.records[server.download]["draft"])

    def test_main_moving_before_source_publication_stops_both_destinations(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            metadata = self.fixture(root)
            server = ReleaseServer(root)
            with self.context(server), patch.object(release, "require_current_main", side_effect=ValueError("main moved")):
                with self.assertRaisesRegex(ValueError, "main moved"):
                    release.publish_verified(root, "42", "both", metadata)
            self.assertTrue(server.records[release.REPO]["draft"])
            self.assertNotIn(server.download, server.records)

    def test_partial_xposed_failure_resumes_only_missing_assets_without_changing_source(self):
        for prerelease in (False, True):
            with self.subTest(prerelease=prerelease), tempfile.TemporaryDirectory() as temp:
                root = Path(temp)
                metadata = self.fixture(root)
                server = ReleaseServer(root)
                server.fail_download_upload = True
                with self.context(server):
                    with self.assertRaisesRegex(RuntimeError, "Xposed network failure"):
                        release.publish_verified(root, "42", "both", metadata, prerelease=prerelease)
                    original = copy.deepcopy(server.records[release.REPO])
                    self.assertFalse(original["draft"])
                    self.assertTrue(server.records[server.download]["draft"])
                    self.assertEqual(1, len(server.records[server.download]["assets"]))
                    server.events.clear()
                    server.fail_download_upload = False
                    release.publish_verified(root, "42", "both", metadata, prerelease=prerelease)
                self.assertEqual(original, server.records[release.REPO])
                self.assertFalse(any(event[0] in {"create", "upload", "publish"} and event[1] == release.REPO for event in server.events))
                uploads = [event for event in server.events if event[0] == "upload"]
                self.assertEqual(1, len(uploads))
                self.assertEqual({"build-metadata.json"}, set(uploads[0][2:]))
                self.assertEqual(2, len(server.records[server.download]["assets"]))
                self.assertFalse(server.records[server.download]["draft"])

    def test_recovery_refuses_asset_or_note_changes_before_any_write(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            metadata = self.fixture(root)
            server = ReleaseServer(root)
            with self.context(server):
                release.publish_verified(root, "42", "both", metadata)
                original = copy.deepcopy(server.records)
                for field in ("assets", "body", "name"):
                    server.records = copy.deepcopy(original)
                    if field == "assets":
                        server.records[release.REPO]["assets"][0]["digest"] = "sha256:" + "c" * 64
                    elif field == "body":
                        server.records[release.REPO]["body"] += "\nUnexpected note"
                    else:
                        server.records[release.REPO]["name"] = "CustoMIUIzer A14 · r14.26.1"
                    server.events.clear()
                    with self.subTest(field=field), self.assertRaises(ValueError):
                        release.publish_verified(root, "42", "both", metadata)
                    self.assertFalse(any(event[0] in {"git", "create", "upload", "publish"} for event in server.events))

    def test_repeated_published_immutable_releases_are_verified_without_any_write(self):
        for legacy in (False, True):
            with self.subTest(legacy=legacy), tempfile.TemporaryDirectory() as temp:
                root = Path(temp)
                metadata = self.fixture(root, legacy=legacy)
                server = ReleaseServer(root)
                with self.context(server):
                    release.publish_verified(root, "42", "both", metadata)
                    for record in server.records.values():
                        record["immutable"] = True
                    original = copy.deepcopy(server.records)
                    server.events.clear()
                    release.publish_verified(root, "42", "both", metadata)
                self.assertEqual(original, server.records)
                self.assertFalse(any(event[0] in {"git", "create", "upload", "publish"} for event in server.events))

    def test_legacy_four_asset_source_and_partial_draft_recover_with_original_names_and_evidence(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            metadata = self.fixture(root, legacy=True)
            server = ReleaseServer(root)
            with self.context(server):
                release.publish_verified(root, "42", "both", metadata)
                for record in server.records.values():
                    record["name"] = "CustoMIUIzer A14 · r14.26.1"
                    for filename in ("SHA256SUMS", "attestation.sigstore.json"):
                        path = root / filename
                        record["assets"].append({"name": filename, "digest": "sha256:" + release.digest(path), "size": path.stat().st_size})
                server.records[release.REPO]["immutable"] = True
                original = copy.deepcopy(server.records[release.REPO])
                draft = server.records[server.download]
                draft["draft"] = True
                draft["assets"] = [asset for asset in draft["assets"] if asset["name"] in {metadata["apk"], "SHA256SUMS"}]
                server.events.clear()
                release.publish_verified(root, "42", "both", metadata)
            self.assertEqual(original, server.records[release.REPO])
            self.assertEqual("r14.26.1", server.records[server.download]["name"])
            uploads = [event for event in server.events if event[0] == "upload"]
            self.assertEqual([("upload", server.download, "build-metadata.json", "attestation.sigstore.json")], uploads)
            self.assertEqual(4, len(server.records[server.download]["assets"]))

    def test_immutable_draft_or_unexpected_new_sidecars_stop_before_any_mutation(self):
        for mutation in ("immutable", "sidecar"):
            with self.subTest(mutation=mutation), tempfile.TemporaryDirectory() as temp:
                root = Path(temp)
                metadata = self.fixture(root)
                server = ReleaseServer(root)
                with self.context(server):
                    release.publish_verified(root, "42", "both", metadata)
                    record = server.records[release.REPO]
                    if mutation == "immutable":
                        record.update(draft=True, immutable=True)
                    else:
                        path = root / "SHA256SUMS"
                        record["assets"].append({"name": path.name, "size": path.stat().st_size, "digest": "sha256:" + release.digest(path)})
                    server.events.clear()
                    with self.assertRaises(ValueError):
                        release.publish_verified(root, "42", "both", metadata)
                self.assertFalse(any(event[0] in {"git", "create", "upload", "publish"} for event in server.events))

    def test_every_publication_stage_can_resume_without_rebuilding_or_replacing_verified_assets(self):
        stages = ["create", "upload", "draft-readback", "publish", "published-readback", "latest"]
        for repository in (release.REPO, ReleaseServer.download):
            for stage in stages + (["git"] if repository == ReleaseServer.download else []):
                with self.subTest(repository=repository, stage=stage), tempfile.TemporaryDirectory() as temp:
                    root = Path(temp)
                    metadata = self.fixture(root)
                    original = {path.name: path.read_bytes() for path in root.iterdir()}
                    server = ReleaseServer(root)
                    server.fail_event = (stage, repository)
                    with self.context(server):
                        with self.assertRaisesRegex(RuntimeError, "publication interruption"):
                            release.publish_verified(root, "42", "both", metadata)
                        release.publish_verified(root, "42", "both", metadata)
                    source, download = server.records[release.REPO], server.records[server.download]
                    self.assertFalse(source["draft"] or download["draft"])
                    self.assertEqual(source["assets"], download["assets"])
                    self.assertEqual(2, len(source["assets"]))
                    for name, data in original.items():
                        self.assertEqual(data, (root / name).read_bytes())

    def test_missing_cross_repo_permission_cannot_partially_publish_source(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            metadata = self.fixture(root)
            with patch.dict(os.environ, A14_XPOSED_RELEASE_TOKEN="test-token"), patch.object(release, "run", return_value='{"permissions":{"push":false}}'):
                with patch.object(release, "api") as api, self.assertRaisesRegex(ValueError, "contents:write"):
                    release.publish_verified(root, "42", "both", metadata)
                api.assert_not_called()

    def test_xposed_newer_version_on_a_later_page_blocks_downgrade(self):
        repository = release.POLICY["download_repository"]
        records = [{"tag_name": "247-r14.26.1", "draft": False}] + [{"tag_name": "246-r14.25.1", "draft": False}] * 99
        with patch.object(release, "api", side_effect=[records, [{"tag_name": "248-r14.26.2", "draft": False}]]) as api:
            with self.assertRaisesRegex(ValueError, "equal or newer versionCode"):
                release.find_release(repository, "247-r14.26.1", {"GH_TOKEN": "test-token"})
            self.assertEqual(2, api.call_count)


class ProtectedWorkflowTest(unittest.TestCase):
    def scan(self, text, filename="a14-signed-build.yml"):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / filename
            path.write_text(text, encoding="utf-8")
            return ci_contract_scan.scan_workflow(path, "main", "main")

    def workflow(self, filename="a14-signed-build.yml"):
        return (REPO_ROOT / ".github/workflows" / filename).read_text(encoding="utf-8")

    def test_real_protected_workflows_pass(self):
        for filename in ("a14-signed-build.yml", "a14-promote-release.yml"):
            self.assertEqual([], self.scan(self.workflow(filename), filename))

    def test_one_native_run_cannot_gain_source_inputs_or_write_permissions(self):
        original=self.workflow()
        self.assertNotIn("dispatch-build",original)
        self.assertNotIn("actions: write",original)
        self.assertNotIn("display_sha",original)
        for old,new in (("inputs:\n", "inputs:\n      ref:\n        type: string\n"),
                        ("run-name: A14 Signed Build / ${{ github.ref_name }}","run-name: A14 Signed Build / ${{ inputs.ref }}"),
                        ("      actions: read", "      actions: write")):
            self.assertIn(old,original)
            with self.subTest(new=new): self.assertTrue(self.scan(original.replace(old,new)))

    def test_filename_alone_cannot_exempt_signing(self):
        text = self.workflow().replace("environment: release-signing", "environment: unprotected")
        self.assertTrue(any("CI_SIGNING" in item for item in self.scan(text)))

    def test_signing_guard_cleanup_checkout_or_verification_cannot_be_removed(self):
        for original, changed in (("needs.validate.outputs.authorized == 'true'", "true"),
                                  ("ref: ${{ github.sha }}", "ref: ${{ inputs.ref }}"),
                                  ("if: always()", "if: failure()"), ("signed_release.py candidate", "signed_release.py source-policy"),
                                  ("id: artifact", "id: artifact\n        if: always()")):
            with self.subTest(changed=changed):
                self.assertTrue(self.scan(self.workflow().replace(original, changed)))

    def test_a_second_signing_secret_step_is_rejected(self):
        for expression in ("secrets.A14_KEYSTORE_BASE64", "secrets['A14_KEYSTORE_BASE64']", 'secrets["A14_KEYSTORE_BASE64"]'):
            text = self.workflow() + "\n      - run: echo unexpected\n        env:\n          LEAK: ${{ " + expression + " }}\n"
            self.assertTrue(any("CI_SIGNING" in item for item in self.scan(text)))

    def test_candidate_upload_cannot_precede_verification_or_reintroduce_online_proof(self):
        original=self.workflow()
        for old,new in (("signed_release.py candidate", "signed_release.py source-policy"),
                        ("path: ${{ runner.temp }}/candidate/", "path: ${{ runner.temp }}/"),
                        ("id: artifact", "id: artifact\n        if: always()"),
                        ("      actions: read", "      actions: read\n      attestations: write")):
            self.assertIn(old,original)
            with self.subTest(new=new): self.assertTrue(self.scan(original.replace(old,new)))

    def test_push_can_never_sign_and_untrusted_triggers_are_rejected(self):
        for event in ("pull_request", "push"):
            text = self.workflow().replace("  workflow_dispatch:", f"  {event}:")
            with self.subTest(event=event):
                self.assertTrue(self.scan(text))

    def test_main_ci_success_sha_and_native_preflight_guards_are_required(self):
        for original, changed in (("branches: [main]", "branches: ['*']"),
                                  ("github.event.workflow_run.head_sha == github.sha", "true"),
                                  ("github.event.workflow_run.conclusion == 'success'", "true"),
                                  ("signed_release.py signing-preflight", "signed_release.py ci-status"),
                                  ("needs.validate.outputs.ready == 'true'", "true")):
            with self.subTest(changed=changed):
                self.assertTrue(self.scan(self.workflow().replace(original, changed)))

    def test_automatic_publication_cannot_drop_receipt_guard_or_its_environment(self):
        for original, changed in (("needs: [validate, sign]", "needs: [validate]"),
                                  ("environment: release-publishing", "environment: release-signing"),
                                  ("${{ needs.sign.outputs.artifact_id }}", "${{ github.run_id }}"),
                                  ("group: a14-release-publish", "group: unrelated"),
                                  (" && needs.validate.outputs.destination != 'actions-only'", "")):
            with self.subTest(changed=changed):
                self.assertTrue(self.scan(self.workflow().replace(original, changed)))

    def test_promotion_bootstrap_cannot_execute_a_job(self):
        filename = "a14-promote-release.yml"
        text = self.workflow(filename).replace("    if: github.repository == 'thetvplus/customiuizer-a14' && github.event_name == 'workflow_dispatch' && github.ref == 'refs/heads/main' && inputs.destination != 'docs-only'\n", "    if: always()\n")
        self.assertTrue(self.scan(text, filename))

    def test_promotion_cannot_rebuild_or_publish_by_default(self):
        filename = "a14-promote-release.yml"
        for original, changed in (("default: actions-only", "default: both"),
                                  ("environment: release-publishing", "environment: release-signing"),
                                  ("needs: verify", "needs: missing"),
                                  ("run: python tools/signed_release.py publish", "run: ./gradlew :app:assembleRelease\n          python tools/signed_release.py publish")):
            self.assertTrue(self.scan(self.workflow(filename).replace(original, changed), filename))

    def test_document_recovery_cannot_lose_its_guard_or_gain_signing_permissions(self):
        filename = "a14-promote-release.yml"
        for original, changed in (("github.ref == 'refs/heads/main'", "true"),
                                  ("inputs.destination == 'docs-only'", "true"),
                                  ("signed_release.py sync-docs", "signed_release.py publish"),
                                  ("RELEASE_DOC_VERSION: ${{ inputs.version }}", "RELEASE_DOC_VERSION: latest")):
            with self.subTest(changed=changed):
                self.assertTrue(self.scan(self.workflow(filename).replace(original, changed), filename))


class FormalTargetAndNotesTest(unittest.TestCase):
    def test_repository_target_matches_the_next_source_and_preserves_both_notes_baselines(self):
        policy = json.loads((REPO_ROOT / "tools/release-policy.json").read_text(encoding="utf-8"))
        name, code = release.source_version((REPO_ROOT / "app/build.gradle.kts").read_text(encoding="utf-8"))
        self.assertEqual("r14.25.7", name)
        self.assertEqual(256, code)
        with patch.object(release, "POLICY", policy):
            release.release_target(name, code)
            for retired in (("r14.25.5", 254), ("r14.25.6", 255), (name, 255)):
                with self.assertRaisesRegex(ValueError, "designated formal"):
                    release.release_target(*retired)
        self.assertEqual({"notes_base": "r14.25.5"}, policy["cumulative_releases"][name])
        self.assertEqual({"notes_base": "r14.24.1"}, policy["cumulative_releases"]["r14.25.5"])

    def test_trial_and_retired_candidate_cannot_be_formal_release(self):
        release.release_target("r14.25.5",254)
        for name,code in (("r14.25.5",250),("r14.25.5",253),("r14.25.6",253),("r14.25.4",254)):
            with self.subTest(name=name,code=code),self.assertRaisesRegex(ValueError,"designated formal"):
                release.release_target(name,code)

    def test_user_change_count_is_unlimited_but_empty_and_duplicates_are_rejected(self):
        for count in (1,3,7,18):
            text="## r14.25.5\n\nversionCode 254. Changes since r14.24.1:\n\n### User changes\n\n"
            text += "\n".join(f"- Core change {i}." for i in range(count))
            text += "\n\n### Engineering notes\n\n- Technical facts.\n"
            notes=release.public_release_notes_section(text,"r14.25.5",254,"CHANGELOG_EN.md")
            self.assertEqual(count,len(re.findall(r"^[-*] ",notes,re.M)))
            with self.assertRaisesRegex(ValueError,"duplicate"):
                release.public_release_notes_section(text.replace("### Engineering notes","- Core change 0.\n\n### Engineering notes"),"r14.25.5",254,"CHANGELOG_EN.md")

    def test_signed_candidate_also_requires_exact_current_main_ci(self):
        with patch.dict(os.environ,ENV), patch.object(release,"dispatch_options",return_value=("actions-only",False)), \
             patch.object(release,"environment_policy"), patch.object(release,"require_main_ci",side_effect=ValueError("missing exact CI")) as ci:
            for operation in (release.release_plan,release.signing_preflight):
                with self.subTest(operation=operation.__name__),self.assertRaisesRegex(ValueError,"missing exact CI"):
                    operation()
            self.assertEqual(2,ci.call_count)


if __name__ == "__main__":
    unittest.main()
