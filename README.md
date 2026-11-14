# عقاقير · Aqaqeer

Arabic RTL Android app for medication availability by pharmacy section, with offline viewing. No patients, prescribing, quantities, billing or inventory transactions.

Implemented: offline creation and administration, catalog editing, real Telegram publication/reconciliation, encrypted connection sharing, backup/restore, reminders, credential maintenance and Compose workflows. Current builds, selected hosted CI, demo startup/refresh and browsing checks are user-reported complete; independent evidence and remaining limits are recorded separately. See [current evidence](docs/validation.md) and [remaining work](docs/plan.md); production signing/update continuity and remaining functional/recovery checks remain open.

## Download and verify

Open [Releases](https://github.com/Hjjar-AI/Aqaqeer/releases) and choose a production release. Download the universal APK unless you specifically need ARM32 or ARM64, plus `SHA256SUMS.txt`. With all listed assets downloaded into one folder, run `sha256sum --check SHA256SUMS.txt`; a missing listed asset is an incomplete download, not a verified bundle. Compare the certificate using `apksigner verify --print-certs <downloaded.apk>` and the published fingerprint. Checksums verify bytes; the certificate identifies the signer.

See the [Arabic user guide](docs/user-guide-ar.md). Debug APKs have separate packages/signing and do not transfer an existing production pharmacy automatically.

## Build

```sh
# Choose standard, demo, or both (one wrapper invocation per flavor).
python3 tools/build_debug.py demo --offline
```

`demoDebug` is labeled **عقاقير — عرض تجريبي** and contains 40 [verified psychiatry/supportive-care sample records](docs/demo-data.md) with fictional stock/expiry in `com.aqaqeer.app.demo.debug`; `standardDebug` starts unconfigured in `com.aqaqeer.app.debug`. Workflow **06 → Run workflow → flavor** selects `standard`, `demo` or `both`; both uses independent jobs. Production package is `com.aqaqeer.app`. Demo source never enters standard builds; demo release is disabled. Debug artifacts are not production releases. See the [build, signing and release guide](docs/build.md), starting with its compact summary. Android Studio is optional. Workflow 10 publishes debug APKs; workflow 11 builds/signs/publishes the standard production release. The guide covers key creation, version codes, local commands, GitHub signing settings, downloads, updates and troubleshooting.

## Documentation

| Document | Purpose |
| --- | --- |
| [Requirements](docs/requirements.md) | Complete product rules and milestone acceptance |
| [Plan](docs/plan.md) | Implemented baseline, ordered packages and deferred validation |
| [Architecture](docs/architecture.md) | Implemented storage, import, activation, refresh and UI behavior |
| [Build, signing and release guide](docs/build.md) | Compact summary, full command-line/local/GitHub walkthrough, permanent keys, versioning and troubleshooting |
| [GitHub Actions guide](docs/github-actions-guide.md) | Comprehensive project-agnostic workflow tutorial, with Aqaqeer as a worked example |
| [CI controls](docs/ci-free-tier.md) | Manual checks, free-runner/storage limits, media downloads, cleanup and release-signing prerequisites |
| [Validation](docs/validation.md) | Executed checks, evidence and remaining limits |
| [دليل المستخدم](docs/user-guide-ar.md) | Current Arabic usage and recovery guidance |
| [Operations](docs/operations.md) | Telegram, backup/restore, credential maintenance and recovery |
| [Branding](docs/branding.md) | Selected artwork and placement |
| [File formats](docs/crypto/FORMATS.md), [reader contracts](docs/crypto/READERS.md) | Crypto, payload/caption rules, bounds and public vectors |

Telegram publishing requires coordinated admins and offers no distributed lock. Shared credentials do not cryptographically enforce reader rights. History is device-local; new installations receive no earlier history. About credits **محمد علي حجار** and opens an email composer for **hjjarmhmmdali@gmail.com** without sending automatically.

Project-owned code uses [Apache-2.0](LICENSE). Third-party terms and notices are retained under [licenses](licenses/THIRD-PARTY-NOTICES.txt). Security issues: [private reporting](SECURITY.md).
