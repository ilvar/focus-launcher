# Tier 3 · The UI system

Compose **foundation + ui + animation only**. No Material: nothing may bring in colour, ripples or
icons. Everything is built from a handful of primitives.

## Palette and primitives
`ui/theme/Theme.kt`: `FocusColors(bg, fg, dim, faint, line)`; `BlackTheme` (#000/#FFF/8E8E8E/
565656/2A2A2A) and `WhiteTheme` (inverted). Locals: `LocalFocusColors`, `LocalFocusFont`
(Default / Serif / Monospace), `LocalTextScale`. `applyFocusWindow()` = edge-to-edge, window
background, optional hidden status bar, and asks for the panel's fastest refresh mode.
`ui/components/Basics.kt`: `T()` is the only text primitive (theme colour, font, scale);
`Label`, `Hairline`, `FocusSwitch` (drawn), `FocusButton` (filled = inverted, or outlined),
`SettingRow`, `ToggleRow`, `Modifier.press`, `Modifier.monochrome()` + `hasColourGlyphs()`.
`Dialogs.kt`: `FocusDialog` (bordered panel; every popup is one), `MenuRow`, `ChoiceDialog`,
`MultiChoiceDialog`, `ConfirmDialog`, `TextInputDialog(numeric)`, `UnderlinedField`,
`AppPickerDialog(leading=…)`.

## Touch feedback: it lights up (owner's requirement)
`PressIndication` (an `IndicationNodeFactory`, installed as `LocalIndication`) draws a rounded
rect of white at `0.17 × level` with **`BlendMode.Difference`** *behind* the content, so the same
code lightens black, darkens white and darkens the inverted primary button without knowing the
theme. Level animates 0→1 in 90 ms and back in 380 ms; on release it first lets the rise finish,
so the quickest tap is still visible. The glow is **exactly the element's bounds** (corner 12dp):
an earlier outset spilled over dialog borders and neighbouring buttons. Bare-text targets
therefore carry their own 12dp horizontal padding; the home column's side padding is 18dp so text
still sits 30dp from the edge. The animated value is read in the draw phase: no recomposition.

## Home layout that always fits (`HomeScreen`)
A home screen must never scroll or push the corner shortcuts off. `BoxWithConstraints` estimates
the height each arrangement needs from measured constants (dp, × text scale) and picks the
roomiest `Fit(ring, textSp, padDp, maxEvents, topApps)` that fits: detail is given up before the
clock is (fewer events, no app names under the bar, then a smaller ring, floor 132dp). Below
172dp the ring switches to compact text ("85% charging", smaller type) so the bottom line still
fits the chord of the circle. Ring arc = battery % (or day fraction), `Animatable` from 0, 900 ms.
Battery via sticky `ACTION_BATTERY_CHANGED`, registered only while STARTED.

## Motion (all finite; idle draws 0 frames)
- Pager pages: `graphicsLayer { alpha = 1 − 1.2·distance; scale = 1 − 0.05·distance }` with
  `CompositingStrategy.ModulateAlpha` (no full-screen off-screen buffer per frame).
- Settings pages: `AnimatedContent` on `(stack.size, route)`, slide 1/7 width + fade,
  direction-aware. Review tabs: `Crossfade`. Wall: rises 28dp while fading in, 420 ms.
- **App launch animation.** Since Android 13 the system **ignores `makeCustomAnimation`
  (resource animations) for task-level opens**, which is what a launcher opening an app is. Only
  the built-in `ANIM_SCALE_UP` / `ANIM_CLIP_REVEAL` types are honoured. Clip-reveal from the tapped
  row was tried first: it hides most of the new app for half the animation, launches *felt* slow
  (worst from the ring, at the top), and the owner complained. Now `launchOptions()` =
  `makeScaleUpAnimation` from a rect inset 3% (the app covers the screen from its first frame);
  setting `LaunchAnimation.SYSTEM` passes no options. Not yet judged by the owner.

## Gestures on the home background
`detectVerticalDragGestures` (down = notifications via the service, else reflection on
`StatusBarManager.expandNotificationsPanel`; up = drawer with search focused) and
`detectTapGestures` (long-press = settings; double-tap = lock, if enabled). Callbacks go through
`rememberUpdatedState` because the detectors outlive recompositions. Children with `clickable`
consume their own taps, so long-press on a fast app opens its menu, on empty space the settings.

## Drawer details
The search field is composed even while the home page shows (pager keeps both pages), so it takes
`focusProperties { canFocus = isActive }` and the window is `stateAlwaysHidden`: otherwise it
grabs initial focus and pops the keyboard. Search ranks: prefix, word prefix, contains, initials,
then a loose in-order match for ≥3 letters; labels are normalized once per list. The A–Z scrubber
consumes its own pointer events so the pager does not scroll.
