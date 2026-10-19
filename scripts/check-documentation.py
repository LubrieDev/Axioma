"""Check public documentation and source-distribution boundaries without staging.

SPDX-License-Identifier: GPL-3.0-only
Uses only Python's standard library; it does not build or evaluate Axioma.
"""
from __future__ import annotations

import hashlib
import json
import re
import subprocess
import sys
from pathlib import Path
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[1]
LICENSE_SHA256 = "3972dc9744f6499f0f9b2dbf76696f2ae7ad8af9b23dde66d6af86c9dfb36986"
PRIVATE_FILES = {"AGENTS.md", "local.properties", "keystore.properties", "key.properties", "id_rsa", "id_ed25519"}
PRIVATE_DIRS = {".local", ".dev", ".vscode", ".codex", ".idea", ".ssh", ".aws", ".gnupg", ".tools", ".gradle",
                ".kotlin", ".cxx", ".externalNativeBuild", "build", "tmp", "temp", "scratch", "coverage", "__pycache__"}
OUTPUT_SUFFIXES = {".apk", ".aab", ".apks", ".idsig", ".jks", ".keystore", ".pem", ".key", ".p12", ".pfx", ".log", ".tmp", ".temp", ".bak", ".orig", ".rej"}
DECLARATIONS = re.compile(
    r"(?im)^\s*(?:[#/>*!-]+\s*)?(?:AXIOMA-LOCAL-ONLY\b|"
    r"(?:this\s+)?(?:file|document|script)\s+(?:is\s+)?(?:a\s+)?(?:temporary|local.only|internal.only)\b|"
    r"temporary\s+(?:(?:working|development)\s+)?(?:file|document|plan)\b|"
    r"(?:archivo|documento|script)\s+temporal\b|(?:do not|must not)\s+(?:be\s+)?commit(?:ted)?\b|"
    r"(?:must|debe)\s+(?:be removed|eliminarse)\s+(?:before|antes)[^\n]*commit)"
)
PERSONAL_PATH = re.compile(r"(?i)\b[a-z]:[\\/]Users[\\/]")
LINKS = re.compile(r"!?\[[^\]\n]+\]\((<[^>]+>|[^\s)]+)(?:\s+\"[^\"]*\")?\)")
FENCES = re.compile(r"(?ms)^\s*(`{3,}|~{3,})[^\n]*\n.*?^\s*\1\s*$")


def candidates() -> list[Path]:
    result = subprocess.run(["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z"],
                            cwd=ROOT, capture_output=True, check=True)
    return sorted({ROOT / name.decode("utf-8") for name in result.stdout.split(b"\0") if name})


def private_reason(path: Path) -> str | None:
    relative = path.relative_to(ROOT)
    if relative.parts[:2] == ("artifacts", "android"):
        return "generated Android artifact/report"
    if relative.name in PRIVATE_FILES or set(relative.parts) & PRIVATE_DIRS:
        return "private/generated path"
    if relative.suffix.lower() in OUTPUT_SUFFIXES or relative.name.startswith(".env") and relative.name != ".env.example":
        return "generated, credential or local output"
    return None


def declarations(text: str) -> bool:
    return bool(DECLARATIONS.search(text))


def headings(text: str) -> set[str]:
    slugs, counts = set(), {}
    for line in FENCES.sub("", text).splitlines():
        match = re.match(r"^#{1,6}\s+(.+?)(?:\s+#+)?$", line)
        if not match:
            continue
        slug = re.sub(r"[^\w\s-]", "", match.group(1).lower())
        slug = re.sub(r"\s", "-", slug.strip())
        number = counts.get(slug, 0)
        counts[slug] = number + 1
        slugs.add(slug + (f"-{number}" if number else ""))
    return slugs


