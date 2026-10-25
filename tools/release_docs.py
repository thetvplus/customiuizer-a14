"""Review the published baseline and synchronize download metadata, without APK writes."""
from __future__ import annotations

import base64
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
from urllib.parse import quote

try:
    from tools import signed_release as release
    from tools.check_release_metadata import changelog_sections, source_changelogs
except ModuleNotFoundError:
    import signed_release as release
    from check_release_metadata import changelog_sections, source_changelogs

DOWNLOAD = release.POLICY["download_repository"]
FILES = {"README.md", "README_EN.md", "CHANGELOG.md", "CHANGELOG_CN.md", "SUMMARY", "SCOPE", "SOURCE_URL"}


def blob_sha(text: str) -> str:
    data = text.encode("utf-8")
    return hashlib.sha1(f"blob {len(data)}\0".encode() + data).hexdigest()


def source_text(path: str, sha: str) -> str:
    item = release.api(f"repos/{release.REPO}/contents/{path}?ref={sha}")
    data = base64.b64decode(item["content"].replace("\n", ""), validate=True)
    release.require(len(data) <= 2 * 1024 * 1024, "Source document is too large")
    return data.decode("utf-8")


def metadata_bytes(asset_id: int) -> bytes:
    result = subprocess.run(["gh", "api", f"repos/{release.REPO}/releases/assets/{asset_id}",
                             "-H", "Accept: application/octet-stream"], capture_output=True)
    if result.returncode:
        raise RuntimeError("Cannot read published metadata bytes")
    return result.stdout


def published_version(version: str | None = None, *, download_env: dict | None = None,
                      require_download: bool = True, latest_only: bool = True) -> dict:
    """Read current stable releases and independently match public asset digests and tags."""
    release.require(latest_only or (version is not None and not require_download),
                    "Historical inspection requires an explicit source release")
    endpoint = "latest" if latest_only else "tags/" + quote(version, safe="")
    source = release.api(f"repos/{release.REPO}/releases/{endpoint}")
    name = source["tag_name"]
    release.require(re.fullmatch(r"r14\.\d+\.\d+", name) is not None and
                    (version is None or name == version), "Only the latest published stable version may update documents")
    release.require(source.get("draft") is False and source.get("prerelease") is False,
                    "A draft or prerelease cannot update stable documents")
    assets = {item["name"]: item for item in source["assets"]}
    release.require(len(assets) == len(source["assets"]) and "build-metadata.json" in assets,
                    "Published metadata is missing or ambiguous")
    metadata_asset = assets["build-metadata.json"]
    release.require(0 < metadata_asset["size"] <= 65536, "Invalid published metadata size")
    # Hash the original bytes, including Windows CRLF, before decoding JSON.
    data = metadata_bytes(metadata_asset["id"])
    release.require(len(data) == metadata_asset["size"] and
                    "sha256:" + hashlib.sha256(data).hexdigest() == metadata_asset.get("digest"),
                    "Published metadata bytes differ from the asset digest/size")
    metadata = json.loads(data)
    code, sha, apk = metadata["version_code"], metadata["source_sha"], metadata["apk"]
    release.require(metadata["version_name"] == name and isinstance(code, int) and code > 0 and
                    release.SHA.fullmatch(sha) is not None and
                    re.fullmatch(r"CustoMIUIzer-A14-[A-Za-z0-9.-]+\.apk", apk) is not None and
                    re.fullmatch(r"[0-9a-f]{64}", metadata["apk_sha256"]) is not None,
                    "Invalid published version or binary identity")
    release.require(metadata["repository"] == release.REPO and metadata["workflow"] == release.WORKFLOW and
                    metadata["source_ref"] == "refs/heads/main" and metadata["certificate_sha256"] == release.CERT and
                    metadata["application_id"] == "tv.withaibuild.customiuizer.r14" and
                    metadata["min_sdk"] == metadata["target_sdk"] == 34 and metadata["abi"] == "arm64-v8a" and
                    metadata["debuggable"] is False, "Published metadata violates the A14 release contract")
    target = None
    repositories = [(release.REPO, source, name, None)]
    if require_download:
        target = release.api(f"repos/{DOWNLOAD}/releases/latest", env=download_env)
        tag = f"{code}-{name}"
        release.require(target.get("draft") is False and target.get("prerelease") is False and
                        target.get("tag_name") == tag, "Both repositories must publish the same latest stable version")
        repositories.append((DOWNLOAD, target, tag, download_env))
    for repository, record, expected_tag, env in repositories:
        release.check_release_identity(record, record["id"], expected_tag, sha)
        release.check_tag(repository, expected_tag, sha, must_exist=True, env=env)
        entries = {item["name"]: item for item in record["assets"]}
        release.require(len(entries) == len(record["assets"]) and apk in entries and
                        entries[apk].get("digest") == "sha256:" + metadata["apk_sha256"] and
                        entries[apk]["size"] == metadata["apk_size"] and
                        entries.get("build-metadata.json", {}).get("digest") == metadata_asset["digest"] and
                        entries["build-metadata.json"]["size"] == metadata_asset["size"],
                        "The two published APK/metadata assets differ")
    return dict(metadata=metadata, source=source, download=target)


