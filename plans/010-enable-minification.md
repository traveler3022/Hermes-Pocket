# Plan 010: Enable code minification in release build

## Status
- **Priority**: P3
- **Effort**: S
- **Risk**: MED
- **Category**: security
- **Planned at**: commit `601aecb`, 2026-07-11

## Current state

`app/build.gradle.kts` has `isMinifyEnabled = false` in the release build type.
This means:
- No dead code elimination (APK is larger than needed)
- No code obfuscation (reverse engineering is trivial)
- No resource shrinking

## Steps
1. Set `isMinifyEnabled = true` in release build type
2. Add `isShrinkResources = true`
3. Update proguard-rules.pro with needed keep rules:
   - Hilt generated classes
   - Kotlinx serialization
   - OkHttp/Okio
   - Compose runtime
4. Build release APK: `./gradlew assembleRelease`
5. Test the APK on device — verify no runtime crashes from over-aggressive shrinking

## STOP conditions
- If release APK crashes on launch — likely missing ProGuard keep rules
- If Hilt/DI fails — add keep rules for @Inject, @Module, @Provides
- If serialization fails — add keep rules for @Serializable classes

## Done criteria
- `isMinifyEnabled = true` in release
- Release APK builds successfully
- APK size reduced by at least 20%
