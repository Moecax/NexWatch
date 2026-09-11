# Design Prompt: GTR 3 Pro Companion App

**How to use:** Paste **Part A** at the start of every generation session. Then paste **one batch from Part B** at a time. Most design tools produce much better results with 5–8 screens per request than with 40 at once. After each batch, carry forward anything you liked ("keep the card style and chart style from the previous batch").

---

## PART A — Master context (paste every time)

You are designing a native Android app (Jetpack Compose, Material 3) that replaces a smartwatch vendor's companion app. It pairs over Bluetooth with a round AMOLED smartwatch (1.43", 466×466 px), then syncs health data, forwards phone notifications and calls, and controls the watch's settings. It is a personal, privacy-first app. All data stays on the phone, it can be exported at any time, and sync to external services such as Health Connect is optional.

**Personality:** premium, calm, precise. Think a high-end instrument panel, not a gamified fitness app. Confident use of dark space, crisp numbers, restrained glow. The data is the hero.

**Canvas:** Android phone, 412 × 915 dp, edge-to-edge with a transparent status bar and gesture navigation. Use an 8 dp spacing grid, 16–20 dp screen margins, and touch targets of at least 48 dp.

**Theme:** dark only.

| Role | Hex | Usage rules |
|---|---|---|
| Primary (Electric Blue) | `#168CFF` | Main actions, selected states, key highlights. Text ON primary must be `#050816` (never white). |
| Primary Dark (Deep Blue) | `#1554D1` | Fills and gradient stops only. Never text or icons on dark backgrounds. Can hold white text. |
| Primary Light (Sky Blue) | `#28B8FF` | Gradient stop, links, active chart lines |
| Secondary (Cyan) | `#16D9E3` | Secondary actions, toggles, info accents |
| Secondary Light (Aqua) | `#43F2C5` | Sparing highlight |
| Accent (Mint) | `#38E8A5` | Small moments of delight, badges |
| Background (Midnight) | `#050816` | Base |
| Background 2 (Deep Navy) | `#080D20` | Lower layers, sheets |
| Surface (Navy) | `#0D1428` | Cards |
| Surface Elevated (Blue Navy) | `#121B35` | Raised cards, dialogs, selected rows |
| Border (Muted Blue) | `#243252` | Decorative dividers only |
| Text Primary | `#F7FAFF` | Headlines, values |
| Text Secondary | `#A8B4CC` | Labels, body |
| Text Muted | `#687590` | Large or disabled text and captions only |
| Success | `#20D889` | Status only (e.g., "Synced") |
| Warning | `#FFB52E` | Status only |
| Error | `#FF4969` | Status only |
| Heart rate | `#FF4775` | Heart data only |
| Activity / steps | `#25D98B` | Activity data only |
| Sleep | `#7655E8` | Sleep charts and fills. Sleep text labels use `#9A82FF` |
| Calories | `#FF8A32` | Calorie data only |

**Premium background:** a vertical gradient `#050816 → #0D1428 → #111B38`, painted behind every main screen. Cards sit on top in Surface or Surface Elevated with 20–24 dp corner radius, a 1 dp border in `#243252` at low opacity, and no heavy drop shadows. Elevation comes from lighter surfaces and faint inner glow.

**Hard color rules:** Success and Activity are nearly the same green, and Error and Heart are nearly the same pink. Never let a health metric read as a status message, or the reverse. Status colors appear only in status chips, banners and icons. Health colors appear only on health data. Color is never the only signal: pair it with an icon, label or pattern.

**Typography:** a modern geometric or grotesque sans (e.g., Inter, Manrope or Plus Jakarta Sans). Use tabular numerals for all metrics. Big metric values are 40–56 sp, semibold, tight tracking, with units in smaller Text Secondary beside the number ("72 bpm"). Section labels are 12–13 sp, medium, with slight letter-spacing.

**Iconography:** rounded outline icons in the Material Symbols Rounded style, 24 dp, 1.5–2 dp stroke. Filled variants only for the selected state.