def documents(published: dict) -> dict[str, str]:
    meta, record = published["metadata"], published["source"]
    name, code, sha, apk = (meta[key] for key in ("version_name", "version_code", "source_sha", "apk"))
    root = release.api(f"repos/{release.REPO}/contents?ref={sha}")
    layout = source_changelogs({item["name"] for item in root if item.get("type") == "file"})
    scope = [line.strip() for line in source_text("app/src/main/resources/META-INF/xposed/scope.list", sha).splitlines()
             if line.strip() and not line.lstrip().startswith("#")]
    props = source_text("app/src/main/resources/META-INF/xposed/module.prop", sha)
    release.require(scope and len(scope) == len(set(scope)) and all(re.fullmatch(r"[A-Za-z0-9_.]+", item) for item in scope)
                    and re.search(r"^minApiVersion=102\s*$", props, re.M), "Unsupported source scope/API contract")
    source_url = f"https://github.com/{release.REPO}"
    download_url = f"https://github.com/{DOWNLOAD}"
    tag = f"{code}-{name}"
    retained = set()
    for page in range(1, 101):
        records = release.api(f"repos/{release.REPO}/releases?per_page=100&page={page}")
        retained.update(item["tag_name"] for item in records if not item["draft"] and not item["prerelease"])
        if len(records) < 100:
            break
    else:
        raise ValueError("Release history exceeds the bounded document review limit")
    result = {"SUMMARY": "米客 A14｜CustoMIUIzer A14\n", "SOURCE_URL": source_url + "\n",
              "SCOPE": json.dumps(scope, ensure_ascii=False, indent=2) + "\n"}
    for filename, language in layout:
        chinese = language == "简体中文"
        text = source_text(filename, sha)
        sections = changelog_sections(text)
        current = release.public_release_notes_section(text, name, code, filename, language=language)
        history = []
        for version, body in sections:
            if version in retained:
                codes = re.findall(r"\bversionCode(?:\s*[:=]\s*|\s+)(\d+)\b", body)
                release.require(len(codes) == 1, "Historical release versionCode is missing or ambiguous")
                public = release.public_release_notes_section(text, version, int(codes[0]), filename, language=language)
                history.append(f"## {version}\n\n{public}\n\n[{'发行记录' if chinese else 'Release'}]({source_url}/releases/tag/{version})")
        release.require(any(version == name for version, _ in sections) and name in retained, "Published changelog is missing")
        target = "CHANGELOG_CN.md" if chinese else "CHANGELOG.md"
        language_link = "简体中文 | [English](CHANGELOG.md)" if chinese else "[简体中文](CHANGELOG_CN.md) | English"
        result[target] = "# 米客 A14 — " + ("更新记录" if chinese else "Changelog") + f"\n\n{language_link}\n\n" + "\n\n".join(history) + "\n"
        readme = "README.md" if chinese else "README_EN.md"
        link = "简体中文 | [English](README_EN.md)" if chinese else "[简体中文](README.md) | English"
        intro = ("面向 **HyperOS 1 / Android 14 / arm64-v8a** 的系统界面与交互定制模块，需要 **libxposed API 102 或更高版本**。"
                 if chinese else "System UI and interaction customization for **HyperOS 1 / Android 14 / arm64-v8a**, requiring **libxposed API 102 or later**.")
        install = ("1. 下载并安装正式 APK；同签名覆盖安装保留应用数据。\n2. 在 Xposed 框架中启用模块并选择所需作用域。\n3. 重启相关进程或设备，按设置页提示启用功能。\n\n其他 Android / HyperOS 大版本不在支持范围内。请勿与上游或其他 CustoMIUIzer 派生模块同时启用。"
                   if chinese else "1. Install the official APK; updates with the same signature preserve app data.\n2. Enable the module in your Xposed framework and select the required scope.\n3. Restart the affected processes or device and follow the settings instructions.\n\nOther Android / HyperOS major versions are unsupported. Do not enable alongside upstream or another CustoMIUIzer fork.")
        result[readme] = (f"# 米客 A14｜CustoMIUIzer A14\n\n{link}\n\n{intro}\n\n"
                         f"[{'下载正式 APK' if chinese else 'Download official APK'}]({download_url}/releases/download/{tag}/{quote(apk)}) · "
                         f"[{'发行说明' if chinese else 'Release notes'}]({download_url}/releases/tag/{tag}) · "
                         f"[{'更新记录' if chinese else 'Changelog'}]({target})\n\n"
                         f"## {'当前正式版' if chinese else 'Current stable release'}\n\n"
                         f"`{name}` · versionCode `{code}` · `{meta['application_id']}`\n\n"
                         f"APK: `{apk}` ({meta['apk_size']:,} bytes)\n\nSHA-256: `{meta['apk_sha256']}`\n\n"
                         f"[{'源码提交' if chinese else 'Source commit'}]({source_url}/commit/{sha}): `{sha}`\n\n"
                         f"## {'本版变化' if chinese else 'Changes'}\n\n{current}\n\n"
                         f"## {'安装与兼容' if chinese else 'Installation and compatibility'}\n\n{install}\n\n"
                         f"[{'使用说明与支持' if chinese else 'Usage and support'}]({source_url}/tree/{sha}) · "
                         f"[{'兼容矩阵' if chinese else 'Compatibility'}]({source_url}/blob/{sha}/COMPATIBILITY.md) · "
                         f"[{'反馈问题' if chinese else 'Report an issue'}]({source_url}/issues)\n\n"
                         + ("此仓库只维护正式下载说明。开发分支的未发布内容不代表本 APK 的功能或验收结果。\n" if chinese else
                            "This repository documents published downloads. Unreleased development changes do not describe this APK or its device acceptance.\n"))
    release.require(set(result) == FILES, "Unexpected download document path")
    return result


