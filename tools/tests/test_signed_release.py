"""Release trust boundaries, APK validation and workflow regression tests."""
import base64
import copy
import hashlib
import json
import os
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
       "GITHUB_REF": REF, "GITHUB_SHA": COMMIT, "GITHUB_RUN_ID": "42", "GITHUB_RUN_ATTEMPT": "1"}
BADGING = """package: name='tv.withaibuild.customiuizer.r14' versionCode='245' versionName='r14.24.1' platformBuildVersionName='x'
sdkVersion:'34'
targetSdkVersion:'34'
native-code: 'arm64-v8a'
"""
SIGNATURE = f"Verified using v2 scheme (APK Signature Scheme v2): true\nSigner #1 certificate SHA-256 digest: {release.CERT}\n"


def build_run():
    return {"id": 42, "run_attempt": 1, "workflow_id": 7, "path": release.WORKFLOW,
            "head_repository": {"full_name": release.REPO}, "head_sha": COMMIT,
            "head_branch": "main", "status": "completed", "conclusion": "success", "event": "workflow_dispatch"}


def attestation():
    return [{"verificationResult": {"statement": {
        "predicateType": "https://slsa.dev/provenance/v1",
        "predicate": {"buildDefinition": {
            "externalParameters": {"workflow": {"repository": f"https://github.com/{release.REPO}", "path": release.WORKFLOW, "ref": REF}},
            "resolvedDependencies": [{"digest": {"gitCommit": COMMIT}}]},
            "runDetails": {"metadata": {"invocationId": f"https://github.com/{release.REPO}/actions/runs/42/attempts/1"}}}}}}]


def ci_run():
    return dict(build_run(), id=100, workflow_id=8, name="A14 CI", path=".github/workflows/a14-ci.yml", event="push")


class AutomaticReleaseTest(unittest.TestCase):
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

    def test_upstream_ci_rerun_after_trigger_requires_a_fresh_confirmation(self):
        with tempfile.TemporaryDirectory() as temp:
            env = self.environment(Path(temp) / "event.json")
            with patch.dict(os.environ, env), patch.object(release, "api", side_effect=[{"id": 8}, dict(ci_run(), run_attempt=2)]):
                with self.assertRaisesRegex(ValueError, "attempt/source changed"):
                    release.automatic_ci()

    def test_unprotected_environment_is_never_treated_as_human_confirmation(self):
        protected = {"protection_rules": [{"type": "required_reviewers", "reviewers": [{"reviewer": {"login": "maintainer"}}]}],
                     "deployment_branch_policy": {"custom_branch_policies": True, "protected_branches": False}}
        with patch.object(release, "api", return_value=protected):
            release.confirmation_policy()
        for environment in ({}, dict(protected, protection_rules=[]), dict(protected, deployment_branch_policy=None)):
            with self.subTest(environment=environment), patch.object(release, "api", return_value=environment):
                with self.assertRaises(ValueError):
                    release.confirmation_policy()

    def test_already_published_main_only_skips_without_signing_or_requesting_approval(self):
        with tempfile.TemporaryDirectory() as temp:
            env = self.environment(Path(temp) / "event.json")
            with patch.dict(os.environ, env), patch.object(release, "automatic_ci"), patch.object(release, "require_current_main"):
                with patch.object(release, "optional_api", return_value={"draft": False}), patch.object(release, "output") as output:
                    with patch.object(release, "confirmation_policy") as approval, patch.object(release, "check_new_release") as version:
                        release.release_plan()
                        self.assertEqual("false", output.call_args.kwargs["ready"])
                        approval.assert_not_called()
                        version.assert_not_called()

    def test_new_release_requests_approval_only_after_version_and_notes_pass(self):
        with tempfile.TemporaryDirectory() as temp:
            env = self.environment(Path(temp) / "event.json")
            with ExitStack() as stack:
                stack.enter_context(patch.dict(os.environ, env))
                for name in ("automatic_ci", "require_current_main", "check_new_release"):
                    stack.enter_context(patch.object(release, name))
                stack.enter_context(patch.object(release, "optional_api", return_value=None))
                notes = stack.enter_context(patch.object(release, "local_release_notes", return_value=["Reviewed changes"]))
                approval = stack.enter_context(patch.object(release, "confirmation_policy"))
                output = stack.enter_context(patch.object(release, "output"))
                release.release_plan()
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

    def test_changelog_release_notes_cannot_be_empty_ambiguous_or_wrong_version(self):
        for text in ("## r14.26.1\n\nversionCode 247.\n", "## r14.26.1\n\nversionCode 246.\n- Change\n",
                     "## r14.26.1\n\nversionCode 247.\n- Change\n\n## r14.26.1\n- Duplicate\n"):
            with self.subTest(text=text), self.assertRaises(ValueError):
                release.release_notes_section(text, "r14.26.1", 247, "CHANGELOG.md")


