# UI font provenance

Noto Sans Arabic variable font downloaded on 2026-11-13 from the [official Google Fonts source](https://github.com/google/fonts/tree/main/ofl/notosansarabic). The upstream filename is `NotoSansArabic[wdth,wght].ttf`; the Android resource is [noto_sans_arabic.ttf](../../app/src/main/res/font/noto_sans_arabic.ttf).

- Font SHA-256: `63111b5b2e074dd48cc67692e0a2726d86ee94c1c37fe8598257b7b4e87e869e`.
- [SIL Open Font License](NotoSansArabic-OFL.txt), SHA-256: `07fc70bfeb985cc1a87a8587d0a0c80bab11c86c9dc3fd95b6f0cb332f983e96`; an identical copy ships in application assets.
- SFNT directory bounds and required tables were checked; variable axes: `[('wght', 100.0, 400.0, 900.0), ('wdth', 62.5, 100.0, 100.0)]`. UI chooses weights 400/500/600/700. These structural checks are not Android rendering acceptance.

Noto Naskh Arabic remains on medication headings. This addition changes no Android dependency/version pins, launcher or emblem artwork.

Android API 23–25 use static Noto Sans Arabic instances generated from the same variable source with FontTools 4.61.1. Both variation axes are fixed at `wdth=100` and the weight below. Instancing updates the font names, preserves the original timestamp, and removes the variation tables. The same SIL Open Font License applies.

| Weight | Static resource | SHA-256 |
| --- | --- | --- |
| 400 | [Regular](../../app/src/main/res/font/noto_sans_arabic_400.ttf) | `65efad4885c590b640d9601b8cc6d9b66026e9ff74154ac600207600842c0f70` |
| 500 | [Medium](../../app/src/main/res/font/noto_sans_arabic_500.ttf) | `d8aeed490c9e28160ebcd2c5840477973201427bb91de3c9e6c02b4142eb98de` |
| 600 | [SemiBold](../../app/src/main/res/font/noto_sans_arabic_600.ttf) | `459f7d8909fc26e7ccc85c220ca9c86f63cb934754e8284900b26ed05fbbd5a0` |
| 700 | [Bold](../../app/src/main/res/font/noto_sans_arabic_700.ttf) | `8bdca58348d24273c150b823327956577589ef230f6c378a6aa66e08a5581a75` |
