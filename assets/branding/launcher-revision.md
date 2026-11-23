# Selected artwork provenance

The selected masters and prompts are retained here. [Branding](../../docs/branding.md) owns placement and identifies the resources currently used by the app.

## Current launcher and splash — 2026-11-23

Built-in imagegen edited the previous [violet and emerald master](aqaqeer-icon-violet-emerald.png) using the [exact selected edit prompt](launcher-calm-emerald-prompt.txt). The [selected result](aqaqeer-launcher-calm-emerald.png) targets #20BA82 with tonal variation and widens the tablet score lines and ring bonds. It retains the visible Arabic wordmark, the complementary bond orientations and the closed capsule. The generated edit retains the framing approximately.

The **1254 × 1254 RGBA PNG** with transparent corners and ring openings is copied byte-for-byte to the [launcher and splash resource](../../app/src/main/res/drawable-nodpi/aqaqeer_launcher.png). Master and packaged PNG SHA-256: `32b8e126df7ab3335b785442aeebbf14f36bff7b348a9ca1364efb4fab24761a`.

## Current in-app and PDF emblem — 2026-11-23

The [violet and emerald master](aqaqeer-icon-violet-emerald.png) was produced with built-in imagegen using the previous launcher bitmap as input and the user's light/dark preview as the palette reference. The [exact edit prompt](launcher-violet-emerald-prompt.txt) targets #8048F2 and #10CC80. The generated artwork has tonal variation and preserves the visible wordmark, bond orientations, tablet score lines and outline.

The master is a **1254 × 1254 RGBA PNG** with transparent corners and ring openings. The [in-app and PDF resource](../../app/src/main/res/drawable-nodpi/aqaqeer_logo.png) is a byte-identical copy. The launcher and splash also used this master before the calmer emerald revision.

Master and packaged PNG SHA-256: `97e2288a582311d8f1df926b81b2e9a0a3ff5b9d6edfcc402af6e51649547577`.

## Previous launcher — 2026-11-17

The then-selected gold/teal/navy PNG was softened using built-in imagegen from the preceding bold-outline artwork. The [softening prompt](launcher-softened-prompt.txt) records the exact edit; the earlier [palette](launcher-artwork-prompt.txt) and [outline](launcher-outline-bold-prompt.txt) prompts retain generation provenance. Rich warm gold remains bright, with only slight softening. The slate-navy edge is thinner and lighter, teal details remain restrained, and the finish is flatter. The Arabic wordmark, chemical rings and capsule retain their arrangement. The right ring’s three teal bonds occupy the alternate set of sides to the left ring. The four round tablet dots are teal with bolder slate score lines; after an initial enlargement trial, the latest edit requests a 5% reduction from that trial. The [tablet and ring prompts](launcher-tablets-prompt.txt) retain the exact edit sequence. This is a generated variant, not a pixel-preserving recolor.

The final output is **1254 × 1254 RGBA**, with alpha 0–255 and transparent corners. It was copied byte-for-byte to the launcher resource at that revision. No runtime outline wrapper, backing shape or adaptive layer is used. Original in-app bitmap bytes remain unchanged.

Selected/packaged PNG SHA-256: `59081b805e4582ea8bd49f0a4f78cd84af6da7a73a81a9e7283c22a19a79a37d`.
