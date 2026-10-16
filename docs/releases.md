# Versions, Signing, and GitHub Releases

## Everyday changes

For an app/build change, add `.changes/descriptive-name.json`:

```json
{
  "type": "minor",
  "summary": "Describe the improvement customers will notice."
}
```

Use `patch` for fixes, performance improvements, and maintenance; `minor` for compatible new features; `major` for explicit breaking changes. Classification comes from the request and code review, not commit size or commit-message guessing. Several pending records produce one release using the highest impact. Commit messages can use any format. Documentation and test-only changes do not release an APK.

`version.properties` is the single version source for Android, generated BuildConfig, and the home screen's inline About version. Do not bump it by hand. The baseline is 1.0.0/code 1; pending Uber support makes the first release 1.1.0/code 2. Each release increments the code by one, up to Android's 2,100,000,000 limit.

## Permanent signing key

Run `python3 scripts/init_signing.py` **once** to create `.signing/live-progress.jks`, local `keystore.properties`, and a public fingerprint at `release/signing-certificate.sha256`. The command refuses to replace existing material. The first two paths are private, ignored by Git, and created with restrictive permissions. Both local debug and release builds use the private key. Contributors without it can build their own debug APK; release builds fail without private signing.

Keep an encrypted backup of BOTH `.signing/live-progress.jks` and `keystore.properties` in a password manager or another secure location. Do not commit, publish, or paste these files into issues or chat. GitHub secrets cannot be downloaded later as a backup. Losing the key/password prevents signing upgrades for existing users. Do not generate a new key for each release.

Existing APKs signed by an older debug key cannot be upgraded with this new key. Switching requires a one-time uninstall/reinstall, which deletes local app data unless separately backed up. Do not perform this silently. Future local and GitHub builds using this permanent key can upgrade each other, provided the new version code is not lower.

## One-time GitHub setup

In this repository's Settings > Secrets and variables > Actions, configure:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64 of `.signing/live-progress.jks` |
| `ANDROID_STORE_PASSWORD` | `storePassword` from local signing settings |
| `ANDROID_KEY_ALIAS` | `keyAlias` from local signing settings |
| `ANDROID_KEY_PASSWORD` | `keyPassword` from local signing settings |

Only trusted main-branch release jobs receive these values. Missing secrets fail the job; there is no debug-key fallback. No private keys are uploaded as build artifacts. The temporary runner keystore and properties file are removed even when a job fails. The public fingerprint verifies every release APK.

Enable GitHub Actions and allow the release workflow's `contents: write` permission. Branch/tag rules must permit its non-force metadata commit and `v*` tag. The workflow does not bypass protections. Pull-request jobs have read-only permissions, no signing secrets, and no persisted checkout credentials. Review changes to release workflows/scripts before merging: trusted main code with signing access can use the key.

## Release lifecycle and recovery

For an authenticated GitHub CLI session with repository administration access, `python3 scripts/configure_github_signing.py` uploads the four existing local values directly through stdin to encrypted repository secrets. It does not create a key, print passwords, or change the repository's permissions.

`Checks` builds debug, runs unit tests/lint, and validates new change records. `Signed APK Release` serializes main pushes, checks out the latest main, and includes all unpublished app changes; rapid pushes may be combined. It runs release-script tests, restores signing secrets, calculates versions, then builds/tests/lints and verifies the signed release APK. Build or validation failures never advance the remote version; publishing failures after the atomic push resume the already allocated version.

After validation it commits only the version file, release history, and consumed records. That commit and its annotated version tag are pushed atomically without force; if main advanced during the build, the push is refused. Rerun against current main. All publishing happens in the same job because a tag pushed using `GITHUB_TOKEN` does not normally trigger another workflow.

The job creates a draft Release, uploads `Live-Progress-vX.Y.Z.apk` and its `.sha256` checksum, then publishes it as Latest. Notes group features, fixes, and breaking changes. GitHub's source archives correspond to the tagged release commit, including its version metadata.

Release lookup uses the GitHub CLI's draft-aware lookup rather than the REST published-release-by-tag endpoint, so a newly created or interrupted draft is found and resumed instead of duplicated. Authentication and network failures stop the job rather than being mistaken for a missing release.

If upload/publishing fails after the tag push, rerun `Signed APK Release` from Actions > Run workflow on main. It recovers tagged unfinished releases before calculating anything new, reuses uploaded APK assets, verifies checksums, and does not change already published releases. Never move/delete a published tag or replace its APK. A published release with missing assets fails for manual investigation rather than silently rewriting it. Release history is retained in `release/history/`.

Local clones must pull main after the bot's release commit to receive the new home-screen version. Before another push, integrate that commit normally; never force-push over it.

## Validation

```sh
python3 -m unittest discover -s scripts/tests -v
./gradlew --no-daemon --console=plain --no-watch-fs assembleDebug testDebugUnitTest lintDebug assembleRelease
python3 scripts/release.py verify-apk --apk app/build/outputs/apk/release/app-release.apk
```

Set `ANDROID_HOME` to your SDK for APK verification (build-tools 36.0.0). The APK check rejects the wrong package, version, certificate, or a debuggable release. CI runners use Java 21 and the repository's pinned Gradle toolchain configuration. Tests cover version precedence, release scope, records, failed builds, races, and retry behavior.
