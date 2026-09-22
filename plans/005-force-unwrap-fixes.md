# Plan 005: Fix 5 force-unwraps (!!)) — crash risk

## Status
- **Priority**: P2
- **Effort**: S
- **Risk**: LOW
- **Category**: bugs
- **Planned at**: commit `601aecb`, 2026-07-11

## Current state

5 force-unwraps that can crash the app:
1. `HermesMarkdown.kt:184` — `headingRe.find(t)!!`
2. `HermesMarkdown.kt:191` — `bulletRe.find(t)!!.groupValues[1]`
3. `HermesMarkdown.kt:195` — `numberRe.find(t)!!`
4. `CronScreen.kt:360` — `existingJob!!.id`
5. `RemoteRuntime.kt:192` — `settings.webSocketUrl()!!`

## Steps
1. Replace each `!!` with safe call + null check + fallback
2. For regex matches: use `?.let { }` pattern
3. For `existingJob!!`: add null guard with error message
4. For `webSocketUrl()!!`: return early with error state if null
5. Run build + tests

## Done criteria
- `grep -rn "!!" app/src/main --include="*.kt" | grep -v import` ≤ 0
- Build passes
- Tests pass
