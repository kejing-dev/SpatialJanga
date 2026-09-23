<!-- pico-cli:plugin-context:pico-spatial-agentic-tools:start -->
## Plugin Context

Also read `./PICO-SPATIAL-AGENTIC-TOOLS.AGENTS.md` for PICO Spatial plugin guidance.
<!-- pico-cli:plugin-context:pico-spatial-agentic-tools:end -->

## Repository layout (SpatialJanga, published 2026-09-22)

GitHub repo `SpatialJanga`; product name 叠木之间 · Tower Wood; Android package `com.kejing.towerwood`; Gradle root project `TowerWood`.

- `app/` Android/Kotlin module (PICO Spatial SDK BOM 6.1.9, Spatial UI, Compose compiler, no Material).
- `art/` Blender sources, GLBs, original wood textures, palette GLBs, generator scripts (`generate_tower.py`, `themes/generate_themes.py`), previews and manifests. Runtime copies live in `app/src/main/assets/`.
- `docs/PRD.zh-CN.md` product requirements v0.1 plus the 2026-09-17 implementation addendum. `docs/DELIVERY-NOTES.zh-CN.md` original delivery notes. `docs/verification/` emulator verification report (`verification.json`), full Shared Space captures and panel renders. `docs/screenshots/` README images.
- `releases/` shipped debug APK (`SpatialJanga-v1.0.0-debug.apk`, sha256 in `docs/verification/verification.json`); the same file is attached to the GitHub release.
- `README.md` (English) and `README.zh-CN.md` (中文) must stay in sync.

## Spatial onboarding scaffold (2026-09-17)

This project was generated using `pico-cli project create --name TowerWood --package com.kejing.towerwood --template volumetric` for a shared-space volumetric app, then implemented under spatial-design-to-app.

- `Main.kt` preserves the generated `DefaultWindowContainer` → `TowerTheme` (PicoTheme) → `HomeVolume` entry chain.
- `platform/SpatialApplication.kt` and `platform/LaunchActivity.kt` are generated SDK entry points.
- `app/src/main/AndroidManifest.xml` owns the volumetric window configuration (0.7 × 0.6 × 0.4 m, WorldScale.Fixed, gravity aligned, no material background).
- SDK BOM is 6.1.9. All 2D UI must use public SpatialUI components with PicoTheme; Material/Material3 is forbidden.
- Build: `JAVA_HOME=/Users/kejing/.pico/primer-cli/jdk/jdk-21.0.12.1+1/Contents/Home ./gradlew assembleDebug`.
- APK: `app/build/outputs/apk/debug/app-debug.apk`; package `com.kejing.towerwood`, activity `.platform.LaunchActivity`.
- Installation, launch, screenshots and logs are owned by spatial-emulator-usage.

## Current game implementation

- This is a working product prototype, not an empty scaffold. `scene/TowerScene.kt` owns the 54-block scene, grab/release, turn snapshots, drop detection, undo and reset; `ui/game/GameScreen.kt` owns the SpatialUI controls in an `AttachmentPanel`.
- Restart is available during play, pause and result; undo restores a pre-grab scene snapshot and score, entering practice mode. Starting a new round clears undo history. UI omits the single-player/shared-space subtitle.
- Regression coverage: `RuntimePhysicsTest` exercises placement, dropped-block recovery, undo, restart and the theme picker on the emulator through scene handlers. This does not verify real hand tracking or UI ray hits.
- `WoodTheme.kt` defines four persistent palettes and preview images. `TowerScene.applyTheme` replaces visual children only. Target instrumentation with `ANDROID_SERIAL=emulator-5554` to exclude ordinary Android phone emulators.
- Physics is turn-based sleeping: resting blocks keep colliders without dynamic bodies; the held block follows a speed-limited handle; blocks above the active floor and the released block get dynamic rigid bodies; static again after scoring. No general swept collision for the held body yet.
