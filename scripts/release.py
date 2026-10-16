"""Version, validate, and publish APK releases. Uses only Python's standard library."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
REPOSITORY = "kpranjal2047/Android-Live-Progress"
MAX_CODE = 2_100_000_000
VERSION = re.compile(r"(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\Z")
TYPES = {"patch": 0, "minor": 1, "major": 2}


def run(*args, capture=False, cwd=None):
    return subprocess.run(args, cwd=cwd or ROOT, check=True, text=True,
                          stdout=subprocess.PIPE if capture else None).stdout


def git(*args):
    return run("git", *args, capture=True).strip()


def read_version(text=None):
    text = text if text is not None else (ROOT / "version.properties").read_text()
    values = dict(line.split("=", 1) for line in text.splitlines() if line and not line.startswith("#"))
    name, code = values["versionName"], int(values["versionCode"])
    if not VERSION.fullmatch(name) or not 1 <= code <= MAX_CODE:
        raise ValueError("Invalid Android version metadata")
    return name, code


def next_version(name, code, records):
    if not records:
        raise ValueError("An app release requires a change record")
    if code >= MAX_CODE:
        raise ValueError("Android versionCode limit reached")
    major, minor, patch = map(int, name.split("."))
    kind = max((record["type"] for record in records), key=TYPES.get)
    if kind == "major":
        major, minor, patch = major + 1, 0, 0
    elif kind == "minor":
        minor, patch = minor + 1, 0
    else:
        patch += 1
    return f"{major}.{minor}.{patch}", code + 1


def app_change(path):
    if path.endswith(".md") or path.startswith(("app/src/test/", "app/src/androidTest/", "scripts/tests/")):
        return False
    return path.startswith(("app/", "gradle/", "scripts/", ".github/workflows/")) or path in {
        "build.gradle.kts", "settings.gradle.kts", "gradle.properties", "gradlew", "gradlew.bat",
        "version.properties", "release/signing-certificate.sha256",
    }


def records():
    result = []
    for path in sorted((ROOT / ".changes").glob("*.json")):
        if not re.fullmatch(r"[a-z0-9][a-z0-9-]*\.json", path.name) or path.is_symlink():
            raise ValueError(f"Invalid change record path: {path.name}")
        item = json.loads(path.read_text())
        if set(item) != {"type", "summary"} or item["type"] not in TYPES:
            raise ValueError(f"Invalid change record: {path.name}")
        summary = item["summary"]
        if not isinstance(summary, str) or not summary.strip() or len(summary) > 500 or "\n" in summary:
            raise ValueError(f"Use one short summary in {path.name}")
        result.append({**item, "file": path.relative_to(ROOT).as_posix()})
    return result


def validate(base, head="HEAD"):
    read_version()
    pending = records()
    changed = git("diff", "--name-only", base, head, "--").splitlines()
    added = git("diff", "--name-only", "--diff-filter=A", base, head, "--").splitlines()
    if "version.properties" in changed and "version.properties" not in added:
        raise ValueError("Only release automation may change version.properties")
    if any(path.startswith("release/history/") for path in changed):
        raise ValueError("Release history is immutable and owned by automation")
    needs_release = any(map(app_change, changed))
    if needs_release and not any(item["file"] in added for item in pending):
        raise ValueError("App/build changes require a NEW .changes/<name>.json record")
    print("Release validation passed; app changes=" + str(needs_release))
    return needs_release


def history():
    return sorted((json.loads(path.read_text()) for path in (ROOT / "release/history").glob("*.json")),
                  key=lambda item: item["versionCode"])


def notes(manifest):
    sections = [f"# Live Progress {manifest['versionName']}", "", "Requires Android 16 or newer.", ""]
    for kind, heading in (("major", "Breaking Changes"), ("minor", "Features"), ("patch", "Fixes and Improvements")):
        summaries = [entry["summary"] for entry in manifest["changes"] if entry["type"] == kind]
        if summaries:
            sections.extend([f"## {heading}", "", *[f"- {text}" for text in summaries], ""])
    sections.extend(["Download the APK below to install or update Live Progress.", ""])
    return "\n".join(sections)


def prepare(source):
    if git("status", "--porcelain"):
        raise ValueError("Release preparation requires a clean checkout")
    previous = history()
    base = f"v{previous[-1]['versionName']}" if previous else json.loads((ROOT / "release/baseline.json").read_text())["sourceCommit"]
    if not validate(base, source):
        return None
    pending = records()
    name, code = next_version(*read_version(), pending)
    if run("git", "tag", "--list", f"v{name}", capture=True).strip():
        raise ValueError("Version tag already exists; refusing to overwrite it")
    manifest = {"versionName": name, "versionCode": code, "sourceCommit": source, "changes": pending}
    (ROOT / "version.properties").write_text(f"versionName={name}\nversionCode={code}\n")
    directory = ROOT / "release/history"
    directory.mkdir(parents=True, exist_ok=True)
    (directory / f"v{name}.json").write_text(json.dumps(manifest, indent=2) + "\n")
    for item in pending:
        (ROOT / item["file"]).unlink()
    return manifest


def android_tool(name):
    sdk = Path(os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT", ""))
    tool = sdk / "build-tools/36.0.0" / name
    if not tool.is_file():
        raise ValueError(f"Missing Android build-tools 36.0.0: {name}")
    return str(tool)


def verify_apk(apk, manifest):
    badging = run(android_tool("aapt"), "dump", "badging", str(apk), capture=True)
    package_line = badging.splitlines()[0]
    expected = ("name='com.pranjal.liveprogress'", f"versionCode='{manifest['versionCode']}'",
                f"versionName='{manifest['versionName']}'")
    if not all(value in package_line for value in expected) or "application-debuggable" in badging:
        raise ValueError("Release APK package/version/debuggable check failed")
    certificates = run(android_tool("apksigner"), "verify", "--verbose", "--print-certs", str(apk), capture=True)
    digest = (ROOT / "release/signing-certificate.sha256").read_text().strip().lower()
    found = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([0-9a-f]+)", certificates, re.I)
    if not re.fullmatch(r"[0-9a-f]{64}", digest) or [value.lower() for value in found] != [digest]:
        raise ValueError("APK certificate does not match the permanent release key")


def build(manifest):
    run("./gradlew", "--no-daemon", "--console=plain", "--no-watch-fs",
        "assembleDebug", "testDebugUnitTest", "lintDebug", "assembleRelease")
    apk = ROOT / "app/build/outputs/apk/release/app-release.apk"
    verify_apk(apk, manifest)
    return apk


def github_release(tag):
    result = subprocess.run(["gh", "api", f"repos/{REPOSITORY}/releases/tags/{tag}"],
                            text=True, capture_output=True, cwd=ROOT)
    if result.returncode:
        if "HTTP 404" in result.stderr:
            return None
        raise RuntimeError("Unable to read GitHub release metadata: " + result.stderr)
    return json.loads(result.stdout)


def asset_names(manifest):
    name = f"Live-Progress-v{manifest['versionName']}.apk"
    return name, name + ".sha256"


def complete(release, manifest):
    if not release or release["draft"]:
        return False
    assets = {asset["name"] for asset in release["assets"] if asset["size"] > 0}
    return set(asset_names(manifest)) <= assets


def publish(manifest, apk):
    tag = "v" + manifest["versionName"]
    release = github_release(tag)
    if complete(release, manifest):
        print(f"{tag} already published; leaving its assets unchanged")
        return
    if release and not release["draft"]:
        raise ValueError("Published release is missing assets; refusing to mutate it")
    with tempfile.TemporaryDirectory() as temp:
        directory = Path(temp)
        notes_file = directory / "notes.md"
        notes_file.write_text(notes(manifest))
        if release is None:
            run("gh", "release", "create", tag, "--repo", REPOSITORY, "--verify-tag", "--draft",
                "--title", "Live Progress " + manifest["versionName"], "--notes-file", str(notes_file))
            release = github_release(tag)
        apk_name, checksum_name = asset_names(manifest)
        existing = {asset["name"] for asset in release["assets"]}
        output = directory / apk_name
        if apk_name in existing:
            # Resume with the already-uploaded binary, never replace a signed asset.
            run("gh", "release", "download", tag, "--repo", REPOSITORY, "--pattern", apk_name, "--dir", temp)
            verify_apk(output, manifest)
        else:
            output.write_bytes(apk.read_bytes())
            run("gh", "release", "upload", tag, str(output), "--repo", REPOSITORY)
        checksum = hashlib.sha256(output.read_bytes()).hexdigest() + "  " + apk_name + "\n"
        checksum_file = directory / checksum_name
        if checksum_name in existing:
            run("gh", "release", "download", tag, "--repo", REPOSITORY, "--pattern", checksum_name, "--dir", temp)
            if checksum_file.read_text() != checksum:
                raise ValueError("Existing release checksum mismatch")
        else:
            checksum_file.write_text(checksum)
            run("gh", "release", "upload", tag, str(checksum_file), "--repo", REPOSITORY)
        run("gh", "release", "edit", tag, "--repo", REPOSITORY, "--draft=false", "--latest",
            "--notes-file", str(notes_file))


def commit_and_tag(manifest):
    source = manifest["sourceCommit"]
    remote_head = git("ls-remote", "origin", "refs/heads/main").split()[0]
    if remote_head != source:
        raise ValueError("main advanced during the build; retry with the latest main. No release was pushed.")
    tag = "v" + manifest["versionName"]
    git("add", "version.properties", "release/history/" + tag + ".json", *[item["file"] for item in manifest["changes"]])
    git("commit", "-m", "Release " + tag)
    git("tag", "-a", tag, "-m", "Live Progress " + manifest["versionName"])
    # Commit and tag are one transaction. A concurrent main change fails without force.
    git("push", "--atomic", "origin", "HEAD:refs/heads/main", f"refs/tags/{tag}:refs/tags/{tag}")


def release_all():
    if os.environ.get("GITHUB_REPOSITORY") != REPOSITORY or os.environ.get("GITHUB_REF") != "refs/heads/main":
        raise ValueError("Publishing is restricted to the original repository's main branch")
    if git("status", "--porcelain"):
        raise ValueError("Release runner checkout must be clean")
    source = git("rev-parse", "HEAD")
    # Recover earlier tagged builds before considering a new version. A failed upload
    # must not strand a version or turn the next push into a duplicate release.
    for manifest in history():
        tag = "v" + manifest["versionName"]
        if complete(github_release(tag), manifest):
            continue
        git("checkout", "--detach", tag)
        try:
            publish(manifest, build(manifest))
        finally:
            git("checkout", "--detach", source)
    manifest = prepare(source)
    if manifest is None:
        print("No unpublished app changes; no new version or APK release.")
        return
    apk = build(manifest)
    commit_and_tag(manifest)
    publish(manifest, apk)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=("validate", "run", "verify-apk"))
    parser.add_argument("--base")
    parser.add_argument("--head", default="HEAD")
    parser.add_argument("--apk", type=Path)
    args = parser.parse_args()
    if args.command == "validate":
        if not args.base or set(args.base) == {"0"}:
            args.base = json.loads((ROOT / "release/baseline.json").read_text())["sourceCommit"]
        validate(args.base, args.head)
    elif args.command == "verify-apk":
        name, code = read_version()
        verify_apk(args.apk, {"versionName": name, "versionCode": code})
    else:
        release_all()


if __name__ == "__main__":
    main()
