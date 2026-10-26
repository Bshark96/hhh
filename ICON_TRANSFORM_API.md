# Xiaomi MTZ `<IconTransform>` & `<IconFilters>` XML API Reference

This document describes the complete `<IconTransform>` XML specification supported by **MTZLauncher** (`transform_config.xml` / `icon_config.xml`), including both **Stock MIUI / HyperOS** filters and the **Extended Sequential Transform API** (`ColorSplash`, `Duotone`, `Adjust`, `EdgeGlow`, `Posterize`, `Sepia`, `Invert`, `Monochrome`, `StripAdaptivePlate`).

---

## 1. Pipeline Execution Modes: Stock MIUI vs. Sequential XML Order

MTZLauncher supports two execution modes for `<IconFilters>`:

### A. Stock MIUI Pipeline (Default when unmodified)
When `transform_config.xml` uses the standard MIUI filter structure (`[ColorMatrix / Lighting / HSL] -> [Edges] -> [GrayScale] -> [Levels] -> [Spread / GausBlur / Alpha]`) and does **not** set `sequential="true"` or include extended filters:
- The engine preserves the **exact stock MIUI `<IconTransform>` pipeline** (`Midnight Blue`, `Trickster hejo V10`, `Valorant`, `B22 N Theme`).
- Outer adaptive plates on 3rd-party icons are stripped automatically via BFS perimeter traversal, inner foreground glyphs are shaded into the `BlackColor..WhiteColor` ramp with `Edges` and `Levels`, and the foreground bounding box is normalized before `<PointsMapping>` and `icon_mask.png` / `icon_border.png` compositing.

### B. Custom Sequential Pipeline (Arbitrary Filter Ordering)
When you:
1. Add `sequential="true"` (or `pipeline="custom"`) to `<IconTransform>` or `<IconFilters>`, **OR**
2. Reorder native MIUI filters (for example, placing `Levels` before `GrayScale`, `GrayScale` before `Edges`, or `HSL` after `GrayScale`), **OR**
3. Include any extended filter (`ColorSplash`, `Duotone`, `Adjust`, `EdgeGlow`, `Posterize`, `Sepia`, `Invert`, `Monochrome`, `StripAdaptivePlate`),

the engine switches to **Sequential Filter Execution**:
- Every `<Filter name="...">` inside `<IconFilters>` is executed **one by one in the exact top-to-bottom XML order** you wrote.
- You can freely interleave native MIUI filters (`StripAdaptivePlate`, `Edges`, `GrayScale`, `Levels`, `Spread`, `GausBlur`, `HSL`, `ColorMatrix`, `Lighting`, `Alpha`, `PointsMapping`) with extended filters (`ColorSplash`, `Duotone`, `Adjust`, `EdgeGlow`, `Posterize`, `Sepia`, `Invert`, `Monochrome`) in any order and repeat filters multiple times.

---

## 2. Target Scope (`scope="third_party"` vs `scope="all"`)

You can configure which icons receive `<IconFilters>` using the `scope` (or `applyToAll`) attribute on `<IconTransform>` or `<IconFilters>`:

| Attribute | Values | Default | Description |
| :--- | :--- | :--- | :--- |
| `scope` | `"third_party"` \| `"all"` \| `"global"` | `"third_party"` | `"third_party"` applies `<IconFilters>` only to unmatched 3rd-party fallback icons (Stage 5). `"all"` / `"global"` applies the filter pipeline to **every icon** globally, including direct `.mtz` PNGs (`res/drawable-xxhdpi/`), `icon_rule.xml` matches, MAML `fancy_icons`, and manual overrides. |
| `applyToAll` | `"true"` \| `"false"` | `"false"` | Shorthand boolean alias for `scope="all"`. |
| `sequential` | `"true"` \| `"false"` | Auto-detected | Forces strict top-to-bottom sequential execution even when only standard MIUI filters are present. |

```xml
<IconTransform scope="all" sequential="true">
    <IconFilters>
        <!-- Applied in exact order to all icons -->
    </IconFilters>
</IconTransform>
```

---

## 3. Parameter Syntax (Inline Attributes vs. `<Param>` Tags)

Every `<Filter>` supports both **inline XML attributes** and nested **`<Param name="..." value="..." />`** tags interchangeably (case-insensitive):

```xml
<!-- Inline attribute syntax -->
<Filter name="ColorSplash" Colors="red,orange" Tolerance="25" Mode="KEEP_ORIGINAL" />

<!-- Nested <Param> syntax -->
<Filter name="ColorSplash">
    <Param name="Colors" value="red,orange" />
    <Param name="Tolerance" value="25" />
    <Param name="Mode" value="KEEP_ORIGINAL" />
</Filter>
```

