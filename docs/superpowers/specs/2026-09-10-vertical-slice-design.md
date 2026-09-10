# Unblock — Vertical Slice Design

Date: 2026-09-10

## Goal

A better native-Android version of "Block Escape" (sliding-block/Rush Hour puzzle). This
spec covers the first vertical slice: a genuinely playable single level with real drag
interaction, not a mockup.

## Scope for this session

**In scope:**
- Gradle + Compose project scaffold, minSdk 24, target/compileSdk 35 (latest stable)
- Pure-Kotlin domain model: board, vehicles, legal-move logic, win condition
- BFS solver (ships with unit tests; not wired to any UI this session)
- JSON level format + a handful of hand-authored levels
- Compose game board: drag-to-slide, axis-constrained, snap-to-cell spring animation,
  blocked-move resistance/shake feedback
- Procedural wood-grain board + glossy vehicle rendering via Compose `Canvas` (gradients +
  noise) — no external texture image assets
- Undo stack, move counter, restart, win overlay

**Explicitly out of scope this session:**
- Hint UI (solver wired to a hint button)
- Daily challenge, endless mode
- Coins/ads/monetization
- Full 500-level pack (only a handful of starter levels)
- Settings screen
- Automated UI tests (no emulator/device available yet at time of writing)

## Stack

- Kotlin + Jetpack Compose (not Unity) — small APK, fast startup, flat-2D visuals are a
  good fit for Compose
- minSdk 24, target/compileSdk 35
- Single Gradle module (`app`) for now; split into `domain`/`solver`/`app` packages
  within it, promote to Gradle modules later only if the project outgrows one module
- Gradle version catalog (`libs.versions.toml`), Compose BOM
- kotlinx.serialization for level JSON

## Core domain model (`domain` package — pure Kotlin, no Android deps)

- `Vehicle(id: String, orientation: Orientation, length: Int, row: Int, col: Int, isPrimary: Boolean)`
- `Board(width: Int = 6, height: Int = 6, exitRow: Int = 2, vehicles: List<Vehicle>)`
- `Board.legalMoves(vehicleId: String): IntRange` — how far a vehicle can slide in each
  direction along its axis, bounded by grid edges and other vehicles
- `Board.move(vehicleId: String, delta: Int): Board` — immutable, returns new `Board`
- `Board.isSolved: Boolean` — true when the primary vehicle's leading edge reaches the
  exit column

No Android/Compose types leak into this package — it must be unit-testable with plain
JUnit, no instrumentation/emulator required.

## Solver (`solver` package)

BFS over reachable board states (state = tuple of vehicle positions, hashable/equatable)
to find shortest solution length. Pure function over `Board`. Ships with unit tests
against known Rush Hour puzzles with known optimal solution lengths. Not called from any
UI code this session — it exists and is correct, ready for the hint system to consume
next session.

## Level format

JSON, loaded from `assets/levels/*.json` via kotlinx.serialization:

```json
{
  "id": "level_001",
  "gridSize": 6,
  "exitRow": 2,
  "vehicles": [
    {"id": "primary", "orientation": "horizontal", "length": 2, "row": 2, "col": 1, "isPrimary": true},
    {"id": "v1", "orientation": "vertical", "length": 3, "row": 0, "col": 3, "isPrimary": false}
  ]
}
```

A handful of hand-authored levels ship this session, solvable and validated by hand
(solver validation of the level pack as a batch process is a later-session concern).

## UI (`ui` package)

- `GameBoardScreen`: Compose `Canvas`-drawn board and vehicles.
  - Board: layered gradients + procedural Canvas noise approximating wood grain. This is
    a first-pass procedural texture, not final art — flagged here explicitly so it isn't
    mistaken for a deliberate placeholder-forever choice. Real texture/image assets can
    replace the Canvas drawing later without changing the rendering architecture.
  - Vehicles: rounded-rect shapes with a radial-gradient highlight overlay for a glossy
    look, per vehicle color.
- Drag gesture handling: `pointerInput` + `detectDragGestures`, constrained to the
  vehicle's own axis (horizontal vehicles only respond to horizontal drag delta, etc.),
  live-follows the finger within the range returned by `legalMoves`, snaps to the nearest
  legal cell with a spring animation (`Animatable` + spring spec) on release. Dragging
  past the legal range shows resistance (rubber-band damping) and a small shake animation
  when released against the block.
- Move counter, undo stack (list of prior `Board` states, pop on undo), restart (reload
  initial level state), win overlay (shown when `Board.isSolved`).

## Testing

- JUnit unit tests for `Board`/`Vehicle` legal-move logic (edge cases: blocked by wall,
  blocked by other vehicle, vehicle already at edge)
- JUnit unit tests for the solver against known Rush Hour puzzles with known optimal
  solution lengths
- No instrumented/UI tests this session — no emulator or device was available in the dev
  environment at the time of writing; Android Studio + SDK + emulator setup is happening
  in parallel with this spec being written

## Environment notes (as of 2026-09-10)

- JDK 24 was the only JDK present on the machine; current stable AGP does not support
  JDK 24. Gradle for this project must be pinned to JDK 17 or 21, installed separately
  from the system default.
- No Android Studio, no Android SDK, no emulator/device were present. Android Studio
  (`Google.AndroidStudio` via winget) install was kicked off in parallel with this spec.
  SDK components (platform-tools, platform 35, build-tools, emulator, a system image)
  will be installed headlessly via `sdkmanager` pointed at `D:\Android\Sdk`, so the
  project can build and run from the command line without depending on the Studio GUI
  being opened.
- C: drive had only ~20GB free; D: had ~258GB free. All SDK/AVD/emulator data is
  redirected to D: for this reason.
