#!/usr/bin/env python3
"""Verify and package a signed CCTV APK for a matching release tag."""
from __future__ import annotations

import argparse
from dataclasses import asdict, dataclass
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import zipfile

EXPECTED_CERT = "4bb96b0ff9021cc49885f9cdd02d62ec3e58c3704cd65853e8dc2269316c5f9b"
EXPECTED_PACKAGE = "com.pnntrn.cctv"
MAX_APK_BYTES = 64 * 1024 * 1024


class ReleaseError(ValueError):
    """The candidate does not satisfy this project's release contract."""


@dataclass(frozen=True)
class ApkInfo:
    package: str
    version_code: int
    version_name: str
    min_sdk: int


def parse_badging(output: str) -> ApkInfo:
    package = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", output, re.M)
    sdk = re.search(r"^sdkVersion:'(\d+)'", output, re.M)
    if package is None or sdk is None:
        raise ReleaseError("APK metadata is missing or malformed")
    info = ApkInfo(package[1], int(package[2]), package[3], int(sdk[1]))
    if info.package != EXPECTED_PACKAGE:
        raise ReleaseError("Unexpected APK package")
    if info.version_code <= 0 or re.fullmatch(r"\d+\.\d+\.\d+", info.version_name) is None:
        raise ReleaseError("Invalid APK version metadata")
    if info.min_sdk != 26:
        raise ReleaseError("Unexpected minimum Android version; expected API 26")
    return info


def validate_source_version(info: ApkInfo, source: str) -> None:
    codes = re.findall(r"^\s*versionCode\s+(\d+)\s*$", source, re.M)
    names = re.findall(r"^\s*versionName\s+['\"]([^'\"]+)['\"]\s*$", source, re.M)
    if codes != [str(info.version_code)] or names != [info.version_name]:
        raise ReleaseError("APK version code/name must match app/build.gradle")


def validate_tag(tag: str, version: str) -> bool:
    pattern = r"v" + re.escape(version) + r"(?P<candidate>-(?:alpha|beta|rc)\.[1-9]\d*)?"
    match = re.fullmatch(pattern, tag)
    if match is None:
        raise ReleaseError("Release tag must match the APK version (optionally -alpha.N, -beta.N or -rc.N)")
    return match["candidate"] is not None


def validate_certificate(report: str) -> None:
    digests = re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})\s*$", report, re.M)
    if [digest.lower() for digest in digests] != [EXPECTED_CERT]:
        raise ReleaseError("APK must have exactly one signer matching the retained release certificate")


def tool_output(command: list[str]) -> str:
    try:
        return subprocess.check_output(command, stderr=subprocess.STDOUT, text=True, encoding="utf-8", timeout=120)
    except subprocess.CalledProcessError as error:
        # Do not echo tool diagnostics or private machine paths into public release notes.
        raise ReleaseError(f"{Path(command[0]).name} verification failed (exit {error.returncode})") from error
    except subprocess.TimeoutExpired as error:
        raise ReleaseError(f"{Path(command[0]).name} verification timed out") from error


def prepare(apk: Path, tag: str, output: Path, build_tools: Path, root: Path) -> dict[str, object]:
    if not apk.is_file() or not 0 < apk.stat().st_size <= MAX_APK_BYTES:
        raise ReleaseError("APK missing or outside the permitted size range")
    data = apk.read_bytes()
    if not 0 < len(data) <= MAX_APK_BYTES:
        raise ReleaseError("APK outside the permitted size range")
    if output.exists() and (not output.is_dir() or any(output.iterdir())):
        raise ReleaseError("Release output must be a new or empty directory")
    output.mkdir(parents=True, exist_ok=True)
    candidate = output / "cctv-release.apk"
    candidate.write_bytes(data)
    suffix = ".bat" if sys.platform == "win32" else ""
    signer = build_tools / ("apksigner" + suffix)
    aapt = build_tools / ("aapt.exe" if sys.platform == "win32" else "aapt")
    if not signer.is_file() or not aapt.is_file():
        raise ReleaseError("Pinned Android build tools are unavailable")
    validate_certificate(tool_output([str(signer), "verify", "--verbose", "--print-certs", str(candidate)]))
    info = parse_badging(tool_output([str(aapt), "dump", "badging", str(candidate)]))
    validate_source_version(info, (root / "app/build.gradle").read_text(encoding="utf-8"))
    prerelease = validate_tag(tag, info.version_name)
    with zipfile.ZipFile(candidate) as archive:
        notices = archive.read("assets/third_party_licenses.txt").decode("utf-8")
        if "Apache License" not in notices or "Android Open Source Project" not in notices:
            raise ReleaseError("Bundled third-party license notices are incomplete")
    digest = hashlib.sha256(data).hexdigest()
    (output / "SHA256SUMS").write_text(digest + "  cctv-release.apk\n", encoding="utf-8")
    for name in ("THIRD_PARTY_NOTICES.md",):
        source = root / name
        if not source.is_file():
            raise ReleaseError(f"Required distribution notice is missing: {name}")
        shutil.copyfile(source, output / name)
    shutil.copyfile(root / "rtsp-compat/LICENSE", output / "APACHE-2.0.txt")
    project_license = root / "LICENSE"
    if project_license.is_file():
        shutil.copyfile(project_license, output / "LICENSE")
    return {**asdict(info), "prerelease": prerelease, "tag": tag, "size": len(data),
            "sha256": digest, "certificate_sha256": EXPECTED_CERT,
            "project_license": "LICENSE" if project_license.is_file() else "not supplied"}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--build-tools", required=True, type=Path)
    args = parser.parse_args()
    try:
        result = prepare(args.apk, args.tag, args.output, args.build_tools, Path(__file__).resolve().parents[1])
    except (ReleaseError, OSError, KeyError, zipfile.BadZipFile, UnicodeError) as error:
        print(f"Release rejected: {error}", file=sys.stderr)
        return 1
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