**Data visualisation:**
- Heart rate: a smooth line or range band in `#FF4775` with a soft gradient fill fading to transparent. Min/avg/max are marked, and resting HR is a dashed reference line.
- Steps: rounded vertical bars in `#25D98B`, with a goal line as a subtle dashed rule.
- Sleep: a hypnogram (stepped horizontal bands) with stages Awake / REM / Light / Deep, using tints of `#7655E8`, lightest for Awake and deepest for Deep, plus a stage legend.
- SpO2: dots or a thin line in Cyan.
- Rings: an activity-style ring cluster for steps (green), calories (orange) and active minutes (blue).
- Gridlines are very faint (`#243252` at ~40%). Axis labels use Text Muted. Show selected-point tooltips as small elevated pills.

**Motion hints** (describe or annotate where relevant): gentle pulsing on the "connected" indicator and the live heart-rate readout, ring fill animation on load, and shimmer skeletons while loading.

**Watch depiction:** a generic round smartwatch with a thin polished steel bezel, two side buttons and a black silicone strap. **No brand names, no logos, no real-world watch designs.** The watch screen shows a simple invented watchface.

**Sample data** (keep it consistent across screens): user "Des". Today: 8,432 steps of a 10,000 goal, 6.1 km, 412 kcal, 38 active minutes. Heart rate 72 bpm now, resting 61, today's range 54–138. Last night's sleep 7 h 12 m (Deep 1 h 25 m, REM 1 h 38 m, Light 3 h 51 m, Awake 18 m), score 84. SpO2 97%. Watch battery 72%. Last sync 4 minutes ago. Time format 24 h, metric units.

**Navigation:** bottom navigation with 4 destinations: **Today**, **Health**, **Watch**, **Data**. Settings is reached from the top-right of Today. Detail screens use a large collapsing top app bar with a back arrow.

**For every screen, deliver:** the default populated state, plus any state listed for that screen (empty, loading, error, disconnected). Label each frame clearly, e.g. "Health / Heart Rate / Week / Populated".

**Avoid:** light mode; neumorphism; glassmorphism overload; neon glow on everything; cartoon mascots; stock-photo people; generic "fitness app" gradients like purple-to-pink; cluttered dashboards; tiny low-contrast gray text; any trademarked logos or product imagery.

---

## PART B — Screen batches (paste one per request)

### Batch 1 — Onboarding and pairing

Design these screens in sequence, as one flow:

1. **Welcome.** A large hero render of the watch floating on the premium background with a soft blue rim light. App name placeholder "Pulse Companion" (a placeholder name; keep it easy to swap). One line: "Your watch. Your data. On your phone." Primary button "Get started".
2. **Your profile.** Explains that the watch uses this for calorie and distance calculations. Inputs: sex (segmented control), age, height (cm), weight (kg), each shown as a large-number stepper or wheel. Primary "Continue".
3. **Permissions checklist.** A vertical list of permission cards, each with an icon, title, one-line reason, and a status (Granted ✓ in Success, or a "Grant" button): Nearby devices (Bluetooth), Notifications, Notification access, Phone & contacts (for caller ID and rejecting calls from the watch), Location (for weather, optional). A progress indicator shows 3 of 5 granted.
4. **Find your watch.** A radar-style scanning animation with concentric rings in Primary. Discovered watches appear as list rows with the watch name, signal-strength bars, and last 4 characters of the address. Show states: *scanning with 1 result* and *nothing found after 30 s*, with tips such as "Make sure the watch isn't connected to another app" and "Keep it within 1 m".
5. **Pair confirmation (destructive warning).** Before first pairing: a warning card in Warning amber explains that pairing as a new user **clears the data currently stored on the watch**, with a checkbox "I understand" and buttons "Pair watch" and "Cancel". Calm but unmistakable.
6. **Pairing in progress.** The watch render with a progress ring and steps: Connecting → Authenticating → Reading watch features → First sync. Include a success end state: "Connected" with battery and firmware version, and button "Continue".
7. **Keep it running.** Explains that Android may stop the app in the background. Cards: "Allow unrestricted battery use" (button), "Enable Autostart" (with a manufacturer-specific hint, e.g. for Tecno/Infinix/Xiaomi), "Test background connection". Friendly, not alarming.

### Batch 2 — Today (home dashboard)