class SourceTrustTest(unittest.TestCase):
    def test_native_branch_and_exact_sha_are_accepted(self):
        self.assertEqual((REF, COMMIT), release.event_source(ENV))

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
    def test_candidate_uses_source_version_and_sha_without_altering_the_binary(self):
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
            name = f"CustoMIUIzer-A14-r14.25.2-{COMMIT[:6]}.apk"
            self.assertEqual([folder / name], list(folder.glob("*.apk")))
            self.assertEqual(apk.read_bytes(), (folder / name).read_bytes())
            metadata = json.loads((folder / "build-metadata.json").read_text())
            self.assertEqual(name, metadata["apk"])
            self.assertEqual(COMMIT, metadata["source_sha"])
            self.assertEqual(f"{release.digest(apk)}  {name}\n", (folder / "SHA256SUMS").read_text())

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

    def test_candidate_tampering_is_rejected_before_attestation(self):
        record = build_run()
        metadata = {"repository": release.REPO, "workflow": release.WORKFLOW, "source_sha": COMMIT,
                    "source_ref": REF, "run_id": 42, "run_attempt": 1, "version_name": "r14.24.1", "apk": "CustoMIUIzer-A14-r14.24.1.apk", "apk_sha256": "a" * 64}
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp) / "download"
            def download(*args):
                folder.mkdir()
                (folder / metadata["apk"]).write_bytes(b"tampered")
                (folder / "build-metadata.json").write_text(json.dumps(metadata))
                return ""
            with patch.object(release, "api", side_effect=[{"id": 7}, record, {"artifacts": [{"name": "a14-signed-42-1", "expired": False}]}]):
                with patch.object(release, "run", side_effect=download), self.assertRaisesRegex(ValueError, "digest differs"):
                    release.fetch_candidate("42", folder)


