# Follow Mode — Specification

Specification for the ship-following view on `/chart`, agreed 2026-09-09 and implemented over the
nine stages listed at the end.

Picking one ship on the ship list switches the map from the normal view (north-up, whole chart)
to following that ship: the camera keeps it in frame, the chart is oriented so that the ship's
course — or its heading, on a switch — points at the top of the screen, and the zoom is set
automatically.

Files this will touch:

| File | Role |
|---|---|
| `src/main/resources/static/js/chart-script.js` | camera, mode state, toggles, watchdog |
| `src/main/resources/templates/chart.html` | row toggles, chip, compass badge |
| `src/main/resources/static/css/chart.css` | toggle and active-row styling, chip, compass |
| `src/test/java/.../ChartShipListTest.java` | markup assertions for the new elements |
| `Resources`, `MapController`, `application.properties` | the two settings below, read at startup |
| `docs/chart-map-view.md` | cross-reference once implemented |

## Interaction

| Gesture | Effect |
|---|---|
| Crosshair toggle in a ship row | engages the mode for that ship; pressing it again leaves |
| Another ship's toggle | moves the mode in one click |
| Compass badge | leaves the mode (removes the rotation in place) |
| Chip over the map, bottom left | same as the toggle, at every window width |
| ⟲ | inside the mode: optimal zoom and ship back to centre |
| Double click / double tap | leaves the mode, *then* fits the whole chart — in that order, or the fit would hit the mode's 0.9 floor. The one gesture that does not animate: unwinding the rotation only to throw the view away would be motion for nothing |
| Wheel / pinch / `+` `−` | zoom, anchored on the ship, does not interrupt the mode |
| Drag | moves the anchor — the ship stays followed at a different screen position |
| Click on a ship on the map | unchanged: tooltip |
| Tab + Enter | works, the toggle is an ordinary `<button>`; there is no `Esc` shortcut |

The explicit `window.followShip` / `window.unfollowShip` exports, which existed while the camera was
built ahead of its UI, are gone. That is cosmetic and worth saying so: `chart-script.js` is a
classic script, so *every* top-level function in it is a property of `window` whether or not
anything assigns it. Genuinely closing that surface means wrapping the file in an IIFE or making it
a module — a change to how the whole page loads, not to this feature.

**Exactly one ship is followed, or none.** The mode lives in JS state (`followedShipId`); the DOM
only ever reflects it. Reading the mode back out of the markup is the trap that once froze the map
tooltip's speed after the list was restyled.

A ship that has **never reported** cannot be followed: its toggle carries `aria-disabled` and
becomes active by itself on the first message. `aria-disabled` rather than `disabled` so keyboard
users can find the control before that first message — the price is that it does *not* block clicks
or focus, so the handler must test the attribute itself.

A ship that fell silent *after* reporting stays followable: it has a last known position.

## Camera

- **Orientation:** two sources, switched by a button, dead band 1° and easing 300 ms either way.
  - **HDG** — the bow at the top, scene angle `-(heading + ANGLE_CORRECTION)`.
  - **COG** (default) — the direction of travel at the top, derived from the movement between the
    last two *reported* positions and expressed in map-frame degrees, so it takes no
    `ANGLE_CORRECTION`; the hull then visibly crabs by the drift angle, which is the point of
    offering it.

  Labelled HDG/COG rather than "head up"/"course up": plotter vendors disagree about whether
  "course up" means heading or course over ground, and IMO renamed the ship's own axis from Course
  to Heading to end exactly that confusion.

  **No averaging window** — the freshest value is fed in, and the dead band and easing downstream
  are what filter it. **Below `chart.ship.cog-min-speed-kn` (default 1.0)** a course over ground
  means nothing — at 1 kn a ship covers half a metre between messages, so 20 cm of position noise
  swings it by 20° — and the camera falls back to HDG, with the button saying so. A frozen
  course-up, fixed at the moment of engaging, was considered and rejected separately: this is a
  manoeuvring simulator, so a frozen course would rarely have anything useful at the top.
- **Position:** eased with the same ~300 ms as the rotation — for **every ship, in both views**,
  not only the followed one and not only inside the mode. A uniform delay is what keeps the
  distances and bearings between ships truthful; easing one ship alone would misplace it against
  the others by up to a full message of travel. The consequence is that `/chart` also changed
  outside follow mode, where the step it smooths is only about 4 px. The ship is pinned to the anchor, so
  every bit of apparent motion is the chart sliding underneath it, and messages arrive about once a
  second: at 10 kn (5.14 m/s, 2.407 px/m) and 8× zoom that is roughly **100 screen pixels per
  message**. Unsmoothed, the whole chart would lurch once a second — far more conspicuous than the
  rotation jitter the dead band exists for. In the normal view the same step is about 4 px, which is
  why this has never mattered before. Dead reckoning between messages was rejected: it would show a
  computed position rather than a received one, and it diverges worst during berthing and thruster
  work, exactly when precision matters most.
