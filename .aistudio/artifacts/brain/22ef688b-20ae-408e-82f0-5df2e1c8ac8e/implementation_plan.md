# Dark Fate MAML Fidelity, Full Launcher Widget Theming & Independent Widget Theme Mixing

This update resolves every rendering discrepancy in the **Dark Fate** `.mtz` theme (restoring the `<Time>` bitmap clock digits, day-of-month `<Time format="d">` sprites, dynamic `fancy_icons` `<Image srcid="#date">` Calendar icon, AppleDouble `._*` resource filtering, and themed folder plates), extends `.mtz` theme styling across all existing launcher widgets and folders, and adds a **Global Widget Theme Selector** in Theme Studio so users can apply icons from one `.mtz` theme while rendering workspace widgets from another.

## User Review & Critical Decisions

> [!IMPORTANT]
> Confirmed user preferences from our clarification step:
> - **Confirmed Decision 1 (Mix Icons & Widgets Across Themes)**: Provide a **Global Widget Theme Selector in Theme Studio only**, allowing the user to keep widgets synced with the active Icon Theme (`Follow Active Icon Theme`) or independently pick any installed `.mtz` theme as the global Widget Theme.
> - **Confirmed Decision 2 (Scope of MTZ Widget & Launcher Mods)**: Style **only the widgets and workspace elements that the current launcher already has** (`MIUI Clock & Date`, `MIUI Clock & Weather 2x4`, `MIUI Live Weather`, `MIUI Calendar & Agenda`, `System Status`, `Storage Status`, `Clock & Calendar Shortcuts`, and Workspace `Folder` plates) without adding extra widget types.

---

## 1. Root-Cause Diagnosis: Why Dark Fate Looked Different ("Planned vs. Actual")

| Visual Element | Official Dark Fate Preview ("Planned") | Current Launcher ("Actual") | Root Cause Identified in Archive |
| :--- | :--- | :--- | :--- |
| **Main Clock Digits (`02 36`)** | Large red cyber-segmented `time_0.png`..`time_9.png` (`132x144`) separated by `time_dot.png` (`35x144`) | Missing completely | Dark Fate's `clock_2x4/manifest.xml` uses the MAML **`<Time x="#w/2" y="120" align="center" src="time.png" />`** tag rather than `<Image>` or `<DateTime>`. The MAML clock parser previously ignored `<Time>` tags. |
| **Date Row (`THU - 11 JUN`)** | Displays weekday sprite (`w_4.png`), dash (`date_dot.png`), day of month (`date_1.png` `date_1.png`), and month (`m_5.png`) | Rendered `MON -   OCT` with a blank gap where the day number belongs | The day number uses **`<Time name="date_w1" src="date.png" format="d" />`**, and its parent `<Group w="#week_w.bmp_width+24+#date_w.bmp_width+24+#date_w1.bmp_width+24+#month_w.bmp_width">` relies on `#date_w1.bmp_width` to center the row. |
| **Calendar Workspace Icon** | Red & black cyber Calendar icon showing the live day of month (`calendar_icon_1.png`..`calendar_icon_31.png`) | Blank / invisible icon above the `Calendar` label | Dark Fate's `fancy_icons/com.android.calendar/manifest.xml` uses **`<Image x="0" y="0" src="calendar_icon.png" srcid="#date" />`**. The fancy icon parser did not read `srcid` or `<ImageNumber>` on `<Icon>` manifests, so it looked for a non-existent `calendar_icon.png`. |
| **macOS AppleDouble Files (`._*`)** | Ignores 4 KB macOS resource fork files (`._time_0.png`, `._manifest.xml`, `._icon_folder.png`) | Extracted `._*` files alongside real assets | Dark Fate was zipped on macOS and contains `._*` AppleDouble metadata entries inside `clock_2x4` and `icons`. Filtering out `._*` and `__MACOSX` entries during extraction prevents corrupt 4 KB stubs from shadowing real PNGs or manifests. |
| **Folder Container (`Google`)** | Uses Dark Fate's custom red-and-black tech frame (`res/drawable-xxhdpi/icon_folder.png`) | Generic semi-transparent grey rounded box | Workspace folders drew a flat translucent color plate instead of rendering `iconFolderPath` (`icon_folder.png`) and `folderCoverPath` (`folder_icon_cover.png`) from the active theme. |

