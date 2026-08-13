# Plan 011 — Chat UI Rewrite (Aether-style)

## Goal
Make Hermes Pocket's Chat screen visually and interactively match Aether's Chat screen at pixel-level fidelity, while keeping the project MIT-licensed and without copying Aether's source code. Scope: **only the Chat screen** (message list, top bar, composer, drawer, empty state, search-in-chat). All other screens, settings, and backends are out of scope.

## Constraints
- MIT license stays intact. No copy-paste of Aether source. Visual/behavioral reference only.
- Pixel-level fidelity means: same colors (hex), same spacing (dp), same typography (size/weight/line-height), same interactions, same animations (duration/easing).
- Existing business logic is preserved: session drawer, draft save, search-in-chat, retry/regenerate, copy, tool approval, image viewer, code copy, scroll-to-bottom FAB, etc.
- Build must pass: `./gradlew assembleDebug` and `./gradlew test`.
- AGENTS.md conventions: Kotlin/Compose/Material3/Hilt; StateFlow for state; sealed classes; Timber; no force-unwraps.

## Architecture Decisions
1. **Design system module**: Create `ui/design/` package with semantic tokens (Color, Spacing, Typography, Shape, Elevation, Motion) extracted from Aether's `Color.kt`, `SharedTheme.kt`, and observed measurements. All screens (this one, and future) consume tokens.
2. **No copy of Aether source**: Reimplement every Composable from scratch using the spec. Read Aether code only to *understand* behavior/visuals; never transcribe. Where Aether's expression is non-trivial (e.g., custom markdown parser), use existing libraries where possible.
3. **Markdown**: Audit current Hermes Pocket markdown implementation first. If it cannot match Aether's visual output (headings, tables, code fences, links, images, KaTeX), wrap or replace it. Decision deferred to implementation phase after audit.
4. **Animation utilities**: Centralize in `ui/design/motion/` so timing/curves are consistent.

## Spec Extraction — Source-Only Rule
The ONLY reference for implementation is the Aether source code itself:
- `shared/src/commonMain/kotlin/com/zhousl/aether/ui/SharedConversationMessages.kt`
- `shared/src/commonMain/kotlin/com/zhousl/aether/ui/ConversationChrome.kt`
- `shared/src/commonMain/kotlin/com/zhousl/aether/ui/SharedMarkdownRenderer.kt`
- `shared/src/commonMain/kotlin/com/zhousl/aether/ui/SharedMarkdownContent.kt`
- `shared/src/commonMain/kotlin/com/zhousl/aether/ui/theme/Color.kt`
- `shared/src/commonMain/kotlin/com/zhousl/aether/ui/SharedTheme.kt`
- Android-specific: `app/src/main/java/com/zhousl/aether/ui/ConversationUi.kt`, `ConversationMessages.kt`, `ConversationDrawer.kt`, `MarkdownRenderer.kt`, `AetherApp.kt`

**Hard rule**: Screenshots are NOT part of the spec extraction process. If and only if the source code does not contain enough information for a **specific, individual value** (e.g., a hex color, a dp measurement, a duration in ms), the implementation agent must:
1. Report that exact gap to the user.
2. Wait for explicit approval before using any screenshot.
3. Use the screenshot only for that specific missing value, nothing more.

No guessing, no pixel-sampling from screenshots, no design decisions based on visual approximation. The implementation agent reads the Aether source, extracts behavior and visual parameters from the code itself, and reimplements them from scratch.

---

## Phased Tasks

### Phase 0 — Design System Foundation (no screen changes yet)
Goal: Standalone design tokens that any Composable can use. Nothing in the app references these yet except a smoke-test screen.

1. **Create `ui/design/Color.kt`** — semantic colors matching Aether:
   - `AetherPalette` with roles: `background`, `surface`, `surfaceVariant`, `primary`, `onPrimary`, `secondary`, `tertiary`, `error`, `outline`, `messageBubble`, `messageBubbleAssistant`, `scrim`, `topBarGradientStart`, `topBarGradientEnd`.
   - Light / Dark / LightHC / DarkHC variants.
   - Map to `lightColorScheme(...)` / `darkColorScheme(...)` for Material 3.
