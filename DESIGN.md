---
name: Penombra
description: Screen darkness drawn as a waning moon, on an almanac night ground.
colors:
  night-ground: "#0b1022"
  night-surface: "#141b35"
  moon-body: "#171e3a"
  bone-ink: "#e3dcc8"
  moonlight: "#b9b3a1"
  slate: "#8d94aa"
  almanac-ochre: "#c59b4f"
  hairline-rule: "#e3dcc824"
  bone-outline: "#e3dcc840"
  bone-ripple: "#e3dcc822"
typography:
  display:
    fontFamily: "sans-serif (platform default)"
    fontSize: "30sp"
    fontWeight: 300
    letterSpacing: "-0.01em"
  headline:
    fontFamily: "sans-serif (platform default)"
    fontSize: "18sp"
    fontWeight: 500
  body:
    fontFamily: "sans-serif (platform default)"
    fontSize: "14sp"
    fontWeight: 400
    lineHeight: "1 + 2dp extra"
  body-strong:
    fontFamily: "sans-serif (platform default)"
    fontSize: "14sp"
    fontWeight: 500
    fontFeature: "tnum"
  label:
    fontFamily: "sans-serif (platform default)"
    fontSize: "15sp"
    fontWeight: 500
    fontFeature: "tnum"
  control:
    fontFamily: "sans-serif (platform default)"
    fontSize: "15sp"
    fontWeight: 400
    fontFeature: "tnum"
  caption:
    fontFamily: "sans-serif (platform default)"
    fontSize: "13sp"
    fontWeight: 400
rounded:
  well: "8px"
  tile: "16px"
  pill: "26px"
spacing:
  hair: "4px"
  tight: "8px"
  row: "14px"
  inset: "16px"
  gutter: "20px"
  section: "24px"
  block: "28px"
components:
  preset-tile:
    backgroundColor: "{colors.night-ground}"
    textColor: "{colors.bone-ink}"
    typography: "{typography.caption}"
    rounded: "{rounded.tile}"
    padding: "16px 4px 14px"
    height: "80px"
  preset-tile-selected:
    backgroundColor: "{colors.night-ground}"
    textColor: "{colors.almanac-ochre}"
    typography: "{typography.caption}"
    rounded: "{rounded.tile}"
  pill-button:
    backgroundColor: "{colors.night-ground}"
    textColor: "{colors.bone-ink}"
    typography: "{typography.control}"
    rounded: "{rounded.pill}"
    padding: "0 16px"
    height: "52px"
  switch-on:
    backgroundColor: "{colors.almanac-ochre}"
    textColor: "{colors.night-ground}"
    rounded: "16px"
    width: "52px"
    height: "32px"
  switch-off:
    backgroundColor: "{colors.night-ground}"
    textColor: "{colors.slate}"
    rounded: "16px"
    width: "52px"
    height: "32px"
  slider-live:
    backgroundColor: "{colors.bone-outline}"
    textColor: "{colors.almanac-ochre}"
    typography: "{typography.label}"
    height: "48px"
  slider-idle:
    backgroundColor: "{colors.bone-outline}"
    textColor: "{colors.slate}"
    typography: "{typography.label}"
    height: "48px"
  section-header:
    textColor: "{colors.bone-ink}"
    typography: "{typography.headline}"
    padding: "14px 0 0"
    height: "64px"
  permission-card:
    backgroundColor: "{colors.night-surface}"
    textColor: "{colors.bone-ink}"
    rounded: "{rounded.tile}"
    padding: "16px 16px 4px"
---

# Design System: Penombra

## Overview

**Creative North Star: "The Night Almanac"**

Penombra treats how dark the screen is as a lunar phase. A large moon, drawn live on Canvas, wanes toward new moon as the native dim deepens; the extra black veil arrives as an eclipse disc sliding across it. Everything around the moon is a page from a Barbanera almanac read at night: indigo ground, bone ink, slate marginalia, and one warm ochre that only ever marks what is switched on.

The system is dark-first by construction, not by theme toggle. No surface is lighter than the moon's own disc, the moonlight itself is held under the ink colour so the signature never glares in a dark room, and there is no entrance animation. Density is low and thumb-sized: one column, generous 20dp side gutters, rows at 48 to 64dp, controls stacked under the moon in reach of one thumb.

Form is quiet and drawn: hairline rules at one device pixel, outlined tiles and pills on the same ground colour, Material 3 switch geometry painted directly. The only marks in the app are moon phases; there are no icons.

**Key Characteristics:**
- Moon as the readout: phase follows Luce ridotta, eclipse disc follows Eclissi, vertical drag on the moon sets the level.
- Indigo night ground with bone ink; slate for everything secondary or idle.
- Ochre as the single live signal (switch track, live slider, selected preset, eclipse rim, scheduled next event, notification).
- Flat: depth by tone and hairline, never by elevation.
- Motion only on state change, short and decelerating, and off when the system animator scale is 0.

