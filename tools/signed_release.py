#!/usr/bin/env python3
"""Protected Actions signing, APK verification and exact-binary promotion.

No workflow ref input is accepted. Signing uses the native event ref/SHA;
promotion treats downloaded metadata as untrusted until independently checked.
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

try:
    from tools.verify_apk_provenance import verify_apk_provenance
except ModuleNotFoundError:
    from verify_apk_provenance import verify_apk_provenance

ROOT = Path(__file__).resolve().parents[1]
POLICY = json.loads((ROOT / "tools/release-policy.json").read_text(encoding="utf-8"))
REPO = POLICY["repository"]
WORKFLOW = ".github/workflows/a14-signed-build.yml"
SHA = re.compile(r"[0-9a-f]{40}")
CERT = POLICY["certificate_sha256"]


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def run(*args: str, env: dict | None = None) -> str:
    result = subprocess.run(args, capture_output=True, text=True, encoding="utf-8", env=env)
    # Never echo arguments/output from keytool or a failing signing operation.
    if result.returncode:
        raise RuntimeError(f"{Path(args[0]).name} failed (exit {result.returncode})")
    return result.stdout


def api(endpoint: str, *, env: dict | None = None, **fields) -> dict | list:
    args = ["gh", "api", endpoint]
    for key, value in fields.items():
        args.extend(["-f", f"{key}={value}"])
    return json.loads(run(*args, env=env))


def write_json(path: Path, value) -> None:
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def digest(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def output(**values) -> None:
    if os.environ.get("GITHUB_OUTPUT"):
        with Path(os.environ["GITHUB_OUTPUT"]).open("a", encoding="utf-8") as stream:
            for key, value in values.items():
                require("\n" not in str(value), "Invalid multiline workflow output")
                stream.write(f"{key}={value}\n")


def event_source(env: dict = os.environ) -> tuple[str, str]:
    require(env.get("GITHUB_REPOSITORY") == REPO, "Only the upstream repository is allowed")
    require(env.get("GITHUB_EVENT_NAME") in {"workflow_dispatch", "workflow_run"}, "Signing requires native dispatch or completed main CI")
    ref, sha = env.get("GITHUB_REF", ""), env.get("GITHUB_SHA", "")
    require(ref.startswith("refs/heads/"), "Select an existing branch, not a tag or pull request")
    require(bool(SHA.fullmatch(sha)), "A full triggering commit SHA is required")
    if env.get("GITHUB_EVENT_NAME") == "workflow_run":
        event = json.loads(Path(env["GITHUB_EVENT_PATH"]).read_text(encoding="utf-8"))
        record = event.get("workflow_run") or {}
        require(ref == "refs/heads/main" and event.get("action") == "completed"
                and record.get("path") == ".github/workflows/a14-ci.yml" and record.get("head_branch") == "main"
                and record.get("head_sha") == sha and record.get("event") in {"push", "workflow_dispatch"}
                and record.get("head_repository", {}).get("full_name") == REPO
                and record.get("status") == "completed" and record.get("conclusion") == "success",
                "Automatic signing requires successful upstream main CI on the native exact SHA")
    return ref, sha


def source_policy() -> None:
    ref, sha = event_source()
    require(run("git", "rev-parse", "HEAD").strip() == sha, "Checkout differs from triggering SHA")
    authorized = ref.removeprefix("refs/heads/") in POLICY["trusted_branches"]
    output(authorized=str(authorized).lower())
    print("Trusted signing source" if authorized else "Validation only: branch is not authorized for signing")
    if not authorized and os.environ.get("GITHUB_STEP_SUMMARY"):
        with Path(os.environ["GITHUB_STEP_SUMMARY"]).open("a", encoding="utf-8") as stream:
            stream.write("Validation only: this branch is not authorized for signing. No official APK will be created.\n")


def trusted_ci(record: dict, sha: str, workflow_id: int) -> bool:
    return (record.get("workflow_id") == workflow_id and record.get("head_sha") == sha
            and record.get("status") == "completed" and record.get("conclusion") == "success"
            and record.get("event") in {"push", "workflow_dispatch"}
            and record.get("head_repository", {}).get("full_name") == REPO
            and record.get("head_branch") in POLICY["trusted_branches"]
            and record.get("path") == ".github/workflows/a14-ci.yml")


def ci_status() -> None:
    _, sha = event_source()
    if os.environ["GITHUB_EVENT_NAME"] == "workflow_run":
        match = automatic_ci()
        output(reuse="true")
        print(f"Reusing completed main CI run {match['id']} for {sha}")
        return
    workflow = api(f"repos/{REPO}/actions/workflows/a14-ci.yml")
    records = api(f"repos/{REPO}/actions/workflows/{workflow['id']}/runs?head_sha={sha}&status=success&per_page=30")
    match = next((record for record in records["workflow_runs"] if trusted_ci(record, sha, workflow["id"])), None)
    output(reuse=str(match is not None).lower())
    print(f"Reusing trusted A14 CI run {match['id']} for {sha}" if match else "No trusted CI for this SHA; validating source once")


def automatic_ci() -> dict:
    _, sha = event_source()
    event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text(encoding="utf-8"))
    original = event["workflow_run"]
    workflow = api(f"repos/{REPO}/actions/workflows/a14-ci.yml")
    record = api(f"repos/{REPO}/actions/runs/{int(original['id'])}")
    require(trusted_ci(record, sha, workflow["id"]) and record.get("head_branch") == "main"
            and record.get("run_attempt") == original.get("run_attempt"),
            "Triggering main CI is not successful, or its attempt/source changed")
    return record


def require_current_main(sha: str) -> None:
    require(api(f"repos/{REPO}/git/ref/heads/main").get("object", {}).get("sha") == sha,
            "main moved; validate and confirm its latest commit instead")


def require_main_ci(sha: str, *, current: bool = True) -> None:
    if current:
        require_current_main(sha)
    workflow = api(f"repos/{REPO}/actions/workflows/a14-ci.yml")
    records = api(f"repos/{REPO}/actions/workflows/{workflow['id']}/runs?head_sha={sha}&status=success&per_page=30")
    require(any(trusted_ci(record, sha, workflow["id"]) and record.get("head_branch") == "main"
                for record in records["workflow_runs"]), "Publication requires successful A14 CI on this exact main commit")


def confirmation_policy() -> None:
    """Publication cannot interpret an unprotected Environment as approval."""
    environment = api(f"repos/{REPO}/environments/release-signing")
    rules = environment.get("protection_rules", [])
    require(any(rule.get("type") == "required_reviewers" and rule.get("reviewers") for rule in rules),
            "Publication requires a required reviewer on release-signing; configure the protected Environment before retrying")
    policy = environment.get("deployment_branch_policy") or {}
    require(policy.get("custom_branch_policies") is True and policy.get("protected_branches") is False,
            "release-signing must restrict deployment to explicitly selected trusted branches")


def property_escape(value: str) -> str:
    # java.util.Properties.load(InputStream) uses ISO-8859-1 and backslash escapes.
    result = []
    for char in value:
        if char in "\\:=#! ":
            result.append("\\" + char)
        elif char in "\n\r\t\f":
            result.append({"\n": "\\n", "\r": "\\r", "\t": "\\t", "\f": "\\f"}[char])
        elif ord(char) > 126 or ord(char) < 32:
            units = char.encode("utf-16-be")
            result.extend(f"\\u{int.from_bytes(units[i:i+2], 'big'):04x}" for i in range(0, len(units), 2))
        else:
            result.append(char)
    return "".join(result)


def signing_directory() -> Path:
    return Path(os.environ["RUNNER_TEMP"]) / "a14-signing"


def prepare_signing(env: dict = os.environ) -> None:
    ref, _ = event_source(env)
    require(ref.removeprefix("refs/heads/") in POLICY["trusted_branches"], "Branch is not authorized for signing")
    names = ("A14_KEYSTORE_BASE64", "A14_STORE_PASSWORD", "A14_KEY_ALIAS", "A14_KEY_PASSWORD")
    missing = [name for name in names if not env.get(name)]
    require(not missing, "Missing release-signing secrets: " + ", ".join(missing))
    folder = signing_directory()
    folder.mkdir(mode=0o700, exist_ok=False)
    store = folder / "release.p12"
    data = base64.b64decode("".join(env[names[0]].split()), validate=True)
    require(0 < len(data) <= 8 * 1024 * 1024, "Invalid PKCS#12 size")
    store.write_bytes(data)
    store.chmod(0o600)
    certificate = subprocess.run([
        "keytool", "-exportcert", "-storetype", "PKCS12", "-keystore", str(store),
        "-storepass:env", "A14_STORE_PASSWORD", "-alias", env["A14_KEY_ALIAS"],
    ], capture_output=True, env=env)
    require(certificate.returncode == 0, "Cannot open the PKCS#12 signing entry; check environment secrets")
    require(hashlib.sha256(certificate.stdout).hexdigest() == CERT, "Signing certificate differs from the historical A14 certificate")
    props = folder / "keystore.properties"
    values = {"storeFile": store.as_posix(), "storePassword": env[names[1]],
              "keyAlias": env[names[2]], "keyPassword": env[names[3]]}
    props.write_text("".join(f"{key}={property_escape(value)}\n" for key, value in values.items()), encoding="ascii")
    props.chmod(0o600)
    print("Historical signing certificate confirmed; temporary PKCS#12 configuration ready")


def source_version(text: str) -> tuple[str, int]:
    name = re.search(r'^val lastVersionName\s*=\s*"(r14\.\d+\.\d+)"\s*$', text, re.M)
    code = re.search(r"^val lastVersion\s*=\s*(\d+)\s*$", text, re.M)
    require(name is not None and code is not None, "Source version metadata is invalid")
    return name[1], int(code[1])


def dispatch_options() -> tuple[str, bool]:
    """Read native event options; publication cannot come from an env flag."""
    ref, sha = event_source()
    require(os.environ.get("GITHUB_WORKFLOW_REF") == f"{REPO}/{WORKFLOW}@{ref}"
            and os.environ.get("GITHUB_WORKFLOW_SHA") == sha, "Automatic publication requires the native signed-build workflow")
    if os.environ["GITHUB_EVENT_NAME"] == "workflow_run":
        return "both", False
    event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text(encoding="utf-8"))
    inputs = event.get("inputs") or {}
    destination, prerelease = inputs.get("destination"), inputs.get("prerelease")
    require(destination in {"source", "both", "actions-only"}, "Select a release destination explicitly")
    require(prerelease in {"true", "false"}, "Invalid pre-release selection")
    return destination, prerelease == "true"


def check_new_release(name: str, code: int, sha: str, *, resume: bool = False) -> None:
    check_tag(REPO, name, sha)
    require(resume or optional_api(f"repos/{REPO}/releases/tags/{name}") is None,
            f"Release {name} already exists; increment versionName and versionCode, or select actions-only")
    # Include pre-releases, and paginate rather than assuming /latest is newest.
    published = []
    page = 1
    while True:
        records = api(f"repos/{REPO}/releases?per_page=100&page={page}")
        require(resume or not any(record["tag_name"] == name for record in records),
                f"Release or draft {name} already exists; inspect it instead of overwriting")
        published.extend(record["tag_name"] for record in records
                         if not record["draft"] and not (resume and record["tag_name"] == name)
                         and re.fullmatch(r"r14\.\d+\.\d+", record["tag_name"]))
        if len(records) < 100:
            break
        page += 1
    if published:
        previous = max(published, key=lambda tag: tuple(map(int, tag[1:].split("."))))
        content = api(f"repos/{REPO}/contents/app/build.gradle.kts?ref={previous}")
        _, previous_code = source_version(base64.b64decode(content["content"]).decode("utf-8"))
        require(code > previous_code, f"versionCode {code} must exceed published {previous_code} ({previous})")
        require(tuple(map(int, name[1:].split("."))) > tuple(map(int, previous[1:].split("."))),
                f"versionName {name} must exceed published {previous}")


def release_plan() -> None:
    destination, prerelease = dispatch_options()
    _, sha = event_source()
    automatic = os.environ["GITHUB_EVENT_NAME"] == "workflow_run"
    if automatic:
        automatic_ci()
        require_current_main(sha)
    if destination == "actions-only":
        confirmation_policy()
        output(ready="true", destination=destination, prerelease=str(prerelease).lower())
        print("Build a signed candidate only; no Release or tag will be created")
        return
    require(os.environ["GITHUB_REF"] == "refs/heads/main", "Publish from main after CI; use actions-only on a trusted test branch")
    name, code = source_version((ROOT / "app/build.gradle.kts").read_text(encoding="utf-8"))
    existing = optional_api(f"repos/{REPO}/releases/tags/{name}")
    if existing is not None and existing.get("draft") is False:
        output(ready="false", destination=destination, prerelease="false")
        print(f"{name} is already published; no signing or publication was started")
        if os.environ.get("GITHUB_STEP_SUMMARY"):
            with Path(os.environ["GITHUB_STEP_SUMMARY"]).open("a", encoding="utf-8") as summary:
                summary.write(f"### No new release requested\n\n{name} is already published. No signing or publication was started. Use Promote Release with the original candidate for incomplete publication.\n")
        return
    if not automatic:
        require_main_ci(sha)
    check_new_release(name, code, sha)
    local_release_notes(name, code)
    confirmation_policy()
    output(ready="true", destination=destination, prerelease=str(prerelease).lower())
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with Path(os.environ["GITHUB_STEP_SUMMARY"]).open("a", encoding="utf-8") as summary:
            summary.write(f"### Release confirmation\n\n{name} ({code}) · `{sha}`\n\n"
                          "Approve **release-signing** to build and publish this exact version. Reject/cancel to keep it unpublished.\n\n"
                          + "\n\n".join(local_release_notes(name, code)) + "\n")
    print(f"Prepared release request: {name} ({code}), destination={destination}, prerelease={prerelease}")


def release_notes_section(text: str, name: str, code: int, filename: str) -> str:
    try:
        from tools.check_release_metadata import changelog_sections
    except ModuleNotFoundError:
        from check_release_metadata import changelog_sections
    matches = [body.strip() for version, body in changelog_sections(text) if version == name]
    require(len(matches) == 1 and bool(matches[0]), f"Source release notes are missing or ambiguous: {filename}")
    body = matches[0]
    require(re.findall(r"\bversionCode(?:\s*[:=]\s*|\s+)(\d+)\b", body) == [str(code)]
            and re.search(r"^[-*] \S", body, re.M) is not None, f"Release notes need versionCode {code} and change entries: {filename}")
    return body


def local_release_notes(name: str, code: int) -> list[str]:
    try:
        from tools.check_release_metadata import check
    except ModuleNotFoundError:
        from check_release_metadata import check
    errors = check()
    require(not errors, "Release metadata is not aligned: " + "; ".join(errors))
    return [f"## {language}\n\n" + public_release_notes_section((ROOT / filename).read_text(encoding="utf-8"), name, code, filename)
            for filename, language in (("CHANGELOG_CN.md", "简体中文"), ("CHANGELOG.md", "English"))]


def public_release_notes_section(text: str, name: str, code: int, filename: str) -> str:
    """Render reviewed user changes; legacy releases retain their original format."""
    body = release_notes_section(text, name, code, filename)
    user = "用户变化" if filename.endswith("_CN.md") else "User changes"
    engineering = "工程说明" if filename.endswith("_CN.md") else "Engineering notes"
    headings = list(re.finditer(r"^### (.+?)\s*$", body, re.M))
    reserved = {"用户变化", "User changes", "工程说明", "Engineering notes"}
    if not any(item[1] in reserved for item in headings):
        return body
    require([item[1] for item in headings] == [user, engineering],
            f"{filename} needs one ordered user/engineering section pair")
    changes = body[headings[0].end():headings[1].start()].strip()
    count = len(re.findall(r"^[-*] \S", changes, re.M))
    require(4 <= count <= 6, f"{filename} public notes need 4–6 reviewed user changes")
    intro = re.sub(r"^versionCode\s+\d+[。.]\s*", "", body[:headings[0].start()].strip())
    return f"{intro}\n\n{changes}".strip()


def signing_preflight() -> None:
    """Recheck after the human approval, before any signing secret is exposed."""
    destination, _ = dispatch_options()
    _, sha = event_source()
    confirmation_policy()
    if os.environ["GITHUB_EVENT_NAME"] == "workflow_run":
        automatic_ci()
    if destination != "actions-only":
        require_main_ci(sha)
        name, code = source_version((ROOT / "app/build.gradle.kts").read_text(encoding="utf-8"))
        check_new_release(name, code, sha)
        local_release_notes(name, code)


def check_badging(badging: str, name: str, code: int) -> None:
    package = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging, re.M)
    require(package is not None and package.groups() == ("tv.withaibuild.customiuizer.r14", str(code), name), "APK package/version differs from source")
    require("application-debuggable" not in badging, "APK must not be debuggable")
    require(re.search(r"^sdkVersion:'34'$", badging, re.M) is not None, "APK minSdk must be 34")
    require(re.search(r"^targetSdkVersion:'34'$", badging, re.M) is not None, "APK targetSdk must be 34")
    require(re.search(r"^native-code: 'arm64-v8a'\s*$", badging, re.M) is not None, "APK must contain only arm64-v8a native code")


def check_signature(signature: str) -> None:
    require("Verified using v2 scheme (APK Signature Scheme v2): true" in signature, "APK V2 signature verification failed")
    certificates = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([0-9a-f]+)", signature)
    require(certificates == [CERT], "APK must have exactly the historical A14 signer")


def verify_apk(apk: Path, sha: str, name: str, code: int) -> dict:
    require(bool(SHA.fullmatch(sha)), "Invalid source SHA")
    tools = Path(os.environ["ANDROID_HOME"]) / "build-tools" / "36.0.0"
    badging = run(str(tools / "aapt"), "dump", "badging", str(apk))
    check_badging(badging, name, code)
    run(str(tools / "zipalign"), "-c", "-P", "16", "4", str(apk))
    signature = run(str(tools / "apksigner"), "verify", "--verbose", "--print-certs", str(apk))
    check_signature(signature)
    verify_apk_provenance(apk, sha[:8], "release", name, str(code), expected_source_sha=sha)
    return {"application_id": "tv.withaibuild.customiuizer.r14", "version_name": name, "version_code": code,
            "min_sdk": 34, "target_sdk": 34, "abi": "arm64-v8a", "debuggable": False,
            "certificate_sha256": CERT, "apk": apk.name, "apk_sha256": digest(apk), "apk_size": apk.stat().st_size,
            "checks": ["manifest", "zipalign-16k", "apk-v2", "historical-certificate", "embedded-provenance"]}


def candidate_filename(version: str, sha: str) -> str:
    require(isinstance(version, str) and re.fullmatch(r"r14\.\d+\.\d+", version) is not None, "Invalid candidate version")
    require(bool(SHA.fullmatch(sha)), "Invalid candidate source SHA")
    return f"CustoMIUIzer-A14-{version}.apk"


def candidate(folder: Path) -> None:
    ref, sha = event_source()
    require(ref.removeprefix("refs/heads/") in POLICY["trusted_branches"], "Untrusted signing source")
    apks = list((ROOT / "app/build/outputs/apk/release").glob("*.apk"))
    require(len(apks) == 1, "Expected exactly one Release APK")
    for filename in ("mapping.txt", "configuration.txt", "usage.txt"):
        require((ROOT / "app/build/outputs/mapping/release" / filename).is_file(), "Missing R8 outputs")
    name, code = source_version((ROOT / "app/build.gradle.kts").read_text(encoding="utf-8"))
    folder.mkdir(parents=True, exist_ok=False)
    apk = folder / candidate_filename(name, sha)
    shutil.copyfile(apks[0], apk)
    metadata = verify_apk(apk, sha, name, code)
    metadata.update(repository=REPO, source_sha=sha, source_ref=ref, workflow=WORKFLOW,
                    run_id=int(os.environ["GITHUB_RUN_ID"]), run_attempt=int(os.environ["GITHUB_RUN_ATTEMPT"]),
                    repository_url=f"https://github.com/{REPO}",
                    workflow_url=f"https://github.com/{REPO}/blob/{sha}/{WORKFLOW}",
                    run_url=f"https://github.com/{REPO}/actions/runs/{os.environ['GITHUB_RUN_ID']}/attempts/{os.environ['GITHUB_RUN_ATTEMPT']}",
                    recorded_at=datetime.now(timezone.utc).isoformat(),
                    release_assets=[apk.name, "build-metadata.json"])
    write_json(folder / "build-metadata.json", metadata)
    (folder / "SHA256SUMS").write_text(f"{metadata['apk_sha256']}  {metadata['apk']}\n", encoding="ascii")
    output(source_sha=sha, apk_sha256=metadata["apk_sha256"], run_attempt=metadata["run_attempt"])
    print(f"APK checks passed: {name} ({code}), {metadata['apk_sha256']}")


def check_run(record: dict, workflow_id: int, *, current_run: bool = False, publication_failed: bool = False) -> None:
    require(record.get("workflow_id") == workflow_id and record.get("path") == WORKFLOW, "Wrong source workflow")
    require(record.get("event") == "workflow_dispatch" or
            record.get("event") == "workflow_run" and record.get("head_branch") == "main",
            "Source must be a native dispatch or a main CI release")
    if current_run:
        # The publish job is still part of this run. It may consume only the
        # successful sign dependency's immutable artifact/receipt, never another
        # running workflow. Manual promotion still requires a completed run.
        ref, sha = event_source()
        require(record.get("id") == int(os.environ["GITHUB_RUN_ID"])
                and record.get("run_attempt") == int(os.environ["GITHUB_RUN_ATTEMPT"])
                and record.get("head_sha") == sha and "refs/heads/" + record.get("head_branch", "") == ref,
                "Automatic publication cannot select another run, attempt or source")
        require(record.get("status") == "in_progress" and record.get("conclusion") is None,
                "Current signed-build run is not in progress")
    else:
        require(record.get("status") == "completed" and
                (record.get("conclusion") == "success" or publication_failed and record.get("conclusion") == "failure"),
                "Source build did not finish successfully")
    require(record.get("head_repository", {}).get("full_name") == REPO, "Wrong source repository")
    require(record.get("head_branch") in POLICY["trusted_branches"], "Source branch is not authorized")
    require(bool(SHA.fullmatch(record.get("head_sha", ""))), "Invalid source commit")


def check_metadata(metadata: dict, record: dict) -> None:
    expected = {"repository": REPO, "source_sha": record["head_sha"], "source_ref": "refs/heads/" + record["head_branch"],
                "workflow": WORKFLOW, "run_id": record["id"], "run_attempt": record["run_attempt"]}
    require(all(metadata.get(key) == value for key, value in expected.items()), "Artifact metadata differs from the selected workflow run")
    version = metadata.get("version_name", "")
    # Existing attested candidates retain their original names and exact binary identity.
    names = {candidate_filename(version, record["head_sha"]), f"CustoMIUIzer-A14-{version}-{record['head_sha'][:6]}.apk"}
    filename = metadata.get("apk")
    require(isinstance(filename, str) and filename in names, "Invalid APK filename or version/source suffix")
    if "release_assets" in metadata:
        require(filename == candidate_filename(version, record["head_sha"])
                and metadata["release_assets"] == [filename, "build-metadata.json"], "Invalid public release asset contract")


def check_attestation(results: list, sha: str, ref: str, run_id: int, attempt: int,
                      *, filename: str | None = None, sha256: str | None = None) -> None:
    invocation = f"https://github.com/{REPO}/actions/runs/{run_id}/attempts/{attempt}"
    for result in results:
        statement = result.get("verificationResult", {}).get("statement", {})
        predicate = statement.get("predicate", {})
        definition = predicate.get("buildDefinition", {})
        workflow = definition.get("externalParameters", {}).get("workflow", {})
        dependencies = definition.get("resolvedDependencies", [])
        if (statement.get("predicateType") == "https://slsa.dev/provenance/v1"
                and predicate.get("runDetails", {}).get("metadata", {}).get("invocationId") == invocation
                and workflow == {"repository": f"https://github.com/{REPO}", "path": WORKFLOW, "ref": ref}
                and any(item.get("digest", {}).get("gitCommit") == sha for item in dependencies)
                and (filename is None or any(item.get("name") == filename and item.get("digest", {}).get("sha256") == sha256
                                             for item in statement.get("subject", [])))):
            return
    raise ValueError("Verified attestation does not match the original workflow run/source")


def verify_attestations(folder: Path, metadata: dict, filenames: tuple[str, ...]) -> dict:
    reports = {}
    for filename in filenames:
        result = json.loads(run("gh", "attestation", "verify", str(folder / filename), "--repo", REPO,
                                "--signer-workflow", f"{REPO}/{WORKFLOW}", "--signer-digest", metadata["source_sha"],
                                "--source-digest", metadata["source_sha"], "--source-ref", metadata["source_ref"],
                                "--deny-self-hosted-runners", "--format", "json"))
        check_attestation(result, metadata["source_sha"], metadata["source_ref"], metadata["run_id"], metadata["run_attempt"],
                          filename=filename, sha256=digest(folder / filename))
        reports[filename] = result
    return reports


def attest_apk(folder: Path, metadata: dict) -> None:
    """Finalize the manifest only after the APK's online proof actually passes."""
    require("attestation" not in metadata, "Candidate metadata has already been finalized; do not rewrite attested evidence")
    require(digest(folder / metadata["apk"]) == metadata["apk_sha256"], "APK digest differs from candidate metadata")
    verify_attestations(folder, metadata, (metadata["apk"],))
    metadata["attestation"] = {"apk_verified": True, "method": "github-online",
                               "verified_at": datetime.now(timezone.utc).isoformat(),
                               "url": f"https://github.com/{REPO}/attestations"}
    write_json(folder / "build-metadata.json", metadata)


