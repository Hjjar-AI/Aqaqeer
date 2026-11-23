# Branding

The [Aqaqeer logo](../assets/branding/aqaqeer-icon-violet-emerald.png) uses Electric Violet for the Arabic wordmark and Vivid Emerald Green for the tablet dots, ring bonds and half of the capsule. The dark navy contours and score lines are embedded in the transparent PNG. The two rings keep their complementary bond orientations. The normal and round launcher icons reference the same [non-adaptive bitmap](../app/src/main/res/drawable-nodpi/aqaqeer_launcher.png). The [in-app and PDF resource](../app/src/main/res/drawable-nodpi/aqaqeer_logo.png) carries identical artwork. [Generation provenance and prompt](../assets/branding/launcher-revision.md).

## Splash artwork and motion

Android’s starting window centers the violet and emerald logo on a solid background. A 17% inset on each side frames the original PNG within the native icon slot. With no saved launch hint available, the starting window follows system mode and uses Violet in light mode. The app applies the saved theme and system-bar contrast, including pure black in AMOLED.

The app continues with the same centered logo at about 190 dp. While the local catalog opens, the logo grows from 1× to 1.65× and returns to 1× every 720 ms. Once the destination is ready, the logo stops at its current scale and the splash fades over 100 ms. If the app splash has yet to draw or system animations are disabled, the destination appears directly.

## In-app placement

Toolbar, drawer, onboarding and About load the logo through BrandAssets. PDF headers load the same bitmap through the report renderer. About displays the app name, version and contact beside the logo. [Home/navigation requirements](requirements.md#home-controls-and-filters) own toolbar, drawer, Add/search, grouping, pane and touch behavior; [architecture](architecture.md#responsive-browsing-and-navigation) owns their mechanisms. Android/OEM visual acceptance remains unverified, with additional hardware/human coverage outside required delivery in the [plan](plan.md); [validation](validation.md) owns dated evidence.

## Typography, color and illustration

Presentation uses Noto Sans Arabic UI weights 400/500/600/700, Noto Naskh Arabic medication headings and Latin sans-serif names. Small labels are at least 12 sp. Scientific names lead; optional commercial names use a secondary Arabic label. Violet is the default accent (#6333B6 in light mode and #D1B6FF in dark mode). Blue, emerald Green and Rose each have light and dark primary and container colors. Light mode uses white cards and faintly tinted backgrounds. Dark mode uses a shared cool ink background. The separate «أسود» AMOLED mode makes neutral content surfaces pure black with transparent surface tint, keeping the artwork and semantic badge colors. The logo palette is shared across theme choices. Logo colors serve a decorative role. Status palettes stay independently green/amber/red/neutral with text/icons; expiry shares amber/red. Grouped surfaces retain outlineVariant boundaries; medication cards use the lighter outlineVariant role and a 4 dp status edge with colors independent of badge text. Compact strength wraps beneath the first scientific name; availability stays text-only beside expiry. Major group spacing is 24 dp; action-blocking text and medium labels have a 14 sp floor. Compose status/list transitions follow system animation scaling.

All displayed digits use Western 123, including counts and days. Bidi-isolate Latin/date runs; digit formatting never changes stored values. About intentionally retains its separate app name alongside the emblem. Seeded dosage forms use original shaded VectorDrawable illustrations alongside their labels; custom forms use a neutral Other illustration. Linear gradients, contour highlights and beveled edges provide modest depth without ground shadows. Day/night color resources support light, dark and AMOLED surfaces. Only dosage illustrations preserve intrinsic colors; navigation/action icons retain their normal theme tint.

The capsule has a straight two-piece body, rounded ends and a crosswise seam; the suppository has a rounded tip. Syrup/suspension and cream/ointment/gel have distinct illustrative silhouettes/details. Spray uses a nozzle and visible mist. Containers are representative, while written dosage-form labels remain authoritative. The [saved reference](../assets/reference/dosage-forms/README.md) includes the final-set PNG/SVG and the separate gel-material candidate.

## Empty states and previews

Empty states use a dedicated [native capsule illustration](../app/src/main/res/drawable/empty_state_capsule.xml) at 30% opacity. Two separated emerald and violet shells face one another diagonally, with visible empty interiors and navy contours. EmptyCapsule draws the vector on a transparent canvas.

Preference controls use 12 dp accent dots sampled from the actual theme primary color and a card-layout sample. The persisted PURPLE key is labeled بنفسجي. OUT cards add faint diagonal stripes behind opaque readable content. Launch-neutral colors remain canonical in presentation_colors.xml for native styles and Compose. These are source decisions, not physical visual acceptance.

[Responsive presentation](requirements.md#home-controls-and-filters) keeps Filters/What’s new inside search and eligible admin Add outside, with no Select shortcut or floating overlay. Tabs reserve a 16 dp search-icon slot; pane limits live in the navigation requirements. Theme shape tokens preserve 8/12/16/24 dp radii. Arabic titleSmall/titleMedium/titleLarge line heights are 23/26/34 sp and headlineSmall/Medium/Large are 36/42/48 sp; display and body/label styles retain their existing values. Short-window decoration shrinks while touch targets stay at least 48 dp.
