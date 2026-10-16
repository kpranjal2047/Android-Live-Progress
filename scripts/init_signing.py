"""One-time private signing setup. Never overwrites existing key material."""
import os
from pathlib import Path
import secrets
import subprocess


def main():
    root = Path(__file__).resolve().parents[1]
    private = root / ".signing"
    config = root / "keystore.properties"
    fingerprint = root / "release/signing-certificate.sha256"
    if private.exists() or config.exists() or fingerprint.exists():
        raise SystemExit("Signing material/configuration already exists; refusing to replace it.")
    os.umask(0o077)
    private.mkdir(mode=0o700)
    password = secrets.token_hex(32)
    alias = "live-progress"
    store = private / "live-progress.jks"
    env = {**os.environ, "LP_KEY_PASSWORD": password}
    subprocess.run([
        "keytool", "-genkeypair", "-keystore", str(store), "-storetype", "JKS",
        "-storepass:env", "LP_KEY_PASSWORD", "-keypass:env", "LP_KEY_PASSWORD",
        "-alias", alias, "-keyalg", "RSA", "-keysize", "4096", "-validity", "10000",
        "-dname", "CN=Kumar Pranjal, OU=Live Progress", "-noprompt",
    ], env=env, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
    config.write_text(
        f"storeFile=.signing/live-progress.jks\nstorePassword={password}\n"
        f"keyAlias={alias}\nkeyPassword={password}\nsignDebugWithPrivateKey=true\n"
    )
    store.chmod(0o600)
    config.chmod(0o600)
    certificate = subprocess.check_output([
        "keytool", "-exportcert", "-keystore", str(store), "-alias", alias,
        "-storepass:env", "LP_KEY_PASSWORD",
    ], env=env, stderr=subprocess.PIPE)
    import hashlib
    fingerprint.write_text(hashlib.sha256(certificate).hexdigest() + "\n")
    fingerprint.chmod(0o644)
    print("Permanent key created in .signing; private settings saved in keystore.properties.")
    print("Back up BOTH privately. No passwords were printed.")


if __name__ == "__main__":
    main()