class PromotionTrustTest(unittest.TestCase):
    def test_only_a_failed_publisher_after_successful_signing_may_recover_its_original_artifact(self):
        record = dict(build_run(), conclusion="failure")
        valid = {"total_count": 3, "jobs": [{"name": name, "status": "completed", "conclusion": conclusion}
                 for name, conclusion in (("Validate exact source", "success"), ("Build, sign and verify Release", "success"),
                                          ("Publish verified APK to Release", "failure"))]}
        with tempfile.TemporaryDirectory() as temp:
            with patch.object(release, "api", side_effect=[{"id": 7}, record, valid, {"artifacts": []}]):
                with self.assertRaisesRegex(ValueError, "missing, expired"):
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
        metadata = {"source_sha": COMMIT, "apk_sha256": "c" * 64, "run_attempt": 1, "artifact_id": 123}
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

    def test_verified_attestation_must_match_source_workflow_ref_sha_and_run(self):
        release.check_attestation(attestation(), COMMIT, REF, 42, 1)
        for sha, ref, run_id, attempt in (("b" * 40, REF, 42, 1), (COMMIT, "refs/heads/other", 42, 1),
                                         (COMMIT, REF, 43, 1), (COMMIT, REF, 42, 2)):
            with self.assertRaises(ValueError):
                release.check_attestation(attestation(), sha, ref, run_id, attempt)

    def test_metadata_must_match_original_run_and_safe_filename(self):
        meta = {"repository": release.REPO, "workflow": release.WORKFLOW, "source_sha": COMMIT, "source_ref": REF,
                "run_id": 42, "run_attempt": 1, "version_name": "r14.24.1", "apk": "CustoMIUIzer-A14-r14.24.1.apk"}
        release.check_metadata(meta, build_run())
        release.check_metadata(dict(meta, apk=f"CustoMIUIzer-A14-r14.24.1-{COMMIT[:6]}.apk"), build_run())
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

    def test_empty_or_wrong_publish_authorization_never_writes(self):
        metadata = {"version_name": "r14.24.1", "version_code": 245, "source_sha": COMMIT}
        with patch.dict(os.environ, ENV), patch.object(release, "fetch_candidate", return_value=metadata):
            with patch.object(release, "api") as command, self.assertRaisesRegex(ValueError, "Explicit authorization"):
                release.publish(Path("candidate"), "42", "source", "")
            command.assert_not_called()


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
                filenames = (apk, "SHA256SUMS", "build-metadata.json", "attestation.sigstore.json")
                for filename in filenames:
                    (folder / filename).write_bytes(filename.encode())
                metadata = {"version_name": "r14.25.2", "version_code": 247, "source_sha": COMMIT,
                            "source_ref": REF, "apk": apk, "apk_sha256": release.digest(folder / apk)}
                assets = [{"name": filename, "digest": "sha256:" + release.digest(folder / filename), "size": (folder / filename).stat().st_size} for filename in filenames]
                if tamper:
                    assets[-1]["digest"] = "sha256:" + "a" * 64
                records = {"id": 99, "tag_name": "r14.25.2", "target_commitish": COMMIT, "draft": True, "prerelease": prerelease, "assets": assets,
                           "html_url": "https://github.com/example/release"}
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
                        self.assertIn("CustoMIUIzer A14 · r14.25.2", request["name"])
                        self.assertIn("## English", request["body"])
                        self.assertIn("gh attestation verify", request["body"])
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
                    with patch.object(release, "find_release", return_value=None), patch.object(release, "api", return_value=notes):
                        with patch.object(release, "run", side_effect=command), patch.object(release, "require_main_ci"), patch.object(release, "require_current_main"):
                            if tamper:
                                with self.assertRaisesRegex(ValueError, "digest/size differs"):
                                    release.publish_verified(folder, "42", "source", metadata)
                                self.assertNotIn("publish", writes)
                            else:
                                release.publish_verified(folder, "42", "source", metadata, prerelease=prerelease)
                                self.assertEqual(["create", "upload", "check-draft", "publish"], writes)
                                self.assertTrue(tag.call_args.kwargs["must_exist"])

    def test_release_assets_must_include_one_exact_set_of_four_files(self):
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
        self.notes = {"content": base64.b64encode(b"## r14.26.1\n\nversionCode 247.\n\n- Reviewed change.\n").decode()}

    def token(self, repository, env):
        if repository == self.download:
            if not env or env.get("GH_TOKEN") != "test-xposed-token":
                raise AssertionError("Every Xposed read/write must use the separate token")

    def api(self, endpoint, *, env=None, **fields):
        if "/contents/" in endpoint:
            return self.notes
        if "/releases?" in endpoint:
            repository = endpoint.split("/releases?", 1)[0].removeprefix("repos/")
            self.token(repository, env)
            if repository in self.records and not self.records[repository]["draft"]:
                self.events.append(("existing-readback", repository))
            return [copy.deepcopy(self.records[repository])] if repository in self.records else []
        if endpoint.endswith("/releases/latest"):
            repository = endpoint.removesuffix("/releases/latest").removeprefix("repos/")
            self.token(repository, env)
            self.events.append(("latest", repository))
            return copy.deepcopy(self.records[repository])
        raise AssertionError(endpoint)

    def command(self, *args, env=None):
        if args[:3] == ("gh", "api", f"repos/{self.download}"):
            self.token(self.download, env)
            return json.dumps({"permissions": {"push": True}})
        if args[0] == "git":
            self.events.append(("git", self.download))
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
                self.events.append(("create", repository))
                return json.dumps(record)
            record = copy.deepcopy(self.records[repository])
            if endpoint.endswith("/latest"):
                self.events.append(("latest", repository))
            elif "/tags/" in endpoint:
                self.events.append(("published-readback", repository))
            else:
                self.events.append(("draft-readback", repository))
                if self.tamper_source_readback and repository == release.REPO:
                    record["assets"][0]["digest"] = "sha256:" + "b" * 64
            return json.dumps(record)
        if args[:2] == ("gh", "release"):
            repository = args[args.index("--repo") + 1]
            self.token(repository, env)
            record = self.records[repository]
            if args[2] == "upload":
                paths = args[4:args.index("--repo")]
                self.events.append(("upload", repository, *(Path(path).name for path in paths)))
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
                self.events.append(("publish", repository))
                return ""
        raise AssertionError(args)


