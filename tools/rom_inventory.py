#!/usr/bin/env python3
"""Offline ROM sample inventory scanner.

Scans a directory of local APK/JAR/zip/dex samples, computes SHA-256,
extracts package and version information where possible, and optionally
invokes apkanalyzer/jadx/javap when they are available on the local system.
It generates class/method/field manifests from the samples and writes a
JSON/CSV catalog.

This tool is deliberately offline-only: it never downloads ROMs from the
network and it degrades safely when external tools are missing.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import io
import json
import os
import re
import shutil
import struct
import subprocess
import sys
import tempfile
import time
import zipfile
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Any, Optional

REPO_ROOT = Path(__file__).resolve().parent.parent

EXTERNAL_TOOLS = ("apkanalyzer", "jadx", "javap")

SAMPLE_FIELDS = (
    "sampleId",
    "device",
    "codename",
    "androidApi",
    "sdk",
    "hyperosVersion",
    "fingerprint",
    "packageName",
    "process",
    "appVersion",
    "sha256",
    "source",
    "sampleType",
    "verificationStatus",
)


def fail(message: str, code: int = 2) -> None:
    print(f"rom_inventory: {message}", file=sys.stderr)
    sys.exit(code)


# ---------------------------------------------------------------------------
# Hashing and file type detection
# ---------------------------------------------------------------------------


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(8192), b""):
            h.update(chunk)
    return h.hexdigest()


def is_dex(data: bytes) -> bool:
    return len(data) >= 8 and data[:4] == b"dex\n" and data[7:8] == b"\x00"


def is_zip(path: Path) -> bool:
    try:
        with open(path, "rb") as f:
            return f.read(4) in (b"PK\x03\x04", b"PK\x05\x06", b"PK\x07\x08")
    except OSError:
        return False


def detect_sample_type(path: Path) -> str:
    suffix = path.suffix.lower()
    if suffix == ".apk":
        return "APK"
    if suffix == ".jar":
        return "JAR"
    if suffix == ".zip":
        return "ZIP"
    if suffix == ".dex":
        return "DEX"
    if is_zip(path):
        return "ZIP"
    try:
        with open(path, "rb") as f:
            head = f.read(8)
        if is_dex(head):
            return "DEX"
    except OSError:
        pass
    return "UNKNOWN"


# ---------------------------------------------------------------------------
# Optional external tools
# ---------------------------------------------------------------------------


def find_external_tools() -> dict[str, Optional[str]]:
    return {name: shutil.which(name) for name in EXTERNAL_TOOLS}


def run_external(name: str, cmd: list[str], timeout: int = 120) -> tuple[int, str, str]:
    try:
        proc = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            timeout=timeout,
            encoding="utf-8",
            errors="replace",
        )
        return proc.returncode, proc.stdout, proc.stderr
    except FileNotFoundError:
        return -1, "", f"{name} not found"
    except subprocess.TimeoutExpired:
        return -2, "", f"{name} timed out"


def apkanalyzer_manifest(apk: Path, tools: dict[str, Optional[str]]) -> dict[str, Any]:
    binary = tools.get("apkanalyzer")
    if not binary:
        return {}
    result: dict[str, Any] = {}
    for key, sub in (
        ("packageName", "application-id"),
        ("versionCode", "version-code"),
        ("versionName", "version-name"),
        ("minSdkVersion", "min-sdk"),
        ("targetSdkVersion", "target-sdk"),
    ):
        code, out, _ = run_external(
            "apkanalyzer", [binary, "manifest", sub, str(apk)], timeout=30
        )
        if code == 0:
            out = out.strip()
            if sub in ("version-code", "min-sdk", "target-sdk"):
                try:
                    result[key] = int(out)
                except ValueError:
                    result[key] = out
            else:
                result[key] = out
    return result


def javap_manifest(jar: Path, tools: dict[str, Optional[str]]) -> dict[str, Any]:
    binary = tools.get("javap")
    if not binary:
        return {}
    code, out, err = run_external(
        "javap", [binary, "-classpath", str(jar), "-public", "-p", "-s"], timeout=120
    )
    if code != 0:
        return {"warnings": [f"javap failed: {err.strip()[:200]}"]}
    return {"raw_javap": out[:50_000]}


def jadx_manifest(path: Path, tools: dict[str, Optional[str]]) -> dict[str, Any]:
    binary = tools.get("jadx")
    if not binary:
        return {}
    with tempfile.TemporaryDirectory() as tmp:
        out_dir = Path(tmp) / "out"
        code, out, err = run_external(
            "jadx",
            [binary, "-d", str(out_dir), "--no-res", "--no-src", str(path)],
            timeout=300,
        )
        if code != 0:
            return {"warnings": [f"jadx failed: {err.strip()[:200]}"]}
        classes: list[str] = []
        for root, _, files in os.walk(out_dir):
            for f in files:
                if f.endswith(".java"):
                    classes.append(str(Path(root, f).relative_to(out_dir)))
        return {"decompiled_sources": classes}


# ---------------------------------------------------------------------------
# DEX parsing
# ---------------------------------------------------------------------------

NO_INDEX = 0xFFFFFFFF


def uleb128(data: bytes, offset: int) -> tuple[int, int]:
    result = 0
    shift = 0
    i = 0
    while True:
        b = data[offset + i]
        result |= (b & 0x7F) << shift
        i += 1
        if (b & 0x80) == 0:
            break
        shift += 7
    return result, i


def read_dex_string(data: bytes, offset: int) -> str:
    _, consumed = uleb128(data, offset)
    start = offset + consumed
    end = data.index(b"\x00", start)
    return data[start:end].decode("utf-8", errors="replace")


def parse_dex(data: bytes) -> dict[str, Any]:
    if not is_dex(data):
        raise ValueError("not a DEX file")

    magic, checksum, signature = struct.unpack_from("<8s I 20s", data, 0)
    (
        file_size,
        header_size,
        endian_tag,
        link_size,
        link_off,
        map_off,
        string_ids_size,
        string_ids_off,
        type_ids_size,
        type_ids_off,
        proto_ids_size,
        proto_ids_off,
        field_ids_size,
        field_ids_off,
        method_ids_size,
        method_ids_off,
        class_defs_size,
        class_defs_off,
        data_size,
        data_off,
    ) = struct.unpack_from("<20I", data, 32)

    if header_size < 0x70:
        raise ValueError(f"unsupported DEX header size {header_size}")

    string_ids = [
        struct.unpack_from("<I", data, string_ids_off + i * 4)[0]
        for i in range(string_ids_size)
    ]
    strings = [read_dex_string(data, off) for off in string_ids]

    type_ids = [
        struct.unpack_from("<I", data, type_ids_off + i * 4)[0]
        for i in range(type_ids_size)
    ]
    types = [strings[t] for t in type_ids]

    proto_ids: list[dict[str, Any]] = []
    for i in range(proto_ids_size):
        shorty_idx, return_type_idx, parameters_off = struct.unpack_from(
            "<III", data, proto_ids_off + i * 12
        )
        params: list[str] = []
        if parameters_off:
            param_count = struct.unpack_from("<I", data, parameters_off)[0]
            param_offsets = parameters_off + 4
            for j in range(param_count):
                p_idx = struct.unpack_from("<H", data, param_offsets + j * 2)[0]
                params.append(types[p_idx])
        proto_ids.append(
            {
                "shorty": strings[shorty_idx],
                "return": types[return_type_idx],
                "parameters": params,
            }
        )

    field_ids: list[tuple[int, int, int]] = []
    for i in range(field_ids_size):
        class_idx, type_idx, name_idx = struct.unpack_from(
            "<HHI", data, field_ids_off + i * 8
        )
        field_ids.append((class_idx, type_idx, name_idx))

    method_ids: list[tuple[int, int, int]] = []
    for i in range(method_ids_size):
        class_idx, proto_idx, name_idx = struct.unpack_from(
            "<HHI", data, method_ids_off + i * 8
        )
        method_ids.append((class_idx, proto_idx, name_idx))

    def format_field(field_idx: int) -> str:
        class_idx, type_idx, name_idx = field_ids[field_idx]
        return f"{types[class_idx]}->{strings[name_idx]}:{types[type_idx]}"

    def format_method(method_idx: int) -> str:
        class_idx, proto_idx, name_idx = method_ids[method_idx]
        proto = proto_ids[proto_idx]
        desc = f"({''.join(proto['parameters'])}){proto['return']}"
        return f"{types[class_idx]}->{strings[name_idx]}{desc}"

    def parse_class_data_off(off: int) -> dict[str, Any]:
        pos = off
        static_count, n = uleb128(data, pos)
        pos += n
        instance_count, n = uleb128(data, pos)
        pos += n
        direct_count, n = uleb128(data, pos)
        pos += n
        virtual_count, n = uleb128(data, pos)
        pos += n

        fields: list[dict[str, str]] = []
        for count in (static_count, instance_count):
            last = 0
            for _ in range(count):
                diff, n = uleb128(data, pos)
                pos += n
                _, n = uleb128(data, pos)
                pos += n
                last += diff
                fields.append({"signature": format_field(last), "kind": "field"})

        methods: list[dict[str, str]] = []
        for count in (direct_count, virtual_count):
            last = 0
            for _ in range(count):
                diff, n = uleb128(data, pos)
                pos += n
                _, n = uleb128(data, pos)
                pos += n
                _, n = uleb128(data, pos)
                pos += n
                last += diff
                methods.append({"signature": format_method(last), "kind": "method"})

        return {"fields": fields, "methods": methods}

    classes: list[dict[str, Any]] = []
    for i in range(class_defs_size):
        (
            class_idx,
            access_flags,
            superclass_idx,
            interfaces_off,
            source_file_idx,
            annotations_off,
            class_data_off,
            static_values_off,
        ) = struct.unpack_from("<8I", data, class_defs_off + i * 32)

        class_name = types[class_idx]
        superclass = types[superclass_idx] if superclass_idx != NO_INDEX else ""
        source_file = (
            strings[source_file_idx] if source_file_idx != NO_INDEX else ""
        )

        class_data = (
            parse_class_data_off(class_data_off)
            if class_data_off
            else {"fields": [], "methods": []}
        )

        classes.append(
            {
                "className": class_name,
                "superclass": superclass,
                "sourceFile": source_file,
                "accessFlags": access_flags,
                "fields": class_data["fields"],
                "methods": class_data["methods"],
            }
        )

    return {
        "classCount": len(classes),
        "methodCount": sum(len(c["methods"]) for c in classes),
        "fieldCount": sum(len(c["fields"]) for c in classes),
        "classes": classes,
    }


# ---------------------------------------------------------------------------
# Java class file parsing
# ---------------------------------------------------------------------------


def _class_to_smali(name: str) -> str:
    return "L" + name.replace(".", "/") + ";"


def parse_class_file(data: bytes) -> dict[str, Any]:
    if data[:4] != b"\xca\xfe\xba\xbe":
        raise ValueError("not a Java class file")

    minor, major, cp_count = struct.unpack_from(">HHH", data, 4)
    pos = 10
    cp: list[Optional[dict[str, Any]]] = [None]
    i = 1
    while i < cp_count:
        tag = data[pos]
        pos += 1
        if tag == 1:  # Utf8
            length = struct.unpack_from(">H", data, pos)[0]
            pos += 2
            raw = data[pos : pos + length]
            pos += length
            cp.append({"tag": tag, "value": raw.decode("utf-8", errors="replace")})
        elif tag in (3,):
            value = struct.unpack_from(">i", data, pos)[0]
            pos += 4
            cp.append({"tag": tag, "value": value})
        elif tag in (4,):
            value = struct.unpack_from(">f", data, pos)[0]
            pos += 4
            cp.append({"tag": tag, "value": value})
        elif tag in (5,):
            value = struct.unpack_from(">q", data, pos)[0]
            pos += 8
            cp.append({"tag": tag, "value": value})
            cp.append(None)
            i += 1
        elif tag in (6,):
            value = struct.unpack_from(">d", data, pos)[0]
            pos += 8
            cp.append({"tag": tag, "value": value})
            cp.append(None)
            i += 1
        elif tag in (7, 8, 19, 20):  # Class / String / Module / Package
            idx = struct.unpack_from(">H", data, pos)[0]
            pos += 2
            cp.append({"tag": tag, "name_index": idx})
        elif tag == 16:  # MethodType
            idx = struct.unpack_from(">H", data, pos)[0]
            pos += 2
            cp.append({"tag": tag, "descriptor_index": idx})
        elif tag in (9, 10, 11):  # Fieldref / Methodref / InterfaceMethodref
            class_idx, nat_idx = struct.unpack_from(">HH", data, pos)
            pos += 4
            cp.append({"tag": tag, "class_index": class_idx, "name_and_type_index": nat_idx})
        elif tag == 12:  # NameAndType
            name_idx, desc_idx = struct.unpack_from(">HH", data, pos)
            pos += 4
            cp.append({"tag": tag, "name_index": name_idx, "descriptor_index": desc_idx})
        elif tag in (17, 18):  # Dynamic / InvokeDynamic
            bsm_idx, nat_idx = struct.unpack_from(">HH", data, pos)
            pos += 4
            cp.append({"tag": tag, "bootstrap_method_attr_index": bsm_idx, "name_and_type_index": nat_idx})
        elif tag == 15:  # MethodHandle
            ref_kind = data[pos]
            ref_idx = struct.unpack_from(">H", data, pos + 1)[0]
            pos += 3
            cp.append({"tag": tag, "reference_kind": ref_kind, "reference_index": ref_idx})
        elif tag == 14:
            pos += 4
            cp.append({"tag": tag})
        else:
            raise ValueError(f"unsupported constant pool tag {tag} at index {i}")
        i += 1

    def get_utf8(idx: int) -> str:
        entry = cp[idx]
        return entry["value"] if entry and entry.get("tag") == 1 else ""

    access_flags, this_class, super_class, interfaces_count = struct.unpack_from(
        ">HHHH", data, pos
    )
    pos += 8
    pos += 2 * interfaces_count

    def skip_attributes(data: bytes, pos: int, count: int) -> int:
        for _ in range(count):
            _, attr_len = struct.unpack_from(">HI", data, pos)
            pos += 6 + attr_len
        return pos

    fields_count = struct.unpack_from(">H", data, pos)[0]
    pos += 2
    fields: list[str] = []
    for _ in range(fields_count):
        _, name_idx, desc_idx, attr_count = struct.unpack_from(">HHHH", data, pos)
        pos += 8
        name = get_utf8(name_idx)
        desc = get_utf8(desc_idx)
        fields.append(f"{name}:{desc}")
        pos = skip_attributes(data, pos, attr_count)

    methods_count = struct.unpack_from(">H", data, pos)[0]
    pos += 2
    methods: list[str] = []
    for _ in range(methods_count):
        _, name_idx, desc_idx, attr_count = struct.unpack_from(">HHHH", data, pos)
        pos += 8
        name = get_utf8(name_idx)
        desc = get_utf8(desc_idx)
        methods.append(f"{name}{desc}")
        pos = skip_attributes(data, pos, attr_count)

    this_entry = cp[this_class]
    class_name = ""
    if this_entry and this_entry.get("tag") == 7:
        class_name = get_utf8(this_entry["name_index"]).replace("/", ".")

    superclass = ""
    if super_class:
        super_entry = cp[super_class]
        if super_entry and super_entry.get("tag") == 7:
            superclass = get_utf8(super_entry["name_index"]).replace("/", ".")

    return {
        "className": class_name,
        "superclass": superclass,
        "fields": fields,
        "methods": methods,
    }


def _class_info_record(info: dict[str, Any]) -> dict[str, Any]:
    class_smali = _class_to_smali(info["className"])
    super_smali = _class_to_smali(info["superclass"]) if info["superclass"] else ""
    return {
        "className": class_smali,
        "superclass": super_smali,
        "sourceFile": "",
        "fields": [
            {"signature": f"{class_smali}->{f}", "kind": "field"}
            for f in info["fields"]
        ],
        "methods": [
            {"signature": f"{class_smali}->{m}", "kind": "method"}
            for m in info["methods"]
        ],
    }


def manifest_from_class_entries(zf: zipfile.ZipFile) -> dict[str, Any]:
    classes: list[dict[str, Any]] = []
    for name in zf.namelist():
        if not name.endswith(".class"):
            continue
        try:
            data = zf.read(name)
            info = parse_class_file(data)
            if info["className"]:
                classes.append(_class_info_record(info))
        except Exception:
            pass

    if not classes:
        return {"classCount": 0, "methodCount": 0, "fieldCount": 0, "classes": []}
    return {
        "classCount": len(classes),
        "methodCount": sum(len(c["methods"]) for c in classes),
        "fieldCount": sum(len(c["fields"]) for c in classes),
        "classes": classes,
    }


# ---------------------------------------------------------------------------
# Package/version extraction
# ---------------------------------------------------------------------------


def parse_apk_manifest(path: Path, tools: dict[str, Optional[str]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    try:
        from pyaxmlparser.core import APK

        apk = APK(str(path))
        result["packageName"] = apk.package
        result["versionName"] = apk.version_name
        result["versionCode"] = apk.version_code
        try:
            result["minSdkVersion"] = int(apk.get_min_sdk_version())
        except Exception:
            pass
        try:
            result["targetSdkVersion"] = int(apk.get_target_sdk_version())
        except Exception:
            pass
        result["uses_pyaxmlparser"] = True
    except ImportError:
        result = apkanalyzer_manifest(path, tools)
        if result:
            result["uses_apkanalyzer"] = True
    except Exception as exc:
        result["warnings"] = [f"pyaxmlparser failed: {exc}"]
        fallback = apkanalyzer_manifest(path, tools)
        if fallback:
            result.update(fallback)
            result["uses_apkanalyzer"] = True
    return result


def parse_jar_manifest(path: Path, zf: zipfile.ZipFile) -> dict[str, Any]:
    result: dict[str, Any] = {}
    if "META-INF/MANIFEST.MF" in zf.namelist():
        try:
            manifest_text = zf.read("META-INF/MANIFEST.MF").decode("utf-8", errors="replace")
            for line in manifest_text.splitlines():
                if ":" in line:
                    key, val = line.split(":", 1)
                    key = key.strip()
                    val = val.strip()
                    if key in (
                        "Implementation-Title",
                        "Implementation-Version",
                        "Implementation-Vendor",
                        "Main-Class",
                        "Specification-Title",
                        "Specification-Version",
                    ):
                        result[key.replace("-", "").lower()] = val
        except Exception:
            pass
    return result


# ---------------------------------------------------------------------------
# Manifest generation
# ---------------------------------------------------------------------------


def manifest_from_apk(path: Path, tools: dict[str, Optional[str]]) -> dict[str, Any]:
    result = parse_apk_manifest(path, tools)
    classes: list[dict[str, Any]] = []
    try:
        with zipfile.ZipFile(path, "r") as zf:
            for name in zf.namelist():
                if name.startswith("classes") and name.endswith(".dex"):
                    try:
                        data = zf.read(name)
                        dex = parse_dex(data)
                        classes.extend(dex["classes"])
                    except Exception as exc:
                        result.setdefault("warnings", []).append(
                            f"failed to parse {name}: {exc}"
                        )
    except zipfile.BadZipFile as exc:
        result["errors"] = [f"bad APK zip: {exc}"]

    if not classes:
        result["classCount"] = 0
        result["methodCount"] = 0
        result["fieldCount"] = 0
    else:
        result["classCount"] = len(classes)
        result["methodCount"] = sum(len(c["methods"]) for c in classes)
        result["fieldCount"] = sum(len(c["fields"]) for c in classes)

    result["classes"] = classes
    return result


def manifest_from_jar(path: Path, tools: dict[str, Optional[str]]) -> dict[str, Any]:
    result = {}
    classes: list[dict[str, Any]] = []
    try:
        with zipfile.ZipFile(path, "r") as zf:
            result.update(parse_jar_manifest(path, zf))
            classes = manifest_from_class_entries(zf)["classes"]
    except zipfile.BadZipFile as exc:
        result["errors"] = [f"bad JAR zip: {exc}"]

    javap = javap_manifest(path, tools)
    if javap:
        result["external"] = result.get("external", {})
        result["external"]["javap"] = javap

    result["classCount"] = len(classes)
    result["methodCount"] = sum(len(c["methods"]) for c in classes)
    result["fieldCount"] = sum(len(c["fields"]) for c in classes)
    result["classes"] = classes
    return result


def manifest_from_zip(path: Path, tools: dict[str, Optional[str]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    classes: list[dict[str, Any]] = []
    try:
        with zipfile.ZipFile(path, "r") as zf:
            for name in zf.namelist():
                if name.endswith(".dex"):
                    try:
                        classes.extend(parse_dex(zf.read(name))["classes"])
                    except Exception as exc:
                        result.setdefault("warnings", []).append(
                            f"failed to parse {name}: {exc}"
                        )
                elif name.endswith(".class"):
                    try:
                        info = parse_class_file(zf.read(name))
                        if info["className"]:
                            classes.append(_class_info_record(info))
                    except Exception:
                        pass
    except zipfile.BadZipFile as exc:
        result["errors"] = [f"bad zip: {exc}"]

    result["classCount"] = len(classes)
    result["methodCount"] = sum(len(c["methods"]) for c in classes)
    result["fieldCount"] = sum(len(c["fields"]) for c in classes)
    result["classes"] = classes
    return result


def manifest_from_dex(path: Path, tools: dict[str, Optional[str]]) -> dict[str, Any]:
    try:
        data = path.read_bytes()
        return parse_dex(data)
    except Exception as exc:
        return {
            "errors": [f"failed to parse DEX: {exc}"],
            "classCount": 0,
            "methodCount": 0,
            "fieldCount": 0,
            "classes": [],
        }


def generate_manifest(
    path: Path, sample_type: str, tools: dict[str, Optional[str]]
) -> dict[str, Any]:
    if sample_type == "APK":
        return manifest_from_apk(path, tools)
    if sample_type == "JAR":
        return manifest_from_jar(path, tools)
    if sample_type == "ZIP":
        return manifest_from_zip(path, tools)
    if sample_type == "DEX":
        return manifest_from_dex(path, tools)
    return {"warnings": ["unknown sample type"], "classes": []}


# ---------------------------------------------------------------------------
# Compile stub detection
# ---------------------------------------------------------------------------

COMPILE_STUB_PREFIXES = (
    "Landroid/",
    "Lcom/android/internal/",
    "Lcom/android/test/",
    "Lcom/google/android/",
    "Ljava/",
    "Ljavax/",
    "Lorg/apache/",
    "Lorg/json/",
    "Lorg/w3c/",
    "Lorg/xml/",
    "Lorg/ietf/",
    "Lsun/",
    "Ldalvik/",
    "Llibcore/",
    "Ljunit/",
    "Lorg/hamcrest/",
)


def is_compile_stub(
    path: Path, info: dict[str, Any], manifest: dict[str, Any]
) -> bool:
    rel = path.resolve().as_posix()
    if (
        "/app/lib/framework.jar" in rel
        or rel.endswith("app/lib/framework.jar")
        or path.name == "framework.jar"
    ):
        classes = manifest.get("classes", [])
        names = [c["className"] for c in classes]
        if any(c.startswith("Landroid/") for c in names) and any(
            c.startswith("Lcom/android/internal/") for c in names
        ):
            return True
    sample_type = info.get("sampleType", "")
    if sample_type != "JAR":
        return False
    package = info.get("packageName") or ""
    version = info.get("versionName") or ""
    main_class = info.get("process") or ""
    if package or version or main_class:
        return False
    classes = manifest.get("classes", [])
    if classes and all(
        any(c["className"].startswith(p) for p in COMPILE_STUB_PREFIXES)
        for c in classes
    ):
        return True
    return False


# ---------------------------------------------------------------------------
# Inventory record
# ---------------------------------------------------------------------------


def make_sample_id(sha: str, file_name: str, sample_type: str) -> str:
    return f"{sample_type.lower()}:{sha[:16]}:{file_name}"


@dataclass
class InventoryRecord:
    sampleId: str = ""
    fileName: str = ""
    filePath: str = ""
    fileSize: int = 0
    sampleType: str = ""
    sha256: str = ""
    packageName: str = ""
    process: str = ""
    appVersion: str = ""
    versionCode: Optional[int] = None
    versionName: str = ""
    minSdk: Optional[int] = None
    targetSdk: Optional[int] = None
    androidApi: str = ""
    sdk: str = ""
    hyperosVersion: str = ""
    fingerprint: str = ""
    device: str = ""
    codename: str = ""
    source: str = ""
    sampleKind: str = ""
    verificationStatus: str = ""
    classCount: int = 0
    methodCount: int = 0
    fieldCount: int = 0
    manifest: dict[str, Any] = field(default_factory=dict)
    externalToolsAvailable: list[str] = field(default_factory=list)
    externalToolsMissing: list[str] = field(default_factory=list)
    isDuplicate: bool = False
    duplicateOf: str = ""
    warnings: list[str] = field(default_factory=list)
    errors: list[str] = field(default_factory=list)

    def as_dict(self) -> dict[str, Any]:
        return asdict(self)


def _derive_record_fields(rec: InventoryRecord, manifest: dict[str, Any]) -> None:
    if rec.sampleType == "APK":
        rec.packageName = manifest.get("packageName", "")
        rec.versionName = manifest.get("versionName", "")
        rec.versionCode = manifest.get("versionCode")
        rec.appVersion = rec.versionName
        rec.minSdk = manifest.get("minSdkVersion")
        rec.targetSdk = manifest.get("targetSdkVersion")
        rec.process = rec.packageName
        rec.androidApi = str(rec.targetSdk) if rec.targetSdk is not None else ""
        rec.sdk = str(rec.minSdk) if rec.minSdk is not None else ""
    elif rec.sampleType == "JAR":
        rec.appVersion = manifest.get("implementationversion", "")
        rec.process = manifest.get("main-class", "")
        rec.packageName = manifest.get("specificationtitle", "")


def scan_file(
    path: Path,
    tools: dict[str, Optional[str]],
    seen_sha: dict[str, str],
) -> InventoryRecord:
    rec = InventoryRecord()
    rec.fileName = path.name
    rec.filePath = str(path)
    rec.fileSize = path.stat().st_size
    try:
        rec.source = str(path.parent.relative_to(REPO_ROOT))
    except ValueError:
        rec.source = str(path.parent)
    rec.sampleType = detect_sample_type(path)
    rec.sha256 = sha256_file(path)

    rec.externalToolsAvailable = [n for n, p in tools.items() if p]
    rec.externalToolsMissing = [n for n, p in tools.items() if not p]

    if rec.sha256 in seen_sha:
        rec.isDuplicate = True
        rec.duplicateOf = seen_sha[rec.sha256]
        rec.warnings.append(
            f"duplicate of {rec.duplicateOf}; not re-parsing manifest"
        )
        rec.sampleId = make_sample_id(rec.sha256, path.name, rec.sampleType)
        rec.verificationStatus = "DUPLICATE"
        return rec

    seen_sha[rec.sha256] = str(path)
    rec.sampleId = make_sample_id(rec.sha256, path.name, rec.sampleType)

    manifest = generate_manifest(path, rec.sampleType, tools)
    rec.manifest = manifest
    rec.classCount = manifest.get("classCount", 0)
    rec.methodCount = manifest.get("methodCount", 0)
    rec.fieldCount = manifest.get("fieldCount", 0)
    rec.warnings = manifest.get("warnings", [])
    rec.errors = manifest.get("errors", [])

    _derive_record_fields(rec, manifest)

    if is_compile_stub(
        path,
        {"sampleType": rec.sampleType, "packageName": rec.packageName},
        manifest,
    ):
        rec.sampleType = "COMPILE_STUB"
        rec.sampleKind = "COMPILE_STUB"
        rec.verificationStatus = "NOT_A_SAMPLE"
        rec.warnings.append("detected SDK compile stub; not a real ROM sample")
    else:
        rec.sampleKind = rec.sampleType
        if rec.sampleType == "UNKNOWN":
            rec.verificationStatus = "UNKNOWN"
        elif rec.errors:
            rec.verificationStatus = "PARTIAL"
        else:
            rec.verificationStatus = "CATALOGUED"

    return rec


def scan_directory(
    directory: Path,
    tools: Optional[dict[str, Optional[str]]] = None,
) -> dict[str, Any]:
    if tools is None:
        tools = find_external_tools()

    records: list[dict[str, Any]] = []
    seen_sha: dict[str, str] = {}
    summary = {
        "totalFiles": 0,
        "recognizedSamples": 0,
        "compileStubs": 0,
        "unknownFiles": 0,
        "duplicates": 0,
        "totalClasses": 0,
        "totalMethods": 0,
        "totalFields": 0,
        "externalToolsAvailable": [n for n, p in tools.items() if p],
        "externalToolsMissing": [n for n, p in tools.items() if not p],
    }

    for root, _dirs, files in os.walk(directory):
        for f in files:
            path = Path(root) / f
            if not path.is_file():
                continue
            summary["totalFiles"] += 1
            try:
                rec = scan_file(path, tools, seen_sha)
            except Exception as exc:
                rec = InventoryRecord(
                    fileName=path.name,
                    filePath=str(path),
                    fileSize=path.stat().st_size,
                    sampleType="UNKNOWN",
                    errors=[f"scan failed: {exc}"],
                    verificationStatus="ERROR",
                )
                rec.sampleId = make_sample_id(
                    sha256_file(path), path.name, "UNKNOWN"
                )

            records.append(rec.as_dict())
            if rec.isDuplicate:
                summary["duplicates"] += 1
            elif rec.sampleType == "UNKNOWN":
                summary["unknownFiles"] += 1
            elif rec.sampleType == "COMPILE_STUB":
                summary["compileStubs"] += 1
            else:
                summary["recognizedSamples"] += 1

            if not rec.isDuplicate:
                summary["totalClasses"] += rec.classCount
                summary["totalMethods"] += rec.methodCount
                summary["totalFields"] += rec.fieldCount

    return {
        "schemaVersion": 1,
        "catalogName": "ROM inventory catalog",
        "scannedAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "sourceDirectory": str(directory),
        "summary": summary,
        "records": records,
    }


# ---------------------------------------------------------------------------
# Output
# ---------------------------------------------------------------------------


def write_json(path: Path, catalog: dict[str, Any]) -> None:
    path.write_text(json.dumps(catalog, indent=2, ensure_ascii=False), encoding="utf-8")


def flatten_record_for_csv(rec: dict[str, Any]) -> dict[str, Any]:
    flat = dict(rec)
    flat["manifest_json"] = json.dumps(flat.pop("manifest"), ensure_ascii=False)
    return flat


def write_csv(path: Path, catalog: dict[str, Any]) -> None:
    if not catalog["records"]:
        path.write_text("", encoding="utf-8")
        return
    rows = [flatten_record_for_csv(r) for r in catalog["records"]]
    fieldnames = list(rows[0].keys())
    ordered: list[str] = []
    for f in SAMPLE_FIELDS:
        if f in fieldnames:
            ordered.append(f)
    for f in (
        "fileName",
        "filePath",
        "fileSize",
        "sampleType",
        "sampleKind",
        "classCount",
        "methodCount",
        "fieldCount",
        "isDuplicate",
        "duplicateOf",
        "externalToolsAvailable",
        "externalToolsMissing",
        "warnings",
        "errors",
    ):
        if f in fieldnames and f not in ordered:
            ordered.append(f)
    for f in fieldnames:
        if f not in ordered:
            ordered.append(f)
    with open(path, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=ordered)
        writer.writeheader()
        writer.writerows(rows)


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path, help="directory to scan")
    parser.add_argument(
        "--output-json",
        type=Path,
        help="write JSON catalog to this file",
    )
    parser.add_argument(
        "--output-csv",
        type=Path,
        help="write CSV catalog to this file",
    )
    args = parser.parse_args()

    if not args.directory.is_dir():
        fail(f"not a directory: {args.directory}")

    catalog = scan_directory(args.directory)

    if args.output_json:
        write_json(args.output_json, catalog)
    if args.output_csv:
        write_csv(args.output_csv, catalog)

    print(json.dumps(catalog["summary"], indent=2, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except SystemExit:
        raise
    except Exception as exc:
        fail(f"inventory failed: {exc}")
