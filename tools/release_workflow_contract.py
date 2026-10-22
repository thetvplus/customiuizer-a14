"""Narrow structural exception for protected signing and explicit promotion.

The normal CI security scan still sees every field outside the identified
protected step. Merely renaming a workflow cannot exempt it from signing checks.
"""
from __future__ import annotations

import copy
import json

SIGN_IF = "github.repository == 'thetvplus/customiuizer-a14' && github.event_name == 'workflow_dispatch' && needs.validate.outputs.authorized == 'true'"
PUBLISH_IF = "github.repository == 'thetvplus/customiuizer-a14' && github.event_name == 'workflow_dispatch' && inputs.destination != 'actions-only' && inputs.authorization != ''"
BOOTSTRAP_BRANCH = "codex/a14-feature-refinement-20261019"
KEYS = ("A14_KEYSTORE_BASE64", "A14_STORE_PASSWORD", "A14_KEY_ALIAS", "A14_KEY_PASSWORD")
BUILD_COMMAND = ('trap \'rm -rf -- "$RUNNER_TEMP/a14-signing"\' EXIT\n'
                 'python tools/signed_release.py prepare-signing\n'
                 './gradlew --no-daemon --no-configuration-cache :app:assembleRelease :app:lintVitalRelease -PofficialRelease=true -PrequireBuildRevision=true -PbuildRevision="${GITHUB_SHA:0:8}"')