## Colors

A cold indigo night with warm bone and one almanac gold, all hues kept low in chroma so nothing flares.

### Primary
- **Almanac Ochre** (almanac-ochre): the live signal. Switch track when on, slider progress, thumb and value when its layer is running, selected preset outline, label and glyph, the eclipse disc's rim on the moon, the schedule's next-event line when the schedule is on, the overlay notification colour, and the text selection highlight (at 40% alpha).

### Neutral
- **Night Ground** (night-ground): window, splash, status-bar scrim, adaptive-icon background, and the fill of every outlined tile and pill. Also the eclipse disc's colour on the moon and the switch thumb when on.
- **Night Surface** (night-surface): the one raised plane, used for the permission card and for dialogs (time picker theme).
- **Moon Body** (moon-body): the unlit part of the moon and preset glyphs; a hair lighter than the ground so the dark limb stays readable.
- **Bone Ink** (bone-ink): primary text, section titles, phase name, preset labels, the launcher crescent.
- **Moonlight** (moonlight): the lit part of the moon and idle glyphs. Deliberately dimmer than bone ink.
- **Slate** (slate): secondary text, summaries, idle slider and value, switch outline and thumb when off, theme's normal control colour.
- **Hairline Rule** (hairline-rule): one-device-pixel section dividers and unselected preset outlines.
- **Bone Outline** (bone-outline): pill outlines, slider track background, idle glyph rims.
- **Bone Ripple** (bone-ripple): press ripple on every pressable outline.

### Named Rules
**The Live Ochre Rule.** Ochre means "this is on right now". A control in its idle state is slate, never ochre; the same control turns ochre the moment its layer runs. Test: turn every layer off and no ochre should remain on screen.

**The Dim Moon Rule.** The moon's lit face is moonlight, never bone ink and never white. The brightest pixel on the screen is text, not illustration.

## Typography

