# TalentLens

An AI-powered recruitment platform. Recruiters post jobs, candidates submit resumes, and a language model turns each resume into structured data that the rest of the system can search, rank and reason about.

Built with Java 21, Spring Boot 3, Spring AI, PostgreSQL (pgvector) and Docker.

![CI](https://github.com/iamkarankansal/talentlens/actions/workflows/ci.yml/badge.svg)

## What works today

- Jobs API: create, list, read, update, delete
- Paginated, sortable job and candidate lists
- Job filters by status and location, and candidate search by name or email
- Candidates API: create, list, read, update, delete, with case-insensitive unique emails
- Applications: a candidate applies to an open job once, and the application starts in the `APPLIED` stage of the hiring pipeline
- Application status changes that follow the pipeline, and paginated application lists per job (filterable by status) and per candidate
- Resume upload as PDF, with the text extracted and stored on the candidate
- Resume parsing: the model extracts headline, experience, skills and education as JSON
- Parsed headline, years of experience and skills are stored on the candidate and returned with it
- Embeddings of jobs and candidate profiles, computed by the embedding model and stored in pgvector
- Semantic candidate ranking for a job, by cosine similarity of the stored embeddings
- Match explanations: the model summarises why a candidate fits a job, listing strengths and missing requirements
- OpenAPI 3 description of the API with a Swagger UI to try it out
- Versioned database schema managed by Flyway migrations
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
| `POST` | `/api/v1/candidates/{id}/resume` | Upload a PDF resume (multipart field `file`) and store its text |
| `POST` | `/api/v1/candidates/{id}/resume/parse` | Extract a structured profile from the candidate's resume and store it on the candidate |
| `POST` | `/api/v1/applications` | Apply a candidate to a job (`candidateId`, `jobId`) |
| `GET` `DELETE` | `/api/v1/applications/{id}` | Read or delete an application |
| `PUT` | `/api/v1/applications/{id}/status` | Move an application to another stage (`status`) |
| `GET` | `/api/v1/jobs/{jobId}/applications` | List a job's applications (paginated; filter with `status`) |
| `GET` | `/api/v1/candidates/{candidateId}/applications` | List a candidate's applications (paginated) |
| `POST` | `/api/v1/jobs/{jobId}/embedding` | Compute and store the job's embedding |
| `POST` | `/api/v1/candidates/{candidateId}/embedding` | Compute and store the candidate's embedding |
| `GET` | `/api/v1/jobs/{jobId}/matches` | Rank candidates for the job by semantic similarity (`limit`, default 10) |
| `GET` | `/api/v1/jobs/{jobId}/matches/{candidateId}/explanation` | Explain why the candidate fits the job and what is missing |

Example:

```bash
curl -X POST localhost:8080/api/v1/candidates \
  -H 'Content-Type: application/json' \
  -d '{"fullName":"Asha Verma","email":"asha@example.com","resumeText":"5 years of Java, Spring Boot and Kafka..."}'

curl -X POST localhost:8080/api/v1/candidates/1/resume/parse
```

A resume can also be uploaded as a PDF. The text is extracted with Apache PDFBox and replaces the candidate's resume text. Files that are not PDFs, password-protected PDFs and scanned PDFs with no text layer are rejected with `400`; files over `RESUME_MAX_FILE_SIZE` get `413`.

```bash
curl -X POST localhost:8080/api/v1/candidates/1/resume -F file=@resume.pdf
```

Parsing a resume saves the headline, years of experience and skills on the candidate, so later reads of the candidate include `headline`, `experienceYears`, `skills` and `profileParsedAt`. Skills are trimmed and de-duplicated ignoring case. Changing a candidate's resume text clears the stored profile until the resume is parsed again.

List endpoints accept `page` (from 0), `size` (1-100, default 20) and `sort` (`field` or `field,asc|desc`, default `createdAt,desc`), and return `content` with `page`, `size`, `totalElements` and `totalPages`. Jobs sort by `id`, `title`, `location`, `minExperienceYears`, `status` or `createdAt`; candidates by `id`, `fullName`, `email` or `createdAt`.

```bash
curl 'localhost:8080/api/v1/jobs?page=0&size=10&sort=title,asc'
```

The job list can be narrowed with `status` (`DRAFT`, `OPEN` or `CLOSED`) and `location` (case-insensitive, matches any part of the location). The candidate list takes `q`, which matches any part of the name or email, ignoring case. Filters combine with paging and sorting.

```bash
curl 'localhost:8080/api/v1/jobs?status=OPEN&location=bengaluru'
curl 'localhost:8080/api/v1/candidates?q=verma'
```

An application links a candidate to a job and tracks where they are in the hiring pipeline: `APPLIED`, `SCREENING`, `INTERVIEW`, `OFFER` and `HIRED`, or `REJECTED` / `WITHDRAWN` when it ends early. Only `OPEN` jobs accept applications, and a candidate can apply to a job once; both cases otherwise return `409`. Deleting a candidate or a job also deletes their applications.

```bash
curl -X POST localhost:8080/api/v1/applications \
  -H 'Content-Type: application/json' \
  -d '{"candidateId":1,"jobId":1}'
```

An application moves forward one stage at a time (`APPLIED` → `SCREENING` → `INTERVIEW` → `OFFER` → `HIRED`). Any application that is still open can be moved to `REJECTED` or `WITHDRAWN`. `HIRED`, `REJECTED` and `WITHDRAWN` are final. Any other change, such as skipping a stage or reopening a rejected application, returns `409`.

```bash
curl -X PUT localhost:8080/api/v1/applications/1/status \
  -H 'Content-Type: application/json' \
  -d '{"status":"SCREENING"}'

curl 'localhost:8080/api/v1/jobs/1/applications?status=SCREENING'
curl 'localhost:8080/api/v1/candidates/1/applications'
```

Application lists take the same `page`, `size` and `sort` parameters as the other lists and sort by `id`, `status`, `createdAt` or `updatedAt`.

Jobs and candidates can be turned into embedding vectors for semantic matching. A job's embedding covers its title, location, minimum experience and description; a candidate's covers the parsed headline, experience and skills plus the resume text (cut to the first 8000 characters). Vectors are stored in the `job_embeddings` and `candidate_embeddings` tables as pgvector `vector` columns and are replaced each time the endpoint is called. A candidate with neither a resume nor a parsed profile returns `409`, and a model failure returns `503`. Deleting a job or candidate deletes its embedding.

```bash
curl -X POST localhost:8080/api/v1/jobs/1/embedding
curl -X POST localhost:8080/api/v1/candidates/1/embedding
```

Once a job and some candidates have embeddings, `GET /api/v1/jobs/{jobId}/matches` ranks the candidates by the cosine similarity of their embedding to the job's, best first. Each match has its `rank`, `candidateId`, `fullName`, `headline` and a `score` from -1 to 1 (rounded to four decimals); `candidatesConsidered` counts every candidate that could be compared. `limit` (1-50, default 10) caps the number of matches. Candidates without an embedding, or whose vector has a different size from the job's (for example after changing `OPENAI_EMBEDDING_MODEL`), are left out until they are embedded again. A job without an embedding returns `409`. Ranking reads stored vectors only and never calls the model.

```bash
curl 'localhost:8080/api/v1/jobs/1/matches?limit=5'
```

`GET /api/v1/jobs/{jobId}/matches/{candidateId}/explanation` asks the chat model why a candidate fits a job. It compares the same job and candidate texts used for embeddings and returns a short `summary`, the job requirements the candidate meets (`strengths`) and those they are missing or only partly meet (`gaps`). The response also carries the candidate's `fullName` and the ranking `score`, which is `null` unless both the job and the candidate have comparable embeddings; the explanation itself does not need embeddings. A candidate with neither a resume nor a parsed profile returns `409`, and a model failure returns `503`. Each call asks the model again, so the wording can vary.

```bash
curl localhost:8080/api/v1/jobs/1/matches/3/explanation
```

Interactive documentation is generated from the code: Swagger UI at `http://localhost:8080/swagger-ui.html` and the OpenAPI document at `/v3/api-docs` (JSON) or `/v3/api-docs.yaml`. Set `API_DOCS_ENABLED=false` to turn both off.

The database schema lives in `src/main/resources/db/migration` and is applied by Flyway on startup; Hibernate only validates that the entities match it. To change the schema, add a new `V<n>__description.sql` file rather than editing an existing one. Migrations that only work on one database live in `src/main/resources/db/vendor/<database>`; the pgvector tables are created there, so PostgreSQL needs the `vector` extension available (the `pgvector/pgvector` image in `docker-compose.yml` has it). A database created before migrations were introduced is baselined at version 1 automatically.

## Project layout

```
src/main/java/com/talentlens
├── job/         Job postings
├── candidate/   Candidates and their resume text
├── application/ Candidates applying to jobs, and the hiring pipeline
├── ai/          Model-backed features (resume parsing, match explanations)
├── matching/    Embeddings in pgvector, semantic candidate ranking and match explanations
├── config/      Application configuration (OpenAPI)
└── common/      Error handling shared by all modules
```

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `OPENAI_API_KEY` | none | Required for AI endpoints |
| `OPENAI_MODEL` | `gpt-4o-mini` | Chat model |
| `OPENAI_EMBEDDING_MODEL` | `text-embedding-3-small` | Embedding model for jobs and candidates |
| `DB_URL` | `jdbc:postgresql://localhost:5432/talentlens` | Database URL |
| `DB_USERNAME` / `DB_PASSWORD` | `talentlens` | Database credentials |
| `API_DOCS_ENABLED` | `true` | Serve Swagger UI and the OpenAPI document |
| `RESUME_MAX_FILE_SIZE` | `5MB` | Largest resume PDF accepted for upload |