2. **Create `ui/design/Type.kt`** — Typography matching Aether's Markdown text, headings, code, caption. Use Material 3 `Typography` slots; override per role.
3. **Create `ui/design/Shape.kt`** — `RoundedCornerShape` tokens: `bubble`, `card`, `chip`, `button`, `fab`, `topBar`, `bottomBar`.
4. **Create `ui/design/Spacing.kt`** — `HxSpacing` object with `xs(4)`, `sm(8)`, `md(12)`, `lg(16)`, `xl(24)`, etc., matching Aether's gaps.
5. **Create `ui/design/Elevation.kt`** — shadow tokens (one for floating circles, one for cards, one for dialogs).
6. **Create `ui/design/motion/Motion.kt`** — `tween(220)`, `spring(StiffnessMediumLow)`, custom curves for fold/unfold, bubble appear.
7. **Create `ui/design/HxTheme.kt`** — `HxTheme(content)` Composable that wraps `MaterialTheme` and applies the above. Provider for `LocalReduceMotion`.
8. **Smoke test**: Wrap `MainActivity` content in `HxTheme { ... }`. No visible change yet (tokens default to existing M3 values), but structure is in place. Build must pass.

### Phase 1 — Visual Core (no logic changes)
Goal: Chat screen visually matches Aether; behavior is identical to current Hermes Pocket.

9. **Rewrite `ui/screen/ChatScreen.kt` — Top Bar (`ConversationTopBar`)**:
   - Two floating circle buttons (menu, search) flanking a centered pill (connection state OR "working" indicator). Pixel-equal padding (12dp horizontal, 10dp vertical).
   - Reuse `HxHeaderCircleButton` shape but adjust size/color/shadow per spec.
   - Search bar slides in below when active (`slideInVertically + fadeIn`, ~220ms).
10. **Rewrite top-bar gradient overlay** (transparent gradient over messages under top bar, Aether-style).
11. **Rewrite `ui/screen/ChatMessages.kt` — `MessageBubble`**:
    - User bubble: rounded shape, `messageBubble` color, padding 14dp horizontal / 12dp vertical, shadow.
    - Assistant bubble: surface color (no filled bubble for assistant prose), padding only.
    - Avatar: shown only on first message of each assistant turn (grouped flag), 32dp circle.
    - `combinedClickable` for long-press → copy menu.
    - Group spacing: 0dp within group, 18dp across turn boundary (matches Aether).
12. **Rewrite thinking/tool blocks** (currently in `ChatMessages.kt`):
    - Fold/unfold with `AnimatedVisibility` + `expandVertically/shrinkVertically`.
    - Header chip with icon + status text; expanded shows content.
13. **Rewrite markdown rendering** (audit first):
    - If current library supports headings/tables/code-fences/links/images/KaTeX with Aether-equivalent styling → wrap and restyle.
    - If not → introduce `ui/component/HermesMarkdown.kt` with custom rendering matching Aether.
14. **Rewrite `ui/screen/ChatInputBar.kt` — Composer**:
    - Floating rounded card, top shadow, sits above nav bar with `navigationBarsPadding` + `imePadding`.
    - Inner: attachment chips row (when attachments), multiline `BasicTextField`, action row (attach button, send/stop button, optional skill/mode toggles per Hermes Pocket).
    - Send button morphs to stop while streaming (`animateContentSize` + icon swap).
    - Match Aether's corner radius, padding, and icon size.
15. **Rewrite `ui/screen/ChatSessionDrawer.kt`** (drawer content):
    - Match Aether drawer width (322dp), corner radius (30dp top-end/bottom-end).
    - List rows: title, relative time, working/unviewed indicator dots.
    - Long-press → rename/export/delete sheet (same options as today).
16. **Rewrite `EmptyChatHero`**:
    - Avatar circle, greeting text, 2x2 suggestion grid (rounded pills, icon + title).
    - Vertical spacing matches Aether (16dp greeting/subtitle gap, 28dp before suggestions, 10dp between rows).
17. **Scroll-to-bottom FAB**: keep, restyle to match Aether's `secondaryContainer` small FAB with shadow.
18. **Notification banner** + **connection retry banner** + **shimmer skeleton**: restyle to match Aether's surface treatment.
19. **Image fullscreen viewer** (Dialog): match Aether's dim background (Black 0.92f), close + save icons styled.

