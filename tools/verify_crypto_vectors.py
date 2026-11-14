"""Verify public M3 specification fixtures; not an application file reader.

Requires Python cryptography. No network, real credentials, file imports, or DB writes.
"""
import base64
import hashlib
import json
import argparse
from pathlib import Path

from cryptography.exceptions import InvalidTag
from cryptography.hazmat.primitives.ciphers.aead import AESGCM


def verify(java_input=None):
    vectors = json.loads((Path(__file__).resolve().parents[1] / "docs/crypto/v1-vectors.json").read_text(encoding="utf-8"))["vectors"]
    positives = negatives = 0
    for vector in vectors:
        envelope = vector["envelope"]
        decode = lambda field: base64.b64decode(envelope[field], validate=True)
        salt = decode("salt") if "salt" in envelope else b""
        key = bytes.fromhex(vector["keyHex"])
        if salt:
            derived = hashlib.pbkdf2_hmac("sha256", vector["publicTestPassword"].encode("utf-8"), salt, 200_000, 32)
            assert derived == key, "PBKDF2 mismatch"
        nonce, encrypted = decode("nonce"), decode("ct") + decode("tag")
        aad = vector["domain"].encode("utf-8") + salt + nonce
        assert aad.hex() == vector["aadHex"]
        plain = vector["plaintextUtf8"].encode("utf-8")
        cipher = AESGCM(key)
        assert cipher.encrypt(nonce, plain, aad) == encrypted, "Encryption mismatch"
        assert cipher.decrypt(nonce, encrypted, aad) == plain, "Decryption mismatch"
        positives += 1

        def reject(candidate, candidate_aad, candidate_key=key):
            nonlocal negatives
            try:
                AESGCM(candidate_key).decrypt(nonce, candidate, candidate_aad)
            except InvalidTag:
                negatives += 1
            else:
                raise AssertionError("Unauthenticated input accepted")

        # Change only the domain prefix, keeping the original key/salt/nonce.
        for other in ("aqz:v1", "aqbak:v1", "aqsnapshot:v1"):
            if other != vector["domain"]:
                reject(encrypted, other.encode("utf-8") + salt + nonce)
        reject(encrypted, b"")
        reject(bytes([encrypted[0] ^ 1]) + encrypted[1:], aad)
        reject(encrypted[:-1] + bytes([encrypted[-1] ^ 1]), aad)
        reject(encrypted, aad, bytes([key[0] ^ 1]) + key[1:])
    print(f"Python: {positives} exact-vector round trips; {negatives} authentication rejections passed.")
    if java_input:
        lines = []
        for vector in vectors:
            env = vector["envelope"]
            password = vector["publicTestPassword"]
            lines.append("\t".join([vector["domain"],
                base64.b64encode(password.encode("utf-8")).decode() if password is not None else "-",
                env.get("salt", "-"), vector["keyHex"], env["nonce"], vector["aadHex"],
                base64.b64encode(vector["plaintextUtf8"].encode("utf-8")).decode(), env["ct"], env["tag"]]))
        Path(java_input).write_text("\n".join(lines) + "\n", encoding="utf-8")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--write-java-input", help="Write public fixture columns for the independent Java verifier")
    verify(parser.parse_args().write_java_input)
