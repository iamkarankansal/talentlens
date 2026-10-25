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
- [ ] CSV export of ranked candidates
- [ ] v1.0: architecture overview and end-to-end walkthrough in the README, version bump to 1.0.0
