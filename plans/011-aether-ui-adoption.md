# Plan 011: What to adopt from Aether

## Status
- **Priority**: P2
- **Effort**: L (a backlog, not one change)
- **Risk**: LOW per item
- **Category**: ux
- **Planned at**: commit `5f7d84d`, 2026-09-05
- **Source surveyed**: `Zhou-Shilin/Aether` @ `ca1b14c` (v2.1.6)

## The licence constraint — read first

Aether is **GPL-3.0**. Hermes Pocket is **MIT**. Copying Aether source into this
repo would force the whole app to GPL-3.0.

Layout, colour, spacing, motion and UX ideas are not copyrightable — a design can
be reimplemented freely. Source cannot be pasted. **Every item below is a
from-scratch reimplementation**, which is how `HxThinkingTrace.kt` was built.

One practical rule that keeps this clean: do not copy Aether's identifiers.
`feature/aether-chat-v2` carries function names identical to Aether's
(`ReasoningTimeline`, `TimelineGlyph`, `ToolInvocationCardsColumn`,
`AgentWorkSummaryDisclosure`, `StreamingMarkdownContent`), which is what makes
that branch hard to defend. Name things for what they do here.

## Already done

| Item | Where |
|---|---|
| Floating chrome: circle header buttons, pill selector, soft shadow | `ui/design/DesignSystem.kt` |
| Empty state with starter chips | `ChatScreen.kt` `EmptyChatHero` |
| Thinking as a quiet line + bottom-sheet trace with a glyph rail | `message/HxThinkingTrace.kt` |
| Tool cards folded off the chat surface into that trace | `ChatScreen.kt` `toolsByTurn` |
| Messages sized against the screen instead of a fixed dp cap | `ui/design/DesignSystem.kt` |
| Icons transcribed from Lucide; 9 deprecation warnings down to 1 | `ui/design/HxIcons.kt` |
| Model switching from the composer, next to reasoning effort | `ChatInputBar.kt` |

## Backlog, cheapest-first

### 1. Model selector in the chat itself — P1, effort S

There is no way to change model mid-conversation; it takes a trip to Settings.
Aether puts a pill in the top bar that opens a model list with the provider's
logo per row, and folds the reasoning-effort switch into the same menu.

Hermes already has the reasoning-effort switch and a provider/model list in
`ConfigProviders.kt` — this is mostly rewiring existing state into a menu
anchored to the top bar. Highest ratio of felt improvement to code written.

### 2. Responsive bubble width — P2, effort XS

`UserMessageBubble` is capped at a fixed `420.dp` and `AssistantMessageBubble` at
`460.dp`. On a small phone that is the whole width; on a tablet it is a ribbon.
Measure the parent and take a share of it, clamped — roughly 72%, floor 300dp,
ceiling 520dp.

### 3. Visible branch switcher — P2, effort S

Branching exists but is buried in the long-press menu
(`AssistantMessageBubble.kt`, "Branch conversation"). Aether puts a `‹ 2/3 ›`
stepper under the user message that owns the branch point, so the feature is
discoverable and switching is one tap. Same backend, visible affordance.

### 4. Usage statistics with charts — P2, effort M — PARTLY DONE, then blocked

Hermes drew no charts anywhere. A first primitive now exists — `HxSplitBar` in
`ui/design/HxCharts.kt` — and the session detail uses it to show the input/output
split beside the raw token tiles.

**Correction to this plan's first draft.** It claimed the rest was "a rendering
job on top of numbers Hermes already has". That was wrong, and checking the data
is what showed it:

| What exists | Shape |
|---|---|
| `SessionUsage` | `calls`, `input`, `output`, `total`, `creditsLines` — one session, fetched on demand |
| `InsightsData` | `days`, `sessions`, `messages` — three scalars over 30 days |

There is **no time series anywhere**, so tokens-over-time, a provider mix, and a
throughput chart have nothing to plot. Two ways forward, both bigger than the
original estimate:

- **Client-side aggregate** — call `session.usage` for every session in the list
  and bucket by `updatedAt`. Works today, at the cost of N round-trips per open.
- **Gateway endpoint** — one call returning usage bucketed by day and provider.
  Cheaper on the wire and the better answer, but it is server work.

Pick one before building more chart primitives. Adding a chart with no data to
feed it is how the other branch ended up with 755 lines nothing calls.

### 5. Pure-Compose markdown renderer — P3, effort L

`HermesMarkdown.kt` is a 255-line wrapper around the `compose-markdown` library,
which renders through `AndroidView`. Its own comment warns about the cost inside
a `LazyColumn`, and `AssistantMessageBubble` works around it by falling back to
plain `Text` while streaming — so streamed replies show raw markup until the turn
ends. Aether renders markdown in Compose directly (~3k lines) and highlights code
blocks.

Biggest item here and the only one that removes a dependency. Do it when the
streaming-markup compromise starts to matter, not before.

### 6. Conversation timeline rail — P3, effort M

A thin column of bars down the edge of a long conversation; dragging expands it
into a scrubber with per-turn previews so you can jump between turns. Nothing in
Hermes does this. Genuinely useful past ~20 turns, and pure UI — no gateway
change.

### 7. Lucide icon set — P3, effort S

The debug build emits 9 deprecation warnings, all of them Material icons that
moved to `AutoMirrored` (`Undo`, `OpenInNew`, `CallSplit`, `Sort`). Aether ships
hand-built `ImageVector`s instead of depending on the Material icon set.

Take these from **Lucide itself** (ISC licence), not from Aether — icons are
artwork and carry their own copyright, unlike layout.

## Deliberately not adopting

Aether runs the model **on the phone** (Pi framework, Termux/Alpine, Shizuku for
screen control). Hermes is a thin client for a Hermes Agent on the user's own
VPS. These do not transfer:

- `AlpineTerminalScreen`, `AndroidAlpineFileManagerScreen`, Termux setup flow
- Agent mode — replay panel, screen cursor, Shizuku service
- The Pi extension system and `pi-bridge`
- iOS/macOS sources — Hermes is Android-only, not Kotlin Multiplatform
- `SettingsScreen.kt` (7,133 lines) — Hermes's settings model is its own

## Known gap that is not Aether's to fix

Aether stamps every reasoning chunk and tool invocation with a shared
`timelineOrder`, so its trace interleaves thinking and tool calls in the order
they actually happened. The Hermes gateway streams reasoning as one growing
string (`reasoning.delta`) and tool calls as separate events with no shared
ordering key, so `HxThinkingTrace` can only render steps first, then tools.

Fixing this is a **gateway change**, not an app change: the server would need to
emit a monotonic sequence number on both event kinds.

## Done criteria

Each item lands as its own commit with `assembleDebug` green and the unit suite
passing. No Aether source is copied, and no identifier is carried over from it.
