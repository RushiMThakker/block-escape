# Block Escape

A Rush Hour–style sliding-block puzzle game for Android. Slide vehicles out of the
way to clear a path for the red car to reach the exit — every level is solver-verified,
so there's always a solution.

Built with Kotlin + Jetpack Compose.

## Features

- 10 hand-picked levels with a real difficulty curve, each verified solvable by a
  BFS solver
- Level-select screen with local progress persistence (no account needed)
- "Best possible moves" shown live during play, so you know your target up front
- Unlimited free hints — no ads, no cost
- Undo / Restart
- Banner ads (Google AdMob)

## Tech stack

- Kotlin, Jetpack Compose (Material3), no XML layouts
- Pure-Kotlin puzzle domain model (`domain/`) with a from-scratch BFS solver (`solver/`)
- Procedural Canvas rendering for the board — no image assets for game art
- Local persistence via SharedPreferences (`progress/`)
- Min SDK 24, target/compile SDK 35

## Project layout

```
app/src/main/java/com/rushi/blockescape/
├── domain/     # Board, Vehicle, Orientation — pure game state + legal-move logic
├── solver/     # BFS solver, hint/best-move computation
├── level/      # Level loading, ordering, best-move caching
├── progress/   # Local save/load of cleared levels
├── ui/         # Compose screens (level-select, gameplay, HUD)
├── ads/        # AdMob config + banner view
└── haptics/    # Vibration feedback
app/src/main/assets/levels/   # Level definitions (JSON)
docs/                         # Privacy policy (hosted via GitHub Pages)
store-assets/                 # Play Store listing screenshots, icon, feature graphic
```

## Building

Open in Android Studio and run, or from the command line:

```bash
./gradlew assembleDebug
```

A signed release build requires a `keystore.properties` file at the repo root
(gitignored — not included) pointing at a real upload keystore.