def attest(folder: Path, metadata: dict) -> None:
    if "release_assets" in metadata:
        require(metadata.get("attestation", {}).get("apk_verified") is True
                and metadata["attestation"].get("method") == "github-online", "APK online attestation must pass before metadata attestation")
    reports = verify_attestations(folder, metadata, (metadata["apk"], "build-metadata.json"))
    write_json(folder / "attestation-verification.json", reports)
    print("GitHub provenance verified: repository, workflow, commit, ref, run and file digests")


def fetch_candidate(run_id: str, folder: Path, *, current_run: bool = False) -> dict:
    require(run_id.isdecimal() and int(run_id) > 0, "Build run ID must be a positive integer")
    workflow = api(f"repos/{REPO}/actions/workflows/a14-signed-build.yml")
    record = api(f"repos/{REPO}/actions/runs/{run_id}")
    publication_failed = False
    if not current_run and record.get("status") == "completed" and record.get("conclusion") == "failure":
        jobs = api(f"repos/{REPO}/actions/runs/{run_id}/attempts/{int(record['run_attempt'])}/jobs?per_page=100")
        expected = {"Validate exact source": "success", "Build, sign and verify Release": "success",
                    "Publish verified APK to Release": "failure"}
        publication_failed = (jobs.get("total_count") == len(expected) and len(jobs["jobs"]) == len(expected)
                              and {job["name"]: job["conclusion"] for job in jobs["jobs"]} == expected
                              and all(job["status"] == "completed" for job in jobs["jobs"]))
    check_run(record, workflow["id"], current_run=current_run, publication_failed=publication_failed)
    artifact_name = f"a14-signed-{run_id}-{record['run_attempt']}"
    artifacts = api(f"repos/{REPO}/actions/runs/{run_id}/artifacts?per_page=100")["artifacts"]
    matches = [item for item in artifacts if item["name"] == artifact_name and not item["expired"]]
    require(len(matches) == 1, "Original signed artifact is missing, expired or ambiguous; do not rebuild it")
    require(not folder.exists(), "Use a fresh candidate download directory")
    run("gh", "run", "download", run_id, "--repo", REPO, "--name", artifact_name, "--dir", str(folder))
    metadata = json.loads((folder / "build-metadata.json").read_text(encoding="utf-8"))
    check_metadata(metadata, record)
    apk = folder / metadata["apk"]
    require(list(folder.glob("*.apk")) == [apk], "Artifact must contain exactly one APK")
    require(digest(apk) == metadata.get("apk_sha256"), "APK digest differs from signed build metadata")
    require((folder / "SHA256SUMS").read_text(encoding="ascii") == f"{digest(apk)}  {apk.name}\n", "APK checksum file differs")
    content = api(f"repos/{REPO}/contents/app/build.gradle.kts?ref={record['head_sha']}")
    name, code = source_version(base64.b64decode(content["content"]).decode("utf-8"))
    checked = verify_apk(apk, record["head_sha"], name, code)
    require(all(metadata.get(key) == value for key, value in checked.items()), "APK verification differs from candidate metadata")
    attest(folder, metadata)
    output(version=name, source_sha=record["head_sha"], apk_sha256=checked["apk_sha256"],
           run_attempt=record["run_attempt"], artifact_id=matches[0]["id"])
    print(f"Original candidate verified: {name}, source {record['head_sha']}")
    # Keep the immutable artifact identity in memory, outside the attested metadata file.
    metadata["artifact_id"] = matches[0]["id"]
    return metadata


