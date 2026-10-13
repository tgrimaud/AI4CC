# TASK-OPS-018 — Rebrand the project to AI4CC

**Type:** Technical task (cross-cutting: backend package, build, container images, deploy, docs, repo)
**Status:** 🟡 In progress (2026-10-13) — **Sprint 18** (`feat/sprint-18-ai4cc-rebrand`), ticket branch
`task/TASK-OPS-018-ai4cc-rebranding`.
**Priority:** Medium
**Epic:** EPIC-012 (operability / delivery hygiene)
**Surfaced by:** user request (2026-10-13) — align the project name with the AI4CC programme (pilot hosts
`*-ai4cc-*`, env `eir-ai4cc-tst`).

## Scope (user-confirmed, full rebrand)

| Area | From | To | Notes |
|------|------|----|-------|
| Java package | `com.voicesupport` | `com.ai4cc` | 373 files; move `com/voicesupport/**` dirs + `package`/`import` + ArchUnit `<param>` + base-package scans + logback logger |
| Maven coordinates | `com.voicesupport` / `voice-support-backend` | `com.ai4cc` / `ai4cc-backend` | `pom.xml` groupId/artifactId/name |
| Container images | `ghcr.io/tgrimaud/voice-support-{backend,voice}` | `ghcr.io/tgrimaud/ai4cc-{backend,voice}` | CI workflows + Ansible + pilot `.env` |
| Container/app names | `voice-support-backend` / `voice-support-bridge` / app display name | `ai4cc-*` | compose, healthcheck names, Spring `spring.application.name` |
| Docs / README display name | "Voice Support Bot" / `voice-support-bot` | "AI4CC" | human-facing titles; keep historical references where they document the past |
| GitHub repo | `tgrimaud/V2V` | `tgrimaud/AI4CC` | `gh repo rename` + update local `origin` remote |
| Local directory | `voice-support-bot/` | `ai4cc/` | **last step**, session-sensitive (absolute tool paths) — do after everything else and reopen the workspace |

## Must NOT rename (landmines)

- **Postgres role / database `voicesupport`** (application.yml, `deploy/ansible/group_vars/backend.yml`,
  `deploy/compose/backend/.env.example`, `deploy/backup/pg-*.sh`): this is the live infra identity of the
  **running pilot DB**. Renaming it would break the app↔DB connection unless the DB role is also migrated
  on every environment. Keep as-is (it is not user-facing branding).
- Any secret/vault value.

## Phased plan (each phase ends green + committed)

1. **Java package** `com.voicesupport` → `com.ai4cc` (git mv dirs + source + ArchUnit params + logback +
   base-package scans). `mvn test` green incl. ArchUnit.
2. **Maven coordinates** groupId/artifactId/name. `mvn test` green.
3. **Container images + app/container names + CI + Ansible + compose** (NOT DB creds). Render-check Ansible
   templates; keep the DB role `voicesupport`.
4. **Docs / README / CLAUDE / AGENTS** display name → AI4CC (preserve historical accuracy).
5. **GitHub repo** `V2V` → `AI4CC` + update `origin` remote.
6. **Local directory** rename (last; reopen workspace at the new path).

## Acceptance criteria

- Backend `mvn test` green incl. ArchUnit after the package + coordinates rename.
- No `com.voicesupport` left in source; `com.ai4cc` is the only backend package root.
- Images build/push under `ghcr.io/tgrimaud/ai4cc-*` (verified at the next release/deploy).
- The Postgres role/db `voicesupport` is unchanged (no connection regression).
- Docs/README present the project as AI4CC; git remote points at the renamed repo.
- A release + deploy under the new image names is planned as the closing step (separate from this ticket's
  code changes).

## Risks / open questions

- The local-directory rename changes absolute paths used by the IDE/agent tooling; do it last and reopen.
- Image rename requires a fresh release tag + a pilot redeploy pulling the new image names (and updating the
  pilot `.env` image refs) — coordinate so the pilot is not left pointing at a non-existent image.