---

## 4. Extended Transform Filters Reference

### 4.1 `ColorSplash` (`<Filter name="ColorSplash">`)
Isolates specific color hues while converting the rest of the icon to black-and-white grayscale (or tinting it). Supports keeping matched hues in their original color or recoloring matched hues with a custom tint, plus inverting the selection.

| Parameter | Type / Range | Default | Description |
| :--- | :--- | :--- | :--- |
| `Colors` (or `TargetColors`, `Hues`, `Swatch`) | Comma/pipe-separated list | `"red"` | Named swatches (`red`, `orange`, `yellow`, `lime`, `green`, `teal`, `cyan`, `blue`, `purple`, `magenta`, `pink`), `#RRGGBB` hex colors, or explicit hue angles (`0..360`). |
| `CustomHue` (or `Hue`) | Float (`0..360`) or `#RRGGBB` | None | Additional custom target hue angle in degrees (`0` = Red, `120` = Green, `240` = Blue). |
| `Tolerance` (or `HueTolerance`) | Float (`5..90`) | `24` | Angular hue distance tolerance in degrees (`±Tolerance`) around each target hue. |
| `MinSaturation` (or `MinSat`) | Float (`0.0..1.0`) | `0.15` | Minimum HSV saturation required for a pixel to count as colored rather than neutral gray/white. |
| `Mode` | `"KEEP_ORIGINAL"` \| `"TINT_SPLASH"` | `"KEEP_ORIGINAL"` | `"KEEP_ORIGINAL"` preserves original RGB colors on matched pixels. `"TINT_SPLASH"` recolors matched pixels with `SplashTint` while preserving luminance shading. |
| `Invert` (or `InvertSelection`) | Boolean (`"true"` \| `"false"`) | `"false"` | When `"true"`, flips the selection so matched hues become B&W/rest-tinted and all other colors remain splashed. |
| `SplashTint` (or `TintSplash`) | `#RRGGBB` | `"#FF3B30"` | Tint color applied to splashed pixels when `Mode="TINT_SPLASH"`. |
| `RestTint` (or `TintRest`) | `#RRGGBB` or `"none"` | `"none"` (B&W) | Optional tint color applied to unmatched ("rest") pixels. When omitted or `"none"`, unmatched pixels are pure B&W grayscale. |

---

### 4.2 `Duotone` / `GradientMap` (`<Filter name="Duotone">`)
Maps pixel luminance (`0..255`) onto a 2-stop (`ShadowColor -> HighlightColor`) or 3-stop (`ShadowColor -> MidColor -> HighlightColor`) color ramp.

| Parameter | Type / Range | Default | Description |
| :--- | :--- | :--- | :--- |
| `ShadowColor` (or `DarkColor`, `BlackColor`) | `#RRGGBB` | `"#1B1464"` | Color mapped to dark shadows (`luma = 0`). |
| `MidColor` (or `MiddleColor`) | `#RRGGBB` (optional) | None | Optional midpoint color mapped to `luma = 128`. |
| `HighlightColor` (or `LightColor`, `WhiteColor`) | `#RRGGBB` | `"#00F5D4"` | Color mapped to bright highlights (`luma = 255`). |
| `Intensity` (or `Blend`, `Alpha`) | Float (`0.0..1.0` or `0..255`) | `1.0` | Blend ratio between the original icon (`0.0`) and the duotone ramp (`1.0`). |

---

### 4.3 `Adjust` / `ColorAdjust` / `Contrast` / `Brightness` (`<Filter name="Adjust">`)
Adjusts Hue shift, Saturation multiplier, Contrast, and Brightness either together in one step or as standalone `<Filter name="Contrast">` / `<Filter name="Brightness">` steps.

| Parameter | Type / Range | Default | Description |
| :--- | :--- | :--- | :--- |
| `Hue` (or `HueShift`) | Float (`-180..180`) | `0.0` | Hue rotation in degrees. |
| `Saturation` (or `Sat`) | Float (`0.0..3.0`) | `1.0` | Saturation multiplier (`0.0` = grayscale, `1.0` = unchanged, `>1.0` = boosted vibrancy). |
| `Contrast` | Float (`0.2..3.0`) | `1.0` | RGB contrast multiplier centered around midpoint `128`. |
| `Brightness` | Float (`-1.0..1.0` or `-255..255`) | `0.0` | Additive brightness offset. |

---

### 4.4 `EdgeGlow` / `NeonOutline` / `SobelEdge` (`<Filter name="EdgeGlow">`)
Extracts 3x3 Sobel luminance edges, colors them with `EdgeColor`, optionally applies a Gaussian neon bloom (`GlowRadius`), and composites over `BaseColor` and optional original icon fill (`FillAlpha`).