def optional_api(endpoint: str, *, env: dict | None = None) -> dict | None:
    result = subprocess.run(["gh", "api", endpoint], capture_output=True, text=True, encoding="utf-8", env=env)
    if result.returncode == 0:
        return json.loads(result.stdout)
    require("HTTP 404" in result.stderr, "GitHub permission or API failure; refusing to publish")
    return None


def check_tag(repository: str, tag: str, sha: str, *, must_exist: bool = False, env: dict | None = None) -> None:
    record = optional_api(f"repos/{repository}/git/ref/tags/{tag}", env=env)
    if record is None:
        require(not must_exist, f"Published tag {repository}:{tag} is missing")
        return
    obj = record["object"]
    for _ in range(4):
        if obj["type"] == "commit":
            require(obj["sha"] == sha, f"Existing tag {repository}:{tag} points to another source commit")
            return
        require(obj["type"] == "tag", "Unexpected tag target type")
        obj = api(f"repos/{repository}/git/tags/{obj['sha']}", env=env)["object"]
    raise ValueError("Tag chain exceeds verification limit")


def check_verification_receipt(metadata: dict, env: dict = os.environ) -> None:
    fields = {"VERIFIED_SOURCE_SHA": "source_sha", "VERIFIED_APK_SHA256": "apk_sha256",
              "VERIFIED_RUN_ATTEMPT": "run_attempt", "VERIFIED_ARTIFACT_ID": "artifact_id"}
    require(all(env.get(key) and env[key] == str(metadata.get(field)) for key, field in fields.items()),
            "Original verified artifact changed or receipt is missing; select and verify the build again")


