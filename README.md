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
- Skill-gap reports: the job's required skills, extracted by the model, compared with the candidate's parsed skills and experience
- Recruiter assistant chat: questions about the candidate pool answered by the chat model from the most relevant candidate profiles, retrieved by embedding similarity, with those candidates listed as sources
- User accounts: registration with case-insensitive unique emails and BCrypt-hashed passwords
- JWT login: email and password exchanged for a signed, expiring access token, and the current user read back from it
- Roles: every account is an `ADMIN`, `RECRUITER` or `CANDIDATE`; new accounts are candidates and admins assign roles
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
| `GET` | `/api/v1/jobs/{jobId}/matches/{candidateId}/skill-gap` | Compare the job's required skills with the candidate's skills |
| `POST` | `/api/v1/assistant/chat` | Ask the recruiter assistant a question about the candidate pool (`question`, `topK`) |
| `POST` | `/api/v1/users` | Register a user account (`email`, `fullName`, `password`) |
| `GET` | `/api/v1/users/{id}` | Read a user account |
| `PUT` | `/api/v1/users/{id}/role` | Change a user's `role` (admin token required) |
| `POST` | `/api/v1/auth/login` | Exchange `email` and `password` for a JWT access token |
| `GET` | `/api/v1/auth/me` | Read the user the bearer token belongs to |

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

`GET /api/v1/jobs/{jobId}/matches/{candidateId}/skill-gap` lists the skills the job requires (`requiredSkills`, extracted from the job by the chat model) and splits them into those on the candidate's parsed profile (`matchedSkills`) and those that are not (`missingSkills`). Skills the candidate has that the job does not ask for are in `additionalSkills`. Skills are compared ignoring case, spacing and hyphens, so `Spring-Boot` matches `spring boot`. `coverage` is the share of required skills the candidate has, from 0 to 1, or `null` when the job names none. The report also compares the job's `requiredExperienceYears` with the candidate's `candidateExperienceYears`; `meetsExperience` is `null` when either is unknown. The candidate's resume must be parsed first, otherwise the endpoint returns `409`; a model failure returns `503`.

```bash
curl localhost:8080/api/v1/jobs/1/matches/3/skill-gap
```

`POST /api/v1/assistant/chat` answers a recruiter's `question` (up to 2000 characters) about the candidate pool with retrieval-augmented generation. The question is embedded with the embedding model, the `topK` candidates (1-20, default 5) whose stored embeddings are closest to it are retrieved, and their profiles (headline, experience, skills and the start of the resume) are given to the chat model, which answers from those profiles only. The response has the `answer` and the `sources` it was based on, each with `candidateId`, `fullName`, `headline` and similarity `score`. Only candidates with an embedding are searched; if none have one the endpoint returns `409`, and a model failure returns `503`.

```bash
curl -X POST localhost:8080/api/v1/assistant/chat \
  -H 'Content-Type: application/json' \
  -d '{"question":"Who has run Kafka in production?","topK":3}'
```

`POST /api/v1/users` creates a user account. Emails are trimmed, stored in lower case and must be unique ignoring case (`409` otherwise). Passwords must be 8 to 72 characters (BCrypt ignores anything longer) and are stored only as a salted BCrypt hash; no response ever includes the password or its hash. `BCRYPT_STRENGTH` sets the BCrypt cost (log rounds, 4 to 31).

```bash
curl -X POST localhost:8080/api/v1/users \
  -H 'Content-Type: application/json' \
  -d '{"email":"recruiter@example.com","fullName":"Riya Sen","password":"a-long-passphrase"}'
```

`POST /api/v1/auth/login` checks an email and password and returns an `accessToken` (an HS256-signed JWT whose subject is the user id), its `tokenType` (`Bearer`) and `expiresIn` (seconds, from `JWT_EXPIRATION`). A wrong password and an unknown email both return `401` with the same message. Send the token as `Authorization: Bearer <token>`; `GET /api/v1/auth/me` returns the account it belongs to, or `401` if the token is missing, malformed, signed with another key, from another issuer, expired, or belongs to a deleted user. Set `JWT_SECRET` to at least 32 random bytes in production; without it a random key is generated at startup and every token stops working when the application restarts.

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"recruiter@example.com","password":"a-long-passphrase"}' | jq -r .accessToken)

curl localhost:8080/api/v1/auth/me -H "Authorization: Bearer $TOKEN"
```

Every account has a `role`: `ADMIN`, `RECRUITER` or `CANDIDATE`. It is returned with the user and written into access tokens as the `role` claim. Registration always creates a `CANDIDATE`, except for the account registered with `ADMIN_EMAIL`, which becomes the first `ADMIN`. An admin changes roles with `PUT /api/v1/users/{id}/role`; without a token the endpoint returns `401`, with a non-admin token `403`, and demoting the last remaining admin returns `409`. Roles are checked against the database, so a change applies straight away, even to tokens issued before it.

```bash
curl -X PUT localhost:8080/api/v1/users/2/role \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"role":"RECRUITER"}'
```

Interactive documentation is generated from the code: Swagger UI at `http://localhost:8080/swagger-ui.html` and the OpenAPI document at `/v3/api-docs` (JSON) or `/v3/api-docs.yaml`. Set `API_DOCS_ENABLED=false` to turn both off.

The database schema lives in `src/main/resources/db/migration` and is applied by Flyway on startup; Hibernate only validates that the entities match it. To change the schema, add a new `V<n>__description.sql` file rather than editing an existing one. Migrations that only work on one database live in `src/main/resources/db/vendor/<database>`; the pgvector tables are created there, so PostgreSQL needs the `vector` extension available (the `pgvector/pgvector` image in `docker-compose.yml` has it). A database created before migrations were introduced is baselined at version 1 automatically.

## Project layout

```
src/main/java/com/talentlens
├── job/         Job postings
├── candidate/   Candidates and their resume text
├── application/ Candidates applying to jobs, and the hiring pipeline
├── ai/          Model-backed features (resume parsing, match explanations, skill extraction, assistant answers)
├── matching/    Embeddings in pgvector, semantic candidate ranking, match explanations and skill gaps
├── assistant/   Recruiter assistant: chat over the candidate pool
├── user/        User accounts with hashed passwords and roles
├── auth/        JWT login and access tokens
├── config/      Application configuration (OpenAPI, password hashing, JWT settings)
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
| `BCRYPT_STRENGTH` | `10` | BCrypt cost (log rounds) for password hashes |
| `JWT_SECRET` | random per start | HS256 signing key for access tokens, at least 32 bytes |
| `JWT_EXPIRATION` | `1h` | How long an access token is valid (e.g. `30m`, `8h`) |
| `JWT_ISSUER` | `talentlens` | Issuer written into and required on access tokens |
| `ADMIN_EMAIL` | none | The account registered with this email becomes an `ADMIN` |
