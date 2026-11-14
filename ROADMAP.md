# Roadmap

Target: v1.0 by 31 October 2026. Items are built top to bottom, each with tests.

## Foundation
- [x] Project setup, Jobs and Candidates CRUD, error handling, Docker, CI
- [x] Resume parsing into a structured profile
- [x] Pagination and sorting on job and candidate lists
- [x] Filter jobs by status and location; search candidates by name or email
- [x] OpenAPI / Swagger UI documentation
- [x] Flyway migrations replacing `ddl-auto: update`
- [x] Persist the parsed resume profile on the candidate (skills, experience, headline)
- [x] Resume upload as PDF with text extraction

## Applications
- [x] Application entity linking a candidate to a job, with a status pipeline
- [x] Application status transitions with validation; list applications per job and per candidate

## Matching
- [x] Embeddings for jobs and candidate profiles stored in pgvector
- [x] Semantic candidate ranking for a job
- [x] Match explanation: why a candidate fits, and what is missing
- [x] Skill-gap report for a candidate against a job

## Security
- [x] User accounts with password hashing
- [x] JWT login
- [x] Roles: ADMIN, RECRUITER, CANDIDATE

## Recruiter assistant
- [x] RAG chat over the candidate pool
- [x] Interview question generator tailored to a candidate and job
- [x] Tool-calling agent: shortlist candidates for a job
- [x] Tool-calling agent: draft rejection and next-round emails

## Release
- [x] Redis caching for job and match results
- [x] Testcontainers integration tests against real PostgreSQL
- [x] CSV export of ranked candidates
- [x] v1.0: architecture overview and end-to-end walkthrough in the README, version bump to 1.0.0

## v1.1

Target: v1.1 by 30 November 2026. Small improvements to the v1.0 product, built top to bottom, each with tests.

### Jobs and applications
- [x] Job lifecycle: `PUT /api/v1/jobs/{id}/status` moves a job from `DRAFT` to `OPEN`, `OPEN` to `CLOSED` and back; other moves return `409`
- [x] Application deadline: an optional `closesAt` on jobs, after which applications return `409`
- [x] Application status history: every stage change recorded with its time, listed at `GET /api/v1/applications/{id}/history`
- [x] Recruiter notes on applications: add and list notes
- [x] Pipeline summary: the number of applications in each stage for a job at `GET /api/v1/jobs/{jobId}/pipeline`
- [x] Candidate list filters by skill and minimum years of experience
- [x] Soft delete for jobs: deleted jobs disappear from lists and reads but keep their applications, and can be restored
- [x] Candidate validation gaps: phone number format, trimmed lower-case emails and length limits

### Security
- [x] Optional access control: with `AUTH_ENFORCED=true`, write and AI endpoints need a `RECRUITER` or `ADMIN` token
- [x] Change password for the signed-in user
- [x] Login throttling: a temporary lockout after repeated failed logins for one email
- [ ] Audit log of recruiter actions (job changes, application stage changes) with the acting user, readable by admins

### AI reliability
- [ ] Rate limiting on AI endpoints per client, answering `429` with `Retry-After`
- [ ] Configurable model retries and timeouts wired to Spring AI
- [ ] Cache the model's required-skill extraction per job so repeated skill-gap reports do not ask the model again
- [ ] Resume parsing edge cases: resumes too short to parse rejected, overlong text cut before the model call, implausible experience values dropped
- [ ] Batch embedding: one call embeds every candidate that has no embedding yet
- [ ] Recruiter chat fallback: when the chat model fails, return the retrieved candidates with a notice instead of a bare `503`

### Observability
- [ ] Correlation IDs: `X-Request-Id` accepted or generated, echoed in responses and written to every log line
- [ ] Structured JSON logging, switched on with `LOG_FORMAT`
- [ ] Actuator metrics and a Prometheus endpoint, with timers around model calls
- [ ] Liveness and readiness probes, with the database part of readiness

### Quality and operations
- [ ] Unit tests for `SkillGapService` and `MatchingService` with mocked collaborators
- [ ] Docker hardening: non-root user, container-aware JVM settings and an application healthcheck in Docker Compose
- [ ] Demo data: `SEED_DEMO_DATA=true` loads a few jobs and candidates into an empty database
- [ ] v1.1: release notes in the README and version bump to 1.1.0
