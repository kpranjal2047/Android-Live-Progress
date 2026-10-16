# Project Changes and Releases

- Preserve existing user changes. Update README for customer-facing changes and features.md/decisions.md for implementation details.
- README is customer-facing; keep build, signing, and workflow details in docs/releases.md.
- For changes to app code/resources, dependencies, build configuration, signing, or release automation, add a new `.changes/<descriptive-name>.json` file with `type` and `summary`.
- Classify from the user's request AND the actual diff: `patch` for fixes/performance/maintenance, `minor` for compatible new features, `major` only for an explicitly identified breaking change. Ask when impact is ambiguous; never infer a major bump merely from diff size.
- Use a short customer-facing summary. Never include secrets, notification contents, or personal trip details.
- Documentation/test-only changes do not need a release record. Do not edit consumed records or release history.
- Do not manually bump version.properties. GitHub consumes pending records and updates it after successful release validation. MainActivity reads BuildConfig.VERSION_NAME; do not hardcode another UI version.
- Before committing, run release-script tests, debug build, unit tests, lint, and `git diff --check`. Never commit private signing material.
- Never generate a replacement signing key, overwrite an existing key, force-push release history, or install a differently signed APK over a user's data without authorization.
