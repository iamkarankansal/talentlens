# Roadmap

One item is built per day, top to bottom. Each item ships with tests.

## Foundation
- [x] Project setup, Jobs and Candidates CRUD, error handling, Docker, CI
- [x] Resume parsing into a structured profile
- [ ] Pagination and sorting on job and candidate lists
- [ ] Filter jobs by status and location; search candidates by name or email
- [ ] OpenAPI / Swagger UI documentation
- [ ] Flyway migrations replacing `ddl-auto: update`
- [ ] Persist the parsed resume profile on the candidate (skills, experience, headline)
- [ ] Resume upload as PDF with text extraction

## Applications
- [ ] Application entity linking a candidate to a job, with a status pipeline
- [ ] Application status transitions with validation and history
- [ ] List applications per job and per candidate

## Matching
- [ ] Embeddings for jobs and candidate profiles stored in pgvector
- [ ] Semantic candidate ranking for a job
- [ ] Match explanation: why a candidate fits, and what is missing
- [ ] Skill-gap report for a candidate against a job
- [ ] Re-embed automatically when a job or resume changes

## Security
- [ ] User accounts with password hashing
- [ ] JWT login and refresh
- [ ] Roles: ADMIN, RECRUITER, CANDIDATE
- [ ] Rate limiting on AI endpoints

## Recruiter assistant
- [ ] RAG chat over the candidate pool
- [ ] Interview question generator tailored to a candidate and job
- [ ] Tool-calling agent: shortlist candidates for a job
- [ ] Tool-calling agent: draft rejection and next-round emails
- [ ] Interview scheduling with slots and feedback forms
- [ ] Interview feedback summarisation

## Production readiness
- [ ] Redis caching for job and match results
- [ ] Async resume processing with a job queue
- [ ] Structured logging and request tracing
- [ ] Metrics for model latency, token usage and cost
- [ ] Testcontainers integration tests against real PostgreSQL
- [ ] Configurable model provider (OpenAI, Gemini, Ollama)
- [ ] CSV export of ranked candidates
- [ ] Audit log of recruiter actions