| Parameter | Type / Range | Default | Description |
| :--- | :--- | :--- | :--- |
| `EdgeColor` (or `Color`, `OutlineColor`) | `#RRGGBB` | `"#00F5D4"` | Color of detected Sobel edges and neon bloom. |
| `BaseColor` (or `BackgroundColor`) | `#AARRGGBB` / `#RRGGBB` | `"#00000000"` | Interior backing color within the icon's alpha silhouette. |
| `Threshold` (or `Value`) | Int (`8..180`) | `36` | Minimum Sobel gradient magnitude (`1..255`) required to emit an edge pixel. |
| `FillAlpha` (or `OriginalAlpha`) | Float (`0.0..1.0`) | `0.0` | Opacity of the original icon colors underneath the glowing edge outline. |
| `GlowRadius` (or `Radius`) | Int (`0..12`) | `1` | Gaussian bloom radius around detected edges (`0` = crisp 1px wireframe). |

---

### 4.5 `Posterize` / `Quantize` (`<Filter name="Posterize">`)
Quantizes continuous RGB gradients into flat retro/pixel-art color bands.

| Parameter | Type / Range | Default | Description |
| :--- | :--- | :--- | :--- |
| `Levels` (or `Value`) | Int (`2..16`) | `4` | Number of discrete tonal steps per RGB channel. |

---

### 4.6 `Sepia`, `Invert`, and `Monochrome`
Standalone tonal filters that can be placed anywhere in the filter chain:

| Filter Name | Parameters | Defaults | Description |
| :--- | :--- | :--- | :--- |
| `Sepia` | `Intensity` (`0.0..1.0`) | `Intensity="1.0"` | Applies warm archival sepia matrix toning. |
| `Invert` (or `Negative`) | `Intensity` (`0.0..1.0`) | `Intensity="1.0"` | Inverts RGB channels (`255 - channel`) while preserving alpha. |
| `Monochrome` (or `Tint`) | `Color` (`#RRGGBB`), `Intensity` (`0.0..1.0`) | `Color="#ECF0F1"`, `Intensity="1.0"` | Tints pixel luminance with a single color while preserving highlights and shadows. |

---

## 5. Native MIUI Filters Reference (Available in Any Order)

All native MIUI filters can be placed at any position in `<IconFilters>`:

| Filter Name | Parameters | Description |
| :--- | :--- | :--- |
| `StripAdaptivePlate` | `NormalizeBounds="true\|false"` | Strips outer white or solid/gradient adaptive background plates via BFS perimeter flood-fill and optionally normalizes the inner glyph's bounding box. |
| `Edges` | `Threshold="32"` (optional, `1..255`) | Native MIUI 3x3 Sobel edge detector. |
| `GrayScale` | `BlackColor="#..."`, `WhiteColor="#..."`, `Alpha="255"` | Maps luminance onto the two-point `BlackColor -> WhiteColor` ramp (supports `#00000000` transparent cutouts). |
| `Levels` | `InputMin="0..240"`, `InputMiddle="0.1..5.0"`, `InputMax="10..255"`, `OutputMin="0..255"`, `OutputMax="0..255"` | Remaps input tonal range and gamma onto the output range. |
| `Spread` | `Radius="-12..12"` | Morphological dilation (`Radius > 0`, thickens strokes) or erosion (`Radius < 0`, thins strokes). |
| `GausBlur` | `Radius="1..25"` | Separable 2-pass premultiplied Gaussian blur. |
| `HSL` / `HSB` | `HueModify="-180..180"`, `SaturationModify="-1.0..1.0"`, `LightnessModify="-1.0..1.0"` | Shifts HSV/HSL channels. |
| `ColorMatrix` | `Matrix="r0 r1 r2 r3 r4 g0 ... a4"` | Applies a 4x5 (20-float) Android `ColorMatrix`. |
| `Lighting` | `Mul="#RRGGBB"`, `Add="#RRGGBB"` | Applies `(pixel * Mul) + Add` lighting filter. |
| `Alpha` | `Alpha="0..255"` | Scales pixel opacity across the layer. |
| `PointsMapping` | `Points="x0,y0,x1,y1,x2,y2,x3,y3"` or `Scale="0.78"` | Applies a 4-point perspective/affine warp (`Matrix.setPolyToPoly`) at that exact point in the filter chain. |

---

## 6. Copy-Pasteable `transform_config.xml` Examples

