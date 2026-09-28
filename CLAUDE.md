# CLAUDE.md

Spring Boot store API: JWT auth, ADMIN/USER roles, pagination (page/size/sortBy/direction),
response DTOs, centralized exception handling, OpenAPI docs (springdoc, /swagger-ui.html).

## Workflow rules

- One milestone at a time, each on its own branch: `feat/<milestone-name>`.
- Before writing code for a milestone, present a short plan (files to touch, approach)
  and wait for approval.
- Small commits in Conventional Commits style (`feat:`, `fix:`, `test:`, `chore:`, `docs:`).
- Run `.\mvnw.cmd verify` before every commit (Maven is not on PATH; use the wrapper).
  Never commit failing code. `verify` runs Testcontainers ITs, so Docker must be running
  (`-DskipITs` runs unit tests only, but never skip ITs before a commit).
- When a milestone is done: push the branch and open a PR with `gh pr create`.
- PR descriptions must include: what changed, why, alternatives considered,
  new dependencies with reasons, and how to test it manually.
- NEVER merge PRs. NEVER push to `main` after the initial commit.
- Never commit secrets. Use `${ENV_VAR}` placeholders; keep `.env.example` updated.
- Never run `gh auth` or handle tokens.
- Do not change existing API contracts (endpoints, request/response DTOs, pagination
  params `page`/`size`/`sortBy`/`direction`, role rules) unless the milestone requires it, and call
  it out explicitly in the PR.
