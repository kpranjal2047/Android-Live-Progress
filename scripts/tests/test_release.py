import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("release", Path(__file__).resolve().parents[1] / "release.py")
release = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(release)


class VersionTests(unittest.TestCase):
    def test_release_lookup_includes_drafts(self):
        result = subprocess.CompletedProcess([], 0, '{"isDraft":true,"assets":[]}', "")
        with patch.object(release.subprocess, "run", return_value=result) as command:
            self.assertEqual({"draft": True, "assets": []}, release.github_release("v1.1.0"))
            self.assertEqual(["gh", "release", "view", "v1.1.0"], command.call_args.args[0][:4])

    def test_release_lookup_only_treats_missing_release_as_absent(self):
        for message in ("release not found\n", "authentication failed", "network unavailable"):
            result = subprocess.CompletedProcess([], 1, "", message)
            with patch.object(release.subprocess, "run", return_value=result):
                if message.strip() == "release not found":
                    self.assertIsNone(release.github_release("v1.1.0"))
                else:
                    with self.assertRaises(RuntimeError):
                        release.github_release("v1.1.0")

    def test_publish_resumes_existing_draft_without_creating_another(self):
        manifest = {"versionName": "1.1.0", "changes": []}
        with tempfile.TemporaryDirectory() as temp:
            apk = Path(temp) / "test.apk"
            apk.write_bytes(b"test apk")
            with patch.object(release, "github_release", return_value={"draft": True, "assets": []}), \
                    patch.object(release, "run") as command:
                release.publish(manifest, apk)
                calls = [call.args[:3] for call in command.call_args_list]
                self.assertEqual([("gh", "release", "upload"), ("gh", "release", "upload"),
                                  ("gh", "release", "edit")], calls)

    def test_bumps_and_highest_impact(self):
        for kind, expected in (("patch", "1.2.4"), ("minor", "1.3.0"), ("major", "2.0.0")):
            self.assertEqual((expected, 8), release.next_version("1.2.3", 7, [{"type": kind}]))
        self.assertEqual(("2.0.0", 8), release.next_version("1.2.3", 7, [{"type": "minor"}, {"type": "major"}, {"type": "patch"}]))

    def test_initial_uber_release(self):
        self.assertEqual(("1.1.0", 2), release.next_version("1.0.0", 1, [{"type": "minor"}, {"type": "patch"}]))

    def test_invalid_versions_and_limit(self):
        for name in ("1.0", "01.2.3", "1.0.0-beta", "1.2.3\ninvalid"):
            with self.assertRaises((ValueError, KeyError)):
                release.read_version(f"versionName={name}\nversionCode=1\n")
        with self.assertRaises(ValueError):
            release.next_version("1.0.0", release.MAX_CODE, [{"type": "patch"}])
        with self.assertRaises(ValueError):
            release.next_version("1.0.0", 1, [])

    def test_change_scope(self):
        for path in ("app/src/main/A.kt", "app/src/main/res/values/strings.xml", "app/build.gradle.kts", "gradle/wrapper/gradle-wrapper.properties", ".github/workflows/release.yml", "scripts/release.py"):
            self.assertTrue(release.app_change(path), path)
        for path in ("README.md", "docs/releases.md", "app/src/test/Test.kt", "scripts/tests/test_release.py", "docs/assets/icon.svg"):
            self.assertFalse(release.app_change(path), path)

    def test_complete_release_requires_both_assets_and_not_draft(self):
        manifest = {"versionName": "1.1.0"}
        assets = [{"name": name, "size": 12} for name in release.asset_names(manifest)]
        self.assertTrue(release.complete({"draft": False, "assets": assets}, manifest))
        self.assertFalse(release.complete({"draft": True, "assets": assets}, manifest))
        self.assertFalse(release.complete({"draft": False, "assets": assets[:1]}, manifest))
        self.assertFalse(release.complete(None, manifest))

    def test_notes_group_changes(self):
        text = release.notes({"versionName": "1.1.0", "changes": [{"type": "minor", "summary": "New feature"}, {"type": "patch", "summary": "Fixed bug"}]})
        self.assertIn("## Features\n\n- New feature", text)
        self.assertIn("## Fixes and Improvements\n\n- Fixed bug", text)
        self.assertNotIn("## Breaking Changes", text)


class RepositoryTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.root_patch = patch.object(release, "ROOT", self.root)
        self.root_patch.start()
        release.git("init", "-b", "main")
        release.git("config", "user.name", "Test")
        release.git("config", "user.email", "test@example.invalid")
        self.write("version.properties", "versionName=1.0.0\nversionCode=1\n")
        self.base = self.commit()
        self.write("release/baseline.json", json.dumps({"sourceCommit": self.base}))
        self.commit()

    def tearDown(self):
        self.root_patch.stop()
        self.temp.cleanup()

    def write(self, path, content):
        destination = self.root / path
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(content)

    def commit(self):
        release.git("add", ".")
        release.git("commit", "-m", "test")
        return release.git("rev-parse", "HEAD")

    def record(self, name="feature", kind="minor"):
        self.write(f".changes/{name}.json", json.dumps({"type": kind, "summary": "A change"}))

    def test_missing_new_record_fails(self):
        self.write("app/src/main/Test.kt", "code")
        self.commit()
        with self.assertRaisesRegex(ValueError, "NEW"):
            release.validate(self.base)

    def test_docs_only_does_not_release(self):
        self.write("README.md", "docs")
        source = self.commit()
        self.assertIsNone(release.prepare(source))
        self.assertEqual(("1.0.0", 1), release.read_version())

    def test_prepare_increments_consumes_and_archives(self):
        self.record()
        self.write("app/src/main/Test.kt", "code")
        source = self.commit()
        manifest = release.prepare(source)
        self.assertEqual(("1.1.0", 2), release.read_version())
        self.assertEqual(source, manifest["sourceCommit"])
        self.assertFalse((self.root / ".changes/feature.json").exists())
        self.assertEqual(manifest, json.loads((self.root / "release/history/v1.1.0.json").read_text()))

    def test_manual_version_bump_rejected(self):
        self.write("version.properties", "versionName=9.0.0\nversionCode=2\n")
        self.commit()
        with self.assertRaisesRegex(ValueError, "automation"):
            release.validate(self.base)

    def test_malformed_record_rejected(self):
        self.write(".changes/bad.json", '{"type":"guess","summary":"bad"}')
        with self.assertRaises(ValueError):
            release.records()

    def test_race_rejects_before_commit_or_push(self):
        with patch.object(release, "git", return_value="new-head\trefs/heads/main") as command:
            with self.assertRaisesRegex(ValueError, "advanced"):
                release.commit_and_tag({"sourceCommit": "old-head"})
            self.assertEqual(1, command.call_count)

    def test_build_failure_never_pushes_or_publishes(self):
        with patch.dict("os.environ", {"GITHUB_REPOSITORY": release.REPOSITORY, "GITHUB_REF": "refs/heads/main"}), \
                patch.object(release, "prepare", return_value={"versionName": "1.1.0"}), \
                patch.object(release, "build", side_effect=RuntimeError("build failed")), \
                patch.object(release, "commit_and_tag") as push, patch.object(release, "publish") as publish:
            with self.assertRaisesRegex(RuntimeError, "build failed"):
                release.release_all()
            push.assert_not_called()
            publish.assert_not_called()

    def test_retry_recovers_same_tag_without_new_version(self):
        manifest = {"versionName": "1.1.0", "versionCode": 2}
        self.write("release/history/v1.1.0.json", json.dumps(manifest))
        self.commit()
        release.git("tag", "v1.1.0")
        with patch.dict("os.environ", {"GITHUB_REPOSITORY": release.REPOSITORY, "GITHUB_REF": "refs/heads/main"}), \
                patch.object(release, "github_release", return_value=None), \
                patch.object(release, "build", return_value=Path("test.apk")) as build, \
                patch.object(release, "publish") as publish, patch.object(release, "prepare", return_value=None), \
                patch.object(release, "commit_and_tag") as push:
            release.release_all()
            build.assert_called_once_with(manifest)
            publish.assert_called_once_with(manifest, Path("test.apk"))
            push.assert_not_called()

    def test_completed_release_is_not_overwritten(self):
        manifest = {"versionName": "1.1.0"}
        info = {"draft": False, "assets": [{"name": name, "size": 10} for name in release.asset_names(manifest)]}
        with patch.object(release, "github_release", return_value=info), patch.object(release, "run") as command:
            release.publish(manifest, Path("unused.apk"))
            command.assert_not_called()

    def test_forks_and_other_branches_cannot_publish(self):
        with patch.dict("os.environ", {"GITHUB_REPOSITORY": "someone/fork", "GITHUB_REF": "refs/heads/main"}):
            with self.assertRaises(ValueError):
                release.release_all()


if __name__ == "__main__":
    unittest.main()
