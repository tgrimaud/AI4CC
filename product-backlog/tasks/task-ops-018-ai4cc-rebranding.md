# TASK-OPS-018 — Rebrand the project to AI4CC

**Type:** Technical task (cross-cutting: backend package, build, container images, deploy, docs, repo)
**Status:** ✅ Done (2026-10-13) — **Sprint 18** (`feat/sprint-18-ai4cc-rebrand`). Code phases 1–4 done
(`mvn clean test` 766 green incl. ArchUnit). Phase 6 local dir `voice-support-bot` → `ai4cc` done. Phase 5
GitHub repo renamed `tgrimaud/V2V` → `tgrimaud/AI4CC` (via UI, 2026-10-13) and the local `origin` remote
updated to `git@github.com:tgrimaud/AI4CC.git` (verified via `git ls-remote`). Release tag + pilot redeploy
under `ghcr.io/tgrimaud/ai4cc-*` remains the only operational closing step. **Adversarial review 92/100 (Pass, 2026-10-13)** — no blocking finding;
preserved infra identities (DB role `voicesupport`, host paths, `voice-support.*` config prefix) documented.
Review: `docs/qa/task-ops-018-adversarial-review.md`.
**Priority:** Medium
**Epic:** EPIC-012 (operability / delivery hygiene)
**Surfaced by:** user request (2026-10-13) — align the project name with the AI4CC programme (pilot hosts
`*-ai4cc-*`, env `eir-ai4cc-tst`).

## Scope (user-confirmed, full rebrand)

| Area | From | To | Notes |
|------|------|----|-------|
| Java package | `com.ai4cc` | `com.ai4cc` | 373 files; move `com/voicesupport/**` dirs + `package`/`import` + ArchUnit `<param>` + base-package scans + logback logger |
| Maven coordinates | `com.ai4cc` / `voice-support-backend` | `com.ai4cc` / `ai4cc-backend` | `pom.xml` groupId/artifactId/name |
| Container images | `ghcr.io/tgrimaud/voice-support-{backend,voice}` | `ghcr.io/tgrimaud/ai4cc-{backend,voice}` | CI workflows + Ansible + pilot `.env` |
| Container/app names | `voice-support-backend` / `voice-support-bridge` / app display name | `ai4cc-*` | compose, healthcheck names, Spring `spring.application.name` |
| Docs / README display name | "Voice Support Bot" / `voice-support-bot` | "AI4CC" | human-facing titles; keep historical references where they document the past |
| GitHub repo | `tgrimaud/AI4CC` | `tgrimaud/AI4CC` | `gh repo rename` + update local `origin` remote |
| Local directory | `voice-support-bot/` | `ai4cc/` | **last step**, session-sensitive (absolute tool paths) — do after everything else and reopen the workspace |

## Must NOT rename (preserved infra identities)

These are **live identities of the running pilot**, not user-facing branding. Renaming them without a
coordinated data/stack migration would break the pilot, so they are kept as-is (same class of decision as the
DB role). A later, separately-ticketed migration can align them during a maintenance window if desired.

- **Postgres role / database `voicesupport`** (application.yml, `deploy/ansible/group_vars/backend.yml`,
  `deploy/compose/backend/.env.example`, `deploy/backup/pg-*.sh`): renaming breaks the app↔DB connection
  unless the DB role is migrated on every environment.
- **Host filesystem paths** `/opt/voice-support` (`compose_root`) and `/var/backups/voice-support/{postgres,redis}`:
  these point at the pilot's existing compose stacks (with vault-rendered `.env`) and existing backups.
  Renaming them orphans the live stacks/backups.
- **Spring config property prefix `voice-support.*`** (`application.yml` root key line ~147 + nested keys,
  `@ConfigurationProperties`/`@Value`, Ansible `java_opts -Dvoice-support.embedding.timeout-ms`): internal,
  non-user-facing config namespace; a rename is a deep backend refactor with no branding value.
- **Liquibase changeset `author: voice-support`** (`db/changelog/changes/001-vector-store.yaml`,
  `002-kb-source-state.yaml`, `bootstrap/db.changelog-bootstrap.yaml`): Liquibase identifies an applied
  changeset by `(id, author, filename)` in `DATABASECHANGELOG`. Changing the author on changesets already
  applied on the pilot DB would cause a validation/checksum mismatch and risk a re-run. Keep as-is.
- **Ansible backup job names** `"voice-support redis backup"` / `"voice-support postgres backup"`
  (`roles/compose_tier/tasks/backup.yml`): systemd timer identities already installed on the pilot host;
  renaming creates new timers and orphans the old ones.
- **Genesys HMAC `--key-id` default `voice-support-pilot`** (`voice-agent/scripts/genesys_local_client.py`):
  the key-id must match what the Genesys org / pilot expects for the AudioHook auth handshake; the test-client
  default is left aligned with the deployed contract (the real secret is `vault_genesys_audiohook_api_key`).
- Any secret/vault value.

> **Pre-existing (out of scope):** `deploy/ansible/qa-validate-ansible.sh` reports 2 pre-existing `key drift`
> failures (backend/voice rendered `.env` vs `.env.example`) that exist before this ticket and are unrelated
> to the rebrand (the rebrand changes values, not keys). 88/90 checks pass before and after.

## Phased plan (each phase ends green + committed)

1. **Java package** `com.ai4cc` → `com.ai4cc` (git mv dirs + source + ArchUnit params + logback +
   base-package scans). `mvn test` green incl. ArchUnit.
2. **Maven coordinates** groupId/artifactId/name. `mvn test` green.
3. **Container images + app/container names + CI + Ansible + compose** (NOT DB creds). Render-check Ansible
   templates; keep the DB role `voicesupport`.
4. **Docs / README / CLAUDE / AGENTS** display name → AI4CC (preserve historical accuracy).
5. **GitHub repo** `V2V` → `AI4CC` + update `origin` remote.
6. **Local directory** rename (last; reopen workspace at the new path).

## Acceptance criteria

- Backend `mvn test` green incl. ArchUnit after the package + coordinates rename.
- No `com.ai4cc` left in source; `com.ai4cc` is the only backend package root.
- Images build/push under `ghcr.io/tgrimaud/ai4cc-*` (verified at the next release/deploy).
- The Postgres role/db `voicesupport` is unchanged (no connection regression).
- Docs/README present the project as AI4CC; git remote points at the renamed repo.
- A release + deploy under the new image names is planned as the closing step (separate from this ticket's
  code changes).

## Risks / open questions

- The local-directory rename changes absolute paths used by the IDE/agent tooling; do it last and reopen.
- Image rename requires a fresh release tag + a pilot redeploy pulling the new image names (and updating the
  pilot `.env` image refs) — coordinate so the pilot is not left pointing at a non-existent image.