---

## 2. User Experience & Visual Design

- **Authentic Dark Fate & Multi-Theme MAML Clock Rendering**:
  - Support the MAML **`<Time>`** element (`src="time.png"` / `src="time/time.png"` / `src="date.png"`, optional `format="d"` / `"HH:mm"` / `"hh:mm"`, `space`, `align`, `alignV`) and expose `#<name>.bmp_width` and `#<name>.bmp_height` during layout pre-measurement so `<Group w="...">` expressions align multi-sprite rows (`THU - 11 JUN`) with pixel accuracy.
  - Support `<Text format="%s..." paras="...">` formatted parameter interpolation and localized `strings/strings.xml` lookup tables used by themes like `B22 N Theme` and `Trickster hejo V10`.
- **Dynamic `fancy_icons` MAML Parity (`Calendar`, `Weather`, `Clock` Icons)**:
  - Upgrade the `fancy_icons` `<Icon>` parser and renderer to support `<Image srcid="...">` (`calendar_icon.png` + `#date` -> `calendar_icon_26.png`), `<ImageNumber number="..." src="..." space="...">` (`d_0.png..d_9.png`, `w_0.png..w_9.png`, `t_0.png..t_9.png` with `#<name>.bmp_width`), `<Var>` expressions, and `visibility="..."` conditions.
  - Preserve MAML's default `align="left"` and `alignV="top"` when `<Image>` inside `fancy_icons` omits alignment attributes so `x="0" y="0"` icons fill the canvas cleanly without clipping.
- **MTZ Styling Across Existing Workspace Widgets & Folders**:
  - **MIUI Clock & Date (`CLOCK_MTZ_STATUS`) & MIUI Clock & Weather 2x4 (`MIUI_CLOCK_WEATHER_2X4`)**: Render the selected Widget Theme's full MAML `clock_2x4` / `clock_3x4` scene graph (`<Time>`, `<Image>`, `<ImageNumber>`, `<DateTime>`, `<Text>`, `<Group>`).
  - **MIUI Live Weather (`MIUI_WEATHER_BRIDGE`)**: Render weather condition graphics from the Widget Theme's `weather` / `clock_2x4` / `fancy_icons/com.miui.weather2` assets (`qing.png`, `yun.png`, `yu.png`, `xue.png`, `yin.png`, `wu.png`, `mai.png`), temperature digit sprites (`tem_*.png` / `w_*.png`), custom theme font, and theme background/accent palette.
  - **MIUI Calendar & Agenda (`MIUI_CALENDAR_BRIDGE`)**: Display the Widget Theme's live MAML Calendar emblem (`calendar_icon_<day>.png` or `bg.png` + `d_<day>.png`), custom font, and theme accent styling.
  - **System Status (`DEVICE_TELEMETRY`) & Storage Status (`STORAGE_AND_PACKS`)**: Apply the Widget Theme's `com.miui.home` gadget graphics (`gadget_clear_button_bg.png`, `gadget_clear_button_circle.png`, `gadget_power_clear_fore_normal.png` when present), custom font, and theme accent/plate colors.
  - **Clock & Calendar Shortcuts (`MAML_LIVE_DUO`)**: Render live MAML Clock and Calendar icons styled by the Widget Theme.
  - **Workspace Folders (`HyperFolderCard`)**: When `enableMtzWidgetStyling` is enabled and the active theme provides `icon_folder.png` (and optional `folder_icon_cover.png`), render the theme's custom folder container plate behind the folder's app grid.