def write_api(endpoint: str, payload: dict, env: dict, method: str = "POST") -> dict:
    result = subprocess.run(["gh", "api", endpoint, "--method", method, "--input", "-"],
                            input=json.dumps(payload), text=True, encoding="utf-8", capture_output=True, env=env)
    if result.returncode:
        raise RuntimeError("Document Git API write failed; retry sync-docs, without rebuilding or publishing assets")
    return json.loads(result.stdout)


def sync_published(version: str | None, *, apply: bool, env: dict | None = None) -> str:
    env = dict(os.environ) if env is None else env
    published = published_version(version, download_env=env)
    contents = documents(published)
    head = release.api(f"repos/{DOWNLOAD}/git/ref/heads/main", env=env)["object"]["sha"]
    commit = release.api(f"repos/{DOWNLOAD}/git/commits/{head}", env=env)
    tree_sha = commit["tree"]["sha"]
    tree = release.api(f"repos/{DOWNLOAD}/git/trees/{tree_sha}", env=env)
    release.require(not tree.get("truncated"), "Download tree is incomplete")
    existing = {entry["path"]: entry for entry in tree["tree"]}
    changed = {path: text for path, text in contents.items() if existing.get(path, {}).get("sha") != blob_sha(text)}
    release.require(all(existing.get(path, {}).get("mode", "100644") == "100644" for path in contents),
                    "Download documents must be regular files")
    if not changed:
        print(f"Download documents already match {published['metadata']['version_name']}: {head}")
        return head
    print("Download document changes: " + ", ".join(sorted(changed)))
    if not apply:
        return head
    payload = {"base_tree": tree_sha, "tree": [{"path": path, "mode": "100644", "type": "blob", "content": text}
                                               for path, text in sorted(changed.items())]}
    new_tree = write_api(f"repos/{DOWNLOAD}/git/trees", payload, env)
    new_commit = write_api(f"repos/{DOWNLOAD}/git/commits", {"message": f"docs: synchronize verified {published['metadata']['version_name']}",
                           "tree": new_tree["sha"], "parents": [head]}, env)
    # Recheck Latest and compare-and-swap ancestry before the only public mutation.
    published_version(published["metadata"]["version_name"], download_env=env)
    release.require(release.api(f"repos/{DOWNLOAD}/git/ref/heads/main", env=env)["object"]["sha"] == head,
                    "Download main advanced; review and retry document synchronization")
    write_api(f"repos/{DOWNLOAD}/git/refs/heads/main", {"sha": new_commit["sha"], "force": False}, env, "PATCH")
    actual = release.api(f"repos/{DOWNLOAD}/git/ref/heads/main", env=env)["object"]["sha"]
    actual_tree = release.api(f"repos/{DOWNLOAD}/git/commits/{actual}", env=env)["tree"]["sha"]
    readback = release.api(f"repos/{DOWNLOAD}/git/trees/{actual_tree}", env=env)
    entries = {item["path"]: item["sha"] for item in readback["tree"]}
    release.require(all(entries.get(path) == blob_sha(text) for path, text in contents.items()), "Download document readback differs")
    print(f"Download documents synchronized: {actual}")
    return actual


