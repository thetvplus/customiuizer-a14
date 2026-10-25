"""Narrow structural exception for protected signing and explicit promotion.

The normal CI security scan still sees every field outside the identified
protected step. Merely renaming a workflow cannot exempt it from signing checks.
"""
from __future__ import annotations

import copy
import json

VALIDATE_IF = "github.repository == 'thetvplus/customiuizer-a14' && (github.event_name == 'workflow_dispatch' || (github.event_name == 'workflow_run' && github.event.workflow_run.conclusion == 'success' && github.event.workflow_run.head_branch == 'main' && github.event.workflow_run.head_sha == github.sha && (github.event.workflow_run.event == 'push' || github.event.workflow_run.event == 'workflow_dispatch')))"
SIGN_IF = "github.repository == 'thetvplus/customiuizer-a14' && needs.validate.outputs.authorized == 'true' && needs.validate.outputs.ready == 'true'"
PUBLISH_IF = "github.repository == 'thetvplus/customiuizer-a14' && github.event_name == 'workflow_dispatch' && (inputs.destination == 'source' || inputs.destination == 'both')"
DOCS_IF = "github.repository == 'thetvplus/customiuizer-a14' && github.event_name == 'workflow_dispatch' && github.ref == 'refs/heads/main' && inputs.destination == 'docs-only'"
AUTO_PUBLISH_IF = SIGN_IF + " && needs.validate.outputs.destination != 'actions-only'"
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
    need(set(events) <= ({"workflow_dispatch", "workflow_run"} if signed else {"workflow_dispatch"}), "untrusted triggers are forbidden")
    if signed:
        need(events.get("workflow_run") == {"workflows": ["A14 CI"], "types": ["completed"], "branches": ["main"]},
             "automatic release may follow only completed main CI")
    dispatch = events.get("workflow_dispatch") or {}
    need(isinstance(dispatch, dict), "invalid dispatch configuration")
    if signed:
        inputs = dispatch.get("inputs", {}) if isinstance(dispatch, dict) else {}
        need(set(inputs) == {"destination", "prerelease"}, "signing accepts only release options, never a source ref/SHA input")
        need(workflow.get("run-name") == "A14 Signed Build / ${{ github.ref_name }}",
             "run titles use the native branch without a duplicate signing dispatch")
        need(inputs.get("destination", {}).get("type") == "choice" and inputs.get("destination", {}).get("default") == "both"
             and inputs.get("destination", {}).get("options") == ["both", "source", "actions-only"], "native dispatch must preselect sequential dual publication and allow candidate-only builds")
        need(inputs.get("prerelease", {}).get("type") == "boolean" and inputs.get("prerelease", {}).get("default") == "false", "pre-release must be explicit")
        need(set(jobs) == {"validate", "sign", "publish"}, "validation, signing and publication must stay separate")
        job = jobs.get("sign", {})
        need(job.get("environment") == "release-signing", "signing requires release-signing Environment")
        need(job.get("if") == SIGN_IF and job.get("needs") == "validate", "signing must require successful authorized release validation")
        need(job.get("permissions") == {"contents": "read", "actions": "read"}, "signing permissions must be minimal")
        need(jobs.get("validate", {}).get("permissions") == {"contents": "read", "actions": "read"}, "validation must not have signing/write permissions")
        validate = jobs.get("validate", {})
        need(validate.get("if") == VALIDATE_IF, "automatic validation must reject failed, PR or stale CI events before checkout")
        need(validate.get("outputs") == {"authorized": "${{ steps.source.outputs.authorized }}", "ready": "${{ steps.plan.outputs.ready }}",
                                         "destination": "${{ steps.plan.outputs.destination }}"}, "authorization and release options must come from native source policy/plan")
        need(any(step.get("id") == "source" and step.get("run") == "python tools/signed_release.py source-policy"
                 and "if" not in step for step in validate.get("steps", [])), "native source policy validation is required")
        need(any(step.get("run") == "python tools/signed_release.py release-plan"
                 and step.get("id") == "plan" and step.get("if") == "steps.source.outputs.authorized == 'true'"
                 for step in validate.get("steps", [])), "release version must be checked before signing")
        receipt = {key: "${{ steps.candidate.outputs." + key + " }}" for key in ("source_sha", "apk_sha256", "run_attempt")}
        receipt["artifact_id"] = "${{ steps.artifact.outputs.artifact-id }}"
        need(job.get("outputs") == receipt, "signing must pin the original candidate and immutable artifact ID")
        publisher = jobs.get("publish", {})
        need(publisher.get("if") == AUTO_PUBLISH_IF and publisher.get("needs") == ["validate", "sign"], "automatic publication must depend on successful validation/signing and native dispatch selection")
        need(publisher.get("environment") == "release-publishing", "automatic publication requires a separate protected Environment")
        need(publisher.get("permissions") == {"contents": "write", "actions": "read"}, "automatic publisher permissions must be minimal")
        need(publisher.get("concurrency") == {"group": "a14-release-publish", "cancel-in-progress": "false"}, "all publication paths must serialize writes")
        need("gradlew" not in json.dumps(publisher) and "prepare-signing" not in json.dumps(publisher), "automatic publication cannot rebuild or resign")
        publications = [(i, step) for i, step in enumerate(publisher.get("steps", [])) if "signed_release.py publish" in step.get("run", "")]
        need(len(publications) == 1, "one automatic publication step is required")
        if len(publications) == 1:
            _, publication = publications[0]
            env = {"VERIFIED_" + key.upper(): "${{ needs.sign.outputs." + key + " }}" for key in receipt}
            env.update(GH_TOKEN="${{ github.token }}", A14_XPOSED_RELEASE_TOKEN="${{ secrets.A14_XPOSED_RELEASE_TOKEN }}")
            need(publication.get("env") == env and publication.get("run") == 'python tools/signed_release.py publish-current --directory "$RUNNER_TEMP/original-candidate"', "automatic publisher must consume the signing receipt and native dispatch")
        steps = job.get("steps", [])
        candidates = [i for i, step in enumerate(steps) if step.get("id") == "candidate"
                      and step.get("run") == 'python tools/signed_release.py candidate --directory "$RUNNER_TEMP/candidate"']
        need(len(candidates) == 1, "candidate outputs must come from APK verification")
        builds = [(index, step) for index, step in enumerate(steps) if "-PofficialRelease=true" in step.get("run", "")]
        need(len(builds) == 1, "exactly one official APK build is required")
        if len(builds) == 1:
            index, build = builds[0]
            env = {key: "${{ secrets." + key + " }}" for key in KEYS}
            env["CUSTOMIUIZER_A14_KEYSTORE_PROPERTIES"] = "${{ runner.temp }}/a14-signing/keystore.properties"
            need(build.get("env") == env and build.get("run", "").strip() == BUILD_COMMAND, "secrets are scoped to the bounded build/cleanup step")
            preflight = [i for i, step in enumerate(steps) if step.get("run") == "python tools/signed_release.py signing-preflight" and "if" not in step]
            need(len(preflight) == 1 and preflight[0] < index, "source, completed main CI and environment restrictions must be rechecked before exposing keys")
            cleanup = [i for i, step in enumerate(steps) if step.get("if") == "always()" and step.get("run") == 'rm -rf -- "$RUNNER_TEMP/a14-signing"']
            uploads = [(i, step) for i, step in enumerate(steps) if step.get("uses", "").startswith("actions/upload-artifact@")]
            need(len(cleanup) == len(candidates) == len(uploads) == 1,
                 "one cleanup, verified candidate and artifact upload are required")
            if len(cleanup) == len(candidates) == len(uploads) == 1:
                upload_index, upload = uploads[0]
                need(index < cleanup[0] < candidates[0] < upload_index,
                     "remove signing material before APK verification and upload")
                need("if" not in upload and upload.get("with", {}).get("path") == "${{ runner.temp }}/candidate/"
                     and upload["with"].get("if-no-files-found") == "error", "only a verified candidate may be uploaded")
            need(not any(step.get("uses", "").startswith("actions/attest@") for step in steps),
                 "online proof generation is not part of the release contract")
            if not errors:
                sanitized["jobs"]["sign"]["steps"][index]["env"] = {}
                sanitized["jobs"]["sign"]["steps"][index]["run"] = "protected signing build"
                sanitized["jobs"]["publish"]["steps"][publications[0][0]]["run"] = "verified scoped publication"
    else:
        inputs = dispatch.get("inputs", {}) if isinstance(dispatch, dict) else {}
        need(set(inputs) == {"build_run_id", "destination", "prerelease", "version"}, "promotion inputs must identify the original build or published document version and native destination selection")
        need(inputs.get("prerelease", {}).get("type") == "boolean" and inputs.get("prerelease", {}).get("default") == "false", "promotion pre-release must be explicit")
        need(inputs.get("destination", {}).get("default") == "actions-only" and inputs.get("destination", {}).get("options") == ["actions-only", "source", "both", "docs-only"], "publication must be opt-in")
        need(set(jobs) == {"verify", "publish", "docs"}, "promotion must isolate candidate verification, publication and document-only recovery")
        job = jobs.get("publish", {})
        need(job.get("environment") == "release-publishing", "publication requires its own protected Environment")
        need(job.get("if") == PUBLISH_IF and job.get("needs") == "verify", "publication must require verification and explicit opt-in")
        need(job.get("permissions") == {"contents": "write", "actions": "read"}, "publisher permissions must be minimal")
        need(job.get("concurrency") == {"group": "a14-release-publish", "cancel-in-progress": "false"}, "all publication paths must serialize writes")
        need(jobs.get("verify", {}).get("permissions") == {"contents": "read", "actions": "read"}, "candidate verification must be read-only")
        receipt = {key: "${{ steps.candidate.outputs." + key + " }}" for key in ("source_sha", "apk_sha256", "run_attempt", "artifact_id")}
        need(jobs.get("verify", {}).get("outputs") == receipt, "verification must pin the original artifact identity")
        need(jobs.get("verify", {}).get("if") == "github.repository == 'thetvplus/customiuizer-a14' && github.event_name == 'workflow_dispatch' && github.ref == 'refs/heads/main' && inputs.destination != 'docs-only'", "promotion must require native manual dispatch and bypass artifacts only for document recovery")
        docs = jobs.get("docs", {})
        need(docs.get("if") == DOCS_IF and docs.get("environment") == "release-publishing"
             and docs.get("permissions") == {"contents": "read"} and "needs" not in docs,
             "document recovery requires trusted main, explicit opt-in and the publishing Environment, without candidate or signing access")
        need(docs.get("concurrency") == {"group": "a14-release-publish", "cancel-in-progress": "false"}, "document recovery must serialize with publication")
        docs_steps = docs.get("steps", [])
        need(len(docs_steps) == 2 and docs_steps[-1].get("run") == 'python tools/signed_release.py sync-docs --version "$RELEASE_DOC_VERSION" --apply'
             and docs_steps[-1].get("env") == {"GH_TOKEN": "${{ github.token }}", "A14_XPOSED_RELEASE_TOKEN": "${{ secrets.A14_XPOSED_RELEASE_TOKEN }}",
                     "RELEASE_DOC_VERSION": "${{ inputs.version }}"},
             "document retry must only synchronize the explicitly selected published version")
        need("gradlew" not in json.dumps(workflow) and "prepare-signing" not in json.dumps(workflow), "promotion cannot rebuild or resign")
        for index, step in enumerate(job.get("steps", [])):
            if "signed_release.py publish " in step.get("run", "") and not errors:
                need(all(step.get("env", {}).get("VERIFIED_" + key.upper()) == "${{ needs.verify.outputs." + key + " }}" for key in receipt), "publisher must consume the original verification receipt")
                need(step.get("env", {}).get("RELEASE_PRERELEASE") == "${{ inputs.prerelease }}", "promotion must preserve native pre-release selection")
                sanitized["jobs"]["publish"]["steps"][index]["run"] = step["run"].replace("signed_release.py publish", "explicit protected promotion")
    for job in jobs.values():
        for step in job.get("steps", []):
            if step.get("uses", "").lower().startswith("actions/checkout@"):
                need(step.get("with", {}).get("ref") == "${{ github.sha }}", "checkout must use the native exact triggering SHA")
    return errors, json.dumps(sanitized)
