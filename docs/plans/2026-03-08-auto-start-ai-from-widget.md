# Auto-Start AI from Widget Design

**Date:** 2026-03-08

## Goal

When the home screen widget is tapped, the full cycle runs automatically: glasses stream starts AND the Gemini AI session starts, without any additional taps. If no Gemini API key is configured, AI auto-start is silently skipped (same behavior as the in-app button being non-clickable).

## Approach: `autoStartAI` flag in `WearablesUiState`

`quickStartStreaming()` sets `autoStartAI = true` alongside `isStreaming = true`. `StreamScreen` observes this flag and calls `geminiViewModel.startSession()` once when it becomes true, then clears it.

## Components Changed

- **`WearablesUiState`** — add `val autoStartAI: Boolean = false`
- **`WearablesViewModel.quickStartStreaming()`** — set `autoStartAI = true` in the `_uiState.update` block (in the `else` branch, alongside `isStreaming = true`)
- **`WearablesViewModel`** — add `clearAutoStartAI()` that sets `autoStartAI = false`
- **`StreamScreen`** — add `LaunchedEffect(autoStartAI)` that, when `autoStartAI == true` and `GeminiConfig.isConfigured`, calls `geminiViewModel.startSession()` then `wearablesViewModel.clearAutoStartAI()`

## Data Flow

```
Widget tap
  → MainActivity.handleQuickIntent()
  → WearablesViewModel.quickStartStreaming()
      → _uiState: isStreaming=true, autoStartAI=true
  → StreamScreen appears (isStreaming flipped)
      → LaunchedEffect(isPhoneMode) starts glasses stream [existing]
      → LaunchedEffect(autoStartAI) fires:
          if autoStartAI && GeminiConfig.isConfigured:
              geminiViewModel.startSession()
              wearablesViewModel.clearAutoStartAI()
```

## Error Handling

- If Gemini is not configured: `autoStartAI` is cleared without calling `startSession()` — no error shown (mirrors button being disabled)
- If Gemini session fails to connect: existing error handling in `GeminiSessionViewModel.startSession()` shows a toast — no change needed
- If already streaming when widget is tapped: `quickStartStreaming()` guard prevents double-start — `autoStartAI` is never set

## No New Files

All changes are in existing files. No new classes, no new intents.
