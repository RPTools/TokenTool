# Changelog

All notable changes to the TokenTool application are documented in this file. This project follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/) format.

---

## [2.2.0] — 2026-05-26

### Added
- **Vitest Frontend Testing:** Integrated a comprehensive frontend test framework with 8 unit and integration tests verifying path utilities and core data converters.
- **GitHub Actions CI/CD Pipeline:** Integrated automated workflow pipeline builds to verify npm testing, cargo builds, and code linting on every pull request.
- **Developer DX Linting:** Configured full ESLint 9 (Flat Config) + Prettier tooling ensuring automated code formatting and style validation across Svelte, TypeScript, and CSS.
- **Tauri 2 Dialog, FS & Native Drag Plugins:** Registered official plugins for file selection, system writes, and drag-and-drop operations to meet modern Tauri 2 standards.

### Changed
- **Tauri & Svelte Refactor Rewrite:** Completely migrated the legacy JavaFX-era codebase into a premium, state-of-the-art Tauri desktop application powered by Svelte, TypeScript, and a high-performance Rust PDF extraction backend.
- **Unified Custom Overlay Engine:** Extracted and consolidated duplicate overlay importing systems inside `App.svelte` via a robust `commitCustomOverlay` helper, squashing a coordinate pointer bug.
- **Direct Canvas Redraw Optimization:** Extracted duplicated portrait transformations inside `TokenCanvas.svelte` into a unified `drawPortrait` helper.
- **Memory Leak Protections:** Implemented robust Svelte `onDestroy()` hooks across the frontend to safely revoke dynamic `blob:` Object URLs and nullify active canvas rendering context references.
- **GC Performance Canvas Recycling:** Redesigned composite clipping inside the canvas to recycle a single pre-allocated offscreen composite canvas rather than instantiating DOM canvas nodes on every frame draw.
- **Structured Rust Backend Logging:** Replaced obsolete standard output prints with the official `log` and `env_logger` crates, routing module diagnostics dynamically with level filtering support.
- **Consolidated Backend Color Space Processing:** Consolidated duplicate color space mapping in `pdf_extractor.rs` into a single high-performance tuple evaluation.
- **Modular PDF Reference Resolution:** Consolidated duplicate PDF dictionary lookup implementations in `pdf_extractor.rs` into reusable `resolve_to_dict` and `resolve_resources` helpers with lifetime specifications.
- **Os-Agnostic Path Resolvers:** Replaced fragile path splitting heuristics with standard `getBasename` path parsing to support Windows, macOS, and Linux out-of-the-box.

### Fixed
- **Infinite Reactive Loop:** Split TokenCanvas coordinate mutations from image URL load events, breaking an infinite rendering loop condition during pan/zoom.
- **CSS Utility Typography Typo:** Fixed broken `.tracking-wide` and `.tracking-wider` style classifications in `global.css` to leverage standard `letter-spacing` properties.
- **Stale Dependabot Tracking:** Replaced deprecated Gradle configurations inside `.github/dependabot.yml` with active `npm` and `cargo` weekly dependency tracking.