1. **Today — populated.** Top: greeting "Good morning, Des" and a settings icon. **Watch status card**: small watch render, "Connected" with a pulsing Success dot, battery 72%, "Synced 4 min ago", and a sync icon button. **Activity ring cluster** with steps, calories and active minutes, with values beside it. A **metric card grid** (2 columns): Heart rate (72 bpm, mini sparkline), Sleep (7 h 12 m, mini hypnogram strip, score 84), SpO2 (97%), Distance (6.1 km). **Quick actions row**: Find watch, Camera remote, Sync now, Weather push.
2. **Today — syncing.** Same screen with the status card showing a sync progress bar ("Syncing heart rate… 60%") and cards shimmering where data is updating.
3. **Today — disconnected.** Status card in a muted state: "Watch not nearby" (or "Bluetooth is off", with a "Turn on" button), last seen time. Data cards still show the last known values with a subtle "as of 08:12" note.
4. **Today — first day, empty.** Friendly empty states in each card ("Wear your watch to see your first heart-rate readings"). No sad illustrations.
5. **Error banner variant.** A top banner in Error red, with an icon: "Watch is paired with another app. Close the other app, then retry." Button "Retry".

### Batch 3 — Health overview and metric details

1. **Health — overview.** A list of large metric tiles, each with today's key value, a 7-day mini chart in its health color, and a chevron: Heart rate, Sleep, Activity, SpO2, Workouts. Add tiles for Stress, Temperature and Blood pressure in a "Supported by your watch" style that can hide unsupported metrics.
2. **Heart rate detail.** A Day / Week / Month segmented control. Day view: a smooth HR line with range band, min/avg/max chips, a resting HR dashed line, and a selected-point tooltip ("14:32 · 96 bpm"). Below: HR zone breakdown bars (Rest / Fat burn / Cardio / Peak) and a list of recent readings. A **"Measure now"** button in Primary.
3. **Live heart-rate measurement.** Full-screen focus mode: a large pulsing number (bpm) in Heart pink, a live-scrolling waveform line, "Keep your wrist still", and a Stop button. Show a state that is still measuring (placeholder "--") and a finished state with the result and "Save".
4. **Sleep detail.** Night selector (arrows around "Wed, 10 Sep"), a big total duration and score ring, a hypnogram from 23:05 to 06:17 with the stage legend, stage breakdown cards (duration + percent), and a week view of stacked stage bars.
5. **Activity detail.** A steps bar chart for the day (hourly) with a goal line, totals for steps, distance, calories and active minutes, and a Week view with daily bars and a goal-streak indicator.
6. **SpO2 detail.** Day view dots, average and lowest value, and a gentle note area explaining what the metric means. No medical claims.

### Batch 4 — Workouts

1. **Workouts list.** Grouped by week. Each row has a sport icon, name (Outdoor run, Cycling, Walk), date/time, duration, distance, and average HR. Show an empty state.
2. **Workout detail.** A hero route map (dark-styled map, route line with a Primary→Sky Blue gradient, start and end markers) or, for indoor workouts, a large sport icon in its place. Stat grid: duration, distance, pace, average/max HR, calories, steps. An HR-over-time chart with zone bands and a splits table. An overflow menu with "Export GPX".

### Batch 5 — Watch tab (device control)

1. **Watch — overview.** A large watch render with its current watchface. Rows show battery, firmware version and connection state. Primary actions: Find watch (rings the watch), Sync now. Then grouped settings entry rows, each with an icon, title and current value: Watchfaces, Display & wrist raise, Health monitoring, Reminders, Alarms, Do not disturb, Units & time, Contacts, Weather, Camera remote. Then Firmware update. At the bottom, in Error color: "Unpair watch".
2. **Watchface gallery.** A grid of round watchface previews, with "Current" marked, and a large "Create from photo" card first.
3. **Custom watchface editor.** A circular preview at the top (466×466 aspect). Controls: pick photo, crop circle, time position (top/center/bottom), text color swatches, and complication toggles (steps, HR, battery). Button "Send to watch", with a transfer-progress state ("Uploading… 42%").
4. **Health monitoring settings.** Toggles and interval pickers for continuous heart rate, SpO2 monitoring and heart-rate alerts (with high/low thresholds). Show a small note: "More frequent monitoring uses more watch battery."
5. **Alarms.** A list of alarms (large time, repeat days as small chips, toggle), a FAB "Add alarm", and an alarm editor sheet with a time wheel, day chips and a label.
6. **Reminders & Do Not Disturb.** Sedentary reminder (interval, active hours), drink water reminder, and DND schedule (start/end time). Use cards with time-range pickers.
7. **Display settings.** Brightness slider, screen timeout, raise-to-wake toggle, always-on display toggle, with a caption noting the battery trade-off.
8. **Firmware update.** Current vs available version, a changelog card, and preconditions as a checklist (watch battery above 50%, phone charging recommended, keep the phone near the watch). A big progress ring during the update, with a "Do not close the app" warning. Show success and failure states.
9. **Unpair confirmation dialog.** Options: "Unpair and keep watch data" or "Unpair and clear watch data", with a clear explanation and a destructive button styled in Error.