class DualPublicationTest(unittest.TestCase):
    def fixture(self, root):
        apk = release.candidate_filename("r14.26.1", COMMIT)
        for filename in (apk, "SHA256SUMS", "build-metadata.json", "attestation.sigstore.json"):
            (root / filename).write_bytes(filename.encode())
        return {"version_name": "r14.26.1", "version_code": 247, "source_sha": COMMIT,
                "source_ref": REF, "apk": apk, "apk_sha256": release.digest(root / apk)}

    def context(self, server):
        stack = ExitStack()
        stack.enter_context(patch.dict(os.environ, A14_XPOSED_RELEASE_TOKEN="test-xposed-token"))
        stack.enter_context(patch.object(release, "api", side_effect=server.api))
        stack.enter_context(patch.object(release, "run", side_effect=server.command))
        for name in ("check_tag", "check_new_release", "require_main_ci", "require_current_main"):
            stack.enter_context(patch.object(release, name))
        return stack

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
                self.assertEqual({"SHA256SUMS", "build-metadata.json", "attestation.sigstore.json"}, set(uploads[0][2:]))
                self.assertEqual(4, len(server.records[server.download]["assets"]))
                self.assertFalse(server.records[server.download]["draft"])

    def test_recovery_refuses_asset_or_note_changes_before_any_write(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            metadata = self.fixture(root)
            server = ReleaseServer(root)
            with self.context(server):
                release.publish_verified(root, "42", "both", metadata)
                original = copy.deepcopy(server.records)
                for field in ("assets", "body"):
                    server.records = copy.deepcopy(original)
                    if field == "assets":
                        server.records[release.REPO]["assets"][0]["digest"] = "sha256:" + "c" * 64
                    else:
                        server.records[release.REPO]["body"] += "\nUnexpected note"
                    server.events.clear()
                    with self.subTest(field=field), self.assertRaises(ValueError):
                        release.publish_verified(root, "42", "both", metadata)
                    self.assertFalse(any(event[0] in {"git", "create", "upload", "publish"} for event in server.events))

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

    def test_filename_alone_cannot_exempt_signing(self):
        text = self.workflow().replace("environment: release-signing", "environment: unprotected")
        self.assertTrue(any("CI_SIGNING" in item for item in self.scan(text)))

    def test_signing_guard_cleanup_checkout_or_verification_cannot_be_removed(self):
        for original, changed in (("needs.validate.outputs.authorized == 'true'", "true"),
                                  ("ref: ${{ github.sha }}", "ref: ${{ inputs.ref }}"),
                                  ("if: always()", "if: failure()"), ("signed_release.py attest", "signed_release.py source-policy"),
                                  ("id: artifact", "id: artifact\n        if: always()")):
            with self.subTest(changed=changed):
                self.assertTrue(self.scan(self.workflow().replace(original, changed)))

    def test_a_second_signing_secret_step_is_rejected(self):
        for expression in ("secrets.A14_KEYSTORE_BASE64", "secrets['A14_KEYSTORE_BASE64']", 'secrets["A14_KEYSTORE_BASE64"]'):
            text = self.workflow() + "\n      - run: echo unexpected\n        env:\n          LEAK: ${{ " + expression + " }}\n"
            self.assertTrue(any("CI_SIGNING" in item for item in self.scan(text)))

    def test_push_can_never_sign_and_untrusted_triggers_are_rejected(self):
        for event in ("pull_request", "push"):
            text = self.workflow().replace("  workflow_dispatch:", f"  {event}:")
            with self.subTest(event=event):
                self.assertTrue(self.scan(text))

    def test_main_ci_success_sha_and_post_approval_preflight_guards_are_required(self):
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
        text = self.workflow(filename).replace("    if: github.repository == 'thetvplus/customiuizer-a14' && github.event_name == 'workflow_dispatch'\n", "    if: always()\n")
        self.assertTrue(self.scan(text, filename))

    def test_promotion_cannot_rebuild_or_publish_by_default(self):
        filename = "a14-promote-release.yml"
        for original, changed in (("default: actions-only", "default: both"),
                                  ("environment: release-publishing", "environment: release-signing"),
                                  ("needs: verify", "needs: missing"),
                                  ("run: python tools/signed_release.py publish", "run: ./gradlew :app:assembleRelease\n          python tools/signed_release.py publish")):
            self.assertTrue(self.scan(self.workflow(filename).replace(original, changed), filename))


if __name__ == "__main__":
    unittest.main()
