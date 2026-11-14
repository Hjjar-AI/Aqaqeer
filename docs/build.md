<a id="building-aqaqeer"></a>

# Aqaqeer build, signing and release guide

## Compact summary

**Android Studio is optional.** You can create the signing key with JDK `keytool`, build with the project's Gradle wrapper, and sign with Android SDK command-line tools. GitHub Actions prepares the hosted build tools for you.

- **Create each signing identity once.** Production uses `local/signing/release.jks` / alias `aqaqeer`; hosted debug uses the separate `local/signing/debug.jks` / alias `aqaqeer-debug`. Keep protected backups and reuse these identities for later updates. Existing distributed apps require their original signer.
- **Copy the public certificate fingerprint.** `keytool -list -v` displays the value labeled `SHA256`; the signing helper checks that fingerprint against the finished APK. The private key and its passwords stay private.
- **Increase the version code for each release.** It is a positive integer, not another key. After code **40**, a coordinated next code can be **41**, producing app version **0.0.41**. Failed hosted reservations also consume their code.
- **Local builds reserve the next code automatically.** The wrapper keeps its locked counter; use the documented minimum-code floor when coordinating with higher codes used elsewhere. Build the unsigned release first, then run `python3 tools/sign_release.py` with signing settings.
- **GitHub workflow 11 asks for the code.** Enter a number greater than all previous production versions and `android-build-N` tags. Configure the permanent key and passwords in the `release-signing` environment, then choose **release**, **prerelease**, or **draft**; the default is draft.
- **Workflow 06 builds debug APKs; workflow 10 publishes debug APKs; workflow 11 builds and publishes the signed standard release APK.** Workflow 08 inspects release output and does not publish it. Workflow 10's default is prerelease.
- **Production and debug are separate Android apps.** Production uses `com.aqaqeer.app`; standard debug uses `com.aqaqeer.app.debug`; demo debug uses `com.aqaqeer.app.demo.debug`. Installing production does not automatically transfer the debug app's stored data.
- **No Android Studio, Play Console account or paid signing service is needed for ordinary APK distribution.** Local builds need JDK 21, the pinned Android SDK packages and Python; GitHub signing needs your permanent key. GitHub hosting still follows the repository's public-runner and storage controls.

The complete walkthrough below expands these points with commands, settings, outputs, update rules and troubleshooting. Examples are instructions for the reader. Updating this guide does not execute its commands. The completed signing-setup checkpoint, user-reported actions, assistant verification and remaining limits are recorded in validation. Current executed evidence and stops remain in [validation](validation.md) and [plan](plan.md).

## Reading routes

