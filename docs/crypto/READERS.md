# Reader and wire contracts

## Envelope reader

`EnvelopeReader` is the bounded in-memory codec for snapshot/connection reads and crypto fixtures. Production backup export/restore uses the chunked `V1BackupEnvelope` path described in [architecture](../architecture.md#encrypted-backup-and-restore). The in-memory codec accepts an explicitly selected connection/backup domain with a CharArray password, or a snapshot with a 32-byte key. It stages the supplied input into its own subdirectory under a caller-provided private, backup-excluded root. Android integration supplies `Context.noBackupFilesDir`; device checks use a random test subdirectory there. No decrypted data is written to disk.

Actual input bytes are counted, irrespective of `available()`/external metadata. Reads stop at cap + 1, using the 10 MiB connection/snapshot or 64 MiB backup cap. Staging completes before parsing, PBKDF2, and decryption. A process-wide coroutine Mutex serializes operations, including cleanup. Cancellation closes even a queued input. Stages are removed on success/failure/cancellation; abandoned owned stage names are removed on the next operation or explicit `cleanupAbandoned()`. Unrelated files are retained. Startup/import requests abandoned-stage cleanup.

Memory preflight reserves 16 times the staged byte length plus 64 KiB, against 75% of currently available heap after a 16 MiB reserve. This deliberately accounts for JCA provider copies, decoded arrays, UTF-16 keys, and duplicate-key sets without claiming universal large-backup support. It may reject an under-cap file for insufficient resources. The decoder streams JSON/Base64 and retains no full encoded envelope String/tree, but the JCA call and returned authenticated payload use bounded byte arrays. These in-memory limits do not describe the separate chunked backup codec; representative controlled resource measurements and remaining platform limits are recorded in [validation](../validation.md).

`StrictJson` validates one root object without materializing a payload tree. It rejects duplicate decoded keys in every object, malformed UTF-8, control characters, invalid Unicode escapes/surrogates, trailing data/commas, malformed numbers, and container nesting above 64. Number tokens above 1024 characters produce a resource-limit failure. The Base64 decoder validates canonical alphabet, padding, and unused bits incrementally. The envelope field set, lengths, integer version/iteration settings, and fixed KDF are checked before key derivation.

PBKDF2-HMAC-SHA256 remains exactly 200,000 iterations/256 bits. Connection passwords normalize Arabic/Persian numeric digits to ASCII; issuance and reading accept at least four digits, preserving existing valid numeric credentials; issuance offers secure 16-digit generation; backup passphrases preserve their exact Unicode and whitespace, with eight Unicode code points as the character-count default. Unpaired password surrogates are rejected. Caller-owned arrays remain unchanged; owned password/key/decrypted buffers are cleared where the codec controls them. Provider-internal copies cannot be claimed erased by this code.

AES-GCM uses the [documented v1 domains/AAD](FORMATS.md), and authentication completes before payload JSON validation or return. Wrong passwords, wrong keys, and changed authenticated data return the same `AUTHENTICATION` category. Other error codes distinguish unsupported envelope settings, invalid structure/credentials, size, resource, IO, and unavailable crypto. Provider/IO exception messages are not forwarded. Cancellation is rethrown. A result lost during cancellation/dispatch back or input-close failure is closed and its owned plaintext cleared; see [withContext's cancellation contract](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/with-context.html). AES-GCM call ordering follows the [Android Cipher API](https://developer.android.com/reference/javax/crypto/Cipher).

`AuthenticatedJson` is closeable and exposes its bytes through a scoped UTF-8 Reader. It means authentication and JSON syntax succeeded, **not** that pharmacy identity/schema/compatibility or domain invariants passed. The caller must still validate those before applying anything. Import and snapshot clients validate payloads before applying data. Snapshot/connection writers and staged backup export/restore are implemented; executed backup evidence and limits live in [validation](../validation.md).

## Payload reader

`PayloadReader` consumes and closes an `AuthenticatedJson` on success, failure, or cancellation. Envelope results carry their authenticated domain, preventing a backup or connection payload from being accepted as a snapshot. Envelope crypto, staging cleanup, and payload decoding share one process-wide mutex. Decode runs off the main thread, with cancellation checks during stream reads/header scans and before returning. It uses the existing strict syntax/duplicate-key validation and [kotlinx.serialization stream decoding](https://kotlinlang.org/api/kotlinx.serialization/kotlinx-serialization-json/kotlinx.serialization.json/decode-from-stream.html), without constructing another complete plaintext String or JSON tree.

Before models are allocated, an additional conservative preflight reserves 32 times the authenticated byte length plus 64 KiB against 75% of available heap after a 16 MiB reserve. This is a rejection safeguard, not a measured low-memory guarantee. Model lists and invariant-validation sets remain in memory. Backups are deliberately unsupported here.

## Connection contract

The v1 payload has exactly `schemaVersion`, `pharmacyId`, `pharmacyName`, `botToken`, `groupId`, `snapshotKey`, `keyVersion`, `createdAt`, and `createdBy`. Required fields have no wire defaults. `schemaVersion` is integer 1; `keyVersion` is a positive integer; `groupId` is a negative signed 64-bit JSON integer for a Telegram group. `createdAt` is an Instant string; `createdBy` uses the existing `{id, name}` EditorRef model, with a canonical lowercase UUID and trimmed nonblank name. `snapshotKey` is canonical padded standard Base64 encoding exactly 32 bytes. Pharmacy identity is a canonical lowercase UUID; pharmacy name and token must be trimmed and nonblank. Token checks are structural only; Telegram validates actual credentials during verification/activation.

An optional expected pharmacy identity supports explicit same-pharmacy replacement. Replacement/join authorization remains a repository responsibility. Candidate credentials never include role, PIN, or device authorization. `ConnectionCandidate` has no generated data-class `toString`; its owned key is wiped on close and dispatch-back cancellation. `copySecrets()` returns a caller-owned key copy that the caller must clear. Immutable token/Base64 strings and serializer-internal copies cannot be claimed erased.

## Snapshot contract

The supported v1 root has exactly `compatibility`, `revision`, `publishId`, `parentPublishId`, `basedOnRevision`, `publishedAt`, `publishedBy`, `pharmacy`, `categories`, `forms`, `sections`, `medications`, `availability`, and `tombstones`. Arrays are required even when empty. Shared records reuse the existing serializable models and their optional/defaulted fields; pharmacy schema/minimum-app/key metadata must be explicitly supplied. Editor fields use EditorRef. No local history, pending operations, attempts, credentials, or device authorization are accepted as root fields.

The stable observation scan also requires revision, publishId and publishedAt for supported and unsupported schemas, without materializing future entity structures. `RequiresUpdate` carries these fields for caption checks. The stable compatibility object has exactly three positive JSON integers: `schemaVersion`, `minVersionCode`, and `keyVersion`. The first scan reads that object and `pharmacy.pharmacyId`, skipping future entity structures without deserializing them. Pharmacy and key-version mismatch are rejected before any compatibility observation is returned. Unsupported schema or a higher minimum app version returns `RequiresUpdate`; it carries no catalog candidate. Unsupported data is not applied; repositories persist requirements and enforce command restrictions.

For supported requirements, another bounded scan checks explicit integer pharmacy/publication metadata before typed decoding. Pharmacy compatibility metadata must match the stable header. Publications have positive revision, `basedOnRevision = revision - 1`, a UUID publish ID, and a distinct UUID parent for revisions above 1; revision 1 requires a null parent. A migration's source-revision-plus-one lineage fits this rule. The read client checks caption agreement; refresh applies the lineage policy described in [architecture](../architecture.md).

Catalog validation reuses `CatalogRules`: sections, protected fallbacks, normalized taxonomy names, unique IDs/pairs, references, at least one trimmed scientific name (genericAr/genericEn), optional commercial names (nameAr/nameEn), required strength, expiry threshold, timezone, and fixed PIN iteration count. Additional boundary checks require canonical entity/editor UUIDs, nonblank trimmed editor names, canonical 16-byte PIN salt/32-byte verifier hash, and unique tombstone composite keys with nonblank trimmed entity types. Domain commands interpret tombstone entity types and implement conflict/rebase behavior.

All domain/serialization error messages are reduced to stable error codes. Cancellation propagates. Repositories own all persistence and command authorization; the payload reader returns candidates only.

## Telegram caption and stable header

The caption is exactly four LF-separated lines, with no trailing newline:

```text
aqaqeer:v1
revision=1
publishId=00000000-0000-4000-8000-000000000123
publishedAt=2026-11-09T12:00:00Z
```

The example is metadata only. Revision is a positive canonical decimal Long, publishId a canonical lowercase UUID, and publishedAt canonical Instant.toString() output. Extra lines/whitespace, alternate numeric spellings, or malformed fields are rejected. Caption equality with the authenticated revision/ID/instant is mandatory; it never supplies authority by itself. Writers use SnapshotCaption.encode.

The encrypted v1 payload's stable observation header requires revision, publishId, and publishedAt alongside compatibility and pharmacy.pharmacyId, even when entity schema is unsupported. PayloadReader reads those fields without materializing future entity structures. RequiresUpdate carries that publication identity so a caption mismatch cannot activate a compatibility observation. Supported metadata is also checked against typed decoding. Public crypto-only fixtures remain incomplete domain payloads.

## Telegram resource and transport limits

API metadata counts actual bytes up to 256 KiB + 1, validates strict UTF-8/JSON/duplicate keys, then parses a bounded tree. A conservative 64-times-byte-count plus 64 KiB memory preflight uses 75% of available heap after a 16 MiB reserve. Unknown API fields are allowed; required consumed fields retain strict types. Snapshot download counts actual bytes against the 10 MiB envelope cap; EnvelopeReader independently enforces that cap. Provider/file sizes are not trusted as limits.

Only the official HTTPS host is constructed. Token syntax is constrained before URL construction; getFile paths reject traversal, escaped segments, query/host substitutions, or unrecognized characters. Redirects and HTTP caching are disabled, non-identity content encodings are rejected, and platform TLS validation is retained. Error bodies, token URLs, and provider exception messages are not forwarded or logged. Credentials/access/rate-limit/network/format/resource/changed-document outcomes have stable categories; the read adapter has no automatic retries or publication endpoints. Separate setup/write adapters handle publication.

Connect/read timeouts are 15 seconds, with a cooperative 60-second per-request elapsed-time check between IO steps. These checks are not a hard interruption guarantee for blocking platform IO. Returned download streams capture their caller's cancellation context, not the completed IO-dispatch child. Streams disconnect on close/failure and are closed if cancellation discards an opened stream during dispatch back.

Cancellation is cooperative between chunks/provider calls; blocking external streams and JCA operations may delay it. See [architecture](../architecture.md) for persistence and [validation](../validation.md) for executed checks.

## Initial medication contract

Supported initial snapshots require at least one scientific name, permit optional commercial names, and contain no price/currency fields. Earlier development payloads are not compatibility targets. CatalogRules still validates complete references before application; UI fallback labels never replace this validation. Connection and encryption contracts are unchanged.
