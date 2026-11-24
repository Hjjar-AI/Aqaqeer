# Selected logo provenance

## Current matte wordmark — 2026-11-24

The user adopted the [Gemini reference](aqaqeer-logo-reference.jpeg), requested matte colors, discarded older logo variants and then selected **Cobalt / silver** from the [palette previews](previews/README.md). The complete wordmark retains the two horizontal capsule-shaped upper dots, lower split circular tablet, empty hexagonal counters and navy outlines.

Built-in imagegen extracted transparency, produced the matte base and generated color-only palette examples. The [exact prompt set](logo-edit-prompts.txt) records the three accepted stages. The selected palette targets cobalt #3D5E98, matte silver-grey #A9B2BE and the source navy outline. Generated tones/contours can vary from targets; the user-approved cobalt preview itself is copied byte-for-byte as the final master, with no further raster editing.

The [selected master](aqaqeer-logo-matte.png) is **1254 × 1254 RGBA** with alpha 0–255 and transparent background/counters. It is byte-identical to both the [launcher/splash bitmap](../../app/src/main/res/drawable-nodpi/aqaqeer_launcher.png) and [in-app/PDF bitmap](../../app/src/main/res/drawable-nodpi/aqaqeer_logo.png).

- Reference JPEG SHA-256: `9a53bc0a302406caa88ac703205ce372669acadb5492208b9ea0dcbb7b218ef3`
- Approved cobalt preview, selected master and both resource PNGs SHA-256: `21f5202985278c0c83c78564887a526878bf26ed594435f63f867e95961a4169`

## Launcher padding only

The user rejected a taller/bolder redraw and explicitly required unchanged proportions. That redraw was not installed. Normal and round home icons use a [padding-only drawable](../../app/src/main/res/drawable/aqaqeer_launcher_icon.xml) around the unchanged launcher bitmap: equal negative 3% insets on all sides give nominal uniform 6% enlargement, with device-pixel rounding. No font, letter contour, relative proportion, palette or PNG pixel is altered by this layout step. No background shape, adaptive layer or extra outline is added.

At the source canvas resolution, foreground bounds using alpha≥128 are `[49,376,1204,938]`; the nominal inset transform yields approximately `[14.3,360.9,1238.6,956.7]`, still inside the square. This is a source geometry check, not Android launcher/OEM rendering acceptance. The separate native splash inset remains 17%; Compose continues to decode the original bitmap. Splash motion and in-app/PDF framing are retained. Fractional inset parsing and bound handling were source-reviewed in [Android 8's InsetDrawable implementation](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-8.0.0_r1/graphics/java/android/graphics/drawable/InsetDrawable.java).

## Supersession

The two prior violet/emerald masters and seven earlier edit-prompt files were removed from the working artwork set at the user's request. Palette alternatives are explicitly requested comparison previews, not older selected logos. Dated verification evidence and Git history retain their original source scope.

Historical master identities: November 23 launcher `2db1c754eb9aab760b2a70039f827a66494ee67203a22705e8698c32a01e59aa`; November 23 in-app/PDF `97e2288a582311d8f1df926b81b2e9a0a3ff5b9d6edfcc402af6e51649547577`; November 17 softened-gold launcher `59081b805e4582ea8bd49f0a4f78cd84af6da7a73a81a9e7283c22a19a79a37d`. Superseded files are not retained as current alternatives.

[Branding](../../docs/branding.md) owns product choices. No build/device run was performed for this replacement; packaged rendering and human acceptance remain unverified.
