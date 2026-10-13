# Adversarial Code Review — TASK-OPS-018 (AI4CC rebranding)

- **Ticket:** TASK-OPS-018 — rebrand the project to AI4CC
- **Branch:** `task/TASK-OPS-018-ai4cc-rebranding` (Sprint 18)
- **Date:** 2026-10-13
- **Verdict:** **Pass**
- **Score:** 92/100
- **Scope reviewed:** code phases 1–4 (Java package, Maven coordinates, container images/names +
  CI/compose/Ansible, docs display name). Phases 5 (GitHub repo rename) and 6 (local directory rename) are
  external/operational and executed under explicit user validation, outside this code review.

## Blocking findings

_None._

## Non-Blocking Findings

| # | Severity | Finding | Resolution |
|---|----------|---------|------------|
| 1 | Low | Pre-existing `qa-validate-ansible.sh` reports 2 `key drift` failures (backend/voice `.env` vs `.env.example`) | Verified identical before and after the rebrand (88/90 pass both ways); unrelated to this ticket, left untouched and documented. |
| 2 | Low | `/opt/voice-support`, `/var/backups/voice-support/*` host paths and the `voice-support.*` Spring config prefix keep the old name | Deliberate: live pilot infra identities; renaming requires a coordinated migration. Documented in the ticket "preserved infra identities". |
| 3 | Info | Historical records (`done-tasks.md`, closed-sprint files) still say "Voice Support Bot" | Intentional — historical accuracy; only living entry-point docs rebranded. |
| 4 | Info | `V2V` acronym kept where it means voice-to-voice (media plane) | Correct — only the `tgrimaud/V2V` repo identity is renamed to `tgrimaud/AI4CC`. |

## Story Coverage

- Java package `com.voicesupport` → `com.ai4cc` across 373 files + ArchUnit params + Maven groupId. ✅
- Maven coordinates/app/jar name `voice-support-backend` → `ai4cc-backend`. ✅
- Container images `voice-support-{backend,voice}` → `ai4cc-{backend,voice}`; container names `ai4cc-*`
  (CI workflow, compose, Ansible group_vars + health gate + backup defaults). ✅
- Display rebrand (README, CLAUDE, AGENTS, product-backlog README) + factual package/repo refs in docs. ✅
- DB role `voicesupport` preserved (no app↔DB regression). ✅

## Test Evidence

- `mvn clean test` → **766 tests, 0 failures, 0 errors** including ArchUnit (which asserts the
  `com.ai4cc.*` layering rules via the renamed `<param>` entries). Green after phases 1 and 2.
- `qa-validate-ansible.sh` → 88 pass / 2 pre-existing fail (unchanged by the rebrand).

## Observability

Not runtime-affecting beyond identifiers; telemetry slice names and semantics unchanged. Logger MDC app name
flips from `voice-support-backend` to `ai4cc-backend` (expected, cosmetic).

## Security

No secret touched. DB role/password untouched. No `--password=` on argv. No vault value printed.

## Required Actions

- Phase 5/6 (GitHub repo rename + local dir rename) must run under explicit user validation.
- A release tag + pilot redeploy under the new image names (`ghcr.io/tgrimaud/ai4cc-*`) and updated pilot
  `.env` image refs is the closing operational step (so the pilot is not left pointing at a renamed image).

## Residual (accepted)

Preserved infra identities (DB role, host paths, config prefix) keep the old name by design; a coordinated
infra migration can align them later if the pilot owner wants.