def publish(folder: Path, run_id: str, destination: str, authorization: str, *, prerelease: bool = False) -> None:
    ref, _ = event_source()
    require(ref.removeprefix("refs/heads/") in POLICY["trusted_branches"], "Untrusted promotion workflow branch")
    metadata = fetch_candidate(run_id, folder)
    name, code, sha = metadata["version_name"], metadata["version_code"], metadata["source_sha"]
    require(authorization == f"publish {name} from {run_id}", "Explicit authorization must match version and original build run")
    check_verification_receipt(metadata)
    publish_verified(folder, run_id, destination, metadata, prerelease=prerelease)


def publish_current(folder: Path) -> None:
    destination, prerelease = dispatch_options()
    require(destination != "actions-only", "This dispatch did not authorize publication")
    require(os.environ.get("GITHUB_JOB") == "publish", "Automatic publication requires the protected publish job")
    run_id = os.environ["GITHUB_RUN_ID"]
    metadata = fetch_candidate(run_id, folder, current_run=True)
    check_verification_receipt(metadata)
    publish_verified(folder, run_id, destination, metadata, prerelease=prerelease)


def check_release_assets(record: dict, folder: Path, filenames: tuple[str, ...], *, draft: bool, prerelease: bool, partial: bool = False) -> None:
    require(record["draft"] is draft and record["prerelease"] is prerelease, "Release draft/pre-release state differs")
    require(not draft or record.get("immutable") is not True, "Immutable release cannot be modified")
    assets = record["assets"]
    names = {item["name"] for item in assets}
    require(len(assets) == len(names) and (names <= set(filenames) if partial else names == set(filenames)),
            "Release must contain exactly the expected verified public assets")
    for asset in assets:
        path = folder / asset["name"]
        require(asset.get("digest") == "sha256:" + digest(path) and asset["size"] == path.stat().st_size,
                f"Published asset digest/size differs: {path.name}")


