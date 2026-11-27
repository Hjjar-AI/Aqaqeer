# Branding

The selected matte wordmark follows the user's Gemini reference and their chosen **Cobalt / silver** palette. It keeps the navy outlines, two horizontal capsule-shaped upper dots, empty hexagonal counters and lower split circular tablet. Palette targets are cobalt #3D5E98 and matte silver-grey #A9B2BE. Generated shades vary, and the approved preview is retained unchanged. [Selected wordmark](../assets/branding/aqaqeer-logo-matte.png)

The Android build copies the sole 1254 × 1254 RGBA master byte-for-byte into the generated launcher/splash and in-app/PDF resources. Normal and round home icons use a [padding-only drawable](../app/src/main/res/drawable/aqaqeer_launcher_icon.xml) with equal −3% insets. This uniformly enlarges the existing bitmap nominally 6%, without changing letter proportions, pixel data or adding a backing/adaptive shape. The taller generated redraw was rejected and is not used.

The native and Compose splash use the approved bitmap and framing. Android/OEM rendering remains unverified. [Provenance, exact prompts and hashes](../assets/branding/launcher-revision.md)

## Splash artwork and motion

Android’s starting window centers the cobalt and silver logo on a solid background. A 17% inset on each side frames the original PNG within the native icon slot. The native drawable uses filtered scaling and a mipmap hint. With no saved launch hint available, the starting window uses the Cobalt palette in the system’s light or dark mode. The app applies the saved theme and system-bar contrast, including pure black in AMOLED.

The app continues with the same centered logo at about 190 dp. While the local catalog opens, the logo grows from 1× to 1.65× and returns to 1× every 1,440 ms. Once the destination is ready, the logo stops at its current scale and the splash fades over 100 ms. If the app splash has yet to draw or system animations are disabled, the destination appears directly.

## In-app placement

Toolbar, drawer, onboarding and About load the logo through BrandAssets. PDF headers load the same bitmap through the report renderer. About displays the app name, version and contact with the logo and a small decorative hexagon. [Home/navigation requirements](requirements.md#home-controls-and-filters) own toolbar, drawer, Add/search, grouping, pane and touch behavior; [architecture](architecture.md#responsive-browsing-and-navigation) owns their mechanisms. Android/OEM visual acceptance remains unverified, with additional hardware/human coverage outside required delivery in the [plan](plan.md); [validation](validation.md) owns dated evidence.

## Typography, color and illustration

Presentation uses Noto Sans Arabic UI weights 400/500/600/700, Noto Naskh Arabic medication headings and Latin sans-serif names. Small labels are at least 12 sp. Scientific names lead; optional commercial names use a secondary Arabic label. Cobalt is the default interface accent (#48549A in Light and #BBC2FF in Dark). The other primary choices are Copper (#8B4C23 / #FFB786), Green (#007F5D / #83E3BA) and warm Rose (#96323F / #FFA8AE). Light mode uses white cards and faintly tinted backgrounds. Dark mode uses tinted near-black backgrounds and shared neutral cards. The separate «أسود» AMOLED mode makes neutral content surfaces pure black with transparent surface tint, keeping the artwork and semantic badge colors. The logo palette is shared across theme choices. Logo colors serve a decorative role. Stock uses blue, gold and vermilion with written labels. Expiry uses purple and magenta with calendar icons. PDF and Excel availability cells use print shades #D8F0FF, #F0D589 and #EFB594 with black labels. The PDF rule uses logo navy #14213C. Grouped surfaces retain outlineVariant boundaries; medication cards use the lighter outlineVariant role and a 4 dp status edge with colors independent of badge text. Compact strength wraps beneath the first scientific name; availability stays text-only beside expiry. Major group spacing is 24 dp; action-blocking text and medium labels have a 14 sp floor. Compose status/list transitions follow system animation scaling.

All displayed digits use Western 123, including counts and days. Bidi-isolate Latin/date runs; digit formatting never changes stored values. About intentionally retains its separate app name alongside the emblem. Seeded dosage forms use flat VectorDrawable illustrations alongside their labels. Custom forms use a neutral Other illustration. Light mode uses navy #14213C, silver #D9E1EC and muted blue #617DA5. Dark and AMOLED use pale blue-grey #B8C7D9, slate #34455D and muted blue #7B9AC4. Dosage artwork uses the app’s selected mode. Navigation and action icons retain their normal theme tint.

The capsule has a straight two-piece body, rounded ends and a crosswise seam; the suppository has a rounded tip. Syrup/suspension and cream/ointment/gel have distinct illustrative silhouettes/details. Spray uses a nozzle and visible mist. Containers are representative, while written dosage-form labels remain authoritative. The [saved reference](../assets/reference/dosage-forms/README.md) includes the final-set PNG/SVG and the separate gel-material candidate.

## Empty states and previews

Empty states use a dedicated [native capsule illustration](../app/src/main/res/drawable/empty_state_capsule.xml) at 30% opacity. Two blue-grey shells face one another diagonally with visible empty interiors and use the same mode-specific colors as the dosage forms. EmptyCapsule draws the vector on a transparent canvas.

Preference controls use 12 dp accent dots sampled from the actual theme primary color and a card-layout sample. The persisted PURPLE key is labeled نحاسي and selects Copper. OUT cards add diagonal stripes at 4% opacity behind fully opaque content. Launch-neutral colors remain canonical in presentation_colors.xml for native styles and Compose. These are source decisions, not physical visual acceptance.

[Responsive presentation](requirements.md#home-controls-and-filters) keeps Filters/What’s new inside search and eligible admin Add outside, with no Select shortcut or floating overlay. Tabs reserve a 16 dp search-icon slot; pane limits live in the navigation requirements. Theme shape tokens preserve 8/12/16/24 dp radii. Arabic titleSmall/titleMedium/titleLarge line heights are 23/26/34 sp and headlineSmall/Medium/Large are 36/42/48 sp; display and body/label styles retain their existing values. Short-window decoration shrinks while touch targets stay at least 48 dp.