- **Zoom on engaging:** ship length = 25% of viewport height, i.e.
  `0.25 × stageHeight / (shipParams[0] × cfg.scale)`. That is 4.3× (Lady Marie on a phone) to 12.6×
  (Kołobrzeg on a tall screen).
- **`maxScale`** rises from a flat 3 to `max(3, largest optimum × 1.5)`, recomputed with the
  viewport. **The normal view inherits this ceiling** — follow needs it, and leaving the mode keeps
  your zoom, so capping the normal view would contradict that.
- **Lower bound while following:** stage scale 0.9, so silhouettes never fall back to LOD arrows.
  A wider view means leaving the mode.
- **Manual zoom is sticky:** it does not interrupt the mode and is not overwritten by later
  position messages.
- **Anchor:** centre of the viewport, movable by dragging, never closer to an edge than one ship
  length. The rule self-scales — the freedom to shift the ship grows as the silhouette shrinks — so
  it needs no second parameter. Because the margin is measured in ship lengths it grows as you zoom
  in, so an existing offset has to be re-clamped on every zoom change. The rule never becomes
  impossible to satisfy: at the ceiling of `optimum × 1.5` the ship spans 37.5% of the height, the
  two margins take 75%, and 25% of freedom remains.
- **Resize** re-baselines the camera (ship centred, optimal zoom), except for a manually set zoom.
  The anchor offset is dropped on a resize and on a ship change.
- **Transitions:** engaging 500 ms, ship change 400 ms, leaving 400 ms, ⟲ 400 ms. Every camera move
  is blended from the pose actually on screen — read off the layers, never inferred from whether a
  ship is being followed, because halfway through a leave the mode is already off while the chart
  still carries rotation.

Dragging moves the anchor rather than pausing the mode. A paused state was designed and dropped:
it would have added a third state to every path — zoom, resize, ship change, silence, each new
message — for roughly 40–60 lines, where the anchor offset costs about 15 and cannot put the ship
off screen at all.

## Chart

- Silhouettes, not LOD arrows. At stage scale ≥ 0.9 the existing LOD crossfade already yields hulls,
  so the ship drawing code needs no change.
- Other ships and their tracks are drawn normally: no dimming, no proximity logic, no highlight for
  the followed ship.
- **Line widths are in screen pixels, not map units** (`strokeScaleEnabled: false`): the track at
  2 px and the hull's outline at 1 px, unchanged at every zoom. As map-unit widths they were 0.9 px
  and 0.5 px across the whole chart — too faint to follow — and 24 px and 12 px at full zoom, where
  the outline began to dominate the silhouette it was meant to define. Screen-space line width is
  what Leaflet's `weight` and MapLibre's `line-width` both mean. The hull's SIZE still scales with
  the chart; only the line around it holds still. Verified that the flag leaves the hit graph
  untouched, so tap targets are still governed by `hitStrokeWidth` alone.
- A track's last vertex is the position its hull is **drawn** at, not the last one reported. The two
  differ only while a ship is easing into place, and without the substitution the line runs past the
  bow — 120 px of it at 12 kn and full zoom. The substitute always lies on the segment between the
  last two reported points, so nothing is invented, and the two coincide once the ship settles.
- **No new symbols on the canvas.** The chip and the compass are DOM elements outside it. This is
  what ruled out a radar-style presentation with range rings and a bearing scale.
- The tooltip stays fully usable. **The counter-rotation this section originally called for turned
  out to be unnecessary**: the tooltip layer is not one of the three the camera rotates, so its
  text was never in danger of standing on its head — a consequence of rotating layers rather than
  the stage. What did need fixing is the **anchor**: `shipPos × stageScale` inverts the stage
  transform alone, which stops describing the picture once the content layers carry a rotation.
  It is now taken from `konvaShipLayer.getAbsoluteTransform()` and converted into the tooltip
  layer's coordinates. The old expression happened to stay correct for the *followed* ship — that
  ship is the rotation pivot, and a rotation leaves its pivot alone — and was out by 262 px for a
  tooltip on any other ship at 5° of chart rotation.

## Indicators

