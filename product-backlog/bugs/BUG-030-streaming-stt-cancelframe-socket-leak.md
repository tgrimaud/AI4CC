# BUG-030 — Streaming STT session not torn down on `CancelFrame` (provider socket leak)

**Type:** Bug (voice runtime)
**Status:** ✅ Fixed (2026-10-13) — Sprint 19, branch `fix/BUG-030-stt-cancelframe-teardown`. The
`CancelFrame` branch now `await self._discard_session()` before closing the pre-warm spare
(`web_voice/streaming_stt_processor.py`), mirroring the `EndFrame`/`_on_end` cleanup. Regression test
`tests/test_streaming_stt_processor.py::test_cancel_frame_discards_open_stt_session` (asserts the
in-flight session is closed and nulled on abrupt cancel). Full voice suite **733 unittest + 43 behave
scenarios green**. Pending user validation + merge.
**Severity:** Medium (resource leak under reconnect storms; not a data/safety defect).
**Component:** `voice-agent` — Python voice runtime (streaming STT).
**Found by:** adversarial code review, 2026-10-13 (`docs/qa/2026-10-13-full-adversarial-review.md`).

## Symptom

On a pipeline **`CancelFrame`** (abrupt teardown — e.g. `StreamingVoiceSession.stop()` →
`task.cancel()`, wired from `_safe_stop`), `StreamingSttProcessor` closes only the STT **pre-warm
spare** (`_warmer.aclose()`) but does **not** discard an **in-flight** streaming STT WebSocket
(`_session`). The graceful `EndFrame` path *does* finalize/discard via `_on_end()`. So a cancel
(barge-in storm, client drop, reconnect loop) can leak the provider's STT socket and skew capacity.

## Evidence

- `web_voice/streaming_stt_processor.py:215-218` (CancelFrame branch closes `_warmer` only) vs
  `:211-212` (`EndFrame` → `_on_end()` → `_discard_session()`), and `:418-421`.
- Abrupt teardown: `web_voice/streaming_runtime.py:166-169` (`stop()` → `task.cancel()`),
  `web_voice/websocket_app.py:628-630` (`_safe_stop`).

## Expected

On `CancelFrame`, the processor must `await self._discard_session()` (and cancel any in-flight
finalize) for the active `_session` before forwarding the frame, mirroring the `EndFrame` cleanup.

## Proposed fix

1. In the `CancelFrame` handler, close/discard the in-flight `_session` (best-effort, guarded).
2. Add a unit test: open a fake streaming STT session, inject `CancelFrame`, assert the session's
   `aclose()` was called (no leak).

## Observability

No new telemetry required; optionally emit a `stt.session.cancelled` event with `correlation_id`
+ outcome so cancel-path teardown is observable in ops.

## Notes

Pre-existing (not caused by the AI4CC rebrand). Keep the normal `EndFrame`/barge-in paths (already
correct: `asyncio.CancelledError` caught + `finally aclose()` in the TTS processor).
