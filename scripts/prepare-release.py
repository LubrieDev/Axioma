"""Assemble a public release directory from verified production APKs.

SPDX-License-Identifier: GPL-3.0-only
"""
from __future__ import annotations
import hashlib
import json
from pathlib import Path
import re
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ARTIFACTS = ROOT / "artifacts/android"


def main() -> None:
    with (ARTIFACTS / ".matrix.lock").open("r+b"):
        manifest_path = ARTIFACTS / "manifests/build-manifest.json"
        manifest = json.loads(manifest_path.read_text(encoding="utf-8-sig"))
        assert manifest["configuration"]["variant"] == "release"
        assert manifest["critical_failures"] == 0 and manifest["status"] in {"passed", "partially_verified"}
        apps = [a for a in manifest["artifacts"] if a["kind"] == "application"]
        assert len(apps) == 5 and {a["build_id"] for a in apps} == {"arm64-v8a", "armeabi-v7a", "x86_64", "x86", "universal"}
        for artifact in apps:
            assert not artifact["debuggable"] and artifact["variant"] == "release"
            assert artifact["signer_sha256"] == manifest["configuration"]["expected_signer_sha256"]
            assert hashlib.sha256((ARTIFACTS / artifact["path"]).read_bytes()).hexdigest() == artifact["sha256"]
        subprocess.run(["py", "-3", str(ROOT / "scripts/check-documentation.py")], cwd=ROOT, check=True)
        destination = ARTIFACTS / "release/0.1.0"
        names = sorted(set(filter(None, subprocess.check_output(
            ["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z"], cwd=ROOT).decode().split("\0"))))
        source = destination / "Axioma-0.1.0-source.zip"
        extras: list[Path] = []
        expected_outputs = [source.name, "README.md", "COMPATIBILITY.md", "KNOWN-ISSUES.md", "LICENSE", "THIRD-PARTY-NOTICES.txt", "SHA256SUMS.txt"]
        for name in expected_outputs:
            if (destination / name).exists():
                raise ValueError("Existing release notes/source archive must be preserved before assembling again")
        with zipfile.ZipFile(source, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
            for name in names:
                path = Path(name)
                assert not ({".dev", ".local", ".git", ".vscode", "artifacts"} & set(path.parts))
                assert path.suffix.lower() not in {".apk", ".aab", ".p12", ".jks", ".keystore", ".key", ".pem", ".pfx"}
                info = zipfile.ZipInfo(path.as_posix(), (1980, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = (0o100755 if name == "gradlew" else 0o100644) << 16
                archive.writestr(info, (ROOT / path).read_bytes())
        extras.append(source)

        def write(name: str, text: str) -> None:
            p = destination / name
            p.write_text(text, encoding="utf-8")
            extras.append(p)

        rows = ["# Android compatibility", "", "All APKs use minSdk 26 (Android 8.0 installation floor), compileSdk 36 and targetSdk 36.",
                "The installation floor is not evidence of execution on Android 8–12. One APK serves multiple Android versions.", "",
                "| APK | Packaged ABI | Successful installation and full instrumentation, by Android/API and ABI |", "| --- | --- | --- |"]
        for a in apps:
            verified = sorted({(int(cell["api"]), cell["abi"]) for cell in a["verified_runtime_cells"]})
            evidence = "; ".join(f"Android {api - 20} (API {api}), {abi}" for api, abi in verified)
            rows.append(f"| {Path(a['path']).name} | {', '.join(a['abis'])} | {evidence or 'Not executed; compiled and statically inspected only'} |")
        rows += ["", "Universal runtime verification covers only the recorded ARM64/x86_64 environments; its 32-bit libraries were not executed.",
                 "All tested environments used 4 KB pages. 16 KB ZIP/64-bit ELF alignment was checked statically, without a 16 KB runtime test.", ""]
        write("COMPATIBILITY.md", "\n".join(rows))
        write("README.md", "\n".join([
            "# Axioma 0.1.0", "", "Production release APKs, signed with Axioma's permanent private key; not debug builds.",
            "This is an early calculator with known mathematical defects. Production signing is not a mathematical stability certification.", "",
            "Choose arm64-v8a for modern ARM phones, armeabi-v7a only for an OS supporting 32-bit ARM,",
            "x86_64/x86 for matching Intel environments, or universal if unsure. Install only one APK.",
            "Actual Android execution coverage is in COMPATIBILITY.md. No per-Android duplicates are needed.", "",
            "Application ID: org.axioma.calculator. Version: 0.1.0 (code 4). This is separate from the debug application.",
            "Signing certificate SHA-256: " + manifest["configuration"]["expected_signer_sha256"], "",
            "Upload the five APKs, the corresponding-source ZIP, SHA256SUMS.txt and these notices/compatibility/known-issue files together.",
            "The source ZIP includes build scripts, wrapper, tests, documentation, GPL license and third-party notices.",
            "Signing keys, credentials, SDKs, private editor state, test APKs and raw logs are excluded.",
            "Nothing in this directory has been uploaded automatically.", ""]))
        issues = (ROOT / "docs/user/known-issues.md").read_text(encoding="utf-8")
        issues = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", issues)
        write("KNOWN-ISSUES.md", issues + "\nThe referenced maintained guides are included in Axioma-0.1.0-source.zip.\n")
        license_path = destination / "LICENSE"
        license_path.write_bytes((ROOT / "LICENSE").read_bytes())
        extras.append(license_path)
        notices = (ROOT / "THIRD_PARTY_NOTICES.md").read_bytes()
        for path in sorted((ROOT / "licenses").rglob("*")):
            if path.is_file():
                notices += b"\n\n===== " + path.relative_to(ROOT).as_posix().encode() + b" =====\n\n" + path.read_bytes()
        notice_path = destination / "THIRD-PARTY-NOTICES.txt"
        notice_path.write_bytes(notices)
        extras.append(notice_path)
        checksums = []
        for path in sorted([ARTIFACTS / a["path"] for a in apps] + extras):
            checksums.append(hashlib.sha256(path.read_bytes()).hexdigest() + "  " + path.name)
        write("SHA256SUMS.txt", "\n".join(checksums) + "\n")
        for path in extras:
            relative = path.relative_to(ARTIFACTS).as_posix()
            manifest["managed_files"].append({"path": relative, "sha256": hashlib.sha256(path.read_bytes()).hexdigest()})
            manifest["generated_files"].append(relative)
        manifest["release_bundle"] = {"path": "release/0.1.0", "source_archive": source.name,
                                      "source_file_count": len(names), "publication_performed": False,
                                      "files": sorted(p.name for p in destination.iterdir() if p.is_file()),
                                      "artifacts": apps,
                                      "file_hashes": {p.name: hashlib.sha256(p.read_bytes()).hexdigest()
                                                      for p in destination.iterdir() if p.is_file()}}
        # Completed versioned releases survive subsequent debug builds. Their
        # immutable hashes stay in release metadata, outside the disposable ledger.
        manifest["managed_files"] = [e for e in manifest["managed_files"]
                                     if not e["path"].startswith("release/0.1.0/")]
        manifest["generated_files"] = sorted(set(manifest["generated_files"])
                                              | {a["path"] for a in apps})
        temporary = ARTIFACTS / ".build/release-manifest-update.tmp"
        temporary.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
        temporary.replace(manifest_path)
        print("Prepared five production APKs, corresponding source, checksums, compatibility and license notices.")


if __name__ == "__main__":
    main()