**Display Font:** platform sans-serif (Typeface.SANS_SERIF; renders as the device's system sans)
**Body Font:** the same family
**Label/Mono Font:** platform monospace, only for the copyable ADB command

**Character:** One family carried by weight. A light 300 phase name sits over regular body and medium titles; numbers are tabular so percentages and times do not jitter while dragging.

### Hierarchy
- **Display** (300, 30sp, -0.01em): the phase name under the moon ("Falce calante"; "Luna in eclissi" when only the veil is on). Centered. One per screen.
- **Headline** (500, 18sp): section titles beside their switch (Luce ridotta, Eclissi, Programma).
- **Body** (400, 14sp, +2dp line spacing, +3dp in the permission card): section explanations and the moon summary line, always slate.
- **Body Strong** (500, 14sp, tnum): the schedule's next-event line and the permission card's copy action.
- **Label** (500, 15sp, tnum): slider percentage values, end-aligned in a 52dp column; also the Quick Settings pill.
- **Control** (400, 15sp, tnum): time pills ("Dalle 23:00").
- **Caption** (400, 13sp): preset labels, centered, up to two lines.

All text is in sp and follows the system font scale; font padding is turned off so rows align to their drawn boxes.

### Named Rules
**The Tabular Numbers Rule.** Any text that shows a changing number (percentages, times, the summary) sets `tnum`.

## Layout

A single scrolling column on the night ground, edge to edge under transparent system bars. Side gutters are 20dp plus the system-bar and cutout insets; the top pads 4dp under the status bar, the bottom 28dp above the navigation bar. A ground-coloured scrim fills the status-bar height, followed by a 20dp fade from ground to transparent, so scrolled content never runs into the clock.

Order is fixed by reach: moon (252dp tall, radius = half the shorter side minus 14dp) at the top, phase name and summary 8dp beneath, then 28dp to the four preset tiles (equal weights, 8dp gaps), a hairline 24dp below, then each section (header row, slider row) separated by hairlines 8dp after the previous slider. The schedule adds two equal 52dp time pills with an 8dp gap and the next-event line 12dp below; on Android 13+ a hairline and a full-width 52dp pill to add the Quick Settings tile close the column.

Spacing steps observed: 4, 8, 14, 16, 20, 24, 28dp. Touch targets are never under 48dp. Spacing and radius tokens are written in px; on device they are dp.

## Elevation & Depth

Flat. No container casts a shadow; depth is tonal (night surface above night ground, moon body above both) and drawn with hairlines. The only shadow in the world is a literal one: the eclipse disc, a ground-coloured circle at 55% to 95% alpha (scaled by Eclissi intensity) clipped to the moon and rimmed in 2px ochre, sliding in from the right.

### Named Rules
**The Only Shadow Is the Eclipse Rule.** Don't lift anything with elevation; if something needs to read as covered or darker, it is drawn as the eclipse.

## Shapes

Circles and soft rectangles. The moon and every glyph are circles with a one-pixel rim; the waning phase is a half-disc unioned with or cut by an ellipse, always lit on the left. Containers are outlined, not filled: tiles at 16dp corners, pills at 26dp (fully round at 52dp height), the code well at 8dp. Strokes are one device pixel (2dp only on the switch's off outline and the eclipse rim). The launcher, splash and Quick Settings tile all use the same waning crescent, lit on the left.

## Components

### Moon (signature)
The readout and the control. Moon body disc, moonlight lit area for the current phase (lit fraction = 1 - Luce ridotta level), 1px bone rim at 20% alpha. The eclipse disc slides over it when Eclissi runs. Vertical drag changes the level (down darkens, a full moon diameter equals 100%), the slider follows live, parent scroll is suspended during drag. Phase changes animate 180ms decelerate (factor 1.5); the eclipse slides 220ms decelerate (factor 2). Announced with a content description that names the phase and explains the drag.

### Preset Tiles
- **Style:** outlined 16dp tile on night ground, hairline-rule stroke, a 26dp moon glyph above a caption label; min height 80dp.
- **Selected:** stroke, label and glyph's lit area all ochre; content description adds "attivo".
- **Glyph:** the same phase drawing as the moon; the eclipse preset shows a full moon covered by a 92%-alpha ground disc offset right.

### Buttons (pills)
- **Shape:** fully round pill (26dp radius, 52dp tall).
- **Style:** night-ground fill, bone-outline stroke, bone text, bone-ripple press.
- **Done state:** the Quick Settings pill turns slate and disables once the tile is added.

### Switch
Material 3 geometry painted on Canvas, measured 60x48dp. On: 52x32dp ochre track, 24dp night-ground thumb at the right. Off: 2dp slate outline, 16dp slate thumb at the left. Track fill and outline cross-fade while the thumb slides and grows, 160ms decelerate. Reports itself as a Switch to accessibility services.

### Sliders
Framework SeekBar, 48dp tall, pulled 12dp left to align the track with the text edge, track background bone-outline. Progress, thumb and value label are ochre while their layer runs, slate when idle.

### Section Header
Min 64dp row, 14dp top padding: headline and slate body on the left (16dp end margin), switch on the right.

### Permission Card
Shown only when WRITE_SECURE_SETTINGS is missing, inside the Luce ridotta section, in place of its switch and slider (the section's body then explains the one-time computer step). Night-surface card with a hairline-rule outline, 16dp corners, 16dp inset, 16sp/500 bone title ("Come sbloccarla"), slate body, the ADB command (built from the package name) in 13sp monospace inside an 8dp well (bone at 8% alpha), a 48dp bone text action ("Copia comando", reading "Comando copiato" for 2s), and a slate note for realme/OPPO. No ochre: nothing in it is live.

### Option Row
A smaller row for an option that belongs to the section above (only "Also on the lock screen", under Luce ridotta, hidden in solo Eclissi): 16sp/500 bone title, slate body, switch on the right, no hairline above.

### Language Pill
Last item of the column on Android 13+: a full-width 52dp pill like the Quick Settings one, "Lingua: Sistema/Italiano/English", opening a single-choice dialog (PenombraAlert, night surface, ochre radio). Android's per-app language recreates the screen.

### Solo Eclissi
Without WRITE_SECURE_SETTINGS (the shareable build on a phone that never saw a computer) Eclissi is the only layer. The moon's drag, the four presets (as veil depths 35/55/75/100%), the Quick Settings tile and the schedule all drive the veil; in the quick panel the head switch and slider become the veil's and the separate Eclissi row is hidden. The Eclissi body says it works right away; under its slider a slate note explains that the status bar stays lit unless Penombra is enabled in Accessibility, with a 48dp bone "Apri Accessibilità" text action (hidden once the service is connected).

## Do's and Don'ts

### Do:
- **Do** keep every surface at or below night-surface in lightness; dialogs use the PenombraDialog theme on night surface.
- **Do** switch a control from slate to ochre only when its layer is actually running.
- **Do** draw new marks as moon phases with `MoonView.drawPhase`, lit on the left, moonlight on moon body.
- **Do** divide sections with one-device-pixel hairline rules, not cards.
- **Do** honour animator duration scale 0: every animation snaps when animators are disabled.
- **Do** set sp sizes and `tnum` on changing numbers, and keep touch targets at 48dp or more.

### Don't:
- **Don't** add elevation shadows to containers; the eclipse disc is the only shadow.
- **Don't** use ochre for idle state or decoration.
- **Don't** paint the moon's lit face in bone ink or white.
- **Don't** use pure black inside the app UI; #000 belongs to the Eclissi veil overlay alone.
- **Don't** introduce icon fonts or generic glyph icons; the app's only marks are drawn moons and the crescent.
- **Don't** add entrance animations or anything that flashes bright on launch.
