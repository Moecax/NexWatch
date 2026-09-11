# NexWatch

Native Android companion app for the Zeblaze GTR 3 Pro (FitCloudPro platform).
Personal project: local-first health data, export, optional external sync, always-on connection.

- `CLAUDE.md` — rules Claude Code follows in this repo
- `docs/HANDOFF.md` — **start here**: ordered sessions with paste-ready prompts
- `docs/implementation-plan.md` — full architecture and milestones
- `docs/design-prompt.md` — prompt used to generate the Claude Design screens

Status: scaffold. Seed code exists for the theme (`:core:designsystem`), the canonical
model (`:core:model`), `WatchClient` (`:core:watch-api`) and `SyncProvider` (`:core:sync-api`).
Gradle files are generated in HANDOFF Session 1.
