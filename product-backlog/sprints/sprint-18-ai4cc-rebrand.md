# Sprint 18 — AI4CC rebranding

## Sprint Objective

Rename the project from **Voice Support Bot** to **AI4CC**, aligning the Java package, Maven
coordinates, container images/names, CI, docs, the GitHub repository and the local working
directory with the AI4CC programme name (pilot hosts `*-ai4cc-*`, env `eir-ai4cc-tst`). Preserve
the live pilot infra identities that cannot be renamed without a coordinated migration.

## Scope

### In Scope

- **TASK-OPS-018** — rebrand to AI4CC:
  - Java package `com.voicesupport` → `com.ai4cc` (+ Maven groupId, ArchUnit params);
  - Maven coordinates / app / jar `voice-support-backend` → `ai4cc-backend`;
  - container images `voice-support-{backend,voice}` → `ai4cc-{backend,voice}` + container names `ai4cc-*`
    (CI `images.yml`, compose, Ansible + health gate);
  - display rebrand (README, CLAUDE, AGENTS, product-backlog) + factual `com.ai4cc` / `tgrimaud/AI4CC` refs;
  - GitHub repo `tgrimaud/V2V` → `tgrimaud/AI4CC` + local remote;
  - local directory `voice-support-bot` → `ai4cc`.

### Preserved (out of scope — live pilot infra identities)

- Postgres role/db `voicesupport`; host paths `/opt/voice-support`, `/var/backups/voice-support/*`;
  Spring config-property prefix `voice-support.*`. A coordinated infra migration can align them later.

## Tickets

| Ticket | Title | Status |
|--------|-------|--------|
| TASK-OPS-018 | Rebrand the project to AI4CC | 🟢 Code phases 1–4 merged into the sprint (`--no-ff`), review 92/100 (Pass); repo + local-dir rename done; pending release/redeploy under new image names — `tasks/task-ops-018-ai4cc-rebranding.md` |

## Status

✅ Done (closed 2026-10-13) — merged into `feat/restart-from-scratch`. Single ticket **TASK-OPS-018**
rebranded the project to AI4CC (Java package `com.ai4cc`, coordinates/images/containers `ai4cc-*`, docs,
GitHub repo, local dir). `mvn clean test` 766 green incl. ArchUnit; adversarial review 92/100 (Pass).
Preserved infra identities (DB role `voicesupport`, host paths, `voice-support.*` config prefix) documented.
A release tag + pilot redeploy under `ghcr.io/tgrimaud/ai4cc-*` is the remaining operational step.
