# Encrypted file formats (v1)

## Envelope and encoding


All files are strict UTF-8 JSON objects. Writers use UTF-8 without BOM and unescaped Unicode where convenient; JSON member order/whitespace need not match examples. Authentication covers the exact decrypted plaintext bytes, not reserialized JSON. No JSON canonicalization is required.

| Domain | Exact envelope fields | Key source | AAD bytes, concatenated without separators |
| --- | --- | --- | --- |
| Connection `.aqz` | `v`, `kdf`, `iter`, `salt`, `nonce`, `ct`, `tag` | PBKDF2-HMAC-SHA256 | UTF-8 `aqz:v1` + raw salt + raw nonce |
| Backup `.aqbak` | `v`, `kdf`, `iter`, `salt`, `nonce`, `ct`, `tag` | PBKDF2-HMAC-SHA256 | UTF-8 `aqbak:v1` + raw salt + raw nonce |
| Telegram snapshot | `v`, `nonce`, `ct`, `tag` | Shared snapshot key directly | UTF-8 `aqsnapshot:v1` + raw nonce |

`v` is integer 1. Password envelopes use the exact string `kdf="PBKDF2-HMAC-SHA256"`, integer `iter=200000`, a 16-byte salt, and a 32-byte derived key. Snapshot keys are exactly 32 bytes and do not undergo another KDF. Every domain uses AES-256-GCM, a 12-byte nonce, and a 16-byte/128-bit tag. `ct` excludes the tag; JCA's combined result must be split/joined at its final 16 bytes. AAD must be supplied before ciphertext processing. These conventions follow the [JCA authenticated-encryption API](https://docs.oracle.com/javase/8/docs/technotes/guides/security/crypto/CryptoSpec.html) and [AESGCM API](https://cryptography.io/en/46.0.3/hazmat/primitives/aead/).

Binary values are standard RFC 4648 Base64, with canonical padding where needed, without embedded whitespace or URL-safe substitutions. Empty/missing required values and incorrect decoded lengths are invalid. The expected domain comes from the operation, not an attacker-controlled extension or envelope hint. V1 envelopes accept their exact field set; a snapshot cannot supply password/KDF fields.

Connection passwords normalize accepted Arabic/Western numeric digits to ASCII before PBKDF2, preserving digit order. Connection-file issuance and reading require at least four numeric digits; existing valid numeric credentials remain readable. Issuance offers a securely generated 16-digit password. Short numeric passwords have limited resistance to offline guessing. Avoid dates, repeated digits and sequences; send the file and password through separate channels. Backup passphrases use their exact UTF-8 bytes, with no trimming, normalization, case folding, or digit substitution; the existing minimum-eight-character rule remains. Snapshot encryption has no password. The public Unicode backup fixture deliberately includes Arabic, `é`, and leading/trailing spaces to pin byte handling.

Production writers generate salts/nonces with SecureRandom for each encryption; fixed values below are exclusively fixtures. Never reuse a fixture key, nonce, password, salt, or random source in production. Wrong password and failed authentication receive the same friendly “Incorrect password or damaged file” message. Unsupported envelope versions/KDF settings are rejected before deriving a key; untrusted iteration counts are never executed. Structural errors may be reported as invalid files without exposing provider details or secrets.

## Exact limits and resource safeguards

| File | Complete encrypted-envelope byte limit |
| --- | ---: |
| Connection | 10 MiB = 10,485,760 bytes |
| Telegram snapshot | 10 MiB = 10,485,760 bytes |
| Backup | **64 MiB = 67,108,864 bytes** |

The backup cap exceeds the snapshot cap to accommodate retained device-local history. Backup export/restore, reminders and maintenance checkpoints use this same cap. The limit includes UTF-8 JSON, Base64 expansion, fields, whitespace, and authentication data. At the cap accept the size; one byte over rejects. This does not guarantee that arbitrary malformed data is valid or that a low-memory device can process every under-cap file.

Reader/writer acceptance constraints:

