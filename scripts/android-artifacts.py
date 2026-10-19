"""Inspect actual Android APKs; standard library only, no mathematical evaluation.

SPDX-License-Identifier: GPL-3.0-only
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ARTIFACTS = ROOT / "artifacts/android"
MACHINES = {"arm64-v8a": (2, 183), "armeabi-v7a": (1, 40), "x86_64": (2, 62), "x86": (1, 3)}
JNI = ["parse", "evaluate", "evaluateExact", "calculateMode"]
SYSTEM_LIBRARIES = {"libc.so", "libm.so", "libdl.so", "liblog.so", "libandroid.so", "libz.so"}


def execute(command: list[str]) -> str:
    result = subprocess.run(command, capture_output=True, text=True, encoding="utf-8", errors="replace")
    if result.returncode:
        # Do not place host paths or tool diagnostics in a shareable manifest.
        raise ValueError(f"{Path(command[0]).name} failed with exit code {result.returncode}")
    return result.stdout + result.stderr


def elf_info(content: bytes, abi: str) -> dict:
    if len(content) < 4096 or content[:4] != b"\x7fELF" or content[5] != 1:
        raise ValueError(f"Invalid, empty or non-little-endian ELF for {abi}")
    elf_class = content[4]
    machine = struct.unpack_from("<H", content, 18)[0]
    if (elf_class, machine) != MACHINES[abi]:
        raise ValueError(f"ELF class/machine mismatch for {abi}")
    if elf_class == 2:
        offset = struct.unpack_from("<Q", content, 32)[0]
        entry_size, count = struct.unpack_from("<HH", content, 54)
    else:
        offset = struct.unpack_from("<I", content, 28)[0]
        entry_size, count = struct.unpack_from("<HH", content, 42)
    alignments = []
    for index in range(count):
        position = offset + index * entry_size
        if struct.unpack_from("<I", content, position)[0] == 1:
            alignment = struct.unpack_from("<Q" if elf_class == 2 else "<I", content,
                                           position + (48 if elf_class == 2 else 28))[0]
            alignments.append(alignment)
    if not alignments or min(alignments) < (16384 if elf_class == 2 else 4096):
        raise ValueError(f"Insufficient PT_LOAD alignment for {abi}")
    return {"class_bits": 64 if elf_class == 2 else 32, "machine": machine,
            "load_alignments": alignments, "elf_16kb_aligned": min(alignments) >= 16384}


def inspect(apk: Path, sdk: Path, ndk: Path, expected: list[str], test_apk: bool,
            variant: str = "debug", signer: str | None = None) -> dict:
    apk = apk.resolve()
    if not apk.is_relative_to(ARTIFACTS.resolve()) or not apk.is_file() or not apk.stat().st_size:
        raise ValueError("APK must be a nonempty file under artifacts/android")
    tools = sdk / "build-tools/35.0.0"
    readelf = ndk / "toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-readelf.exe"
    badging = execute([str(tools / "aapt.exe"), "dump", "badging", str(apk)])
    package = re.search(r"^package: name='([^']+)' versionCode='([^']*)' versionName='([^']*)'", badging, re.M)
    minimum = re.search(r"^sdkVersion:'(\d+)'", badging, re.M)
    target = re.search(r"^targetSdkVersion:'(\d+)'", badging, re.M)
    if not package or not minimum or not target:
        raise ValueError("Missing package/SDK metadata")
    expected_package = "org.axioma.calculator" + (".debug" if variant == "debug" else "") + (".test" if test_apk else "")
    if package[1] != expected_package or int(minimum[1]) != 26 or int(target[1]) != 36:
        raise ValueError("Unexpected package, minSdk or targetSdk")
    expected_version = "0.1.0-dev" if variant == "debug" else "0.1.0"
    if not test_apk and (package[3] != expected_version or package[2] != "4"):
        raise ValueError("Unexpected development version")
    debuggable = "application-debuggable" in badging
    if not test_apk and debuggable != (variant == "debug"):
        raise ValueError("Unexpected application debuggable flag")
    signing = execute([str(tools / "apksigner.bat"), "verify", "--verbose", "--print-certs", str(apk)])
    if "Verified" not in signing and "Verifies" not in signing:
        raise ValueError("APK signature verification did not confirm success")
    certificate = re.search(r"Signer #1 certificate SHA-256 digest: ([a-fA-F0-9]{64})", signing)
    if not certificate or (signer and certificate[1].lower() != signer.lower()):
        raise ValueError("APK signing certificate does not match the expected key")
    if variant == "release" and not signer:
        raise ValueError("Release inspection requires an expected permanent signing certificate")
    execute([str(tools / "zipalign.exe"), "-c", "-P", "16", "-v", "4", str(apk)])
    libraries = []
    vendor = ROOT / "app/src/main/assets/renderer/vendor"
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise ValueError("Duplicate ZIP entry names")
        for name in names:
            path = Path(name)
            if (".." in path.parts or name.startswith(("/", "\\")) or "\\" in name
                    or set(path.parts) & {".dev", ".local", ".git", ".vscode"}
                    or path.name in {"AGENTS.md", "local.properties", "keystore.properties", "key.properties"}
                    or path.suffix.lower() in {".jks", ".keystore", ".pem", ".key", ".p12", ".pfx"}):
                raise ValueError("Private or unsafe ZIP entry")
        entries = [name for name in names if name.startswith("lib/") and name.endswith(".so")]
        actual = sorted({name.split("/")[1] for name in entries})
        if actual != sorted(expected):
            raise ValueError(f"Packaged ABI mismatch: expected {sorted(expected)}, got {actual}")
        if test_apk:
            xml = execute([str(tools / "aapt.exe"), "dump", "xmltree", str(apk), "AndroidManifest.xml"])
            if "org.axioma.calculator.SmokeInstrumentation" not in xml or "targetPackage" not in xml:
                raise ValueError("Missing platform instrumentation runner")
        else:
            if "android.permission.INTERNET" in badging:
                raise ValueError("Unexpected Internet permission")
            manifest = json.loads((vendor / "manifest.json").read_text())
            for record in manifest["files"]:
                data = archive.read("assets/renderer/vendor/" + record["path"])
                if hashlib.sha256(data).hexdigest() != record["sha256"]:
                    raise ValueError("Packaged offline renderer hash mismatch")
            for abi in actual:
                required = {f"lib/{abi}/libaxioma.so"}
                if not required.issubset(entries):
                    raise ValueError(f"Missing application library for {abi}")
            work = ARTIFACTS / ".build/native-inspection"
            work.mkdir(parents=True, exist_ok=True)
            with tempfile.TemporaryDirectory(dir=work) as directory:
                for name in entries:
                    abi = name.split("/")[1]
                    content = archive.read(name)
                    info = elf_info(content, abi)
                    path = Path(directory) / abi / Path(name).name
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_bytes(content)
                    dynamic = execute([str(readelf), "--dynamic", str(path)])
                    needed = re.findall(r"Shared library: \[([^\]]+)\]", dynamic)
                    for dependency in needed:
                        if dependency not in SYSTEM_LIBRARIES and f"lib/{abi}/{dependency}" not in entries:
                            raise ValueError(f"Unresolved packaged native dependency: {dependency}")
                    symbols = []
                    if path.name == "libaxioma.so":
                        exports = execute([str(readelf), "--dyn-syms", "--wide", str(path)])
                        for method in JNI:
                            symbol = "Java_org_axioma_calculator_NativeCalculator_" + method
                            if not re.search(r"\bGLOBAL\s+DEFAULT\s+(?!UND\b)\S+\s+" + symbol + r"(?:\s|$)", exports):
                                raise ValueError(f"Missing defined JNI export: {method}")
                            symbols.append(method)
                    libraries.append({"entry": name, "size_bytes": len(content),
                                      "sha256": hashlib.sha256(content).hexdigest(), **info,
                                      "needed": needed, "jni_exports": symbols})
    return {"path": apk.relative_to(ARTIFACTS).as_posix(), "size_bytes": apk.stat().st_size,
            "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(), "abis": sorted(expected),
            "package": package[1], "version_code": int(package[2]) if package[2] else None,
            "version_name": package[3] or None,
            "min_sdk": int(minimum[1]), "target_sdk": int(target[1]),
            "variant": variant, "debuggable": debuggable, "signer_sha256": certificate[1].lower(),
            "signature_verified": True, "zip_alignment_16kb_verified": True,
            "packaged_renderer_verified": not test_apk, "native_libraries": libraries,
            "kind": "instrumentation" if test_apk else "application",
            "runtime_verified": False}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--sdk", type=Path, required=True)
    parser.add_argument("--ndk", type=Path, required=True)
    parser.add_argument("--abis", default="")
    parser.add_argument("--test-apk", action="store_true")
    parser.add_argument("--variant", choices=["debug", "release"], default="debug")
    parser.add_argument("--expected-cert-sha256")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    output = args.output.resolve()
    if not output.is_relative_to(ARTIFACTS.resolve()):
        parser.error("Output must remain under artifacts/android")
    try:
        result = inspect(args.apk, args.sdk.resolve(), args.ndk.resolve(),
                         [abi for abi in args.abis.split(",") if abi], args.test_apk,
                         args.variant, args.expected_cert_sha256)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
        print(f"Verified {result['path']}: {result['size_bytes']} bytes, ABI {result['abis']}")
        return 0
    except (ValueError, OSError, zipfile.BadZipFile, KeyError, struct.error) as error:
        print(f"APK verification failed: {error}")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
