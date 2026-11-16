# Aqaqeer · عقاقير

<p align="center">
  <img src="assets/branding/aqaqeer-icon-gold-teal.png" width="144" alt="Aqaqeer gold and teal logo">
</p>

**Medication availability by pharmacy section, with offline viewing and encrypted Telegram sharing.**

عقاقير — توافر الأدوية حسب القسم، مع عرض دون اتصال ومشاركة مشفرة.

[Download APKs](https://github.com/Hjjar-AI/Aqaqeer/releases) · [دليل المستخدم](docs/user-guide-ar.md) · [Build and signing guide](docs/build.md) · [Verification evidence](docs/validation.md)

Aqaqeer is an Android app with an Arabic RTL interface and support for Arabic and English medication names. Each phone holds one pharmacy, with independent availability states across 1–5 sections. Browsing and local administration work offline; connected phones exchange encrypted snapshots through a dedicated private Telegram group and bot.

The app tracks availability, not quantities or inventory transactions. It does not manage patients, prescribing or billing. Requires **Android 8.0 / API 26 or later**.

## What you can do

- **Browse and find medications:** search Arabic/English names, trade names and strengths; filter by category, availability, expiry, unpublished edits or fetched changes.
- **Read comfortably:** choose compact or expanded cards, adaptive or single-column lists, collapsible groups, bar placement, light/dark/AMOLED themes and optional secondary Hijri dates.
- **Administer the catalog:** edit medications, sections, categories and dosage forms; save individual availability changes immediately or preview and confirm bulk changes.
- **Share and reconcile:** publish pending work, refresh cached data and review conflicting changes while retaining publication and recovery safeguards.
- **Import, report and recover:** preview CSV imports, export CSV/PDF reports, create encrypted backups and restore through explicit reconciliation.
- **Review local changes:** inspect before/after History with pharmacy-local dates and filters, without collecting editor names.

## Get started

| Your situation | Start here |
| --- | --- |
| Joining an existing pharmacy | Obtain its encrypted `.aqz` connection file and password from an admin, then import, verify and activate it. Joining starts in Reader mode. |
| Creating a pharmacy | Enter its name, timezone, first section and confirmed admin PIN. Add medications offline, then configure Telegram and publish when ready. |
| Recovering a pharmacy | Import an encrypted `.aqbak` backup with its passphrase, review the restored state and reconcile with live data before publication. |
| Exploring the interface | Use the separate demo app; it opens a local sample pharmacy with Reader and simulated Admin modes, without connecting to Telegram. |

Follow the [Arabic user guide](docs/user-guide-ar.md) for screen-by-screen instructions and [operations guide](docs/operations.md) for Telegram setup, backups and recovery.

## Download and verify

1. Open [GitHub Releases](https://github.com/Hjjar-AI/Aqaqeer/releases) and check whether the release is **production** or **debug**. For production, choose the universal APK unless you specifically need ARM32 or ARM64.
2. Download `SHA256SUMS.txt` and its listed assets into one folder, then verify them:

   ```sh
   sha256sum --check SHA256SUMS.txt
   ```

   A missing listed asset means the bundle is incomplete; it is not a successful verification of the full bundle.
3. Inspect the APK signer and compare its certificate fingerprint with the published fingerprint:

   ```sh
   apksigner verify --print-certs Aqaqeer.apk
   ```

Replace `Aqaqeer.apk` with your downloaded filename. Checksums verify downloaded bytes; the certificate identifies the signer. Updates to an existing installation must retain its package and signing identity. Debug and production apps have separate packages/storage; installing one does not automatically transfer the other's pharmacy.

## Build

Use **JDK 21**, Python and the pinned Android SDK/Gradle toolchain described in the [build guide](docs/build.md). Android Studio is optional. Build from the repository root through the supplied helper and wrapper:

```sh
# Normal app: starts with pharmacy setup.
python3 tools/build_debug.py standard

# Demo app: opens a local sample pharmacy.
python3 tools/build_debug.py demo
```

Choose `both` to build the flavors independently. Add `--offline` only when the required dependencies are already cached. Packaging reserves a version through the existing locked counter; use the documented wrappers and preserve consumed codes.

| Variant | Package | Initial state |
| --- | --- | --- |
| Standard debug | `com.aqaqeer.app.debug` | Unconfigured pharmacy |
| Demo debug | `com.aqaqeer.app.demo.debug` | Sample pharmacy: Reader / simulated Admin |
| Production | `com.aqaqeer.app` | Unconfigured pharmacy |

The demo contains [40 psychiatry/supportive-care sample entries](docs/demo-data.md), with fictional availability and expiry dates. Choose the demo mode in Settings. Simulated Admin can edit temporary samples, review changes and view History; Reader follows normal reader visibility. Reset restores the samples, and a fresh app process discards edits. No real synchronization/publication is available. Demo data never enters standard builds; demo release builds are disabled.

### GitHub Actions

| Workflow | Purpose |
| --- | --- |
| **03 — Tests and compilation** | `scope=jvm` runs both JVM flavors and public JDK vectors; `scope=all` also includes compilation jobs. Coverage is optional. |
| **06 — Build Debug APKs** | Build `standard`, `demo` or `both`, with isolated debug signing. |
| **10 — Publish Debug APKs** | Publish selected debug APKs to a GitHub release, prerelease or draft. |
| **11 — Production release** | Build, sign and publish the standard production APKs. |

See the [complete workflow guide](docs/build.md#manual-quality-and-security-workflows), [key/signing setup](docs/build.md#prepare-signing-once) and [CI controls](docs/ci-free-tier.md). Signing identities are permanent; signing keys are excluded from caches and uploaded artifacts.

## Data and sharing model

- **Encrypted local storage:** Room/SQLCipher stores pharmacy data, with keys protected by Android Keystore. Explicit encrypted backups are separate from connection files.
- **Coordinated publication:** admins must coordinate publishing. Telegram sharing provides no distributed lock or guaranteed atomic concurrent writes.
- **Trusted participants:** connected phones share Telegram credentials and a snapshot key. Reader/admin restrictions are enforced by the app, not cryptographically by Telegram.
- **Local History:** History belongs to each installation and is included in its explicit backup, not shared snapshots. A newly joined phone receives the current catalog without earlier History.
- **Initial version-1 formats:** the current database and file definitions target fresh installations. Earlier development databases/files have no conversion path; incompatible data is rejected without automatic wiping.

Exact compatibility, encryption, size limits and recovery rules live in the [requirements](docs/requirements.md) and [file contracts](docs/crypto/READERS.md).

## Verification status

Hosted JVM checks and builds have passed for the revisions recorded in [validation](docs/validation.md). Earlier Android results are dated evidence and do not establish acceptance of later UI changes. Current remaining checks—including Android runtime/UI acceptance, schema-export regeneration and production signing/update continuity—are tracked in the [plan](docs/plan.md).

## Documentation

| Guide | What it covers |
| --- | --- |
| [دليل المستخدم](docs/user-guide-ar.md) | Arabic usage, setup and recovery instructions |
| [Build, signing and releases](docs/build.md) | Toolchain, local/GitHub builds, permanent keys, versions and troubleshooting |
| [Operations](docs/operations.md) | Telegram setup, backup/restore and credential maintenance |
| [Requirements](docs/requirements.md) | Product behavior and acceptance rules |
| [Architecture](docs/architecture.md) | Storage, synchronization, imports and presentation mechanisms |
| [Plan](docs/plan.md) · [Validation](docs/validation.md) | Remaining work and executed evidence |
| [GitHub Actions guide](docs/github-actions-guide.md) | Workflow concepts and examples |
| [CI controls](docs/ci-free-tier.md) | Runner, cache, storage, cleanup and signing boundaries |
| [File formats](docs/crypto/FORMATS.md) · [Reader contracts](docs/crypto/READERS.md) | Encryption, strict payload rules, bounds and public vectors |
| [Demo catalog](docs/demo-data.md) · [Branding](docs/branding.md) | Sample-data provenance and selected artwork |

The repository contains Android sources in `app/`, canonical guidance in `docs/`, artwork in `assets/branding/`, utilities in `tools/` and workflows in `.github/`.

For development, read [AGENTS.md](AGENTS.md) first. Optional local pre-push checks can be enabled with `git config core.hooksPath .githooks`; restore Git's default with `git config --unset core.hooksPath`. The hook checks the working tree and skips deletion-only pushes.

## License and contact

Project-owned code uses [Apache-2.0](LICENSE). Third-party terms are retained in [license notices](licenses/THIRD-PARTY-NOTICES.txt). Report security issues through [private reporting](SECURITY.md).

Created by **محمد علي حجار**. The app's About screen opens an email composer for **hjjarmhmmdali@gmail.com** without sending automatically.