### Batch 6 — Notifications and calls

1. **Notifications — main.** A master toggle card, "Forward notifications to watch", with a status line ("42 forwarded today"). A "Calls" card with toggles: incoming call alerts, reject calls from watch. Smart options: "Don't forward while I'm using my phone", "Group bursts from the same app". Then the **app list** section header with a search field.
2. **App allowlist.** A searchable list of installed apps (generic placeholder app icons, invented names such as "Chat", "Mail", "Messages", "Bank", "News"). Each row has a toggle. A "Suggested" group at the top (messaging apps).
3. **Forwarding activity log.** A reverse-chronological list: app icon, title, time, and a status tag (Sent ✓, Skipped – duplicate, Skipped – watch disconnected). Filter chips: All / Sent / Skipped.
4. **Notification access missing.** Empty or error state explaining that forwarding needs notification access, with a "Grant access" button.

### Batch 7 — Data tab (storage, export, sync)

1. **Data — overview.** A storage card: database size, number of records, "Data since 1 Jun 2026", and a small stacked bar by data type (heart, activity, sleep, workouts in their colors). Sections: Export, Import, Auto-backup, Connected services.
2. **Export.** Choose a date range (preset chips: Last 7 days, Last 30 days, All time, Custom), format (Full backup ZIP — recommended; CSV for spreadsheets; GPX for workouts), and a data-type checklist. A privacy note: "Exports contain your health data. Store them somewhere safe." Primary "Export".
3. **Export in progress / done.** A progress card with per-type progress lines. The done state shows the file name, size, record counts, and buttons "Share" and "Open folder".
4. **Import / restore.** A file picker card. After selection, a preview: date range, record counts, and "Duplicates will be skipped automatically". Button "Import".
5. **Auto-backup.** A toggle, chosen folder, frequency (Weekly), "Keep last 4 backups", the last backup time, and a note: "Runs while charging."
6. **Connected services.** A list of sync providers as cards: **Health Connect** (status "Up to date · 2 min ago", toggle) and a dashed "Add service" card (disabled, "More services coming"). The Health Connect detail shows data types being shared (checklist), the last push, and backfill progress ("Sending history… 12,400 of 48,213 records"). Include an error state card: "Permission revoked — Reconnect".

### Batch 8 — System surfaces and utilities

1. **Foreground service notification** (Android notification shade mockup). A small app icon, "Watch connected · 72%", a secondary line "Last sync 08:42", and actions "Sync now" and "Find watch". Also a collapsed variant. Keep it minimal and low-key.
2. **Find phone alert.** A full-screen incoming alert triggered by the watch: a large pulsing phone icon, "Your watch is looking for this phone", and a big "I found it" button.
3. **Camera remote.** A full-bleed camera viewfinder placeholder, a shutter button, a flip-camera button, and a small "Controlled by watch" indicator showing the watch can trigger the shutter.
4. **Settings (app).** Profile (edit sex/age/height/weight), Keep-alive checkup (status of battery optimisation, autostart, background test), Theme (dark only, disabled), About, and Diagnostics.
5. **Diagnostics.** Connection timeline (a vertical timeline of events: Connected, Disconnected – out of range, Reconnected, Sync completed 1,204 records), key stats (uptime today, last notification forwarded, reconnects today), and buttons "Export logs" and "Run background test". This can be denser and more technical than other screens, using monospace for timestamps.

### Batch 9 — Component sheet (optional, do last)

Produce a single component library frame containing: buttons (primary, secondary, tonal, text, destructive, each with disabled state), a metric card (small, large), chart card, status chip set (Connected, Syncing, Disconnected, Error, Up to date), list rows (with toggle, value, chevron), segmented control, text fields (default, focused, error), bottom navigation (each tab selected), top app bars (small, large collapsing), dialogs, bottom sheet, banner (info, warning, error), snackbar, empty-state block, skeleton loaders, and the color and type scale with labels.