### Phase 2 — Behavior Polish
Goal: Interactions feel identical to Aether.

20. **Streaming tail spacer behavior**: keep current "pin user message to top while AI replies" logic but tune the spacer height + easing to match Aether (450ms tween).
21. **Scroll-to-bottom trigger threshold**: tune `showScrollToBottom` to Aether's observed threshold.
22. **Tool-approval sheet** (`ChatApprovalSheet.kt`): restyle to Aether's bottom sheet (rounded top corners 28dp, drag handle, primary action button style).
23. **Rename assistant dialog**: restyle to Aether's dialog (rounded 28dp, primary button color).
24. **Code block copy button**: restyle to Aether's floating top-right icon button on code blocks.
25. **Search-in-chat filtering animation**: smooth cross-fade between filtered and full list.
26. **Drawer slide animation**: confirm matches Aether's curve/duration; adjust `DrawerState` if needed.

### Phase 3 — Markdown Audit & Decision
27. Read current `ui/component/HermesMarkdown.kt` and `ui/component/ContentBlocks.kt` (and any markdown library in `gradle/libs.versions.toml`).
28. Build a checklist of features Aether supports: H1-H6, ordered/unordered lists, blockquote, table, code fence (with language), inline code, links (auto + inline), images, KaTeX inline + block, horizontal rules.
29. Test current implementation against each. For any gap, decide:
    - Switch library (e.g., compose-richtext → compose-markdown → custom).
    - Add wrapper that injects Aether-style styling.
    - Build custom parser if no library covers KaTeX + tables + code fences with copy.
30. Document the decision in the plan's deviation file.

### Phase 4 — Validation
31. `./gradlew assembleDebug` — must succeed.
32. `./gradlew test` — existing tests must pass; add new tests for any extracted utilities (spacing/shape tokens, motion curves).
33. `./gradlew lint` — clean.
34. Manual visual diff: side-by-side screenshot of Hermes Pocket Chat screen vs Aether Chat screen, on light and dark mode, in empty and populated states. Document any intentional deviations.
35. Manual interaction pass: send message, stream response, long-press copy, scroll-to-bottom, open drawer, rename session, search, attach file, approve tool call, view image fullscreen.

---

## Out of Scope
- Anything outside Chat screen (Tasks, Skills, Settings, Runtime, Billing, Plugins, Projects, Pet, Cron).
- Backend changes (gateway, runtime, repository, ViewModel logic).
- Database schema or Room changes.
- iOS / KMP conversion.
- Animation framework overhaul (we use existing Compose animation APIs).
- New features not in current Hermes Pocket (e.g., Agent Mode replay panel — would only be added if Aether has it AND user wants it; default: skip).

## Risks
1. **Aether's exact hex/values not always available from source** (some may be computed dynamically). Mitigation: if a specific value is missing from the source, report the exact gap to the user and wait for approval before using any screenshot. Never guess or pixel-sample from screenshots. Document any user-approved approximation in deviations file.
2. **Performance regressions** from new animations/shadows on low-end Android. Mitigation: gate heavy effects behind `LocalReduceMotion`.
3. **Markdown library limitations** (item 29). If current library can't match Aether's markdown fidelity, scope of Phase 3 grows. Mitigation: explicit decision matrix; if custom parser required, may need to split into a follow-up plan (012).
4. **i18n**: existing t() helper must be preserved in all strings.
5. **Regression in business behavior**: visual rewrite must not change `ChatViewModel` logic. All callbacks remain the same.

## Validation Checklist
- [ ] `./gradlew assembleDebug` passes
- [ ] `./gradlew test` passes
- [ ] `./gradlew lint` clean
- [ ] Visual diff recorded
- [ ] All `t()` strings preserved
- [ ] No new TODO/FIXME in touched files
- [ ] No force-unwraps introduced
- [ ] No token/credential logging introduced

## Open Questions
- None blocking. Markdown library decision (Phase 3) will be made during implementation.

## Followup (after this plan)
- Plan 012 (if needed): Markdown custom parser if no library matches Aether.
- Plan 013 (later): Apply HxTheme/Design tokens to Settings, Runtime, Tasks screens.
- Plan 014 (later): Refactor ChatViewModel (already noted in `plans/001-chatviewmodel-split.md`).
