# Roadmap

Target: v1.0 by 31 October 2026. Items are built top to bottom, each with tests.

## Foundation
- [x] Project setup, Jobs and Candidates CRUD, error handling, Docker, CI
- [x] Resume parsing into a structured profile
- [x] Pagination and sorting on job and candidate lists
- [ ] Filter jobs by status and location; search candidates by name or email
- [ ] OpenAPI / Swagger UI documentation
- [ ] Flyway migrations replacing `ddl-auto: update`
- [ ] Persist the parsed resume profile on the candidate (skills, experience, headline)
- [ ] Resume upload as PDF with text extraction

## Applications
- [ ] Application entity linking a candidate to a job, with a status pipeline
- [ ] Application status transitions with validation; list applications per job and per candidate

## Matching
- [ ] Embeddings for jobs and candidate profiles stored in pgvector
- [ ] Semantic candidate ranking for a job
- [ ] Match explanation: why a candidate fits, and what is missing
- [ ] Skill-gap report for a candidate against a job

## Security
- [ ] User accounts with password hashing
- [ ] JWT login
- [ ] Roles: ADMIN, RECRUITER, CANDIDATE

## Recruiter assistant
- [ ] RAG chat over the candidate pool
- [ ] Interview question generator tailored to a candidate and job
- [ ] Tool-calling agent: shortlist candidates for a job
- [ ] Tool-calling agent: draft rejection and next-round emails

## Release
- [ ] Redis caching for job and match results
- [ ] Testcontainers integration tests against real PostgreSQL
- [ ] CSV export of ranked candidates
- [ ] v1.0: architecture overview and end-to-end walkthrough in the README, version bump to 1.0.0