def check(workflow: dict, filename: str) -> tuple[list[str], str]:
    sanitized = copy.deepcopy(workflow)
    errors = []

    def need(condition: bool, message: str) -> None:
        if not condition:
            errors.append("CI_PROTECTED_RELEASE: " + message)

    if filename not in {"a14-signed-build.yml", "a14-promote-release.yml"}:
        return [], json.dumps(sanitized)
    events = workflow.get("on", {})
    jobs = workflow["jobs"]
    signed = filename == "a14-signed-build.yml"
    need(isinstance(events, dict) and "workflow_dispatch" in events, "manual dispatch is required")
    need(workflow.get("permissions") == {"contents": "read"}, "default permissions must be contents:read")
    need(workflow.get("concurrency", {}).get("cancel-in-progress") == "false", "protected runs must finish normally")
    need(workflow.get("name") == ("A14 Signed Build" if signed else "A14 Promote Release"), "wrong workflow identity")
    if not isinstance(events, dict):
        return errors, json.dumps(sanitized)
    need(set(events) <= {"workflow_dispatch", "push"}, "untrusted triggers are forbidden")
    if "push" in events:
        need(events["push"] == {"branches": [BOOTSTRAP_BRANCH], "paths": [f".github/workflows/{filename}"]}, "bootstrap push must target only this exact branch/workflow")
    dispatch = events.get("workflow_dispatch") or {}
    need(isinstance(dispatch, dict), "invalid dispatch configuration")
    if signed:
        need(not dispatch, "signing must use the native ref selector without ref inputs")
        need(set(jobs) == {"validate", "sign"}, "only validation and protected signing jobs are allowed")
        job = jobs.get("sign", {})
        need(job.get("environment") == "release-signing", "signing requires release-signing Environment")
        need(job.get("if") == SIGN_IF and job.get("needs") == "validate", "signing must require successful authorized manual validation")
        need(job.get("permissions") == {"contents": "read", "id-token": "write", "attestations": "write"}, "signing permissions must be minimal")
        need(jobs.get("validate", {}).get("permissions") == {"contents": "read", "actions": "read"}, "validation must not have signing/write permissions")
        validate = jobs.get("validate", {})
        need(validate.get("outputs") == {"authorized": "${{ steps.source.outputs.authorized }}"}, "authorization must come from the native source policy step")
        need(any(step.get("id") == "source" and step.get("run") == "python tools/signed_release.py source-policy"
                 and step.get("if") == "github.event_name == 'workflow_dispatch'" for step in validate.get("steps", [])), "native source policy validation is required")
        steps = job.get("steps", [])
        builds = [(index, step) for index, step in enumerate(steps) if "-PofficialRelease=true" in step.get("run", "")]
        need(len(builds) == 1, "exactly one official APK build is required")
        if len(builds) == 1:
            index, build = builds[0]
            env = {key: "${{ secrets." + key + " }}" for key in KEYS}
            env["CUSTOMIUIZER_A14_KEYSTORE_PROPERTIES"] = "${{ runner.temp }}/a14-signing/keystore.properties"
            need(build.get("env") == env and build.get("run", "").strip() == BUILD_COMMAND, "secrets are scoped to the bounded build/cleanup step")
            cleanup = [i for i, step in enumerate(steps) if step.get("if") == "always()" and step.get("run") == 'rm -rf -- "$RUNNER_TEMP/a14-signing"']
            attest = [i for i, step in enumerate(steps) if step.get("uses", "").startswith("actions/attest@")]
            verify = [i for i, step in enumerate(steps) if 'signed_release.py attest --directory "$RUNNER_TEMP/candidate"' in step.get("run", "")]
            uploads = [(i, step) for i, step in enumerate(steps) if step.get("uses", "").startswith("actions/upload-artifact@")]
            need(len(cleanup) == len(attest) == len(verify) == len(uploads) == 1, "one cleanup, attestation, verification and candidate upload are required")
            if len(cleanup) == len(attest) == len(verify) == len(uploads) == 1:
                upload_index, upload = uploads[0]
                need(index < cleanup[0] < attest[0] < verify[0] < upload_index, "verify before uploading, remove keys before attesting")
                need("if" not in upload and upload.get("with", {}).get("path") == "${{ runner.temp }}/candidate/" and upload["with"].get("if-no-files-found") == "error", "only a successfully verified candidate may be uploaded")
            if not errors:
                sanitized["jobs"]["sign"]["steps"][index]["env"] = {}
                sanitized["jobs"]["sign"]["steps"][index]["run"] = "protected signing build"
    else:
        inputs = dispatch.get("inputs", {}) if isinstance(dispatch, dict) else {}
        need(set(inputs) == {"build_run_id", "destination", "authorization"}, "promotion inputs must identify the original build and explicit authorization")
        need(inputs.get("destination", {}).get("default") == "actions-only" and inputs.get("destination", {}).get("options") == ["actions-only", "source", "both"], "publication must be opt-in")
        need(set(jobs) == {"verify", "publish"}, "promotion must isolate verification from writes")
        job = jobs.get("publish", {})
        need(job.get("environment") == "release-publishing", "publication requires its own protected Environment")
        need(job.get("if") == PUBLISH_IF and job.get("needs") == "verify", "publication must require verification and explicit opt-in")
        need(job.get("permissions") == {"contents": "write", "actions": "read", "attestations": "read"}, "publisher permissions must be minimal")
        need(jobs.get("verify", {}).get("permissions") == {"contents": "read", "actions": "read", "attestations": "read"}, "candidate verification must be read-only")
        receipt = {key: "${{ steps.candidate.outputs." + key + " }}" for key in ("source_sha", "apk_sha256", "run_attempt", "artifact_id")}
        need(jobs.get("verify", {}).get("outputs") == receipt, "verification must pin the original artifact identity")
        need(jobs.get("verify", {}).get("if") == "github.repository == 'thetvplus/customiuizer-a14' && github.event_name == 'workflow_dispatch'", "promotion bootstrap must skip every executable job")
        need("gradlew" not in json.dumps(workflow) and "prepare-signing" not in json.dumps(workflow), "promotion cannot rebuild or resign")
        for index, step in enumerate(job.get("steps", [])):
            if "signed_release.py publish " in step.get("run", "") and not errors:
                need(all(step.get("env", {}).get("VERIFIED_" + key.upper()) == "${{ needs.verify.outputs." + key + " }}" for key in receipt), "publisher must consume the original verification receipt")
                sanitized["jobs"]["publish"]["steps"][index]["run"] = step["run"].replace("signed_release.py publish", "explicit protected promotion")
    for job in jobs.values():
        for step in job.get("steps", []):
            if step.get("uses", "").lower().startswith("actions/checkout@"):
                need(step.get("with", {}).get("ref") == "${{ github.sha }}", "checkout must use the native exact triggering SHA")
    return errors, json.dumps(sanitized)