def main() -> int:
    files = candidates()
    public = {p.resolve() for p in files}
    errors, texts, link_count = [], {}, 0
    required = ["README.md", "CONTRIBUTING.md", "LICENSE", "COPYRIGHT", "THIRD_PARTY_NOTICES.md",
                "docs/README.md", "docs/user/README.md", "docs/user/known-issues.md",
                "docs/development/setup.md", "docs/development/testing.md",
                "docs/development/repository-policy.md", "docs/development/licensing.md"]
    for name in required:
        if (ROOT / name).resolve() not in public:
            errors.append(f"Required distribution file missing from candidates: {name}")
    for path in files:
        relative = path.relative_to(ROOT).as_posix()
        reason = private_reason(path)
        if reason:
            errors.append(f"Excluded material is a Git candidate: {relative} ({reason})")
        if not path.is_file():
            errors.append(f"Candidate missing from working tree: {relative}")
            continue
        upstream = relative.startswith(("app/src/main/assets/renderer/vendor/", "licenses/")) or relative in {"LICENSE", "gradle/wrapper/LICENSE", "gradlew", "gradlew.bat"}
        try:
            text = path.read_bytes().decode("utf-8-sig")
        except UnicodeDecodeError:
            continue
        if "\0" in text:
            continue
        texts[path.resolve()] = text
        if not upstream:
            if declarations(text):
                errors.append(f"Local/temporary declaration in public candidate: {relative}")
            if PERSONAL_PATH.search(text):
                errors.append(f"Personal machine path in public candidate: {relative}")
    for path in files:
        if path.suffix.lower() != ".md" or path.resolve() not in texts:
            continue
        relative = path.relative_to(ROOT).as_posix()
        if relative.startswith("app/src/main/assets/renderer/vendor/"):
            continue
        for match in LINKS.finditer(FENCES.sub("", texts[path.resolve()])):
            destination = match.group(1).strip("<>")
            if urlsplit(destination).scheme:
                if urlsplit(destination).scheme not in {"http", "https", "mailto"}:
                    errors.append(f"Unsupported/nonportable link in {relative}: {destination}")
                continue
            target, _, anchor = destination.partition("#")
            resolved = (path.parent / unquote(target)).resolve() if target else path.resolve()
            link_count += 1
            if not resolved.is_relative_to(ROOT) or resolved not in public:
                errors.append(f"Link outside public candidates in {relative}: {destination}")
            elif anchor and resolved.suffix.lower() == ".md" and unquote(anchor) not in headings(texts.get(resolved, "")):
                errors.append(f"Missing heading in {relative}: {destination}")
    license_path = ROOT / "LICENSE"
    if not license_path.exists() or hashlib.sha256(license_path.read_bytes().replace(b"\r\n", b"\n")).hexdigest() != LICENSE_SHA256:
        errors.append("LICENSE does not match the complete official GPLv3 text (LF-normalized).")
    for name in ["README.md", "COPYRIGHT"]:
        if "GPL-3.0-only" not in texts.get((ROOT / name).resolve(), ""):
            errors.append(f"Missing explicit project license identifier: {name}")
    vendor = ROOT / "app/src/main/assets/renderer/vendor"
    manifest = json.loads((vendor / "manifest.json").read_text(encoding="utf-8"))
    expected_assets = {"manifest.json"}
    for entry in manifest["files"]:
        asset = (vendor / entry["path"]).resolve()
        expected_assets.add(entry["path"])
        if not asset.is_relative_to(vendor.resolve()) or asset not in public or not asset.is_file() or hashlib.sha256(asset.read_bytes()).hexdigest() != entry["sha256"]:
            errors.append(f"Pinned asset missing or changed: {entry['path']}")
    actual_assets = {p.relative_to(vendor).as_posix() for p in vendor.rglob("*") if p.is_file()}
    if actual_assets != expected_assets:
        errors.append("Renderer vendor directory contains unrecorded or missing files.")
    for pattern in ["artifacts/android/probe.json", ".dev/AGENTS.md", ".dev/probe.md", ".dev/tools/probe.log", "AGENTS.md", ".local/probe.md", ".vscode/settings.json", ".tools/probe.log", "local.properties", "probe.apk", "probe.keystore", "probe.pem", "probe.key", "probe.p12", "probe.pfx", "keystore.properties", "key.properties", "id_rsa", "id_ed25519"]:
        ignored = subprocess.run(["git", "check-ignore", "--no-index", "--quiet", pattern], cwd=ROOT)
        if ignored.returncode != 0:
            errors.append(f"Missing ignore boundary: {pattern}")
    if errors:
        print("Documentation/distribution check failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1
    print(f"Documentation/distribution: {len(files)} candidates, {link_count} local links, official GPLv3 text, "
          f"{len(manifest['files'])} pinned assets and local exclusions verified. No index changes.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
