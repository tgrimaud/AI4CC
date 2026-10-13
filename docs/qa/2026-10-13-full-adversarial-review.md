# Full Adversarial Review — AI4CC (code + docs), 2026-10-13

- **Trigger:** user request for a full adversarial review of all code and documentation, after the
  AI4CC rebrand (Sprint 18 / TASK-OPS-018) landed on `feat/restart-from-scratch`.
- **Method:** two project skills (`adversarial-code-review` 0–100, `adversarial-architecture-review`
  0–5), three parallel exploration sweeps (backend / voice runtime / docs+rebrand), **plus real test
  execution** as evidence.
- **Reviewers:** [Backend survey](507e4b68-333c-47fb-a326-2002fcdbb9e2), [Voice survey](ee3a333a-fa31-4dc2-a397-831133bf0aee),
  [Docs + rebrand survey](e3da3b50-650f-4e32-b6ad-3a94e38fd4d1).

## Verdict

**Proceed.** The codebase is in strong shape and the rebrand's build/CI/deploy automation is clean.
One **medium blocking** code finding (voice STT cancel-path socket leak, pre-existing → **BUG-030**)
should be fixed before the voice runtime is treated as QA-closed. The rebrand left **human-facing
documentation debt** (ops runbooks, OpenAPI titles, a CLAUDE line) — the operational/visible items
are **fixed in this same pass**; the remaining architecture-spine drift is pre-existing and listed as
recommendations.

## Scores

| Area | Skill | Score | Gate |
|------|-------|------:|------|
| Backend (`backend/`) | code-review 0–100 | **91/100** | Pass |
| Voice runtime (`voice-agent/`) | code-review 0–100 | **88/100** | Blocked on BUG-030 (medium) |
| Docs + architecture consistency | code-review 0–100 | **74/100 → ~86 after this pass's doc fixes** | Conditional |
| **Overall** | — | **~88/100** | Proceed with the listed actions |

### Architecture scorecard (0–5)

| Dimension | Score /5 | Rationale |
|---|---:|---|
| NFR / SLA fitness | 3 | Per-slice telemetry, timeouts, degraded modes present; live-org Genesys SLO still unproven. |
| SLA failure modes | 4 | Fail-closed guardrails/identity/confidence; degraded fallbacks; **one cancel-path leak (BUG-030)**. |
| Modularity & boundaries | 5 | Pure domain, ports/adapters, ArchUnit enforced; providers behind replaceable adapters. |
| External dependency replaceability | 4 | LLM/STT/TTS/vector/memory behind ports; Genesys as transport adapter, not the brain. |
| Evolvability & industrialization | 3 | Clean seams; architecture doc spine is stale and would confuse onboarding. |

## Test Evidence (executed 2026-10-13)

- **Backend** `mvn clean test`: **766 tests, 0 failures, 0 errors** (incl. ArchUnit layering on `com.ai4cc.*`).
- **Voice runtime** `python -m unittest`: **732 tests, OK** (the `RuntimeError: boom` is an intentional
  crash-scenario fixture).
- **Voice runtime** `behave`: **15 features / 43 scenarios / 194 steps, 0 failed**.
- `OpenApiConfigTest` re-verified green after the title rebrand (2 tests, 0 failures).
- **Dev-env note:** the `voice-agent/.venv` was created under the old directory path; after the
  directory rename its script shebangs point at `…/voice-support-bot/.venv/...` and `./.venv/bin/behave`
  fails with `bad interpreter`. Workaround: `./.venv/bin/python -m behave`. **Recommendation:** recreate
  `.venv` after the rename (`python3 -m venv .venv && ./.venv/bin/pip install -r requirements.txt`).

## Blocking Findings

| # | Severity | Area | Finding | Evidence | Required fix |
|---|----------|------|---------|----------|--------------|
| 1 | Medium | Voice | STT in-flight WebSocket not discarded on `CancelFrame` (leak under reconnect storms) | `web_voice/streaming_stt_processor.py:215-218` vs `:211-212`, `:418-421` | **BUG-030** — discard `_session` on cancel + unit test. |

## Non-Blocking Findings (prioritized)

### Must fix before the release/redeploy step (rebrand doc debt) — **FIXED in this pass**
| Finding | Evidence | Status |
|---|---|---|
| Ops runbooks named the old GHCR packages `voice-support-{backend,voice}` | `docs/operations/release-process.md:65-66`, `first-deploy-runbook.md:72-73`, `deployment-eir-ai4cc-tst.md:351` | ✅ → `ai4cc-{backend,voice}` |
| Local Docker container names `voice-support-bot-{ollama,postgres}-1` | `voice-agent/README.md:314`, `docs/engineering/development-guide.md:447,451` | ✅ → `ai4cc-{ollama,postgres}-1` (DB role `voicesupport` preserved) |
| `CLAUDE.md` health-container line said `voice-support-bridge` | `CLAUDE.md:246` | ✅ → `ai4cc-bridge` |
| OpenAPI titles still "Voice Support Bot" (+ asserting tests) | `OpenApiConfig.java:38`, `openapi.yaml:3`, `OpenApiConfigTest.java:26`, `test_voice_openapi.py:39` | ✅ → "AI4CC — …" (tests updated + green) |
| `generalize-knowledge` skill referenced `voice-support-bot/` dir | `.cursor/skills/generalize-knowledge/SKILL.md` | ✅ → `ai4cc/` |