def release_assets(metadata: dict, record: dict | None = None) -> tuple[str, ...]:
    public = (metadata["apk"], "build-metadata.json")
    if "release_assets" in metadata:
        require(metadata["release_assets"] == list(public), "Invalid public release asset contract")
    elif record is not None and any(asset["name"] in {"SHA256SUMS", "attestation.sigstore.json"} for asset in record["assets"]):
        # Preserve an old published release or partially uploaded draft verbatim.
        return (metadata["apk"], "SHA256SUMS", "build-metadata.json", "attestation.sigstore.json")
    return public


def check_release_identity(record: dict, release_id: int, tag: str, sha: str) -> None:
    require(record.get("id") == release_id and record.get("tag_name") == tag
            and record.get("target_commitish") == sha, "Release source identity changed during publication")


def check_release_content(record: dict, title: str, notes: str) -> None:
    require(record.get("name") == title and record.get("body", "").strip() == notes.strip(),
            "Release notes/title differ from the verified candidate")


def find_release(repository: str, tag: str, env: dict) -> dict | None:
    """The by-tag endpoint cannot find drafts; read all pages with the right token."""
    page = 1
    found = None
    while True:
        records = api(f"repos/{repository}/releases?per_page=100&page={page}", env=env)
        for record in records:
            if record["tag_name"] == tag:
                require(found is None, "Ambiguous release identity")
                found = record
            elif repository == POLICY["download_repository"] and not record["draft"]:
                version = re.fullmatch(r"(\d+)-r14\.\d+\.\d+", record["tag_name"])
                require(version is None or int(version[1]) < int(tag.split("-", 1)[0]),
                        "Xposed repository already has an equal or newer versionCode; refusing to roll back")
        if len(records) < 100:
            return found
        page += 1