### Example 1: Color Splash — Keep Red & Orange Original, Convert Rest to B&W
Preserves red and orange hues on all icons while turning all other hues into crisp black-and-white grayscale:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<IconTransform scope="all">
    <PointsMapping>
        <Point fromX="0.0" fromY="0.0" toX="10.0" toY="10.0" />
        <Point fromX="0.0" fromY="90.0" toX="10.0" toY="80.0" />
        <Point fromX="90.0" fromY="90.0" toX="80.0" toY="80.0" />
        <Point fromX="90.0" fromY="0.0" toX="80.0" toY="10.0" />
    </PointsMapping>
    <IconFilters>
        <Filter name="ColorSplash">
            <Param name="Colors" value="red,orange" />
            <Param name="Tolerance" value="25" />
            <Param name="MinSaturation" value="0.18" />
            <Param name="Mode" value="KEEP_ORIGINAL" />
            <Param name="Invert" value="false" />
        </Filter>
        <Filter name="Adjust" Contrast="1.15" />
    </IconFilters>
</IconTransform>
```

### Example 2: Color Splash — Tint Matched Blue/Cyan to Neon Gold, Tint Rest to Deep Slate
Matches blue and cyan hues (`Colors="blue,cyan"`), recolors them to `#FFD166` (`Mode="TINT_SPLASH"`), and tints the unmatched background/rest to `#2B3A4A`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<IconTransform scope="third_party">
    <PointsMapping>
        <Point fromX="0.0" fromY="0.0" toX="10.0" toY="10.0" />
        <Point fromX="0.0" fromY="90.0" toX="10.0" toY="80.0" />
        <Point fromX="90.0" fromY="90.0" toX="80.0" toY="80.0" />
        <Point fromX="90.0" fromY="0.0" toX="80.0" toY="10.0" />
    </PointsMapping>
    <IconFilters>
        <Filter name="StripAdaptivePlate" NormalizeBounds="true" />
        <Filter name="ColorSplash"
            Colors="blue,cyan"
            CustomHue="210"
            Tolerance="30"
            Mode="TINT_SPLASH"
            SplashTint="#FFD166"
            RestTint="#2B3A4A"
            Invert="false" />
    </IconFilters>
</IconTransform>
```

### Example 3: Reordered Native MIUI Filters + Extended Filters
Demonstrates arbitrary ordering of native MIUI filters (`StripAdaptivePlate` -> `Posterize` -> `Levels` -> `Edges` -> `Spread` -> `Duotone`):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<IconTransform sequential="true">
    <PointsMapping>
        <Point fromX="0.0" fromY="0.0" toX="12.0" toY="12.0" />
        <Point fromX="0.0" fromY="90.0" toX="12.0" toY="78.0" />
        <Point fromX="90.0" fromY="90.0" toX="78.0" toY="78.0" />
        <Point fromX="90.0" fromY="0.0" toX="78.0" toY="12.0" />
    </PointsMapping>
    <IconFilters>
        <Filter name="StripAdaptivePlate" NormalizeBounds="true" />
        <Filter name="Posterize" Levels="5" />
        <Filter name="Levels" InputMin="40" InputMiddle="1.2" InputMax="230" />
        <Filter name="Edges" Threshold="28" />
        <Filter name="Spread" Radius="1" />
        <Filter name="Duotone" ShadowColor="#0F172A" MidColor="#3B82F6" HighlightColor="#38BDF8" Intensity="1.0" />
    </IconFilters>
</IconTransform>
```

### Example 4: Cyberpunk Neon EdgeGlow Pipeline
Extracts glowing cyan outlines over a translucent dark base across all icons:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<IconTransform scope="all">
    <IconFilters>
        <Filter name="StripAdaptivePlate" NormalizeBounds="true" />
        <Filter name="EdgeGlow"
            EdgeColor="#00F5D4"
            BaseColor="#141824"
            Threshold="32"
            FillAlpha="0.18"
            GlowRadius="2" />
    </IconFilters>
</IconTransform>
```

### Example 5: Unmodified Stock MIUI Pipeline (Preserved Automatically)
Standard MIUI themes without extended filters or custom ordering run through the unmodified stock MIUI `<IconTransform>` engine:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<IconTransform>
    <PointsMapping>
        <Point fromX="0.0" fromY="0.0" toX="10.0" toY="10.0" />
        <Point fromX="0.0" fromY="90.0" toX="10.0" toY="80.0" />
        <Point fromX="90.0" fromY="90.0" toX="80.0" toY="80.0" />
        <Point fromX="90.0" fromY="0.0" toX="80.0" toY="10.0" />
    </PointsMapping>
    <IconFilters>
        <Filter name="GrayScale">
            <Param name="BlackColor" value="#465967" />
        </Filter>
        <Filter name="Levels">
            <Param name="InputMin" value="95" />
        </Filter>
    </IconFilters>
</IconTransform>
```
