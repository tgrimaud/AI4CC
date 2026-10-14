# Adversarial Code Review — BUG-030 (streaming STT CancelFrame socket leak)

- **Ticket:** BUG-030 — `product-backlog/bugs/BUG-030-streaming-stt-cancelframe-socket-leak.md`
- **Branch:** `fix/BUG-030-stt-cancelframe-teardown` (Sprint 19)
- **Reviewer:** adversarial-code-review skill
- **Date:** 2026-10-13
- **Verdict:** Pass
- **Score:** 95/100

## Summary

On an abrupt call teardown the voice runtime receives a `CancelFrame` (not a graceful
`EndFrame`). Before the fix, `StreamingSttProcessor.process_frame` only closed the pre-warm
spare (`self._warmer.aclose()`) on `CancelFrame` and left the **in-flight** streaming STT
session (`self._session`) open, leaking the provider WebSocket. The `EndFrame` path already
discarded the session via `_discard_session()`; the `CancelFrame` path did not.

## Change

`web_voice/streaming_stt_processor.py` — the `CancelFrame` branch now calls
`await self._discard_session()` (close + null the in-flight session) before
`await self._warmer.aclose()`, then forwards the frame. We discard rather than finalize: an
aborted call has no transcript to flush.

## Findings

| Severity | Finding | Status |
|----------|---------|--------|
| Blocking | In-flight STT session (socket) leaked on `CancelFrame` | Fixed — `_discard_session()` added to the `CancelFrame` branch |
| Low | Order of close (session before spare) | OK — session is the leaking resource; spare close is best-effort and already guarded |
| Info | Symmetry with `EndFrame`/`_on_end` cleanup | Confirmed — both abrupt and graceful teardown now release the session |

## Test Evidence

- Regression test `tests/test_streaming_stt_processor.py::test_cancel_frame_discards_open_stt_session`:
  drives speech frames until `processor._session is not None`, queues a `CancelFrame`, asserts
  `session.closed is True` and `processor._session is None`.
- `tests/test_streaming_stt_processor.py` — 25 tests OK.
- Full voice suite — **733 unittest OK**, `behave` **15 features / 43 scenarios / 194 steps** green.

## Observability / Security

- No runtime telemetry contract change (teardown path); correlation-id handling unchanged.
- No secret handling touched.

## Residual (accepted)

- None material. The `.venv` shebangs are stale after the AI4CC dir rename (unrelated Sprint 19
  follow-up); tests were run via `./.venv/bin/python -m unittest` / `-m behave`.