def publish_verified(folder: Path, run_id: str, destination: str, metadata: dict, *, prerelease: bool = False) -> None:
    name, code, sha = metadata["version_name"], metadata["version_code"], metadata["source_sha"]
    require(destination in {"source", "both"}, "Invalid publication destination")
    repositories = [(REPO, name, dict(os.environ))]
    if destination == "both":
        require(bool(os.environ.get("A14_XPOSED_RELEASE_TOKEN")), "Cross-repository publishing requires A14_XPOSED_RELEASE_TOKEN in release-publishing")
        token_env = dict(os.environ, GH_TOKEN=os.environ["A14_XPOSED_RELEASE_TOKEN"])
        access = json.loads(run("gh", "api", f"repos/{POLICY['download_repository']}", env=token_env))
        require(access.get("permissions", {}).get("push") is True,
                "Download repository contents:write permission is required; source tags containing workflows also need Workflows:write")
        repositories.append((POLICY["download_repository"], f"{code}-{name}", token_env))
    sections = []
    for filename, language in (("CHANGELOG_CN.md", "简体中文"), ("CHANGELOG.md", "English")):
        changelog = api(f"repos/{REPO}/contents/{filename}?ref={sha}")
        body = public_release_notes_section(base64.b64decode(changelog["content"]).decode("utf-8"), name, code, filename)
        sections.append(f"## {language}\n\n{body}")
    notes = folder / "release-notes.md"
    notes.write_text("\n\n".join(sections) + f"\n\n## APK / Provenance\n\nSource: `{sha}`\n\n"
                     f"[Build](https://github.com/{REPO}/actions/runs/{run_id}) · "
                     f"[GitHub attestations](https://github.com/{REPO}/attestations)\n\n"
                     f"APK: `{metadata['apk']}`\n\nAPK SHA-256: `{metadata['apk_sha256']}`\n\n"
                     f"Android signer SHA-256: `{CERT}`\n\n"
                     f"```sh\ngh attestation verify {metadata['apk']} --repo {REPO} "
                     f"--signer-workflow {REPO}/{WORKFLOW} --source-digest {sha} --signer-digest {sha} "
                     f"--source-ref {metadata['source_ref']} --deny-self-hosted-runners\n```\n", encoding="utf-8")
    existing = {}
    # All existing destinations must belong to this exact verified candidate.
    # A matching published source is read-only; a matching draft may be completed
    # by adding missing assets. Never replace assets, tags or another candidate.
    for repository, tag, env in repositories:
        check_tag(repository, tag, sha, env=env)
        record = find_release(repository, tag, env)
        existing[repository] = record
        if record is not None:
            check_release_identity(record, record["id"], tag, sha)
            titles = {name} if "release_assets" in metadata else {name, f"CustoMIUIzer A14 · {name}"}
            require(record.get("name") in titles
                    and record.get("body", "").strip() == notes.read_text(encoding="utf-8").strip(),
                    "Existing release notes/title differ from the verified original candidate")
            check_release_assets(record, folder, release_assets(metadata, record), draft=record["draft"], prerelease=prerelease, partial=record["draft"])
    source = existing[REPO]
    require_main_ci(sha, current=source is None or source["draft"])
    check_new_release(name, code, sha, resume=source is not None)
    for repository, tag, env in repositories:
        record = existing[repository]
        filenames = release_assets(metadata, record)
        if record is not None and not record["draft"]:
            check_tag(repository, tag, sha, must_exist=True, env=env)
            if not prerelease:
                require(api(f"repos/{repository}/releases/latest", env=env)["id"] == record["id"],
                        "Existing stable release is no longer Latest; refusing to roll back publication")
            print(f"Already verified, no changes: {record['html_url']}")
            if os.environ.get("GITHUB_STEP_SUMMARY"):
                with Path(os.environ["GITHUB_STEP_SUMMARY"]).open("a", encoding="utf-8") as summary:
                    summary.write(f"### Existing release verified\n\n[{name}]({record['html_url']})\n\n"
                                  f"Same source, notes and all asset digests; no changes. APK SHA-256: `{metadata['apk_sha256']}`\n")
            continue
        if repository != REPO:
            # The download repository shares source tags, but keeps its metadata main separate.
            run("git", "fetch", "--no-tags", f"https://github.com/{REPO}.git", sha)
            try:
                run("git", "-c", "credential.helper=", "-c", "credential.helper=!gh auth git-credential",
                    "push", f"https://github.com/{repository}.git", f"{sha}:refs/tags/{tag}", env=env)
            except RuntimeError as error:
                raise RuntimeError("Xposed tag push failed; check token Contents/Workflows write, organization approval and connectivity. Recover using the original candidate.") from error
        # Upload and verify every asset while private, then publish. Compatible
        # with repositories that enable immutable releases; never patch binaries.
        draft_title = record["name"] if record is not None else name
        if record is None:
            request = folder / "release-request.json"
            write_json(request, {"tag_name": tag, "target_commitish": sha, "name": name,
                                 "body": notes.read_text(encoding="utf-8"), "draft": True, "prerelease": prerelease})
            created = json.loads(run("gh", "api", f"repos/{repository}/releases", "--method", "POST", "--input", str(request), env=env))
        else:
            created = record
        require(created["draft"] is True and created["tag_name"] == tag, "Draft creation returned another release")
        require(created.get("immutable") is not True, "Immutable release cannot be modified")
        check_release_identity(created, created["id"], tag, sha)
        check_release_content(created, draft_title, notes.read_text(encoding="utf-8"))
        present = {asset["name"] for asset in created.get("assets", [])}
        missing = [str(folder / filename) for filename in filenames if filename not in present]
        if missing:
            run("gh", "release", "upload", tag, *missing, "--repo", repository, env=env)
        # GET by tag is for published releases. Bind draft readback to the
        # numeric ID returned by creation, including when its tag is still new.
        draft = json.loads(run("gh", "api", f"repos/{repository}/releases/{created['id']}", env=env))
        check_release_identity(draft, created["id"], tag, sha)
        check_release_content(draft, draft_title, notes.read_text(encoding="utf-8"))
        check_release_assets(draft, folder, filenames, draft=True, prerelease=prerelease)
        # Before the first publication, current main must still be the approved
        # source. Once that exact source release is publicly verified, syncing
        # its unchanged binary must not be invalidated by later main commits.
        if repository == REPO:
            require_current_main(sha)
        check_tag(repository, tag, sha, env=env)
        run("gh", "release", "edit", tag, "--repo", repository, "--draft=false",
            "--title", name,
            f"--prerelease={str(prerelease).lower()}", f"--latest={str(not prerelease).lower()}", env=env)
        release = json.loads(run("gh", "api", f"repos/{repository}/releases/tags/{tag}", env=env))
        check_release_identity(release, created["id"], tag, sha)
        check_release_content(release, name, notes.read_text(encoding="utf-8"))
        check_release_assets(release, folder, filenames, draft=False, prerelease=prerelease)
        check_tag(repository, tag, sha, must_exist=True, env=env)
        if not prerelease:
            latest = json.loads(run("gh", "api", f"repos/{repository}/releases/latest", env=env))
            require(latest["id"] == release["id"], "Published stable release was not marked Latest")
        if os.environ.get("GITHUB_STEP_SUMMARY"):
            with Path(os.environ["GITHUB_STEP_SUMMARY"]).open("a", encoding="utf-8") as summary:
                summary.write(f"### Published {'pre-release' if prerelease else 'release'}\n\n[{name}]({release['html_url']})\n\n"
                              f"APK: `{metadata['apk']}`\n\nSHA-256: `{metadata['apk_sha256']}`\n\n"
                              "Android signature, GitHub provenance, source tag and all asset digests verified.\n")
        print(release["html_url"])


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["source-policy", "ci-status", "release-plan", "signing-preflight", "prepare-signing", "candidate", "attest-apk", "attest", "fetch", "publish", "publish-current"])
    parser.add_argument("--directory", type=Path)
    parser.add_argument("--run-id")
    parser.add_argument("--destination", choices=["source", "both"], default="source")
    args = parser.parse_args()
    try:
        if args.command == "source-policy": source_policy()
        elif args.command == "ci-status": ci_status()
        elif args.command == "release-plan": release_plan()
        elif args.command == "signing-preflight": signing_preflight()
        elif args.command == "prepare-signing": prepare_signing()
        elif args.command == "candidate": candidate(args.directory)
        elif args.command == "attest-apk": attest_apk(args.directory, json.loads((args.directory / "build-metadata.json").read_text(encoding="utf-8")))
        elif args.command == "attest": attest(args.directory, json.loads((args.directory / "build-metadata.json").read_text(encoding="utf-8")))
        elif args.command == "fetch": fetch_candidate(args.run_id or "", args.directory)
        elif args.command == "publish":
            prerelease = os.environ.get("RELEASE_PRERELEASE", "false")
            require(prerelease in {"true", "false"}, "Invalid promotion pre-release selection")
            publish(args.directory, args.run_id or "", args.destination, os.environ.get("RELEASE_AUTHORIZATION", ""), prerelease=prerelease == "true")
        elif args.command == "publish-current": publish_current(args.directory)
    except (ValueError, RuntimeError, OSError, KeyError) as error:
        print(f"signed_release: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