**Chip** — bottom left, over the map. Two elements, not one: a **button** whose visible label is
`Stop following <ship>` — the verb is shown, not merely announced, so the accessible name and the
visible one agree — which leaves the mode at every window width, and **beside it, outside the
button**, a status element with the age of the last message. The label can outgrow a narrow phone,
so it ellipsises rather than pushing the status out of the pill. They are split deliberately. A button
whose content changes every few seconds has an accessible name that changes with it, which is the
very reason the whole row was not made into a button (see *Active row*); the age is a status, not an
action, so it does not belong inside the control. Putting a fixed `aria-label` on a combined chip
would have hidden the age from screen readers instead.

The age is coarse: it reads "live" until **3 s** of silence, then counts seconds, and after **60 s**
becomes a warning with the camera still holding the last known position. The 3 s grace exists
because messages arrive about once a second, so a live number would flicker between 0 and 1 for
ever and pull the eye to the one moment when nothing is wrong.
Neither element is an `aria-live` region — at about one message per second that would be an endless
stream of announcements, the same trap the packet counter already documents; only the crossing of
the 60 s threshold may be announced, as a state change.

The chip is a button at every window width on purpose: an element that is a button at one viewport
width and inert at another is a worse promise than one consistent button.

**Compass** — a round 40×40 badge in `.map-controls`, following the convention every mapping service
uses (checked against Google Maps and MapLibre/Mapbox): a two-tone needle with north accented and
south in the existing `--fb-led-off` grey, and a button whose meaning is "Reset map to north" —
here, leaving the mode. It is visible whenever the chart is rotated, which includes the whole of
the leaving animation and not merely while the mode is on. Deliberately **not** hidden at the
instant the rotation happens to pass through zero, the way a north-up map hides its compass: a
followed ship steering due north would make the control vanish under the user's finger. The letter **N** sits at the
needle's tip and rotates in *position* with it while the glyph itself stays upright; a letter that
rotates with the needle is upside down at heading 180°, which is exactly why Google and Apple drop
the letter altogether.

**Toggle tooltip** — on hover and on keyboard focus, above the control. Bootstrap's, not the
browser's: a native `title` is placed by the browser (under the cursor, to the right) and cannot be
asked to sit above the button, and it never answers focus. Three states, because the third is the
one worth explaining: `Follow <ship>`, `Stop following <ship>`, and `<ship> has not reported yet`
while the toggle is unavailable. `data-bs-container="body"` is load-bearing — Bootstrap otherwise
inserts the tooltip inside the ship list, which on mobile is an `overflow-y: auto` sheet that would
clip it at the top edge, exactly where the first row sits. Bootstrap caches tooltip content, so a
state change has to call `setContent()`; moving the attribute alone leaves a followed ship still
offering to be followed.

**Active row** — filled crosshair, neutral background tint, 4 px left edge in the ship's colour.
The tint is deliberately *neutral* rather than the ship's colour: the palette includes `lightgray`
(Kołobrzeg), which at low alpha would be nearly invisible on a white list, so one ship of six would
get a much weaker signal than the rest. The row must be identifiable at a glance from across the
room, which a 16 px pressed icon is not.

The crosshair glyph was chosen over an eye or a pin: an eye means show/hide, and this mode hides
nothing; a pin means "a place" or "bookmark". A crosshair is what mapping software already uses for
"centre on this".

**Orientation button** — a fourth control in `.map-controls`, shown only while following. It
carries the mode as TEXT, because there is no honest picture of the difference between where the
bow points and where the ship goes. The label is the **effective** orientation, not merely the
selected one: under the speed gate it reads HDG even though COG is chosen, styled muted and italic
so a fallback never looks like a setting somebody made. The accessible name spells both out in
words, since HDG and COG would otherwise be read letter by letter.

**Mobile** — engaging the mode closes the ship list sheet, leaving the chip as the only visible
toggle. The sheet covers the map, so the moment it has been used to pick a ship it is in the way of
the thing it just selected. Closing it is a no-op on desktop, where the list is a sidebar beside
the map rather than over it.

## Technical model

Rotate the **three content layers**, not the stage: `offset == position ==` the ship's map point,
`rotation = -(heading + ANGLE_CORRECTION)`. Scale and position stay on the stage, so every existing
reader of `konvaStage.scaleX()` — the LOD crossfade, hit areas, arrow sizing, the tooltip's
counter-scale — keeps working untouched. The tooltip layer is excluded from that set, because it has
to stay upright.

Setting `offset == position ==` the ship's point makes that point both the pivot and a fixed point
of the layer transform, so the stage still maps it to the anchor with the same arithmetic the
north-up view uses.

