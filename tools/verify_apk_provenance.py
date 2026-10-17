#!/usr/bin/env python3
"""Verify build provenance embedded in a CustoMIUIzer A14 APK.

Reads `assets/build-provenance.properties` from the APK and checks that the
revision matches the expected engineering SHA. Also validates that the required
fields are present and sane.
"""

from __future__ import annotations

import argparse
import re
import sys
import zipfile
from pathlib import Path

REQUIRED_FIELDS = ("revision", "versionName", "versionCode", "buildType")
REVISION_RE = re.compile(r"^[0-9a-fA-F]{8}$")


def read_apk_provenance(apk_path: Path) -> dict[str, str]:
    """Read and parse `assets/build-provenance.properties` from an APK."""
    with zipfile.ZipFile(apk_path) as zf:
        try:
            data = zf.read("assets/build-provenance.properties").decode("utf-8")
        except KeyError as exc:
            raise RuntimeError("APK is missing assets/build-provenance.properties") from exc

    props: dict[str, str] = {}
    for line in data.splitlines():
        line = line.strip()
        if not line or line.startswith(("#", "!")):
            continue
        if "=" not in line:
            continue
        key, value = line.split("=", 1)
        props[key.strip()] = value.strip()
    return props


def verify_apk_provenance(
    apk_path: Path,
    expected_revision: str,
    expected_build_type: str = "debug",
    expected_version_name: str | None = None,
    expected_version_code: str | None = None,
) -> dict[str, str]:
    """Verify provenance and return the parsed properties."""
    if not REVISION_RE.fullmatch(expected_revision):
        raise ValueError(f"expected revision must be an 8-character hex SHA: {expected_revision!r}")

    props = read_apk_provenance(apk_path)
    for field in REQUIRED_FIELDS:
        if field not in props:
            raise RuntimeError(f"provenance missing required field: {field}")

    revision = props["revision"]
    if not REVISION_RE.fullmatch(revision):
        raise RuntimeError(f"provenance revision is not an 8-character hex SHA: {revision!r}")

    if revision.lower() != expected_revision.lower():
        raise RuntimeError(
            f"provenance revision mismatch: expected {expected_revision}, found {revision}"
        )

    build_type = props["buildType"]
    if build_type != expected_build_type:
        raise RuntimeError(
            f"provenance buildType mismatch: expected {expected_build_type}, found: {build_type!r}"
        )

    if expected_version_name is not None and props["versionName"] != expected_version_name:
        raise RuntimeError(
            f"provenance versionName mismatch: expected {expected_version_name}, found {props['versionName']}"
        )

    if expected_version_code is not None and props["versionCode"] != expected_version_code:
        raise RuntimeError(
            f"provenance versionCode mismatch: expected {expected_version_code}, found {props['versionCode']}"
        )

    try:
        int(props["versionCode"])
    except ValueError as exc:
        raise RuntimeError(f"provenance versionCode is not an integer: {props['versionCode']!r}") from exc

    return props


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", required=True, type=Path, help="path to the APK")
    parser.add_argument(
        "--expected-revision",
        required=True,
        help="expected 8-character engineering SHA",
    )
    parser.add_argument(
        "--expected-build-type",
        default="debug",
        help="expected build type (default: debug)",
    )
    parser.add_argument(
        "--expected-version",
        default=None,
        help="expected versionName",
    )
    parser.add_argument(
        "--expected-version-code",
        default=None,
        help="expected versionCode",
    )
    args = parser.parse_args()

    try:
        props = verify_apk_provenance(
            args.apk,
            args.expected_revision,
            expected_build_type=args.expected_build_type,
            expected_version_name=args.expected_version,
            expected_version_code=args.expected_version_code,
        )
    except (RuntimeError, ValueError) as exc:
        print(f"verify_apk_provenance: {exc}", file=sys.stderr)
        return 1

    print("verify_apk_provenance: OK")
    for key in REQUIRED_FIELDS:
        print(f"  {key}={props[key]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
