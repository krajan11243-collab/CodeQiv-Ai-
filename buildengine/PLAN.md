# Local Build Engine — Roadmap (Sketchware-Pro-style, Gradle-free)

## Status: Phase 1 scaffolded (this commit)

`buildengine/engine/` — new module, Gradle-free at *runtime* (it's plain
Kotlin/JVM code that itself still compiles via the IDE's own Gradle build —
that's unrelated to what it builds for the user's Android project).

Files added:
- `model/BuildModels.kt` — BuildError (file+line for editor click-to-line),
  BuildStage, BuildResult, BuildLogEvent, BuildConfig
- `tools/BuildToolsLocator.kt` — resolves bundled ecj/d8/aapt2/zipalign/
  apksigner/android.jar/kotlinc under `<filesDir>/build-tools/`
- `tools/ProcessRunner.kt` — cancellable subprocess runner w/ line streaming
- `classpath/ClasspathCollector.kt` — libs/*.jar + libs/*.aar → compile/dex
  classpath (AAR exploding with content-hash cache)
- `steps/EcjCompileStep.kt` — Java → .class, parses ECJ diagnostics
- `steps/KotlincCompileStep.kt` — Kotlin → .class (optional component)
- `steps/D8DexStep.kt` — .class + libs → classes.dex
- `steps/Aapt2Step.kt` — res/ + manifest → resources.apk (also emits R.java)
- `steps/ApkAssembleStep.kt` — merges dex+assets into the apk; ZipAlignStep;
  ApkSignStep (apksigner, debug keystore)
- `BuildManager.kt` — async orchestrator (coroutines/Flow), build/clean/
  rebuild/cancel, streams `BuildLogEvent`s stage-by-stage

This directly maps to requirement #3's pipeline and #7/#8 (async, cancel,
structured errors with file/line).

## Requirements still open

| # | Requirement | Status |
|---|---|---|
| 1 | Disable old Gradle flow, keep editor/explorer intact | Not started — old flow still active, by design (nothing breaks yet) |
| 2 | Local/offline engine, avoid Gradle daemon | Done for the engine itself |
| 4 | `build-tools/` dir layout | Defined in `BuildToolsLocator`; actual binaries (ecj.jar, d8.jar, aapt2, zipalign, apksigner.jar, android.jar, kotlin/) still need to be sourced/bundled — see "Bundling tools" below |
| 5 | Java + Kotlin + XML support | Java/Kotlin done; XML is handled implicitly via aapt2 (layouts/manifests) — XML *editing* already works (existing `xml:*` modules), untouched |
| 6 | Auto-collect deps | Done for local `libs/*.jar` + `libs/*.aar`. **Not done:** transitive Maven coordinate resolution (`implementation("com.squareup:okhttp3:...")` style) — needs a minimal resolver or a "vendor your jars" convention, Phase 2 |
| 9 | Don't break existing project format | Not yet validated — needs a real project run through the new engine |
| 10 | Modular, UI-independent | Done — `BuildManager` has zero UI/Android dependencies |
| 11 | Remove unused Gradle code safely | Not started — see "Removal list" below, deliberately deferred until Phase 3 is proven working, so nothing is deleted before its replacement works |
| 12 | No forced huge SDK download | Addressed by design (core tools ~few MB, Kotlin optional) but depends on how tools are bundled |
| 13 | Fully local, no source upload | Naturally true — no network code anywhere in this module |

## Phase 2 — Local project model (the part most likely to surprise you)

`core/projects/.../internal/WorkspaceModelBuilder.kt` currently builds the
IDE's understanding of "what are this project's modules/source-sets/
dependencies" **from the Gradle Tooling API model** (`IGradleProject`).
That same model feeds Java/Kotlin code completion and indexing — not just
building. Removing Gradle means this also needs a local replacement:
a parser that walks the project's folder layout (`src/main/java`,
`src/main/kotlin`, `src/main/res`, `libs/*.jar`) and produces the same
`ModuleProject`/`AndroidModule` shape the rest of the IDE already consumes.
This is the part I'd flag as the real critical path — get it wrong and
autocomplete/error-highlighting silently degrades even if the APK still
builds.

## Phase 3 — Wire BuildManager into the app

Files that call into Gradle today and need a `BuildManager`-based
replacement (found via `grep`, not yet touched):

- `core/app/src/main/java/com/tom/rv2ide/services/builder/GradleBuildService.kt`
- `core/app/src/main/java/com/tom/rv2ide/services/builder/GradleBuildServiceConnnection.kt`
- `core/app/src/main/java/com/tom/rv2ide/services/builder/GradleServiceBinder.kt`
- `core/app/src/main/java/com/tom/rv2ide/services/builder/ToolingServerRunner.kt`
- `core/app/src/main/java/com/tom/rv2ide/actions/BaseBuildAction.kt`
- `core/app/src/main/java/com/tom/rv2ide/actions/build/QuickRunWithCancellationAction.kt`
- `core/app/src/main/java/com/tom/rv2ide/handlers/EditorBuildEventListener.kt` — this is where BuildError→editor click-to-line (requirement #8) plugs in; `BuildLogEvent.Diagnostic` already carries exactly the shape it needs
- `core/app/src/main/java/com/tom/rv2ide/viewmodel/RunTasksViewModel.kt` + `RunTasksDialogFragment.kt` + `RunTasksListAdapter.kt` — currently list *Gradle tasks*; needs to become a fixed Build/Clean/Rebuild/Cancel action list instead (there are no arbitrary Gradle tasks anymore)
- `core/app/src/main/java/com/tom/rv2ide/viewmodel/EditorViewModel.kt` — build-trigger entry point

## Phase 4 — Removal list (only after Phase 3 is verified working)

- `tooling:api`, `tooling:impl`, `tooling:model`, `tooling:events`,
  `tooling:plugin`, `tooling:plugin-config`, `tooling:builder-model-impl`
- `composite-builds/*` (build-logic, build-deps and their Gradle plugin glue)
- Gradle wrapper usage for *target projects* (the IDE's own build can keep
  using Gradle — that's a separate concern from what it builds for users)

## Bundling the actual tool binaries — DECIDED: first-run download

Added `tools/ToolsProvisioner.kt`. On first build (not on app open — so
requirement #12 is satisfied), it downloads one small "core" zip (~5-15MB:
ecj.jar, d8.jar, aapt2, zipalign, apksigner.jar, android.jar), verifies its
SHA-256 against a manifest, zip-slip-guards the extraction, and unpacks it
into `build-tools/`. Kotlin support is a second, separate, larger, optional
package — only fetched the first time a project actually has `.kt` files.

**What you still need to do (I can't do this part for you):**
1. Build the `core` zip yourself: pull `ecj.jar` from an Eclipse JDT Core
   release, `d8.jar`/`android.jar` from an Android SDK / R8 distribution,
   `aapt2` + `zipalign` native binaries per-ABI (arm64-v8a at minimum) from
   Google's Maven `com.android.tools.build:aapt2`, and `apksigner.jar` from
   the SDK build-tools. Zip them into the `build-tools/...` layout
   `BuildToolsLocator` expects.
2. Host that zip somewhere stable (a GitHub Release asset on this repo is
   the simplest option — free, versioned, no extra infra) and compute its
   SHA-256 (`sha256sum core.zip`).
3. Implement `ToolsProvisioner.ManifestProvider` with that URL + checksum
   (a one-file, hardcoded implementation is fine to start — no need for a
   remote manifest server).

**Phase 3 UI hook:** call `provisioner.provision(...)` the first time the
user taps Build, showing `ProvisionEvent.Progress` as a download progress
bar before the pipeline's `BuildStage`s start; skip straight to building on
every run after that since `isProvisioned()` is then true.
