# Hermes2 Material 3 UI Guide

This document defines the current visual direction for Hermes2.

The UI should feel like a focused Android control room for Hermes Agent:

```text
calm • technical • readable • fast • trustworthy
```

---

## Design Goals

1. **Show the active backend clearly**
   - Users must always know whether Hermes is using Gemini, Gemini, OpenRouter, etc.

2. **Make runtime state obvious**
   - Termux missing
   - Hermes installed
   - gateway starting
   - gateway connected
   - gateway failed

3. **Expose capabilities without overwhelming the user**
   - Toolsets should be grouped and summarized.
   - Show enabled/disabled state.
   - Show examples of tools inside a toolset.

4. **Keep Termux details out of normal UI**
   - Termux setup can be visible in Runtime Setup.
   - Chat and Settings should feel native, not like a terminal wrapper.

5. **Respect Android constraints**
   - No desktop-only assumptions.
   - Browser Automation / Computer Use should be marked unavailable/experimental on Android.

---

## Visual Language

### Theme style

Hermes2 uses Material 3 with a dark, agentic control-panel feel.

Recommended tone:

| Element | Style |
|---|---|
| Background | deep neutral / dark navy |
| Primary | electric violet or indigo |
| Secondary | cyan / teal accents |
| Success | green, but not neon |
| Warning | amber |
| Error | Material error red |
| Cards | rounded, elevated, readable |
| Text | high contrast, monospace for commands/URLs/models |

---

## Suggested Palette

These can be mapped into `ui/theme/Color.kt`.

```kotlin
val HermesDarkBg = Color(0xFF0B1020)
val HermesSurface = Color(0xFF111827)
val HermesSurfaceVariant = Color(0xFF1F2937)
val HermesPrimary = Color(0xFF8B5CF6)
val HermesPrimarySoft = Color(0xFFDDD6FE)
val HermesSecondary = Color(0xFF22D3EE)
val HermesSuccess = Color(0xFF22C55E)
val HermesWarning = Color(0xFFF59E0B)
val HermesError = Color(0xFFEF4444)
```

Light mode can use the same accents with softer surfaces.

---

## Navigation Model

Current simple navigation is acceptable for MVP:

```text
Chat
  ↳ Settings
      ↳ Runtime Setup
      ↳ Messaging Platforms
      ↳ Skills
      ↳ Cron
  ↳ Sessions
```

Future improvement:

- Navigation rail on tablets
- Bottom navigation on phones
- Persistent runtime indicator in top app bar

Suggested bottom tabs:

```text
Chat | Runtime | Settings | Memory
```

---

## Screen Guidelines

### Chat Screen

Purpose: daily conversation with Hermes.

Must show:

- connection status
- user messages
- assistant markdown messages
- streaming state
- tool call cards
- approval prompts
- stop generation button

Rules:

- Do not render assistant text twice.
- Markdown should be the primary renderer for assistant responses.
- Tool calls should be visually separate from chat text.
- Errors should appear as status rows and snackbar messages.

---

### Runtime Setup Screen

Purpose: get Hermes running.

Must show:

- Termux detected / missing
- install progress
- gateway running URL
- logs fetch button
- clear troubleshooting hints

Recommended primary actions:

```text
Start Agent Gateway
Fetch Logs
Open Termux
Grant RUN_COMMAND Permission
```

Avoid:

- starting foreground service automatically before install
- showing raw script unless in advanced/fallback mode

---

### Settings Screen

Purpose: backend and capability control.

Current cards:

```text
Active backend
Provider: gemini
Model: gemini-2.5-flash
```

Tabs:

```text
General | Models | Tools
```

Model cards should show:

- provider
- model id
- active marker
- key-needed state
- `Use this model` action

Tool cards should show:

- toolset name
- description
- enabled state
- tool count
- first few tool names

---

### Sessions & Memory

Purpose: inspect persisted sessions and memory files.

Must show:

- session list
- message count
- preview
- USER.md
- MEMORY.md

Future improvement:

- open a session into Chat
- search sessions from UI
- show memory edit/clear actions with confirmation

---

### Skills

Purpose: discover and manage skills.

Current behavior:

- lists skills grouped by category
- install action by skill name
- reload action

Future improvement:

- inspect skill content
- search skills
- show installed vs available

---

### Cron

Purpose: schedule agent tasks.

Current behavior:

- list jobs
- create job dialog
- pause/resume
- delete

Recommended UI improvements:

- natural-language schedule helper
- preview next run time
- show last run result

---

## Component Patterns

### Backend Status Card

Use for active provider/model.

```text
┌──────────────────────────┐
│ Active backend            │
│ Provider: gemini          │
│ Model: gemini-2.5-flash     │
└──────────────────────────┘
```

### Capability Card

Use for toolsets.

```text
terminal
Terminal & Processes
2 tools: terminal, process
[enabled switch]
```

### Runtime Error Card

Use for serious state problems.

```text
Gateway did not become reachable.
Check ~/.hermes/logs/gateway_stdout.log
[Fetch Logs]
```

---

## Android/Termux Capability Labels

Some capabilities are expected to be unavailable on Android. UI should not present them as broken.

| Capability | Android status | UI label |
|---|---|---|
| Terminal | supported | Available |
| File tools | supported | Available |
| Memory | supported | Available |
| Cron | supported, best-effort background | Available |
| Web Search | supported with ddgs/API providers | Available |
| Browser Automation | experimental | Experimental / disabled |
| Computer Use | desktop-only | Not available on Android |
| Docker backend | not available | Hidden / not available |
| Voice local STT | limited | Optional cloud fallback |

---

## Copy Guidelines

Use clear, user-facing language:

Good:

```text
Gateway is running
Hermes install required
Gemini backend active
Fetch logs
```

Avoid:

```text
RUN_COMMAND accepted
Process dispatched
Migration adapter failed
```

Developer details can go in expandable logs.

---

## Future Material Polish Checklist

- [ ] Bottom navigation
- [ ] Runtime status chip on all screens
- [ ] Better dark palette
- [ ] Empty states with clear next actions
- [ ] Tool capability grouping
- [ ] Provider icons / short labels
- [ ] Log viewer with copy/share
- [ ] Permission checklist cards
- [ ] Gemini/Gemini quick backend switcher
- [ ] Tablet navigation rail
