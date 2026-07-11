# Audit Plans — shadcn/improve (Hermes-Pocket)

## Audit: 2026-07-11 (deep)
**Commit:** 601aecb
**Effort:** deep
**Auditor:** shadcn/improve skill
**Stack:** Kotlin, Jetpack Compose, Material 3, Hilt, OkHttp, WebSocket
**Size:** 62 Kotlin files, 20K lines

## Findings Table

| # | Finding | Category | Impact | Effort | Risk | Confidence |
|---|---------|----------|--------|--------|------|------------|
| 1 | ChatViewModel.kt is 1,881 lines — God Object with 15+ responsibilities | tech-debt | H | L | MED | HIGH |
| 2 | ConfigViewModel.kt is 1,712 lines — God Object with 12+ responsibilities | tech-debt | H | L | MED | HIGH |
| 3 | 19 God Files >300 lines (12 >500 lines) | tech-debt | H | M | MED | HIGH |
| 4 | Test coverage 3.3% (2/60 files) — critical paths untested | tests | H | M | LOW | HIGH |
| 5 | 5 force-unwraps (!!)) — crash risk on null values | bugs | M | S | LOW | HIGH |
| 6 | Empty catch block in RuntimeViewModel:319 — silently swallows unregister errors | bugs | L | S | LOW | HIGH |
| 7 | Token in WebSocket URL logged via Timber (RuntimeViewModel:105,189,256) | security | M | S | LOW | HIGH |
| 8 | No AGENTS.md for agent-executed plans | dx | M | S | LOW | HIGH |
| 9 | 128 try-catch blocks — error handling quality unclear without audit | tech-debt | L | M | LOW | MED |
| 10 | isMinifyEnabled=false in release build — no code shrinking/obfuscation | security | M | S | MED | HIGH |

## Considered and Rejected

- [SEC-01] "Token in WebSocket URL" — token is a session token for local Hermes backend, not a credential. By-design.
- [PERF-01] "55 remember calls" — normal for Compose app of this size
- [DEP-01] "AGP 8.7.3" — latest stable at time of audit

## Priority Order

1. Plan 004 (test coverage — critical paths untested)
2. Plan 001 (ChatViewModel split — highest-impact God Object)
3. Plan 002 (ConfigViewModel split)
4. Plan 005 (force-unwrap fixes)
5. Plan 007 (token logging fix)
6. Plan 010 (enable minification)
7. Plan 008 (AGENTS.md)
