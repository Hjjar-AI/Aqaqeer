# Selected artwork provenance

The [transparent gold/teal RGBA PNG](aqaqeer-icon-gold-teal.png) was generated from the historical gold master (`aqaqeer-logo-gold.png`, absent from this checkout) using imagegen. It remains the shared in-app/splash/About/PDF artwork and reference for the revised launcher; [branding](../../docs/branding.md) owns placement. The selected gold/teal PNG is retained here.

Source and output are **1254 × 1254 RGBA**, alpha 0–255 with transparent corners. Alpha >64 bounds changed from **(63,408)–(1179,869)** to **(39,389)–(1210,884)**. The selected output uses deeper gold, restrained teal ring/capsule details and tighter transparent margins. It is a generated raster variant with slight shading/contour differences, not a pixel-preserving recolor. No backing shape or adaptive icon is introduced.

## Current launcher — 2026-11-17

The revised [gold/teal/navy PNG](aqaqeer-icon-gold-teal-navy-bold.png) was edited from the retained gold/teal reference using built-in imagegen. The exact [palette prompt](launcher-artwork-prompt.txt) and [bold-outline prompt](launcher-outline-bold-prompt.txt) are retained. The user approved the first palette revision and requested a bolder outline; [that earlier revision](aqaqeer-icon-gold-teal-navy.png) remains available. Warm gold lettering, clearer jewel-teal details and bold deep navy contours are embedded in the image itself. The wordmark, rings, upper dots and capsule retain the reference arrangement; generated contours/shading are not pixel-identical.

The final output is **1254 × 1254 RGBA**, with alpha 0–255 and transparent corners. It is copied byte-for-byte to [the launcher resource](../../app/src/main/res/drawable-nodpi/aqaqeer_launcher.png), referenced directly by both manifest icon attributes. No runtime outline wrapper, backing shape or adaptive layer is used. Original in-app bitmap bytes remain unchanged.

Selected/packaged PNG SHA-256: `5e6f757a2323d8c697e3971268ac83c9231b4f9c11a69e5c1d0fbd58167745ea`.