- Count actual bytes from a stream; provider-reported lengths are hints only. Stop at cap + 1 bytes even for unknown/misreported lengths. Bound decoded bytes by the same envelope-derived budget before allocating. Do not load an unbounded stream into a String, ByteArray, or JSON tree.
- Parse JSON incrementally with a maximum nesting depth of 64. Reject duplicate decoded member names at every object level, including aliases using JSON escapes; reject malformed UTF-8, malformed Base64, invalid number types, and missing required fields. Count bytes before parsing or PBKDF2. No decompression is part of these formats.
- Run one file crypto operation at a time, off the main thread; support cancellation and close every stream. Avoid holding the Base64 envelope, decoded ciphertext, plaintext String, and model tree concurrently. Encrypted temporary input may be spooled only into private backup-excluded storage and deleted after success/failure/cancellation and on recovery from abandoned operations. Do not stage plaintext passwords, credentials, or decrypted backup contents as unencrypted files.
- Account for the provider's GCM buffering, ciphertext/plaintext buffers, parsed records, and existing catalog when checking memory headroom. Use bounded buffers and conservative preflight; fail with a resource error before large allocations when headroom is insufficient. Do not rely on catching OutOfMemoryError. Representative low-memory Android measurements are required before claiming large-backup support.
- Treat decrypted bytes as untrusted until the final GCM tag verifies. Then validate payload identity, compatibility, required fields, references, and domain invariants before changing active storage. Never change the active pharmacy/connection/compatibility state because of unauthenticated input. Authenticated unsupported versions follow the existing viewing/recovery-only contract, not an ordinary destructive import path.
- Count final serialized output, including Base64 overhead. Stage/validate output before marking an export successful; a partial/cancelled/over-cap SAF write is failure and does not advance backup reminders or maintenance checkpoints. Do not omit/truncate retained history to fit. Resource/size failures preserve existing data and retained work.

Envelope/payload readers, connection/snapshot writers and staged backup export/restore are implemented. Historical device round trips and controlled heap-pressure checks are recorded in [validation](../validation.md#focused-backupresourcepdf-evidence--2026-11-12). Current-source and broader platform verification remain incomplete, with physical/live campaigns stopped as recorded in the [plan](../plan.md); these are verification limits rather than deferred implementation. These safeguards and dated measurements do not establish universal low-memory/large-backup support. The examples below are crypto fixtures, not importable pharmacy data.

## Examples and fixed vectors

[v1-vectors.json](v1-vectors.json) contains three complete example envelopes and their exact plaintext UTF-8 strings, public passwords/keys, salts, nonces, AAD hex, ciphertext, and tags. The connection fixture includes Arabic text; the snapshot plaintext includes the stable compatibility object. They are **crypto-layer fixtures only**, not valid connection files, complete snapshots, or backups. No real bot token, pharmacy credential, or production secret is included, and these docs/tools are outside APK source sets.

Verification commands (Python `cryptography` is a development-only prerequisite; the Java verifier uses standard JDK crypto):

```sh
python3 tools/verify_crypto_vectors.py --write-java-input /tmp/aqaqeer-public-crypto-vectors.tsv
java tools/VerifyCryptoVectors.java /tmp/aqaqeer-public-crypto-vectors.tsv
```

Results and platform limits: [validation](../validation.md). Reader and payload contracts: [readers](READERS.md).

## Backup payload schema 1

The authenticated JSON has `header` and `tables`. Header carries `schemaVersion: 1`, local-draft/shared lifecycle, optional active/setup connection payloads and retained compatibility requirements. This is the new initial payload, without compatibility for earlier development layouts. Unsupported versions/layouts are rejected without application; no development-format migrations are retained.

`tables` contains exactly the frozen ordered columns for pharmacy, categories, forms, sections, medications, availability, tombstones, history, snapshot state, pending operations and publication attempts, as defined by `backupTables` in `BackupArchive.kt`. Each row is a positional cell array. A null cell is JSON null; a non-null cell is an array of canonical Base64 segments of at most 8,192 UTF-8 bytes. Integers use their SQLite decimal text. JSON member names are bounded to 8,192 characters. Concatenated cell bytes are strictly UTF-8 validated before application; chunk boundaries may split a codepoint. This representation preserves embedded NUL and large JSON/history fields. Row order preserves pending-operation replay order. Neither local-control/device tables nor Keystore records are part of the payload.

The outer v1 envelope, KDF/AAD and limits remain unchanged. Cell Base64 and envelope Base64 both count toward the complete-file limit. No history is omitted to fit. Private staging uses independently authenticated encrypted chunks and SQLCipher; it is never a plaintext backup cache. Import GCM buffering is resource-budgeted, so an under-cap file can still fail safely on insufficient memory/storage.


The initial medication row excludes priceMinor/currency. The snapshot-state row includes nullable authenticated publishedAt as exact ISO Instant text immediately after slot; restore checks it against the confirmed payload (null for an unpublished draft). All ordered column changes are defined together in backupTables. Room schema numbering is independent of envelope/payload identifiers; Room is a fresh version-1 baseline and existing development databases/files are not upgrade targets. Cryptographic algorithms, AAD, limits and key handling are unchanged.