| What you want to do | Read next |
| --- | --- |
| Understand or prepare a computer without Android Studio | [Pinned toolchain](#pinned-toolchain), then [command-line setup](#set-up-a-computer-without-android-studio) |
| Build standard/demo debug APKs locally | [Setup and commands](#setup-and-commands) |
| Build or publish debug APKs on GitHub | [Manual workflows](#manual-quality-and-security-workflows), then [debug publication](#publish-debug-apks-on-github-releases) |
| Repeat the complete key/signing setup walkthrough | [Prepare both keys and backups](#prepare-signing-once), then [configure both GitHub environments](#configure-github-signing) |
| See what we completed and what remains unverified | [Current signing checkpoint](validation.md), then [remaining work](plan.md#repositoryrelease-follow-up--2026-11-14) |
| Make a signed production APK locally | [Build and sign locally](#build-and-sign-locally) |
| Publish a signed production APK with GitHub | [Configure GitHub signing](#configure-github-signing) |
| Release an update, recover from a failed run or solve a problem | [Updates and distribution](#updates-and-distribution), then [release troubleshooting](#release-troubleshooting) |

## Pinned toolchain

| Component | Version |
| --- | --- |
| Gradle wrapper | 9.4.1; official distribution SHA-256 pinned |
| Android Gradle plugin | 9.5.1 |
| Built-in Kotlin / Compose compiler | 2.4.21 |
| KSP / Hilt | 2.3.13 / 2.60.1 |
| Compose BOM / Room | 2026.09.00 / 2.8.5 |
| SQLCipher Community | 4.19.1 |
| Compile / target / minimum SDK | 37 / 37 / 26 |
| Build JDK / Java bytecode target | 21 / 17 |

Use the pinned [catalog](../gradle/libs.versions.toml) and AGP built-in Kotlin; do not add the deprecated Android Kotlin plugin. The root buildscript explicitly selects patched KGP 2.4.21 using [AGP’s supported override](https://developer.android.com/build/releases/agp-9-0-0-release-notes#runtime-dependency-on-kotlin-gradle-plugin), aligned with Compose/serialization. KSP 2.3.13 retains shared Room/Hilt processor declarations through `ksp.allow.all.target.configuration=true`. Reviewed buildscript constraints and the separate processor Guava constraint address the reported transitive advisories; current resolution/compilation remains unverified. Commands below require applicable authorization; [plan](plan.md) records stops, [validation](validation.md) executed evidence.

## Setup and commands

Local SDK: `/opt/android-shared/Sdk`; canonical Gradle home: `/opt/android-shared/aqaqeer/gradle-home`. The POSIX wrapper defaults to that existing cache unless an environment/CLI/system-property override is supplied; Windows uses normal Gradle defaults. Other machines set ignored `local.properties` to their SDK. Python 3.8+ is required by the wrapper version helper (`python3` on POSIX, `python` on Windows).

Diagnose cache ownership/group/ACL/chmod failures before repair; never create a replacement cache to bypass access. Preserve shared SDK/tools and unique downloads; do not merge active binary caches, change global PATH or add experimental AAPT2 overrides. Wrappers use HTTPS/checksums. Online resolution is normal; `--offline` fails on missing artifacts.

### Set up a computer without Android Studio

Use the existing shared JDK, SDK and Gradle cache on the supplied development machine. A machine that already builds this project does not need another SDK or cache. The following sequence is for a new computer or a proven missing prerequisite, not a repair by replacement.

1. Obtain a JDK 21 distribution for your operating system. A JRE alone does not provide all development tools. Point `JAVA_HOME` at that JDK and make its `bin` available in your current terminal session. `java -version` and `javac -version` should identify Java 21; `keytool -help` should open its command help. You do not install `keytool` separately.
2. Install Python 3.8 or newer. `python3 --version` is the POSIX command; Windows uses `python`. The wrapper calls the repository's Python helper before Gradle to reserve packaging versions.
3. Obtain Android SDK Command-line Tools from the official Android developer download page. On a new SDK, arrange the extracted tools so `cmdline-tools/latest/bin/sdkmanager` exists beneath the SDK root, alongside `cmdline-tools/latest/lib`. `ANDROID_HOME` names the SDK root, not the `bin` directory.
4. Install the exact SDK platform and build-tools packages shown below, accepting the SDK licenses yourself. Existing SDK installations should receive only missing packages after ownership and access checks. The SDK's `apksigner`, `zipalign`, `aapt` and `apkanalyzer` support the release helper; Android Studio's editor is not involved.
5. Obtain this repository with Git. Work from its root. Use its `gradlew` or `gradlew.bat`; do not install another Gradle version or invoke an unrelated global Gradle executable.

Example session settings on a new computer; replace both absolute paths with your real directories:

```bash
export JAVA_HOME=/absolute/path/to/jdk-21
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME=/absolute/path/to/android-sdk

"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_HOME" --licenses
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_HOME" \
  "platforms;android-37.0" "build-tools;36.0.0"
```

These exports affect this shell and its child commands. They do not require editing global system settings. The supplied machine instead uses `ANDROID_HOME=/opt/android-shared/Sdk`; retain its shared cache path. A brand-new POSIX machine without this project's `/opt/android-shared` setup can explicitly select its normal user Gradle home with `export GRADLE_USER_HOME="$HOME/.gradle"`. Do not use that change to bypass permissions on an existing shared cache.

Gradle can locate the SDK through `ANDROID_HOME` or an ignored root `local.properties` containing `sdk.dir=/absolute/path/to/android-sdk`. The signing helper specifically reads `ANDROID_HOME`, so set it even when Gradle already has `local.properties`. Preserve any existing machine configuration. Initial tool/dependency downloads require network access; `--offline` is appropriate only when the required artifacts already exist.

On Windows, use the same JDK and SDK versions, the SDK's `.bat` commands, the project's `gradlew.bat`, and `python` for Python commands. The signing environment examples later are Bash examples; use Git Bash or translate the environment assignments and password prompts for your shell. Windows execution remains unverified. A GitHub-only build needs no local Gradle/Android SDK; creating the key still needs access to JDK `keytool`, and the keystore can be created on another trusted computer.

### Reusing dependencies from other projects

[Gradle properties](../gradle.properties) enable task-output/configuration caching; no remote build cache exists. Use `--no-build-cache` / `--no-configuration-cache` to disable them. Authorized local verification uses the canonical home and `--offline --no-configuration-cache`; historical consolidation/offline-build results belong in validation. Cache reuse/performance, Windows and locking remain unverified.

Hosted CI's pinned `basic` cache restores only an exact key derived from Gradle build scripts, wrapper properties and version catalogs; it does not fall back to an older dependency graph ([pinned implementation](https://github.com/gradle/actions/blob/8da46b539cd8fe6ab27ca75128919a18d757604c/sources/src/cache-service-basic.ts)). It stores Gradle User Home `caches` and `wrapper`, including the downloaded distribution. Gradle/dependency updates can therefore cause another full download even when earlier cache entries exist. A Kotlin-source-only fix does not change this key. Failed builds can still save the basic cache; inspect restored/saved counts and the actual entry, not the generic summary sentence. Distributed builds obtain an android-build-N reservation before their fresh checkout-local wrapper counter runs; verification-only packaging remains separate. Cross-run installed-update acceptance is unverified.

The POSIX wrapper optionally selects readable `/opt/android-shared/dependency-seeds/mukhtabir-v1` as `GRADLE_RO_DEP_CACHE`. Explicit nonempty values win; `AQAQEER_SHARED_DEPENDENCIES=0` disables automatic selection. Point to the parent of `modules-2`, never an active writable cache. Missing optional seeds are skipped without copying/merging. Older seed metadata (`2.106` versus `2.107`) may still require repository resolution; no download-free guarantee follows. [Read-only cache contract](https://docs.gradle.org/current/userguide/dependency_caching.html#sub:shared-readonly-cache).

Prefer targeted compilation when requested:

```sh
./gradlew --gradle-user-home /opt/android-shared/aqaqeer/gradle-home \
  :app:compileStandardDebugKotlin :app:compileDemoDebugKotlin
```

Packaging, when requested, selects `standard`, `demo` or `both` explicitly:

```sh
# Pick the flavor to build; no Gradle runs until this command is invoked.
python3 tools/build_debug.py standard --offline
```

Use `demo` for the psychiatry sample app, or `both` for separate sequential wrapper invocations. `--offline` uses existing dependencies and disables configuration-cache reuse for the authorized local check. Omit it when online dependency resolution is authorized. On Windows use `python tools/build_debug.py demo`; the selector uses `gradlew.bat`. `--no-export` disables Desktop copies. `--print-command` prints the selected commands without starting Gradle or reserving a code.

The equivalent individual wrapper tasks are `:app:assembleStandardDebug` and `:app:assembleDemoDebug`. A local `both` request attempts each even if the other Gradle invocation fails and returns nonzero if either failed; each packaging invocation reserves its normal code. Existing shared SDK/cache paths are preserved by the wrapper. This source change does not authorize running any build.

Outputs are `app/build/outputs/apk/standard/debug/app-standard-debug.apk` and `app/build/outputs/apk/demo/debug/app-demo-debug.apk`. Packages are `com.aqaqeer.app.debug`, `com.aqaqeer.app.demo.debug` and production `com.aqaqeer.app`. Demo is labeled **عقاقير — عرض تجريبي** and supplies 40 [verified psychiatry/supportive-care sample records](demo-data.md), two fictional sections and fictional availability/expiry. It opens the local sample catalog; standard opens real setup until configured. Demo release is disabled. Debug APKs are not production releases.

Tests/lint require an explicit request: relevant tasks are `:app:testStandardDebugUnitTest`, `:app:testDemoDebugUnitTest`, flavor-specific `connected…DebugAndroidTest` and the production-only lint command below. Device/integration prerequisites need separate authorization; do not substitute a delivered preview for fresh instrumentation artifacts.

### Desktop APK copies

`assembleDebug` exports both APKs; flavor-specific assemble/install exports only that variant. Standard release has its own directory. Export depends on the artifact producer, preventing failed packaging from copying stale output. Prior copies and original build outputs remain.

- `Desktop/Aqaqeer/standardDebug/Aqaqeer-v0.0.<code>-app-standard-debug.apk`
- `Desktop/Aqaqeer/demoDebug/Aqaqeer-v0.0.<code>-app-demo-debug.apk`

Linux reads `XDG_DESKTOP_DIR` from `${XDG_CONFIG_HOME:-$HOME/.config}/user-dirs.dirs`, expands `$HOME`/`${HOME}`, then falls back to `Desktop`. Override the full export root with `-Paqaqeer.apkExportDir=/absolute/path` or `AQAQEER_APK_EXPORT_DIR` (property wins). Disable copying with `-Paqaqeer.exportApks=false`, including isolated/CI packaging. Compile/sync/lint/bundle alone do not export; release copying does not configure signing. Default Linux export has historical evidence; Windows/overrides remain unverified.

## Manual GitHub Actions build

After publication to the default branch, select **Actions → workflow name → Run workflow**. The [general tutorial](github-actions-guide.md) explains Actions; [CI controls](ci-free-tier.md) owns storage/billing, emulator capture, dependency review, cleanup and signing limits.

All fifteen workflows retain manual dispatch; cleanup alone also runs on its approved daily schedule. Jobs are public-repository-only, standard Ubuntu 24.04, with reviewed full action pins and non-cancelling workflow/ref concurrency. Shared [Android setup](../.github/actions/setup-android/action.yml) runs after checkout, selects JDK 21 and setup-gradle's basic GitHub cache provider. Default-branch runs can save caches; other branches remain read-only. No cache allowance is increased. Each debug APK flavor uses its wrapper assemble task through `tools/build_debug.py`, with Desktop exports disabled. Workflow 11 separately builds/signs the standard release APK. Distribution workflows 06/10 reserve shared version codes and sign in the separate debug-signing environment; workflow 11 uses the same registry with an explicit production code and its permanent release identity. Verification-only unsigned builds do not claim distribution/update continuity. Installed-update acceptance remains unverified. Workflow 10 separately offers manual GitHub Release publication of debug APKs; this does not change their Android build type or signing.

API 37 installs as **`platforms;android-37.0`**, plus build tools `36.0.0`. The [SDK reader](../.github/scripts/android_config.py) derives compile/minimum/target expectations from the current literal module declarations and rejects ambiguous/unsupported DSL; [CI toolchain configuration](../.github/android-toolchain.json) owns the shared build-tools version. No Gradle execution is needed to read these values. Shared setup verifies the wrapper's tracked executable mode. Execution status is recorded in [validation](validation.md#ci-quality-security-and-diagnostics--2026-11-13). [AGP compatibility](https://developer.android.com/build/releases/agp-9-2-0-release-notes).

### Manual quality and security workflows

Titles show the suggested review order: **01–05 Checks → 06 Build → 07–08 Verify → 09 Maintenance → 10–11 Publish**. Checks/build/verify remain independent manual workflows; the numbers do not create prerequisites or automatic chaining. Only cleanup also runs daily at 03:23 UTC (06:23 Asia/Damascus). GitHub-managed Dependabot updates are outside this numbering.

| Workflow | Purpose |
| --- | --- |
| [01 - Checks - Repository integrity](../.github/workflows/repository.yml) | Links/configuration/redacted credential patterns and CI policy |
| [02 - Checks - Dependency vulnerabilities](../.github/workflows/dependencies.yml) | Resolve runtime/build-plugin/KSP coordinates and review OSV with expiring exceptions |
| [03 - Checks - Tests and compilation](../.github/workflows/verification.yml) | JVM flavors, debug/instrumentation/release compilation and public JDK vectors |
| [04 - Checks - Android quality](../.github/workflows/quality.yml) | Workflow actionlint and both production lint flavors |
| [05 - Checks - Security analysis](../.github/workflows/security.yml) | Java/Kotlin, Python and Actions CodeQL; alerts under Security → Code scanning |
| [06 - Build - Debug APKs](../.github/workflows/android.yml) | Choose standard, demo or both; independent APK jobs/checksums/diagnostics |
| [07 - Verify - Emulator with visual evidence](../.github/workflows/emulator.yml) | Selected API 29/35 offline demo checks with PNG/MP4 |
| [08 - Verify - Release output](../.github/workflows/release.yml) | R8/resources/native/alignment/signature inspection; unsigned output is not distribution-ready |
| [09 - Maintenance - Clear workflow runs and logs](../.github/workflows/cleanup-evidence.yml) | Daily seven-day completed-run retention, plus manual preview/clear; includes run APK artifacts |
| [10 - Publish - Debug APKs to GitHub Releases](../.github/workflows/publish-debug.yml) | Build selected standard/demo/both debug APKs, then attach them to a regular release, prerelease or draft |
| [11 - Publish - Release APK to GitHub Releases](../.github/workflows/publish-release.yml) | Reserve a production code, build standard release, sign/inspect it, then publish a release/prerelease/draft |

Workflow 06's **Run workflow → flavor** choice defaults to `standard`; choose `demo` or `both` explicitly. Both expands to independent standard/demo jobs, `fail-fast=false`, serialized with `max-parallel=1`. Each job calls the same local selector for its single flavor, publishes only that APK and retains separately named flavor diagnostics. A demo compilation failure does not prevent a standard job from producing its APK. Other workflows keep their existing scope.

Artifacts upload automatically with one-day retention, subject to quota. Reports/summaries retain command outcome and public provenance; `pipefail` preserves failures through `tee`. Ordinary failures attempt diagnostics, cancellation/runner loss may prevent them. Checksums identify output bytes, not runtime/signing acceptance.

Actionlint v1.7.12 is checksum-pinned through one shared installer; workflows 01 and 04 remain independently runnable and retain plain-text logs/summaries. Workflow 01 installs checksum-pinned PyYAML before structural policy checks, including local composite actions. Production lint preserves approved severities/suppressions, reports warnings and fails errors; `--continue` permits independent flavor completion without ignoring failures. CodeQL v4.39.3 uses manual `java-kotlin` extraction for both production flavors, plus no-build Python/Actions analysis with separate categories/artifacts. Java/Kotlin and Python use extended security queries; job-scoped `security-events: write` remains minimal. A successful scan/upload does not mean zero alerts. Current hosted evidence and remaining checks are separated in the plan and validation.

[Dependabot](../.github/dependabot.yml) proposes weekly action-pin and Gradle updates, with at most five PRs per ecosystem and no auto-merge. Gradle proposals have a seven-day cooldown; AGP/Kotlin/KSP/Hilt toolchain changes are grouped for coordinated review, with AndroidX proposals grouped separately. Proposals do not authorize upgrades, builds or tests; pinned application dependencies are unchanged by this CI hardening.

### Publish debug APKs on GitHub Releases

After pushing the workflow to the default branch, open **Actions → 10 - Publish - Debug APKs to GitHub Releases → Run workflow**. Choose the source branch, `flavor=standard|demo|both` (default standard), and `publication=release|prerelease|draft` (default published prerelease). This is an explicit publishing workflow; ordinary workflow 06 continues to upload temporary Actions artifacts only.

Selected flavors build independently through the existing selector, with fail-fast off and one at a time. Only after every selected build and artifact upload succeeds does a separate publisher download this run's bundles, verify checksums and create a new `debug-<workflow run number>-<attempt>` tag at the exact source SHA. It attaches APKs, Gradle output metadata and SHA-256 lists, with source/run provenance in the release notes. It never overwrites an existing Release. A failed upload can leave a draft for inspection; no remote cleanup runs automatically. Re-running failed jobs reuses successful same-run bundles while their one-day retention lasts; re-running all jobs replaces only that run's intermediate bundles. Expired intermediates require a fresh full run.

Choose `release` for a regular published GitHub Release marked Latest; `prerelease` publishes with the prerelease label and does not set Latest. The default remains `prerelease`. Drafts are saved with the prerelease label and remain unpublished until manually published. All three choices contain the same debug APK build type. GitHub Release assets survive the existing workflow-run cleanup; intermediate artifacts/diagnostics keep one-day retention. See [GitHub CLI release creation](https://cli.github.com/manual/gh_release_create). The publisher alone receives `contents: write` and `actions: read`; build jobs retain read-only repository access. The built-in token suffices subject to repository rules; no personal token or new signing secret is required. See [token permissions](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax#permissions).

These remain **debug-signed, debuggable APKs**. The generated release tag is not the embedded app version: each fresh hosted checkout normally reserves code 1, and debug keys/counters are not persisted. A Release download does not establish cross-run update compatibility, production signing or runtime acceptance. Preserve installed pharmacy data; do not uninstall/clear it to bypass a signer conflict. The existing unsigned Android release-output verification workflow 08 is unchanged. Configuring workflow 10 does not itself execute a build or publish anything.

### Signed release APKs: local and GitHub

Workflow 11 produces the production **standard** package `com.aqaqeer.app`, with shrinking enabled and debugging disabled. Demo release remains disabled. Workflow 10 continues to publish debug packages. Android release signing requires a permanent private key, its alias/passwords, and the expected public certificate SHA-256 fingerprint. Reuse the original key for any existing production installation; the debug packages have different package names and cannot be updated into production in place. Keep an independent secure backup of the key/passwords; see [Android signing guidance](https://developer.android.com/studio/publish/app-signing).

#### Understand the files and names

| Item | Meaning | What you do with it |
| --- | --- | --- |
| Signing private key | The cryptographic identity used to sign app updates | Create once for a new production app; preserve it for later releases |
| Keystore | The private file containing the key and its certificate; this guide uses `release.jks` | Keep it in protected ignored storage and back it up securely |
| Alias | The name of the key entry inside the keystore; the example is `aqaqeer` | Use the same value in local settings and GitHub's alias secret |
| Store password / key password | Passwords used to unlock the keystore and key entry | Keep them private; PKCS12 commonly uses the same password for both |
| Certificate SHA-256 fingerprint | A public identifier for the signing certificate | Read it with `keytool -list -v`; configure it as the expected signing identity |
| APK SHA-256 checksum | A public identifier for one exact APK file | Compare it with the release's checksum list; it is not the certificate fingerprint |
| `versionCode` | Android's numeric update version | Choose a higher production code for the next release |
| `versionName` | The version shown to users | This project's current format is `0.0.<versionCode>` |
| Git tag | A repository reference to a source commit | Hosted reservation uses `android-build-N`; production Release publication uses `v0.0.N` |
| GitHub Release | A page with a tag, notes and downloadable files | Publish it normally, label it prerelease, or save it as a draft |

A file extension such as `.jks` does not determine the actual keystore format. The command below explicitly creates PKCS12 content; the filename remains `release.jks` to match this guide's examples. Existing JKS/PKCS12 stores can be used with their existing aliases and passwords. The release keystore is separate from Android Keystore keys used internally by the installed app to protect pharmacy storage. It is also separate from the pharmacy admin PIN and Telegram connection keys.

An Android **release build** and a GitHub **Release page** are separate choices. A GitHub Release page can contain a debug APK. Selecting `release` in workflow 10 changes the page label, while the attached APK remains a debug build. Workflow 11 builds the Android release variant before signing it. Draft versus prerelease versus release changes visibility/labeling; it does not create or replace a signing key.

#### Prepare signing once

This is the step-by-step walkthrough used for the first signing setup. The user confirmed that no APKs had yet been distributed. If you are repeating it, first check for existing keys: preserve them and skip creation. An already distributed app must keep its original private signing key. Creating a new key does not recover the old identity or make existing installations update-compatible.

**1. Open the project in a Bash terminal.** The examples use Linux/JDK `keytool`; Android Studio is optional. Run the commands from the repository root, not the shared key store.

```bash
cd "/mnt/Common/IT Projects/Aqaqeer"
umask 077
mkdir -p local/signing
```

`umask 077` requests restrictive creation permissions for this terminal and its child commands: normally files are owner-readable/writable (`600`) and directories owner-accessible (`700`). It does not encrypt files, change existing permissions or persist across new terminals. Inherited ACLs or tool-specific creation modes can affect the result; explicitly protect the finished files below.

**2. Create the production key once, only if `local/signing/release.jks` does not exist.**

```bash
keytool -genkeypair -v \
  -keystore local/signing/release.jks \
  -storetype PKCS12 \
  -alias aqaqeer \
  -keyalg RSA -keysize 3072 \
  -validity 10000 \
  -dname "CN=Aqaqeer Release"
```

Choose and confirm a password at the hidden terminal prompts; typing shows no characters. Save it in a password manager, never in this document or a command argument. This creates a PKCS12 keystore despite the `.jks` filename. For these PKCS12 commands, use the same password for the store and key password fields later. `3072` is the RSA key size and `10000` is certificate validity in days. The certificate subject is public metadata, not a pharmacy login.

**3. Create the separate debug key once, only if `local/signing/debug.jks` does not exist.**

```bash
keytool -genkeypair -v \
  -keystore local/signing/debug.jks \
  -storetype PKCS12 \
  -alias aqaqeer-debug \
  -keyalg RSA -keysize 3072 \
  -validity 10000 \
  -dname "CN=Aqaqeer Debug"
```

Choose a different password and save it separately. This identity is permanent for future hosted debug updates; it is not the disposable runner-generated debug key and must not be substituted for the production identity.

**4. Protect the original files and back them up.**

```bash
chmod 700 local/signing
chmod 600 local/signing/release.jks local/signing/debug.jks
```

Use a private store outside the checkout, organized as `keys/<Project>/release.jks` and `keys/<Project>/debug.jks`, with a separate subfolder per project. Retain the original files under ignored `local/signing/`. Copy without overwriting an existing different key; compare copies byte-for-byte without printing or hashing private key contents. Protect store/project directories with mode `700` and key files with mode `600`. Keep aliases/passwords in the password manager and public fingerprints with the signing configuration. Record actual private backup locations outside the repository.

The shared store used in this setup is on the same drive as the project. It is useful organization and a second copy; keep protected backups on separate storage as well. Current backup completion is recorded in validation. `.gitignore` protects against accidental tracking; it does not encrypt files or provide recovery.

**5. Obtain each public certificate SHA-256 fingerprint.**

```bash
keytool -list -v \
  -keystore local/signing/release.jks \
  -alias aqaqeer

keytool -list -v \
  -keystore local/signing/debug.jks \
  -alias aqaqeer-debug
```

Enter the corresponding password locally for each command. Under **Certificate fingerprints**, copy **SHA256**, not SHA1 or an APK checksum. Each key has its own fingerprint; do not swap them. Fingerprints are public; private keys and passwords remain private. The helpers accept colon-separated or plain hexadecimal SHA-256 and normalize case/colons. Record the actual values in GitHub environment variables, not in this walkthrough.

If `keytool` is absent, check the existing JDK tools/PATH first. If an alias already exists, preserve that keystore and inspect the existing alias; do not delete or regenerate it to bypass the message.

#### Choose and coordinate the version code

A version code is a whole number between 1 and 2099999999 for this project. It does not come from Google, a certificate authority or a purchase. You choose the next coordinated production number, and the wrapper records it when packaging starts. The current app version string follows that integer: code `41` produces `0.0.41`.

Before selecting a number, compare the latest distributed production code, the local packaging counter and all hosted `android-build-N` reservations. The local counter is `local/versioning/last-code.txt`; it includes this checkout's debug packaging as well. Read it without editing it. Hosted reservations are visible in GitHub's tag list. If the highest relevant number is 40, use 41 or a higher unused number. If a failed hosted run already reserved 41, use at least 42. Gaps are valid. Do not lower a counter or delete a tag to fill a gap.

There is no universal first-release number. Code 1 is possible only if there is no higher relevant version or reservation to preserve. When moving existing pharmacy data between packages, also respect any retained minimum-app-version requirement; rebuilding at a lower code does not bypass it. A GitHub workflow's run number is not automatically the embedded APK version. Debug tags such as `debug-7-1` are not production version codes.

The wrapper advances its local counter while holding its lock before Gradle starts. A failed local packaging command consumes that code. A floor can raise the next reservation, but cannot force it below an existing counter. For example, with a local counter of 50 and a floor of 41, the actual next code is 51. Read the build output/metadata for the actual number instead of assuming the floor was used verbatim.

Workflow 11 creates its durable hosted reservation before starting the unsigned build. It rejects an input at or below the highest existing hosted reservation. It cannot discover a local release that you have never recorded/shared with GitHub. Coordinate local and hosted distributions yourself and do not run them concurrently. Each new code on either side must be chosen with the other side's history in mind; the workflow does not merge local counters automatically.

#### Build and sign locally

Use the pinned JDK/SDK/Python setup above. Select a production code greater than every locally distributed production version and hosted `android-build-N` reservation. Do not distribute releases from local and hosted workflows concurrently. The optional `AQAQEER_MIN_BUILD_CODE` is a floor for the wrapper's next normal reservation, not a forced version override: it chooses `max(previous counter + 1, floor)`, keeps the lock/counter, and never decreases or resets it. Example `123` below must be replaced with your chosen code; omit the variable for ordinary next-local-code packaging when the local counter already includes all distributed production codes.

```bash
AQAQEER_MIN_BUILD_CODE=123 ./gradlew --no-daemon --console=plain \
  --no-configuration-cache -Paqaqeer.exportApks=false -Paqaqeer.releaseApkSet=distribution :app:assembleStandardRelease
```

A successful build creates the unsigned APK, `output-metadata.json` and fresh R8/resource evidence. Then configure signing in the same Bash session, reading passwords without putting them in command history:

The unsigned build is normally under `app/build/outputs/apk/standard/release/`; its exact APK filename is listed in `output-metadata.json`. It is not the installable signed result. Building only `:app:assembleStandardRelease` does not ask for the keystore or sign the APK in this repository. The signing helper needs the APK metadata and fresh mapping/resource files from the successful build. Do not mix a copied old APK with another build's metadata/mappings.

```bash
export ANDROID_HOME=/opt/android-shared/Sdk
export AQAQEER_RELEASE_KEYSTORE="$PWD/local/signing/release.jks"
export AQAQEER_RELEASE_KEY_ALIAS=aqaqeer
export AQAQEER_RELEASE_CERT_SHA256=YOUR_PUBLIC_CERTIFICATE_SHA256
read -r -s -p 'Keystore password: ' AQAQEER_RELEASE_STORE_PASSWORD
export AQAQEER_RELEASE_STORE_PASSWORD
read -r -s -p 'Key password: ' AQAQEER_RELEASE_KEY_PASSWORD
export AQAQEER_RELEASE_KEY_PASSWORD
python3 tools/sign_release.py
unset AQAQEER_RELEASE_STORE_PASSWORD AQAQEER_RELEASE_KEY_PASSWORD
```

Replace `YOUR_PUBLIC_CERTIFICATE_SHA256` with the fingerprint you recorded. That placeholder is deliberately invalid and will not sign successfully. The private local keystore path is supplied through `AQAQEER_RELEASE_KEYSTORE`; do not also set `AQAQEER_RELEASE_KEYSTORE_BASE64` in a local session. The helper accepts exactly one source. Re-enter passwords after opening a new terminal; the guide does not save them in source, command history or a generated settings file.

Use your installed SDK path if it differs. For a PKCS12 keystore, the key password is normally the store password. The [signing helper](../tools/sign_release.py) checks alignment, signs a temporary copy using password environment references, then runs the existing [release inspector](../.github/scripts/check-release.py) with the expected certificate and actual version. It rejects a debug/wrong signer, wrong package/version, debuggable manifest, missing shrinker/license/resource evidence and native/alignment failures. It preserves the unsigned original and existing output directories. Successful signed output goes to `local/release/v0.0.<code>/` with `release-info.json` and `SHA256SUMS.txt`; choose a different parent via `--output-directory` to retain another copy. `ci-output/release/` contains public inspection evidence. This performs artifact inspection, not app runtime/update installation acceptance. [apksigner reference](https://developer.android.com/tools/apksigner).

For a successful code-41 signing operation, the retained distribution directory contains:

```text
local/release/v0.0.41/
  Aqaqeer-v0.0.41-release.apk
  release-info.json
  SHA256SUMS.txt
```

Check the receipt's `versionCode`, `versionName`, `certificateSha256` and APK hash. Only the public receipt and package files are intended for sharing. `SHA256SUMS.txt` covers the signed APK and receipt, not the keystore. The helper does not overwrite an existing version directory. If signing failed before producing a retained directory, fix the signing configuration and sign the same successful unsigned build again; that does not package or reserve another code. If you run Gradle packaging again, its normal counter advances.

You can verify the public output's checksums from inside its directory:

```bash
cd local/release/v0.0.41
sha256sum --check SHA256SUMS.txt
```

Replace 41 with the actual code and return to the project root before another project command. These checks confirm matching bytes, not successful installation or clinical correctness. The signing helper's signature/certificate check is a separate step already performed before these files were retained.

Share the signed APK directly, or attach the signed APK, release-info and checksum files to a GitHub Release at its actual source commit. A local APK does not require GitHub, Telegram credentials, Play Console or a paid signing service.

#### Configure GitHub signing

The workflow signing references already exist; creating keys does not automatically upload them. No private key/password belongs in Gradle, YAML, tracked `.properties`, chat or issue text. Use the original production key for local and hosted releases. For local signing, `AQAQEER_RELEASE_KEYSTORE` selects its protected file; the shared backup location requires no source-code path change.

**6. Confirm the repository and default branch.** This setup targets [Hjjar-AI/Aqaqeer](https://github.com/Hjjar-AI/Aqaqeer), whose verified default branch is **`Final`**. Both signing environments now allow `Final`. During setup the environment rules initially allowed `main`; the assistant found that GitHub had no `main` branch, and changed both rules to `Final` at the user's request. For another repository or a later rename, use its actual default branch. The workflows read `github.event.repository.default_branch` automatically, but environment branch rules must be kept aligned separately.

**7. Create the two environments under [Settings → Environments](https://github.com/Hjjar-AI/Aqaqeer/settings/environments).**

- Name them exactly **`release-signing`** and **`debug-signing`**.
- In each, choose **Deployment branches and tags → Selected branches and tags** and add a **Branch** rule for **`Final`**.
- Leave required reviewers unset, as chosen for this project. Keep any other existing safeguards unless intentionally changing them.

**8. Add the fingerprint as an Environment variable in each environment.** Use **Add variable**, not **Add secret**, for these public values.

| Environment | Variable | Value |
| --- | --- | --- |
| `release-signing` | `AQAQEER_RELEASE_CERT_SHA256` | SHA256 from `release.jks`, alias `aqaqeer` |
| `debug-signing` | `AQAQEER_DEBUG_CERT_SHA256` | SHA256 from `debug.jks`, alias `aqaqeer-debug` |

**9. Add three Environment secrets in each environment.** Use **Add environment secret**, not **Add variable**. Enter passwords directly in GitHub's secret UI; their values are not documented here.

| Environment | Secret | Value |
| --- | --- | --- |
| `release-signing` | `AQAQEER_RELEASE_KEY_ALIAS` | `aqaqeer` |
| `release-signing` | `AQAQEER_RELEASE_STORE_PASSWORD` | Production keystore password |
| `release-signing` | `AQAQEER_RELEASE_KEY_PASSWORD` | Same production password for this PKCS12 setup |
| `debug-signing` | `AQAQEER_DEBUG_KEY_ALIAS` | `aqaqeer-debug` |
| `debug-signing` | `AQAQEER_DEBUG_STORE_PASSWORD` | Debug keystore password |
| `debug-signing` | `AQAQEER_DEBUG_KEY_PASSWORD` | Same debug password for this PKCS12 setup |

**10. Upload each keystore directly as the fourth Environment secret.** These commands perform remote secret writes when explicitly run. Use them only for the intended existing keys/environments after authenticating GitHub CLI to the intended account. The GNU/Linux Base64 pipeline avoids displaying the encoded key or creating another private file. Base64 is encoding, not encryption; GitHub stores the result as an encrypted secret.

```bash
cd "/mnt/Common/IT Projects/Aqaqeer"
set -o pipefail

test -s local/signing/release.jks &&
  base64 -w0 local/signing/release.jks |
  gh secret set AQAQEER_RELEASE_KEYSTORE_BASE64 \
    --repo Hjjar-AI/Aqaqeer --env release-signing

test -s local/signing/debug.jks &&
  base64 -w0 local/signing/debug.jks |
  gh secret set AQAQEER_DEBUG_KEYSTORE_BASE64 \
    --repo Hjjar-AI/Aqaqeer --env debug-signing
```

Wait for success on each command. If authentication or upload fails, resolve it before proceeding. Do not run `base64` alone in a logged/shared terminal. A different OS needs its equivalent no-wrap encoding command. No personal access token is required by the workflows themselves. Keep repository Actions permissions compatible with pinned actions and scoped token writes; tag rules must permit creation of `android-build-N` / `v0.0.N`.

**11. Verify configuration presence without exposing values.** Each environment should have **four secrets**, **one certificate variable** and the **`Final` branch rule**. The assistant verified those names/rules through GitHub's API after the user entered the values. Secret values cannot be retrieved by that check; it does not prove that uploaded keys, aliases, passwords or fingerprints agree. That boundary is established by an authorized signing run and artifact inspection.

If VS Code still shows `Value 'release-signing' is not valid` / `Value 'debug-signing' is not valid` or unknown secret/variable context warnings after the configuration is confirmed, run **Ctrl+Shift+P → Developer: Reload Window**, then reopen the workflow. **GitHub Actions: Refresh** is also available in the installed extension. If warnings persist, check the extension's signed-in account and access to the same repository. Do not replace valid secret references with literal credentials. In this session the user confirmed the diagnostics disappeared after refreshing/reloading.

**12. Start with the manual repository check.** Open [01 - Checks - Repository integrity](https://github.com/Hjjar-AI/Aqaqeer/actions/workflows/repository.yml), click **Run workflow**, select **`Final`**, and wait for its result. This checks source/configuration/workflow integrity without building or signing an APK. It has been recommended, but completion of this new run has not been reported. After a pass, the next planned step is a manually requested workflow 06 standard debug build to exercise the new debug signing setup. Setup/documentation alone does not authorize dispatch, release publication or device installation.

For a later explicitly requested production publication, push the workflow/helper changes to the default branch, then open **Actions → 11 - Publish - Release APK to GitHub Releases → Run workflow**:

1. Select the **default branch**; all other branches are refused before mutation.
2. Enter `version_code`: a positive whole number below 2100000000, greater than all previous production codes, including local releases and failed/unpublished hosted reservations. The UI intentionally has no guessed default.
3. Choose `publication=release|prerelease|draft`; default **draft**. Release marks Latest, prerelease publishes with its label, draft remains unpublished.

The three jobs appear in order as **reserve**, **build** and **publish**. The build job packages unsigned output without receiving the signing secrets. The publish job downloads that exact run's unsigned output and shrinker evidence, signs it, checks the expected public certificate and version, and only then invokes GitHub Release creation. The signing job may wait at your configured environment approval gate. Passing reservation alone does not mean a build or publication succeeded.

After a successful published run, open the repository's **Releases** page and find `v0.0.<code>`. Under its downloadable assets, use `Aqaqeer-v0.0.<code>-universal-release.apk`. GitHub's automatically generated source-code ZIP/tar archives are not Android apps. A draft is visible only to users with the relevant repository access until published. You can review the attached files/notes and publish that same draft from the Releases page; this action does not rebuild or re-sign the APK.

The reservation job records `android-build-<code>` at the exact source SHA before packaging, rejecting codes at or below any existing reservation. Preserve these tags after failures, draft deletion and workflow cleanup. Repository rules must allow the workflow token to create these tags and the final `v0.0.<code>` tag/Release. A failed reservation/API response is not blindly retried. If reservation or packaging fails/interruption occurs, start a new dispatch with a higher code; re-running packaging is refused. A failed **signing/publishing job only** can be rerun against the same successful unsigned bundle while its one-day retention lasts. Expired bundles require a new full dispatch/code. Local releases are not registered automatically: coordinate them and choose a higher hosted input afterward.

The build job has contents-read and no signing secrets. Only the final job uses the `release-signing` environment; the helper decodes the key into a private temporary directory, removes its temporary material on normal completion/failure, and never uploads it or puts it in Gradle caches. Ordinary cancellation/runner loss can interrupt cleanup; GitHub-hosted runners are ephemeral. Reservation and publication alone receive contents-write; the publisher also needs Actions-read to fetch the exact run's artifact. Missing/invalid secrets or a certificate mismatch fail before publication. No unsigned APK is attached to a Release. Existing Release assets are not overwritten; an interrupted asset upload may leave a draft for explicit review.

Published assets are the universal/ARM32/ARM64 signed APKs, public inspection receipts, SHA-256 list and matching compressed R8 mapping. Unsigned builds and diagnostics remain one-day Actions artifacts. Release assets and version-reservation tags survive workflow cleanup. First local/hosted build/signing/transfer/publication/update checks remain unexecuted; configuration does not resume stopped local tests/lint or physical/live campaigns.

#### Updates and distribution

For the first production distribution, retain the signed APK, its public receipt/checksums, the exact source revision, the actual production version code, and the permanent key backup. For a hosted run, source/run identities are recorded in the release receipt/notes. For a local run, record the actual source commit and any uncommitted source changes separately; the local helper does not populate GitHub run provenance. Retain the matching published R8 mapping with the release for future crash deobfuscation.

For each later update, use the same production package and signing identity, choose a higher coordinated code, build/sign it, and share the new signed APK. Do not regenerate the key for an update. Do not try to solve a version conflict by resetting the local counter or removing reservation tags. Switching between local and hosted builds is possible when both use the same permanent key, the same expected certificate and a coordinated version sequence. It is not automatic synchronization between computers.

The production package installs separately from standard/demo debug because their package names differ. It does not inherit the debug app's database, keys or authorization. If you need to move a real pharmacy from a debug installation, use the documented encrypted backup/restore or same-pharmacy connection setup as appropriate; preserve the source installation and its data until the move is verified. Restored admin authorization is not transferred. Demo data is fictional and does not become a real production pharmacy. See [operations](operations.md) and [requirements](requirements.md#11-backup-and-restore) for restore/live-PIN and publication rules.

Recipients can receive the signed APK through file sharing or GitHub Release assets. On a phone, opening the APK may require allowing installation from the specific browser/file-manager source under Android settings. Review the app identity/version and use the normal install/update flow. A signing mismatch is not repaired by uninstalling a pharmacy app or clearing its storage. Keep the existing app/data and obtain an APK with the correct package, key and version.

Publishing is distinct from installation. A successful signed APK inspection checks artifact properties; a successful GitHub job adds hosting; neither proves a phone installation, storage preservation, update compatibility, app behavior or human acceptance. Do not describe those checks as passed until they have actually been performed with the appropriate authorization and retained evidence. A newly documented release route does not resume stopped local tests/lint, device work or live Telegram campaigns.

#### Release troubleshooting

Locate the first failed step and retain its public diagnostics. For signing, the helper deliberately suppresses raw subprocess output that could include private paths/aliases. Failures are reported generically; `ci-output/release/` contains public artifact-inspection evidence when inspection was reached. It may be absent or incomplete when failure happened earlier. Never add password/key printing to diagnose a failure.

| Symptom or question | Meaning and next action |
| --- | --- |
| Do I need Android Studio? | No. Use JDK `keytool`, the pinned SDK command-line tools, Python and the project wrapper. Studio is an optional interface. |
| `keytool` is not found | Check that you installed a JDK and selected its `bin` in the current session. Do not install another Android SDK to fix a missing JDK tool. |
| SDK location or `ANDROID_HOME` is missing | Set the SDK root correctly. The signing helper needs `ANDROID_HOME` even if Gradle has `local.properties`. Preserve an existing shared SDK. |
| A pinned SDK package is missing | Install only that package after verifying it is actually absent and the SDK is accessible. Do not replace an SDK because a directory permission is wrong. |
| Gradle cache is inaccessible | Check ownership, groups, ACLs and the affected path. Do not create a fresh cache or use another Gradle home to bypass the failure. |
| `--offline` cannot resolve dependencies | Required artifacts are missing. Use normal resolution when authorized; offline mode cannot create them. |
| Alias already exists during key generation | Inspect and preserve the existing key entry. Repeated releases do not need repeated key generation. |
| Environment allows `main`, workflow default branch is `Final` | Set the environment's selected **Branch** rule to the actual default branch; changing the rule does not rename Git branches. |
| VS Code still flags configured environments/secrets | Verify names/access, reload the window and refresh GitHub Actions; editor diagnostics can retain stale metadata. |
| Build dependency discovery creates `generate…ComposePreviewRunfiles` and rejects its unit-test prerequisites | AGP 9.5 requires both included Android unit-test resources and an enabled unit-test host component for the active variant. The module enables resources globally and explicitly enables the standard-release `hostTests[HostTestBuilder.UNIT_TEST_TYPE]` builder; demo release remains disabled. Code 3 cleared the earlier debug-resource error but exposed the disabled release component. The host-component correction passed standard-debug compilation/packaging and signing in workflow 06 code 4; production/demo builds remain separate checks. |
| Signing settings are missing | Supply the local keystore path or the hosted Base64 secret, plus alias/passwords and expected certificate. Use exactly one key source. |
| Password/alias error | Check the saved key alias and passwords. For PKCS12, use the store password for the key password unless your existing store requires otherwise. Do not substitute a new key. |
| Certificate mismatch or debug signer rejected | Compare the public fingerprint from the original production key/APK. Use that original key and fingerprint; do not change the expected value just to accept a different identity. |
| APK metadata or R8 evidence is missing | Complete the standard release build and use its matching APK, metadata and mapping/resource evidence. A debug APK or a copied file alone is insufficient. |
| Output version directory already exists | Preserve it. To sign the same successful build into another retained location, choose a new `--output-directory` parent; do not overwrite the old evidence. |
| Workflow 11 is missing from Actions | Push its workflow and helpers to the default branch. A local file alone does not register a manual workflow on GitHub. |
| All production jobs are skipped | Confirm the repository is public and the selected ref is its default branch. Those guards are intentional. |
| Publish job is waiting | Check the `release-signing` environment's reviewer/deployment settings. An owner-configured approval can pause signing. |
| Secret is present but signing says it is missing | Check exact names, environment placement, environment access and that the fingerprint is a variable. Do not print the secret to confirm it. |
| Tag reservation fails | Check the requested integer, existing reservations, token permissions, rulesets and default branch. Start a new dispatch with a higher code after a consumed/uncertain reservation; do not delete old reservations. |
| Build failed or runner was interrupted | Preserve the reservation tag and diagnostics. A new packaging attempt requires a new dispatch/code. The one-day bundle may not exist if upload was never reached. |
| Only signing/publishing failed | Fix that stage and rerun the failed job while the successful same-run unsigned bundle remains available. Do not rebuild just to retry signing. |
| Intermediate artifact expired | Start a new full workflow with a higher code. Releases retain published signed outputs and mappings separately, but cannot recreate expired unsigned build bundles. |
| Release creation says the tag/Release already exists | Inspect the existing page/assets and the prior run. The workflow does not overwrite Releases or automatically delete a partial draft. Reconcile the existing output before another publication attempt. |
| I selected draft and cannot find a public download | Open repository Releases with suitable access. Review and publish the draft when ready; selecting draft intentionally keeps it unpublished. |
| Workflow cleanup removed the run | Release assets and `android-build-N` tags are separate and preserved. Attached Actions artifacts/logs are subject to the configured retention/deletion. |
| APK cannot update an installed app | Compare package, signing certificate and version code. Production/debug have different packages. Preserve app data and get the correct signed build. |
| The private key or its password was lost | Recover the original secure backup. An APK/public fingerprint cannot recover the private key. This repository has no signing-key recovery/rotation bypass; a new unrelated key does not preserve ordinary update continuity. |

For repeated use, keep a short release record with source, code, package, public certificate fingerprint, APK checksum, publication link and actual validation status. Keep private keys and passwords in separate protected storage. This record prevents confusing a hosted tag, app version, certificate fingerprint and APK checksum during the next release.

### Tests and compilation without a device

Four independent `fail-fast: false` entries select `testStandardDebugUnitTest`, `testDemoDebugUnitTest`, both debug/instrumentation `compile…Sources` sets, and `compileStandardReleaseSources`; all use explicit `:app:` tasks. A fifth job runs the existing [JDK vector verifier](../tools/VerifyCryptoVectors.java) through a standard-library exporter. No connected tasks, packaging/version reservation or credentials occur in this workflow. JVM XML totals distinguish failures/errors/skips, reject missing/malformed/empty/all-skipped results, and report executed counts. Partial skips remain visible without an arbitrary failure percentage; compile success is not runtime acceptance. Exact task lists live in the linked workflow.

### Hosted emulator screenshots and recordings

Run the emulator workflow; download `emulator-evidence-api29-*` / `emulator-evidence-api35-*` within one day. It runs six explicit invocations per fresh API, one emulator at a time. There is no live browser screen. [CI guidance](ci-free-tier.md#emulator-presentation-and-assertion-evidence) specifies selected checks, assertion/fallback capture, 45-second recordings, timeouts and limits. Local/physical devices and live Telegram campaigns are excluded.

### Clear older CI evidence manually

Cleanup runs daily at 03:23 UTC (06:23 Asia/Damascus) after its workflow reaches the default branch. It deletes completed runs older than seven days from all workflows, including Dependabot, with their attempts, logs, summaries and attached artifacts. Scheduled runs can be delayed by GitHub; public-repository schedules can be disabled after 60 days without repository activity ([event behavior](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#schedule)).

The manual clear button is **Actions → 09 - Maintenance - Clear workflow runs and logs → Run workflow**, on the default branch. Choose `older_than_days` (0–365, default 7), `scope=all-workflows|emulator` and `dry_run=true` to preview. Set `dry_run=false` to delete; **days=0 clears all completed runs in scope**. A zero-day input is preserved as the string `0`, rather than replaced through a false-value fallback.

```sh
gh workflow run cleanup-evidence.yml -f older_than_days=0 -f scope=all-workflows -f dry_run=false
```

This is a remote deletion command for the reader, not an execution record. Per the user's explicit choice, run deletion includes APK artifacts and check history. Download selected APKs or attach them to a GitHub Release first if they must survive. Cleanup never calls release/repository/cache deletion endpoints. Repository identity, completed status, age, attempt and updated time are rechecked before deletion, pagination completes before mutations, and absence is confirmed afterward with up to six GETs and 31 seconds of bounded backoff for delayed GitHub deletion visibility. Confirmation never repeats DELETE. A still-unconfirmed run is reported accurately while other eligible runs continue; the final job fails if any confirmations remain unresolved. Active/current runs are excluded; API/confirmation failures remain failures.

### Read remote logs with GitHub CLI

Machine-local read authentication was previously verified but may expire; it is not embedded in workflows. From this checkout:

```sh
gh run list --workflow android.yml --limit 5
gh run view RUN_ID --log-failed
gh run view RUN_ID --log
gh run download RUN_ID --dir ./ci-output/downloads
```

Use the chosen workflow filename and a real run ID. Logs can remain available without artifact upload. Inspect raw output before sharing; ignored `ci-output/` holds public diagnostics only. No background monitoring exists.

## Numbering and installation continuity

Each full wrapper `assemble*`, `bundle*`, `install*`, `build`, `buildNeeded` or `buildDependents` invocation atomically reserves a locked `local/versioning/last-code.txt` code before Gradle/configuration-cache lookup. The counter is shared across local invocations; independently selected flavors reserve separately, and demo adds `-demo`. Failed packaging consumes the code, even before Gradle starts. Clean does not reset it; compile/test/lint/sync/help/version/dry-run do not advance it. A fresh checkout starts at 1.

Use full task names; abbreviations are unsupported. Preserve the [Python helper](../gradle/build_version.py) integration when regenerating wrappers. It supplies reserved `aqaqeer.buildCode`; never override/reset it manually. For coordinated production numbering, `AQAQEER_MIN_BUILD_CODE` may raise the next wrapper reservation without replacing its lock/counter; non-packaging commands ignore the floor. Run in this checkout or select it with `-p`. Direct Gradle/IDE packaging without reservation is rejected; sync/non-packaging uses a read-only provider. New versions normally invalidate packaging configuration-cache reuse; task-output caching is separate. Cross-machine release coordination, Windows and concurrent locking remain unverified.

Updates require the original package and signer. No release key is supplied. Never uninstall/clear existing data or substitute a fresh identity to bypass a signer conflict.

## Isolated verification tools

Ignored verification init (local-only: `../local/verification/tools/verification.init.gradle`) supplies `.verification.creation.debug`; preview init (local-only: `../local/verification/tools/preview.init.gradle`) supplies `.preview.debug` and resolves its labels (local-only: `../local/verification/tools/preview-res/values/strings.xml`) relative to itself. Supply exactly one, from repository root. An authorized standard preview uses:

```sh
./gradlew --gradle-user-home /opt/android-shared/aqaqeer/gradle-home \
  -I local/verification/tools/preview.init.gradle --offline --no-configuration-cache \
  -Paqaqeer.exportApks=false :app:assembleStandardDebug
```

This reserves a normal code. Verify artifact/signer and the physical serial before `adb -s <serial> install -r <apk>`; stop on identity conflict. Verification `package…Debug`/`package…DebugAndroidTest` preserve the counter. Fresh app/test IDs, random storage/Keystore namespaces and owned cleanup are mandatory when checks are authorized. The email check intercepts its intent without sending. [Operations](operations.md#verification-tools) owns private integration-runner procedures.

### Production-only lint

When lint is authorized while tests remain stopped:

```sh
./gradlew --gradle-user-home /opt/android-shared/aqaqeer/gradle-home \
  -I .github/production-lint.init.gradle --offline --no-configuration-cache \
  --console=plain --warning-mode fail :app:lintStandardDebug :app:lintDemoDebug
```

The [helper](../.github/production-lint.init.gradle) disables both test-source analysis flags and enables HTML/XML. Retain fresh reports/logs and reconcile selected tasks; lint does not reserve a code. Version notices remain informational. The sole launcher shape exception is scoped to `src/main/res/drawable-nodpi/aqaqeer_launcher.png`. Arabic language splitting is disabled; release enables minification/resource shrinking.

## Fresh initial Room export

Current production declares database version 1 without historical upgrade registrations. [Schema status](../app/schemas/README.md) links its generated/reviewed export. Future exports require separately authorized KSP compilation and review of entities, publication-time column, price/currency exclusion and identity hash. Do not handwrite generated hashes, restore old exports or treat this instruction as build/test/schema-change authorization.

## Repository and release maintenance

### Shared numbering and signing

Hosted distribution workflows 06, 10 and 11 build only the default branch and share durable `android-build-N` reservations. Debug dispatches select the next number automatically; production keeps its explicit code input, which must exceed every reservation, including debug/failed attempts. Reservation jobs share `aqaqeer-version-reservations`; numbers are never recovered by deleting tags. Start a new dispatch after a consumed reservation, rather than rerunning packaging. GitHub may replace a pending concurrency-group job; a canceled job is not a build pass.

The POSIX/Windows wrappers run `gradle/build_version.py` before Gradle; callers must not supply `aqaqeer.buildCode`. The local locked counter remains independent while offline. `python3 tools/next_release_code.py --distributed-maximum N` reads local state and hosted tags without reserving anything. Include any distributed code unknown to the registry. Failed API access produces no suggestion. Coordinate local packaging with hosted dispatches; the helper is not a distributed lock.

Hosted debug builds are built without permanent signing secrets, then signed in a separate `debug-signing` environment. Configure environment secrets `AQAQEER_DEBUG_KEYSTORE_BASE64`, `AQAQEER_DEBUG_KEY_ALIAS`, `AQAQEER_DEBUG_STORE_PASSWORD`, `AQAQEER_DEBUG_KEY_PASSWORD` and the public environment variable `AQAQEER_DEBUG_CERT_SHA256`. Restrict that environment to the default branch. Keep this permanent debug identity separate from production. The signing job decodes the key only in temporary storage and verifies the packaged identity/code/certificate before uploading a distribution bundle. Existing installations signed by former runner keys cannot silently adopt this identity. Preserve their data and follow the documented pharmacy export/restore procedure; never uninstall or clear data to bypass a conflict.

Production uses its existing `release-signing` identity. `-Paqaqeer.releaseApkSet=distribution` on an explicit release-only wrapper invocation enables architecture generation. The distribution helper selects universal, ARM32 (`armeabi-v7a`) and ARM64 (`arm64-v8a`); the universal output retains x86/x86_64 support. Additional generated x86-specific APKs are not published. Ordinary debug packaging stays universal. Every selected output uses the same version and certificate; receipts inspect each APK separately. A production bundle includes all three signed APKs, their inspections, `release-info.json`, matching `mapping.txt.gz` and `SHA256SUMS.txt`. Keep all listed files together when checking the full checksum list.

All debug release/prerelease/draft choices set Latest to false. Release records carry durable source/run identifiers; run hyperlinks are temporary under seven-day Actions cleanup. Signing fingerprints come from inspected APKs. Neither structural inspection nor a checksum establishes an installed-update pass.

### Manual workflows added

| Workflow | Filename | Purpose |
| --- | --- | --- |
| 12 - Checks - Build tooling | [build-tooling.yml](../.github/workflows/build-tooling.yml) | Two `help` invocations with configuration cache, reuse check and packaging dry-run; counter must remain unchanged |
| 13 - Checks - Secret history | [secret-history.yml](../.github/workflows/secret-history.yml) | Full-history pinned Gitleaks scan; public output contains counts only; no raw findings artifact |
| 14 - Maintenance - Debug release cleanup | [cleanup-debug.yml](../.github/workflows/cleanup-debug.yml) | Preview by default; explicit deletion choice; only identifiable publisher-owned debug releases/tags |
| 15 - Maintenance - Actionlint updates | [actionlint-updates.yml](../.github/workflows/actionlint-updates.yml) | Stable upstream version/SHA-256 proposal as a patch; never executes the candidate or applies it |

Workflow 03 offers opt-in per-flavor unit coverage with AGP JaCoCo 0.8.16. Reports have no percentage threshold and do not run device tests. Verification APK workflows 07/08 are also default-branch-only. Workflow 08 generates/inspects the three production outputs and separately opens the universal APK on an owned GitHub-hosted API 35 emulator with a throwaway signer. Its 30-second process observation is startup evidence only. The helper treats an empty Android `pm path` exit-1 result as expected package absence, while transport errors remain failures; progress/failure JSON identifies the failing phase. No local emulators or production signing credentials are involved.

The pinned setup-gradle action already validates wrapper JARs; policy forbids disabling this. Workflow YAML checks reject expressions inside shell bodies and permit secrets only in explicit signing-step environments. All new workflows are manual, public-only, bounded Ubuntu jobs with full action pins and one-day artifacts where appropriate. Only the existing run-cleanup workflow has a schedule.

### Why hosted retries can download Gradle again

The basic Gradle cache requires an exact key from the build scripts, wrapper properties and version catalogs. Changing `app/build.gradle.kts` to correct configuration failures therefore produces a new key, even when the Gradle version stays the same. The code-1/2/3 builds each restored zero entries and saved one under a different key. Code 3 saved approximately 346 MB including reusable Gradle state; Java 21.0.13 came from the runner tool cache. A generic "cache enabled/restored" summary sentence does not override the actual zero-restored count.

A subsequent run with unchanged key inputs should reuse its entry if it remains available and accessible. A corrected configuration can still download dependencies the earlier failed run never reached. Fresh runners also check/install required SDK packages separately; an SDK-manager command is not itself proof of a download. The code-4 build missed after the script change, saved its new key, and the separate signing job restored that exact entry successfully. Java came from the runner tool cache, while SDK-manager output showed metadata checks without package installation. This confirms cross-job cache reuse; another complete build with unchanged inputs remains unobserved. No cache quota, alternate cache home or additional caching service was added.

### History scans and cleanup

`python3 tools/scan_history.py --install` explicitly obtains the missing checksum-pinned Linux x86_64 scanner and runs the local history scan. Without `--install`, a missing archive is a prerequisite error. It preserves an existing archive and rejects checksum mismatches instead of replacing it. Local findings/logs have mode 0600 under a private run directory in `local/verification/security/`. Hosted mode keeps detailed findings temporary and prints counts only. Scanner execution is separately authorized; adding this entrypoint did not run it.

Debug cleanup defaults to older than 30 days, keeping at least five newest identifiable releases and the newest for each flavor. Legacy releases lacking the new ownership marker and drafts are retained. Deletion rechecks release/assets/tag identity and confirms absence; failures remain failures. No `android-build-*` or `v0.0.*` tag is eligible. It never runs on a schedule. GitHub release immutability is not enabled by this change: asset/tag protections differ from whole-release deletion, and deleted immutable tag names cannot be reused. Unique debug tags avoid intended reuse, but setting adoption remains a separate decision.

### Keys, collaboration and repository settings

For each permanent signing identity, maintain two separately stored protected backups, record who controls access and how the passphrase is recovered, and verify access to the original backup before relying on it. Keep locations/passphrases outside the repository. Record only the public fingerprint and custody responsibilities in public records. Loss of the original key can end ordinary update continuity. Debug and production backups must remain distinguishable. No additional signing reviewer gate is configured.

The [settings review](evidence/repository-review-20261114.md) records observed controls and gaps. The JSON proposals in `.github/repository-settings/` protect shared reservations/production tags against updates and deletion, and protect the default branch against force-push/deletion. Creation/direct pushes remain available. They do not require PRs, signatures, linear history or another person's review; they have not been applied. Enable private vulnerability reporting before relying on its form. Review free Dependabot security updates/alerts and server-side full-SHA enforcement without buying additional services.

Use short-lived branches when useful and `type(scope): summary` commit titles. Production tags are workflow-managed. The optional `.githooks/pre-push` runs repository integrity checks; activation is a deliberate local choice (`git config core.hooksPath .githooks`), not performed by this change. CODEOWNERS does not imply required approval. Dependency updates retain explicit review; nothing auto-merges.

The third-party notice inventory preserves original notice texts from cached runtime-library families. Authorized release builds also generate `assets/runtime-components.txt` from the exact production runtime graph; this is required by release inspection. It is not a newly resolved shipping graph: reconcile exact transitive runtime coordinates and any additional required notices before first production distribution. No license-report plugin or dependency resolution was run for this source batch.
