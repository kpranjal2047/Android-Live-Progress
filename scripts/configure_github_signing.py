"""Upload the existing local key to this repository's encrypted Actions secrets."""
import base64
from pathlib import Path
import subprocess

REPOSITORY = "kpranjal2047/Android-Live-Progress"


def main():
    root = Path(__file__).resolve().parents[1]
    values = dict(line.split("=", 1) for line in (root / "keystore.properties").read_text().splitlines()
                  if line and not line.startswith("#"))
    store = root / values["storeFile"]
    secrets = {
        "ANDROID_KEYSTORE_BASE64": base64.b64encode(store.read_bytes()).decode("ascii"),
        "ANDROID_STORE_PASSWORD": values["storePassword"],
        "ANDROID_KEY_ALIAS": values["keyAlias"],
        "ANDROID_KEY_PASSWORD": values["keyPassword"],
    }
    for name, value in secrets.items():
        # Values go through stdin, not command arguments, output, or a temporary file.
        subprocess.run(["gh", "secret", "set", name, "--repo", REPOSITORY], input=value,
                       text=True, check=True, stdout=subprocess.DEVNULL)
        print(f"Configured {name}")


if __name__ == "__main__":
    main()
