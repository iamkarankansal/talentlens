# TalentLens

An AI-powered recruitment platform. Recruiters post jobs, candidates submit resumes, and a language model turns each resume into structured data that the rest of the system can search, rank and reason about.

Built with Java 21, Spring Boot 3, Spring AI, PostgreSQL (pgvector) and Docker.

![CI](https://github.com/iamkarankansal/talentlens/actions/workflows/ci.yml/badge.svg)

## What works today

- Jobs API: create, list, read, update, delete
- Paginated, sortable job and candidate lists
- Job filters by status and location, and candidate search by name or email
- Candidates API: create, list, read, update, delete, with case-insensitive unique emails
- Resume parsing: the model extracts headline, experience, skills and education as JSON
- OpenAPI 3 description of the API with a Swagger UI to try it out
- Consistent JSON error responses, including per-field validation errors
- Tests that run on in-memory H2 with the model mocked, so no API key is needed

See [ROADMAP.md](ROADMAP.md) for what is coming next.

## Run it

You need Docker and an OpenAI API key.

```bash
cp .env.example .env      # then put your key in .env
docker compose up --build
```

The API is served at `http://localhost:8080`. The `.env` file is git-ignored; never commit it.

To run only the tests (no key, no database needed):

```bash
mvn test
```

## API

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/jobs` | Create a job |
| `GET` | `/api/v1/jobs` | List jobs (paginated; filter with `status`, `location`) |
| `GET` `PUT` `DELETE` | `/api/v1/jobs/{id}` | Read, update or delete a job |
| `POST` | `/api/v1/candidates` | Create a candidate |
| `GET` | `/api/v1/candidates` | List candidates (paginated; search with `q`) |
| `GET` `PUT` `DELETE` | `/api/v1/candidates/{id}` | Read, update or delete a candidate |
| `POST` | `/api/v1/candidates/{id}/resume/parse` | Extract a structured profile from the candidate's resume |

Example:

```bash
curl -X POST localhost:8080/api/v1/candidates \
  -H 'Content-Type: application/json' \
  -d '{"fullName":"Asha Verma","email":"asha@example.com","resumeText":"5 years of Java, Spring Boot and Kafka..."}'

curl -X POST localhost:8080/api/v1/candidates/1/resume/parse
```

List endpoints accept `page` (from 0), `size` (1-100, default 20) and `sort` (`field` or `field,asc|desc`, default `createdAt,desc`), and return `content` with `page`, `size`, `totalElements` and `totalPages`. Jobs sort by `id`, `title`, `location`, `minExperienceYears`, `status` or `createdAt`; candidates by `id`, `fullName`, `email` or `createdAt`.

```bash
curl 'localhost:8080/api/v1/jobs?page=0&size=10&sort=title,asc'
```

The job list can be narrowed with `status` (`DRAFT`, `OPEN` or `CLOSED`) and `location` (case-insensitive, matches any part of the location). The candidate list takes `q`, which matches any part of the name or email, ignoring case. Filters combine with paging and sorting.

```bash
curl 'localhost:8080/api/v1/jobs?status=OPEN&location=bengaluru'
curl 'localhost:8080/api/v1/candidates?q=verma'
```

Interactive documentation is generated from the code: Swagger UI at `http://localhost:8080/swagger-ui.html` and the OpenAPI document at `/v3/api-docs` (JSON) or `/v3/api-docs.yaml`. Set `API_DOCS_ENABLED=false` to turn both off.

## Project layout

```
src/main/java/com/talentlens
├── job/         Job postings
├── candidate/   Candidates and their resume text
├── ai/          Model-backed features (resume parsing)
├── config/      Application configuration (OpenAPI)
└── common/      Error handling shared by all modules
```

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `OPENAI_API_KEY` | none | Required for AI endpoints |
| `OPENAI_MODEL` | `gpt-4o-mini` | Chat model |
| `DB_URL` | `jdbc:postgresql://localhost:5432/talentlens` | Database URL |
| `DB_USERNAME` / `DB_PASSWORD` | `talentlens` | Database credentials |
| `API_DOCS_ENABLED` | `true` | Serve Swagger UI and the OpenAPI document |