### Should fix before pilot (pre-existing, recommended — NOT changed in this pass)
| Finding | Evidence | Recommendation |
|---|---|---|
| Architecture spine stale vs reality (billing "not built", "through Sprint 11", legacy `/ask`,`/ask-stream`,`:8081`,`bot.py` read as current) | `docs/architecture/architecture.md:10,36-37,45-47,207-209,242-244` | Refresh the architecture doc to the shipped state (billing v0.9.4+, routing on `/converse` ADR-0055, OpenAI default ADR-0051). |
| `CLAUDE.md` content contradictions: "Still target-only: billing/BSS, invoice comparison" and "pilot pins `LLM_PROVIDER=mistral-api`" | `CLAUDE.md:21-22,112` vs `README.md:90-93`, `group_vars/backend.yml:44-51` (`llm_provider: openai`) | Reconcile CLAUDE.md with the shipped reality. |
| Entry-doc display names still "Voice Support Bot" | `docs/README.md:1`, `architecture.md:1`, `adrs/README.md:3`, `AGENTS.md:6`, `cahier-des-charges-fonctionnel.md` banner | Light display rebrand pass on the remaining human-facing headers. |
| Galaxion `source=pdf` + real `billrun` listing returns empty → billing explain can never succeed on that profile (fail-closed, safe) | `GalaxionBillRunDocumentAdapter`, `BillingExplanationService` | Pilot must use `source=eir` or fixture document sources until OQ-003; document/enforce in deploy review. |

### Can defer (maintainability)
| Finding | Evidence | Note |
|---|---|---|
| Classes > 200 non-blank lines | `AbstractChatClientAnswerAdapter`(234), `BackendTelemetry`(206), `KnowledgeConfig`(202), `InputGuardrail`(202) | Extract helpers when next touched. |
| Large voice modules | `streaming_stt_processor.py`(568), `websocket_app.py`(668), `server.py`(590) | Cohesive but hard to review; split opportunistically. |
| Java 17 vs stated Java 21 | `pom.xml:21`, `Dockerfile` | Align docs/toolchain or bump. |
| Legacy main class `VoiceSupportApplication` | `VoiceSupportApplication.java` | Cosmetic; rename to `Ai4ccApplication` when convenient. |
| Time-based voice tests (`asyncio.sleep`) | `tests/test_streaming_tts_processor.py`, `test_webrtc_signaling.py` | Prefer `SleepFrame`/`run_test`. |

## Rebrand-Integrity Verdict

**Build / CI / deploy automation: PASS.** No operational leftovers: `.github/workflows/images.yml`
builds `ai4cc-backend`/`ai4cc-voice`; Ansible/compose use `ai4cc-*` containers + `ghcr.io/.../ai4cc-*`;
Java is `com.ai4cc` / `ai4cc-backend`; `rg` for `com.voicesupport` / `voice-support-backend` /
`tgrimaud/V2V` in non-`.md` source = **0 hits**. Preserved infra identities (DB role `voicesupport`,
`/opt/voice-support`, `/var/backups/voice-support/*`, `voice-support.*` config prefix, Liquibase
`author: voice-support`, Ansible backup job names, Genesys key-id `voice-support-pilot`) are correctly
untouched. The only rebrand gaps were human-facing docs/titles — the operational ones are fixed here.

## Observability

Backend: slice timers (`llm_wording`, `bss`, `billing_clarify`+`sub_types`, warmup), correlation id
filter, privacy-aware converse logging. Voice: per-slice spans, deterministic cross-tier `traceparent`
from `correlation_id`, failure sanitization/redaction, unmeasured slices honestly marked. No required
instrumentation missing for the reviewed changes.

## Security & Privacy

No committed production secrets (env/vault only). REST errors return stable codes + correlation id
(no upstream leakage). Api-key gate on sensitive routes + converse. BSS read-only in V1. Escalation is
by-reference (PII-free on `/converse`; full handoff only behind the protected fetch). No PII in logs.

## Required Actions

1. Fix **BUG-030** (voice STT cancel-path teardown) + regression test.
2. (Recommended) Refresh `docs/architecture/architecture.md` and reconcile `CLAUDE.md` content lines
   (billing shipped, OpenAI default) with reality.
3. (Recommended) Finish the display rebrand on remaining entry-doc headers.
4. Recreate `voice-agent/.venv` after the directory rename.

## Residual Risk If Accepted

Deferring BUG-030 leaves a provider-socket leak only under abnormal cancel/reconnect storms (no
functional/safety impact on normal turns). Architecture-doc drift is an onboarding/clarity risk, not a
runtime risk. Class-size and Java-version items are maintainability debt.