Leaving the mode is then free: setting rotation to 0 spins the chart about the ship, so the ship
stays exactly where it was on screen while the lake rotates back to north under it. Animate that
and re-clamp into the chart bounds once afterwards. That re-clamp is the one exception to "leaving
keeps your frame": if the anchor had been dragged past the edge of the chart, the view shifts
slightly as the north-up rule takes over again. It is small and happens once.

`clampStagePosition()` does not apply while following — the axis-aligned "the chart must cover the
viewport" rule does not describe a rotated chart. The anchor limit takes its place.

Undoing `ANGLE_CORRECTION` is what stands the hull upright, whatever the 9° originally meant; the
constant's semantics only matter for the compass, where north sits 9° clockwise of raster-up, so
the needle points at `-heading`.

`data.heading` is the model's own heading (it arrives beside `rudder`, `engine` and `bowThruster`,
and `HEADING_CORRECTION` is 0), not a course over ground — which is what makes head-up meaningful
during an in-place turn.

Verified numerically for all six ships across four headings: anchor error 0, bow exactly vertical,
on-screen length exactly 25% of viewport height.

## Stages

1. Camera with no UI — layer rotation, centring, optimal zoom, dynamic `maxScale`
2. Zoom limits — lower bound 0.9, stickiness, zoom anchored on the ship
3. Smoothing — rotation (1°, 300 ms) and position (300 ms), `rAF` loop only while either is still
   moving, **measure in Chrome**
4. Row toggles — `chart.html`, `chart.css`, exclusivity, `aria-disabled`, tint and edge
5. Chip and compass
6. Anchor dragging with the one-ship-length limit
7. Transitions — engaging, ship change, leaving, double click, ⟲
8. Silence watchdog, tooltip counter-rotation and anchor
9. Mobile, tests, documentation

## Verification

`ChartShipListTest` gains assertions for a toggle in every row with its attributes, and for the
presence of the chip and the compass. The `follow{id}` ids do not collide with the test that demands
set equality between `id="led…|rs_model…_no"` in the HTML and the `led:`/`rsField:` keys in
`modelsConfig` — as long as no such key joins `modelsConfig`.

Two settings are read once at page render and handed to the browser, so changing either needs a
restart: `chart.ship.position-smoothing-ms` (default 300, `0` restores the old teleporting hulls)
and `chart.ship.cog-min-speed-kn` (default 1.0). Both exist because how they will feel in practice
is not knowable from here.

**The camera logic will have no automated tests.** The project has no JS test infrastructure, and on
this machine only Chrome is drivable while `resize_window` reports success without changing the
viewport, so a genuine narrow-viewport pass cannot be done here. Verification is manual, in Chrome,
with `app.runs.test-enabled=true`, which drives all six ships without any serial hardware.

## Accepted consequences

At 6–12× the 2666×4000 raster is upscaled about eightfold, so the chart under the ship is soft; the
view spans roughly 40 m of lake. This now applies to the normal view too, since it inherits the
ceiling. No higher-resolution raster is planned.

Hulls are drawn at 2 px/m while positions are scaled at `mapa_x = 2.407` px/m, so silhouettes are
about 17% smaller than the chart's own scale, despite `docs/chart-map-view.md` claiming a faithful
ratio. Left untouched deliberately; the 25% rule is measured against what is actually drawn.

## Rejected alternatives

| Alternative | Why not |
|---|---|
| Course-up frozen at the moment of engaging | A manoeuvring simulator changes course constantly, so a frozen course would rarely have anything useful at the top |
| A three-way switch including north-up | North-up here means leaving the mode, so the third position would duplicate the compass and the row toggle |
| Averaging the derived course over a window | The user's call: the freshest value, with the dead band and easing doing the filtering. A 5 s window would have cost 2.5 s of lag |
| Radar/PPI presentation with range rings and bearing scale | Requires new symbols on the chart |
| Overview plus a picture-in-picture follow window | A second Konva stage with a second copy of the raster, two zoom levels and two sets of controls |
| Dimming the other ships | They are to be drawn normally |
| Dragging pauses the mode | A third state across every path, for three times the code of the anchor offset |
| Persisting the followed ship across a reload | No `localStorage`, URL-parameter or `keydown` precedent anywhere in this front end, and a remembered ship might be one that is not reporting after a restart |
| `Esc` to leave | Would be the only keyboard shortcut in an application with no other keyboard handling |
| Speed-adaptive zoom | Would fight the user's manual zoom |
| Dead reckoning between messages | Would draw a computed position rather than a received one, and diverges worst during berthing and thruster work |
| One chip element that is both the button and the age readout | A button's accessible name would then change every few seconds — the same objection that kept the ship row from becoming a button |
