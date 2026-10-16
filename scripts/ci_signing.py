"""Materialize signing secrets in a trusted release job, without logging them."""
import base64
import os
from pathlib import Path


def properties_escape(value):
    return (value.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r")
            .replace("=", "\\=").replace(":", "\\:").replace(" ", "\\ "))


def main():
    required = ("ANDROID_KEYSTORE_BASE64", "ANDROID_STORE_PASSWORD", "ANDROID_KEY_ALIAS", "ANDROID_KEY_PASSWORD")
    if any(not os.environ.get(key) for key in required):
        raise SystemExit("Missing Android signing secrets; refusing debug-key fallback.")
    os.umask(0o077)
    root = Path(__file__).resolve().parents[1]
    store = Path(os.environ["RUNNER_TEMP"]) / "live-progress-release.jks"
    store.write_bytes(base64.b64decode(os.environ[required[0]], validate=True))
    values = {
        "storeFile": str(store), "storePassword": os.environ[required[1]],
        "keyAlias": os.environ[required[2]], "keyPassword": os.environ[required[3]],
        "signDebugWithPrivateKey": "true",
    }
    (root / "keystore.properties").write_text("".join(f"{key}={properties_escape(value)}\n" for key, value in values.items()))
    print("Private signing configured for this release job.")


if __name__ == "__main__":
    main()