- **Global Widget Theme Selector in Theme Studio**:
  - Add a **Widget Theme Source** selector card in **MIUI .MTZ Themes (Theme Studio)** with chips/options for:
    - `Follow Active Icon Theme (Default)`
    - Any installed/imported `.mtz` theme package (e.g., use icons from *Dark Fate* while rendering widgets from *theme2*, *I.C.H.O.L.S*, or *B22 N Theme*).

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Unified MAML `<Time>` and `<ImageNumber>` Sprite Pipeline**
  - *Chosen Approach*: Treat `<Time>` as a first-class MAML sprite sequence node that resolves either formatted time (`HH:mm` / `hh:mm` with `_0.._9.png` and `_dot.png` / `_colon.png` separator) or custom date/time patterns (`format="d"`, `format="M"`, `format="H"`, `format="m"`), and pre-computes `#<name>.bmp_width` and `#<name>.bmp_height` before parent `<Group>` evaluation.
  - *Why*: Directly matches MIUI's native `MamlClock` implementation and fixes both missing time digits (`02 36`) and missing date numbers (`11`) in Dark Fate and B22 N Theme.
- **Decision 2: Global Widget Theme Selection in Theme Studio**
  - *Chosen Approach*: Persist `widgetThemeId: String = ""` in `LauncherEngineConfig` and `EngineSettingsEntity` (where blank means "Follow Active Icon Theme"). Resolve `activeWidgetThemeMetadata` in the ViewModel and pass it to all Workspace widgets while keeping `activeThemeMetadata` for app icons.
  - *Why*: Gives a clean, single-tap way in Theme Studio to mix icons from one `.mtz` package and widgets from another without cluttering individual widget menus.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

```
┌────────────────────────────────────────────────────────────────────────────┐
│                        MTZ Archive Extraction Layer                        │
│  • Skip macOS AppleDouble (._*) & __MACOSX entries in all nested archives  │
│  • Extract icons, clock_2x4, clock_3x4, weather, com.miui.home, fonts      │
│  • Parse strings/strings.xml & com.miui.home gadget_clear_button_* assets  │
└───────────────────┬────────────────────────────────────┬───────────────────┘
                    │                                    │
                    ▼                                    ▼
┌──────────────────────────────────────┐  ┌──────────────────────────────────┐
│    Icon Theme Engine (Active ID)     │  │  Widget Theme Engine (Widget ID) │
│  • fancy_icons <Image srcid="#date"> │  │  • MAML <Time> & <ImageNumber>   │
│  • fancy_icons <ImageNumber> + <Var> │  │  • #<name>.bmp_width pre-measure │
│  • <IconTransform> & direct PNGs     │  │  • <Text format="..." paras="..">│
│  • icon_folder.png folder plate      │  │  • Weather/Calendar/Status mods  │
└───────────────────┬──────────────────┘  └──────────────┬───────────────────┘
                    │                                    │
                    └─────────────────┬──────────────────┘
                                      ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                   MIUI Workspace & Theme Studio UI Layer                   │
│  • Theme Studio: Global "Widget Theme" selector (Follow Icon Theme or MTZ) │
│  • Workspace Widgets: Render using activeWidgetThemeMetadata               │
│  • Workspace Icons & Folders: Render using activeThemeMetadata             │
└────────────────────────────────────────────────────────────────────────────┘
```

### Interactive Component & State Mapping
- **Theme Studio (`MtzStudioScreen`)**: Displays the active **Icon Theme** alongside a **Global Widget Theme** selector row. Selecting `"Follow Icon Theme"` sets `widgetThemeId = ""`; selecting any `.mtz` theme sets `widgetThemeId = theme.id` and immediately updates all workspace widgets.
- **MAML Clock & Widget Renderer (`MamlClockWidgetRenderer`)**: Evaluates `<Time>`, `<ImageNumber>`, `<Image srcid="...">`, `<Text format="..." paras="...">`, and `strings/strings.xml` variables; computes tight visual bounds for `#w/2`-centered layouts so Dark Fate's `02 36` and `THU - 11 JUN` fill the `4x2` widget cleanly.
- **Fancy Icon Renderer (`XiaomiIconRenderEngine`)**: Evaluates `srcid` (`calendar_icon.png` + `#date` -> `calendar_icon_26.png`), `<ImageNumber>`, `<Var>`, and `visibility` expressions so Dark Fate's and `theme2.mtz`'s live Calendar, Clock, and Weather icons render with zero blank states.
