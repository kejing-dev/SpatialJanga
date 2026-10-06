<!-- pico-cli:plugin-context:pico-spatial-agentic-tools:start -->
## Plugin Context

Also read `./PICO-SPATIAL-AGENTIC-TOOLS.AGENTS.md` for PICO Spatial plugin guidance.
<!-- pico-cli:plugin-context:pico-spatial-agentic-tools:end -->

## Repository layout (SpatialJanga, published 2026-09-22)

GitHub repo `SpatialJanga`; product name 叠木之间 · Tower Wood; Android package `com.kejing.towerwood`; Gradle root project `TowerWood`.

- `app/` Android/Kotlin module (PICO Spatial SDK BOM 6.1.9, Spatial UI, Compose compiler, no Material).
- `art/` Blender sources, GLBs, original wood textures, palette GLBs, generator scripts (`generate_tower.py`, `themes/generate_themes.py`), previews and manifests. Runtime copies live in `app/src/main/assets/`.
- `docs/PRD.zh-CN.md` product requirements v0.1 plus the 2026-09-17 implementation addendum. `docs/DELIVERY-NOTES.zh-CN.md` original delivery notes. `docs/verification/` emulator verification report (`verification.json`), full Shared Space captures and panel renders. `docs/screenshots/` README images.
- `releases/` holds only the v1.0.0 debug APK (sha256 in `docs/verification/verification.json`); from v1.1.0 on, APKs are attached to GitHub Releases only (`gh release create vX.Y.Z app/build/outputs/apk/debug/app-debug.apk#SpatialJanga-vX.Y.Z-debug.apk`) to keep the repository small.
- `README.md` (English) and `README.zh-CN.md` (中文) must stay in sync.
- `docs/DESIGN-CHANGES-v1.1.zh-CN.md` (2026-09-23) is the v1.1 design spec; Figma page "03 · v1.1 变更" mirrors it and page 01 stays the v1.0 baseline. Implemented on 2026-09-24: `BLOCK_SCALE = 1.5` with the 0.8 × 0.9 × 0.5 m container, the collapsible icon HUD plus caption chip, and the low-lag held-block follow. Still open: fingertip-contact grabbing (gaze/ray targeting from `detectSpatialDragGesture` is still in use), the mid-air hover state and the landing zone.

## Spatial onboarding scaffold (2026-09-17)

This project was generated using `pico-cli project create --name TowerWood --package com.kejing.towerwood --template volumetric` for a shared-space volumetric app, then implemented under spatial-design-to-app.

- `Main.kt` preserves the generated `DefaultWindowContainer` → `TowerTheme` (PicoTheme) → `HomeVolume` entry chain.
- `platform/SpatialApplication.kt` and `platform/LaunchActivity.kt` are generated SDK entry points.
- `app/src/main/AndroidManifest.xml` owns the volumetric window configuration (0.8 × 0.9 × 0.5 m since v1.1, WorldScale.Fixed, gravity aligned, no material background).
- SDK BOM is 6.1.9. All 2D UI must use public SpatialUI components with PicoTheme; Material/Material3 is forbidden.
- Build: `JAVA_HOME=/Users/kejing/.pico/primer-cli/jdk/jdk-21.0.12.1+1/Contents/Home ./gradlew assembleDebug`.
- APK: `app/build/outputs/apk/debug/app-debug.apk`; package `com.kejing.towerwood`, activity `.platform.LaunchActivity`.
- Installation, launch, screenshots and logs are owned by spatial-emulator-usage.

## Current game implementation

- This is a working product prototype, not an empty scaffold. `scene/TowerScene.kt` owns the 54-block scene, grab/release, turn snapshots, drop detection, undo and reset; every length derives from `TowerScene.BLOCK_SCALE` / `BASE_Y` / `restY()` (companion), and snapshots carry `SNAPSHOT_TAG` so rounds saved at another scale are ignored instead of restored. `ui/game/GameScreen.kt` owns two `AttachmentPanel`s: `controls` (menu panel outside play at `MENU_POSITION`; during play the HUD at `HUD_POSITION` — one 56 dp `ic_hud` icon that expands to a 340 dp bar with pause/undo/restart/tune/help `IconButton`s and auto-collapses after 10 s) and `caption` (the state message chip that `TowerScene.attachCaption` keeps 7 cm above the tower top). Icons are stroke vector drawables in `res/drawable/ic_*.xml`.
- Restart is available during play, pause and result; undo restores a pre-grab scene snapshot and score, entering practice mode. Starting a new round clears undo history. UI omits the single-player/shared-space subtitle.
- Regression coverage: `RuntimePhysicsTest` exercises placement, dropped-block recovery, undo, restart and the theme picker on the emulator through scene handlers. This does not verify real hand tracking or UI ray hits.
- `WoodTheme.kt` defines four persistent palettes and preview images. `TowerScene.applyTheme` replaces visual children only. Target instrumentation with `ANDROID_SERIAL=emulator-5554` to exclude ordinary Android phone emulators.
- Physics is turn-based sleeping: resting blocks keep colliders without dynamic bodies; the held block follows the hand through an exponential filter (`FOLLOW_TAU` = 50 ms, 1.2 m/s cap) instead of the old 0.08 m/s crawl; blocks above the active floor and the released block get dynamic rigid bodies; static again after scoring. No general swept collision for the held body yet.
- `RuntimePhysicsTest` resolves `RuntimeSession.scene` through a getter and waits 3.5 s after `ready`: the instrumentation launch path rebuilds the SpatialView content once the container gains focus, which recreates `GameScreen` and its `TowerScene` (a plain launcher start loads the scene once).