def notes_baseline(version: str) -> dict:
    """An older notes baseline needs a published stable tag, not modern metadata assets."""
    release.require(re.fullmatch(r"r14\.\d+\.\d+", version) is not None, "Invalid cumulative notes baseline")
    record = release.api(f"repos/{release.REPO}/releases/tags/{quote(version, safe='')}")
    release.require(record.get("tag_name") == version and record.get("draft") is False and record.get("prerelease") is False,
                    "Notes baseline must be an actually published stable release")
    target = release.api(f"repos/{release.REPO}/git/ref/tags/{version}")["object"]
    for _ in range(4):
        if target.get("type") == "commit":
            break
        release.require(target.get("type") == "tag", "Invalid notes-baseline tag target")
        target = release.api(f"repos/{release.REPO}/git/tags/{target['sha']}")["object"]
    sha = target.get("sha", "")
    release.require(target.get("type") == "commit" and release.SHA.fullmatch(sha) is not None,
                    "Notes-baseline tag chain is invalid or too deep")
    name, code = release.source_version(source_text("app/build.gradle.kts", sha))
    release.require(name == version, "Notes-baseline tag and source version disagree")
    return {"metadata": {"version_name": version, "version_code": code, "source_sha": sha}, "source": record}


def review_candidate(name: str, code: int, sha: str) -> str:
    """Keep publication safety and cumulative user-content comparisons independent."""
    safety = published_version(require_download=False)
    safe_meta = safety["metadata"]
    release.require(code > safe_meta["version_code"], "Candidate versionCode must exceed the published safety baseline")
    release.require(tuple(map(int, name[1:].split("."))) > tuple(map(int, safe_meta["version_name"][1:].split("."))),
                    "Candidate versionName must exceed the published safety baseline")
    cumulative = release.POLICY.get("cumulative_releases", {}).get(name)
    if cumulative is not None:
        release.require(set(cumulative) == {"notes_base"}, "Invalid cumulative notes policy")
        baseline = notes_baseline(cumulative["notes_base"])
    else:
        baseline = safety
    meta = baseline["metadata"]
    release.require(meta["version_code"] <= safe_meta["version_code"], "Notes baseline cannot be newer than the safety baseline")
    release.run("git", "merge-base", "--is-ancestor", meta["source_sha"], safe_meta["source_sha"])
    release.run("git", "merge-base", "--is-ancestor", safe_meta["source_sha"], sha)
    changes = release.run("git", "diff", "--name-status", meta["source_sha"], sha, "--").strip()
    release.require(bool(changes), "Candidate has no changes from its notes baseline")
    counts = []
    for filename, language in release.CURRENT_CHANGELOGS:
        text = (release.ROOT / filename).read_text(encoding="utf-8")
        body = release.release_notes_section(text, name, code, filename)
        marker = f"自 {meta['version_name']} 起的变更：" if language == "简体中文" else f"Changes since {meta['version_name']}:"
        release.require(marker in body.split("###", 1)[0], "Changelog baseline must match the designated notes baseline, independent of the last successfully published safety baseline")
        public = release.public_release_notes_section(text, name, code, filename, language=language)
        counts.append(len(re.findall(r"^[-*] \S", public, re.M)))
    release.require(counts[0] == counts[1], "Bilingual public notes must cover the same number of core changes")
    return (f"### Release baselines and complete change review\n\n"
            f"Published safety baseline: [{safe_meta['version_name']}]({safety['source']['html_url']}) · `{safe_meta['source_sha']}`\n\n"
            f"Cumulative notes baseline: [{meta['version_name']}]({baseline['source']['html_url']}) · `{meta['source_sha']}`\n\n"
            f"Candidate: `{sha}`\n\n"
            f"[Complete cumulative source comparison](https://github.com/{release.REPO}/compare/{meta['source_sha']}...{sha})\n\n"
            "Both historical releases and Git tags remain intact. The bilingual Changelog is reviewed as source; "
            "this inventory checks identity and coverage structure, not device acceptance or semantic equivalence.\n\n"
            f"```text\n{changes}\n```\n\n")
