# TalentLens

An AI-powered recruitment platform. Recruiters post jobs, candidates submit resumes, and a language model turns each resume into structured data that the rest of the system can search, rank and reason about.

Built with Java 21, Spring Boot 3, Spring AI, PostgreSQL (pgvector) and Docker.

![CI](https://github.com/iamkarankansal/talentlens/actions/workflows/ci.yml/badge.svg)

## What works today

- Jobs API: create, list, read, update, delete
- Soft delete for jobs: a deleted job drops out of lists and reads but keeps its applications, and can be restored
- Job lifecycle: drafts are published, open jobs closed and closed jobs reopened, with any other move rejected
- Optional application deadline (`closesAt`) on jobs, after which applications are refused
- Paginated, sortable job and candidate lists
- Job filters by status and location, and candidate search by name or email
- Candidate filters by parsed skill and minimum years of experience
- Candidates API: create, list, read, update, delete, with case-insensitive unique emails
- Candidate input checks: names, emails and phone numbers are trimmed, emails lower-cased, phone numbers must have 7 to 15 digits with common separators, and names and emails are length-limited
- Applications: a candidate applies to an open job once, and the application starts in the `APPLIED` stage of the hiring pipeline
- Application status changes that follow the pipeline, and paginated application lists per job (filterable by status) and per candidate
- Application status history: every stage an application enters is recorded with its time
- Recruiter notes on applications, listed oldest first
- Pipeline summary: how many of a job's applications sit in each stage
- Resume upload as PDF, with the text extracted and stored on the candidate
- Resume parsing: the model extracts headline, experience, skills and education as JSON; resumes too short to parse are refused, overlong ones cut before the model call, and implausible experience dropped
- Parsed headline, years of experience and skills are stored on the candidate and returned with it
- Embeddings of jobs and candidate profiles, computed by the embedding model and stored in pgvector, one at a time or every candidate still missing one in a single batch call
- Semantic candidate ranking for a job, by cosine similarity of the stored embeddings
- CSV export of a job's ranked candidates, ready to open in a spreadsheet
- Match explanations: the model summarises why a candidate fits a job, listing strengths and missing requirements
- Skill-gap reports: the job's required skills, extracted by the model, compared with the candidate's parsed skills and experience
- Recruiter assistant chat: questions about the candidate pool answered by the chat model from the most relevant candidate profiles, retrieved by embedding similarity, with those candidates listed as sources, and still listed with a notice when the chat model is down
- Interview question generator: the model writes questions tailored to a candidate and a job, each with the requirement or claim it probes and why it is worth asking
- Shortlisting agent: the model calls tools to rank candidates for a job and read their profiles, then picks a shortlist with a reason for each; picks that are not real candidates are dropped
- Email drafting agent: the model calls tools to read an application and the candidate's profile, then drafts a rejection or next-round email for the recruiter to review; nothing is sent
- Caching of job lookups, candidate rankings, match explanations and per-job required skills, in Redis or in memory, cleared when a job, candidate or embedding changes
- User accounts: registration with case-insensitive unique emails and BCrypt-hashed passwords
- JWT login: email and password exchanged for a signed, expiring access token, and the current user read back from it
- Password change for the signed-in user, checked against the current password
- Login throttling: an email is locked out for a while after repeated failed logins, answered with `429` and `Retry-After`
- Roles: every account is an `ADMIN`, `RECRUITER` or `CANDIDATE`; new accounts are candidates and admins assign roles
- Rate limiting on AI endpoints per client, answered with `429` and `Retry-After`
- Configurable retries with exponential backoff and connect and read timeouts for calls to the model provider
- Optional access control: with `AUTH_ENFORCED=true`, write and AI endpoints need a `RECRUITER` or `ADMIN` token
- Audit log of job changes and application stage changes, with the acting user, readable by admins
- OpenAPI 3 description of the API with a Swagger UI to try it out
- Correlation IDs: a well-formed `X-Request-Id` is kept (or a UUID generated), echoed in every response and written to every log line for that request
- Structured JSON logging: `LOG_FORMAT=json` (Elastic Common Schema) or `logstash` writes one JSON object per log line, request id included
- Metrics at `/actuator/metrics` and in Prometheus format at `/actuator/prometheus`, with a timer around every model call tagged by operation and outcome
- Liveness and readiness probes at `/actuator/health/liveness` and `/actuator/health/readiness`; readiness also checks the database
- Build version reported by `/actuator/info` and the OpenAPI document
- Versioned database schema managed by Flyway migrations
- Hardened container: the image runs as a non-root user with the heap sized from the container's memory limit, and Docker Compose health-checks the application through its readiness probe
- Consistent JSON error responses, including per-field validation errors, malformed request bodies, missing upload fields and unsupported HTTP methods
- Tests that run on in-memory H2 with the model mocked, so no API key is needed, plus plain unit tests for the skill-gap and matching services with their collaborators mocked
- Integration tests against a real PostgreSQL with pgvector, started by Testcontainers, covering the PostgreSQL-only migrations, vector storage and ranking

See [ROADMAP.md](ROADMAP.md) for what is coming next.

## Run it

You need Docker and an OpenAI API key.

```bash
cp .env.example .env      # then put your key in .env
docker compose up --build
```

The API is served at `http://localhost:8080`. The `.env` file is git-ignored; never commit it.

The application container runs as the unprivileged `talentlens` user. `JAVA_TOOL_OPTIONS` defaults to `-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -XX:+ExitOnOutOfMemoryError`, so the heap follows the container's memory limit and an out-of-memory error stops the process instead of leaving it half alive; set `JAVA_TOOL_OPTIONS` to override it. Compose marks the `app` service healthy once `/actuator/health/readiness` answers `200` (allowing 60 seconds for startup) and restarts it if it stops. `docker compose ps` shows the health status. CI builds the image on every push and checks that it does not run as root.

To run only the tests (no key, no database needed):

```bash
mvn test
```

The suite runs on in-memory H2. When Docker is available, `PostgresIntegrationTests` also starts a throwaway `pgvector/pgvector:pg16` container with Testcontainers and runs migrations, embedding storage, ranking and search against it; without Docker those tests are skipped. CI runs them on every push. `SkillGapServiceTests` and `MatchingServiceTests` exercise the comparison and ranking logic without a Spring context, so `mvn test -Dtest='*ServiceTests'` gives quick feedback while changing them.

## Architecture

TalentLens is a single Spring Boot application with one package per feature. Each feature follows the same shape: a REST controller that validates request records, a service that holds the rules, and Spring Data repositories over PostgreSQL. Errors from every layer become JSON through one `GlobalExceptionHandler`.

```
            HTTP clients (curl, Swagger UI, a recruiter front end)
                                   │
       ┌───────────────────────────┴───────────────────────────┐
       │ Controllers: jobs, candidates, applications,           │
       │ matching, assistant, users, auth                       │
       ├────────────────────────────────────────────────────────┤
       │ Services: pipeline rules, ranking, skill gaps,         │──── Cache (Redis or in memory)
       │ RAG chat, shortlist and email agents, JWT              │
       ├──────────────────────────┬─────────────────────────────┤
       │ JPA repositories,        │ AI ports (ResumeParser,     │
       │ EmbeddingStore           │ MatchExplainer, agents ...) │
       └────────────┬─────────────┴──────────────┬──────────────┘
                    │                            │ Spring AI ChatClient / EmbeddingModel
        PostgreSQL + pgvector                OpenAI chat and embedding models
        (schema by Flyway)
```

- **Data.** Jobs, candidates, applications and user accounts are JPA entities. Flyway owns the schema; the pgvector tables are a PostgreSQL-only migration, with an H2 equivalent so the test suite runs in memory.
- **AI behind interfaces.** Every model call sits behind a small interface in `ai/` or `assistant/` (`ResumeParser`, `MatchExplainer`, `SkillExtractor`, `ShortlistAgent`, ...) with an `Llm*` implementation that uses Spring AI. Model output is requested as JSON, mapped to records and checked before it is stored or returned; a model failure becomes `503`. Tests replace these beans with mocks, so they never need an API key.
- **Matching.** The embedding model turns a job or a candidate profile into a vector stored in pgvector. Ranking, CSV export and the assistant's retrieval compare stored vectors by cosine similarity and never call the model.
- **Agents.** The shortlist and email agents give the chat model tools (`@Tool` methods over the services) to look up rankings, profiles and applications. Their answers are checked against the database: unknown candidates are dropped and email recipients always come from the candidate record.
- **Security.** Passwords are BCrypt hashes, logins return HS256 JWTs, and role changes are checked against the database on every request.
- **Caching.** Job lookups, rankings, match explanations and each job's extracted required skills are cached and evicted whenever the data behind them changes.

## End-to-end walkthrough

With `docker compose up --build` running, this takes one job and one candidate from posting to a drafted email. The ids assume an empty database.

```bash
# 1. Post an open job
curl -X POST localhost:8080/api/v1/jobs -H 'Content-Type: application/json' \
  -d '{"title":"Backend Engineer","description":"Java 21, Spring Boot, PostgreSQL and Kafka. Owns services end to end.","location":"Bengaluru","minExperienceYears":4,"status":"OPEN"}'

# 2. Add a candidate and upload their resume
curl -X POST localhost:8080/api/v1/candidates -H 'Content-Type: application/json' \
  -d '{"fullName":"Asha Verma","email":"asha@example.com"}'
curl -X POST localhost:8080/api/v1/candidates/1/resume -F file=@resume.pdf

# 3. Parse the resume into headline, experience and skills
curl -X POST localhost:8080/api/v1/candidates/1/resume/parse

# 4. Embed the job and the candidate, then rank candidates for the job
curl -X POST localhost:8080/api/v1/jobs/1/embedding
curl -X POST localhost:8080/api/v1/candidates/1/embedding
curl localhost:8080/api/v1/jobs/1/matches

# 5. Understand the match
curl localhost:8080/api/v1/jobs/1/matches/1/explanation
curl localhost:8080/api/v1/jobs/1/matches/1/skill-gap

# 6. Ask the assistant, prepare the interview and get a shortlist
curl -X POST localhost:8080/api/v1/assistant/chat -H 'Content-Type: application/json' \
  -d '{"question":"Who has worked with Kafka?"}'
curl -X POST localhost:8080/api/v1/assistant/interview-questions -H 'Content-Type: application/json' \
  -d '{"jobId":1,"candidateId":1}'
curl -X POST localhost:8080/api/v1/assistant/shortlist -H 'Content-Type: application/json' \
  -d '{"jobId":1}'

# 7. Move the candidate through the pipeline and draft the next email
curl -X POST localhost:8080/api/v1/applications -H 'Content-Type: application/json' \
  -d '{"candidateId":1,"jobId":1}'
curl -X PUT localhost:8080/api/v1/applications/1/status -H 'Content-Type: application/json' \
  -d '{"status":"SCREENING"}'
curl -X POST localhost:8080/api/v1/assistant/emails -H 'Content-Type: application/json' \
  -d '{"applicationId":1,"type":"NEXT_ROUND"}'

# 8. Export the ranking for the hiring team
curl -OJ localhost:8080/api/v1/jobs/1/matches/export
```

Steps 3, 4 (embedding), 5, 6 and 7 (email) call OpenAI; everything else works without a key. `curl localhost:8080/actuator/info` shows the running version.

## API

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/jobs` | Create a job |
| `GET` | `/api/v1/jobs` | List jobs (paginated; filter with `status`, `location`) |
| `GET` `PUT` `DELETE` | `/api/v1/jobs/{id}` | Read, update or (soft) delete a job |
| `POST` | `/api/v1/jobs/{id}/restore` | Restore a deleted job with its applications |
| `PUT` | `/api/v1/jobs/{id}/status` | Move a job through its lifecycle (`status`) |
| `POST` | `/api/v1/candidates` | Create a candidate |
| `GET` | `/api/v1/candidates` | List candidates (paginated; search with `q`, filter with `skill`, `minExperience`) |
| `GET` `PUT` `DELETE` | `/api/v1/candidates/{id}` | Read, update or delete a candidate |
| `POST` | `/api/v1/candidates/{id}/resume` | Upload a PDF resume (multipart field `file`) and store its text |
| `POST` | `/api/v1/candidates/{id}/resume/parse` | Extract a structured profile from the candidate's resume and store it on the candidate |
| `POST` | `/api/v1/applications` | Apply a candidate to a job (`candidateId`, `jobId`) |
| `GET` `DELETE` | `/api/v1/applications/{id}` | Read or delete an application |
| `PUT` | `/api/v1/applications/{id}/status` | Move an application to another stage (`status`) |
| `GET` | `/api/v1/applications/{id}/history` | List the application's stage changes, oldest first |
| `POST` `GET` | `/api/v1/applications/{id}/notes` | Add a recruiter note (`body`, optional `author`) or list the notes, oldest first |
| `GET` | `/api/v1/jobs/{jobId}/applications` | List a job's applications (paginated; filter with `status`) |
| `GET` | `/api/v1/jobs/{jobId}/pipeline` | Count the job's applications in each stage |
| `GET` | `/api/v1/candidates/{candidateId}/applications` | List a candidate's applications (paginated) |
| `POST` | `/api/v1/jobs/{jobId}/embedding` | Compute and store the job's embedding |
| `POST` | `/api/v1/candidates/{candidateId}/embedding` | Compute and store the candidate's embedding |
| `POST` | `/api/v1/candidates/embeddings` | Embed every candidate that has no embedding yet |
| `GET` | `/api/v1/jobs/{jobId}/matches` | Rank candidates for the job by semantic similarity (`limit`, default 10) |
| `GET` | `/api/v1/jobs/{jobId}/matches/export` | Download the ranking as a CSV file (`limit`, default 50) |
| `GET` | `/api/v1/jobs/{jobId}/matches/{candidateId}/explanation` | Explain why the candidate fits the job and what is missing |
| `GET` | `/api/v1/jobs/{jobId}/matches/{candidateId}/skill-gap` | Compare the job's required skills with the candidate's skills |
| `POST` | `/api/v1/assistant/chat` | Ask the recruiter assistant a question about the candidate pool (`question`, `topK`) |
| `POST` | `/api/v1/assistant/interview-questions` | Generate interview questions tailored to a candidate and job (`jobId`, `candidateId`, `count`) |
| `POST` | `/api/v1/assistant/shortlist` | Let the tool-calling agent shortlist candidates for a job (`jobId`, `size`, default 5) |
| `POST` | `/api/v1/assistant/emails` | Let the tool-calling agent draft a rejection or next-round email for an application (`applicationId`, `type`, `note`) |
| `POST` | `/api/v1/users` | Register a user account (`email`, `fullName`, `password`) |
| `GET` | `/api/v1/users/{id}` | Read a user account |
| `PUT` | `/api/v1/users/{id}/role` | Change a user's `role` (admin token required) |
| `POST` | `/api/v1/auth/login` | Exchange `email` and `password` for a JWT access token |
| `GET` | `/api/v1/auth/me` | Read the user the bearer token belongs to |
| `PUT` | `/api/v1/auth/password` | Change the signed-in user's password (`currentPassword`, `newPassword`) |
| `GET` | `/api/v1/audit-events` | Read the audit log, newest first (admin token required; filters `entityType`, `entityId`; paged) |
| `GET` | `/actuator/health` | Health check |
| `GET` | `/actuator/health/liveness` | Liveness probe: is the application running |
| `GET` | `/actuator/health/readiness` | Readiness probe: can it take traffic, including a database check (`503` when not) |
| `GET` | `/actuator/info` | Build information, including the running version |
| `GET` | `/actuator/metrics` | Metric names; `/actuator/metrics/{name}` reads one |
| `GET` | `/actuator/prometheus` | All metrics in Prometheus text format, for scraping |

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

Parsing a resume saves the headline, years of experience and skills on the candidate, so later reads of the candidate include `headline`, `experienceYears`, `skills` and `profileParsedAt`. Skills are trimmed and de-duplicated ignoring case. Changing a candidate's resume text clears the stored profile until the resume is parsed again. A resume shorter than 20 characters (after trimming) returns `409` without calling the model, and only the first 20,000 characters of a longer resume are sent to it. A total experience below 0 or above 60 years is treated as a parsing mistake and dropped, both from the response and from the candidate.

Every response carries an `X-Request-Id` header. Send your own (up to 64 letters, digits, `.`, `_`, `:` or `-`) to follow a request through the logs; anything else is replaced by a generated UUID. Log lines written while handling the request show the id after the level.

Set `LOG_FORMAT=json` (or `ecs`) to write console logs as Elastic Common Schema JSON, one object per line, or `LOG_FORMAT=logstash` for the Logstash layout. The request id appears as the `requestId` field. `text` or no value keeps the plain pattern, and any other value stops the application at startup with an error naming `LOG_FORMAT`.

Every call to the model provider is timed as `talentlens.ai.model.calls` (`talentlens_ai_model_calls_seconds` in Prometheus), tagged with `operation` (`skill-extraction`, `match-explanation`, `resume-parsing`, `interview-questions`, `candidate-chat`, `email-draft`, `shortlist` or `embedding`) and `outcome` (`success` or `failure`). The timer publishes histogram buckets, so latency percentiles can be computed with `histogram_quantile`. All metrics carry an `application` tag, and the standard HTTP, JVM and database pool metrics are there too. `ACTUATOR_ENDPOINTS` picks which actuator endpoints are served; set it to `health,info` to keep metrics private.

For orchestrators such as Kubernetes, `/actuator/health/liveness` reports only the application's own state, so a database outage does not get the container restarted, while `/actuator/health/readiness` also includes the database and answers `503` with `OUT_OF_SERVICE` or `DOWN` when the instance should not receive traffic. Both list the checks they ran under `components`.

List endpoints accept `page` (from 0), `size` (1-100, default 20) and `sort` (`field` or `field,asc|desc`, default `createdAt,desc`), and return `content` with `page`, `size`, `totalElements` and `totalPages`. Jobs sort by `id`, `title`, `location`, `minExperienceYears`, `status` or `createdAt`; candidates by `id`, `fullName`, `email` or `createdAt`.

```bash
curl 'localhost:8080/api/v1/jobs?page=0&size=10&sort=title,asc'
```

The job list can be narrowed with `status` (`DRAFT`, `OPEN` or `CLOSED`) and `location` (case-insensitive, matches any part of the location). The candidate list takes `q`, which matches any part of the name or email, ignoring case. It can also be filtered on the parsed resume profile: `skill` keeps candidates with that skill (whole skill, ignoring case and surrounding spaces) and can be repeated, or comma-separated, to require every listed skill; `minExperience` keeps candidates with at least that many years of experience (a decimal, 0 or greater, otherwise `400`). Candidates whose resume has not been parsed have no skills or experience, so they drop out when either filter is used. Filters combine with each other and with paging and sorting.

```bash
curl 'localhost:8080/api/v1/jobs?status=OPEN&location=bengaluru'
curl 'localhost:8080/api/v1/candidates?q=verma'
curl 'localhost:8080/api/v1/candidates?skill=java&skill=kafka&minExperience=4'
```

A job is a `DRAFT` while it is being written, `OPEN` while it takes applications and `CLOSED` when hiring stops. `PUT /api/v1/jobs/{id}/status` publishes a draft (`DRAFT` → `OPEN`), closes an open job (`OPEN` → `CLOSED`) or reopens a closed one (`CLOSED` → `OPEN`). A job never returns to `DRAFT`, and a draft cannot be closed without being published; those moves return `409`, as does a full update (`PUT /api/v1/jobs/{id}`) whose `status` breaks the same rules. Asking for the status a job already has changes nothing. A new job can start in any status and defaults to `OPEN`.

```bash
curl -X PUT localhost:8080/api/v1/jobs/1/status \
  -H 'Content-Type: application/json' \
  -d '{"status":"CLOSED"}'
```

A job can also carry an optional application deadline, `closesAt`, as an ISO-8601 instant (for example `2026-12-31T18:30:00Z`). From that moment on, new applications to the job return `409` even while it is `OPEN`; existing applications are unaffected. The deadline is set on create or full update (`PUT /api/v1/jobs/{id}`), and a full update without it clears it.

An application links a candidate to a job and tracks where they are in the hiring pipeline: `APPLIED`, `SCREENING`, `INTERVIEW`, `OFFER` and `HIRED`, or `REJECTED` / `WITHDRAWN` when it ends early. Only `OPEN` jobs whose deadline has not passed accept applications, and a candidate can apply to a job once; both cases otherwise return `409`. Deleting a candidate or a job also deletes their applications.

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

Every stage change is recorded. `GET /api/v1/applications/{id}/history` lists them oldest first, each with `fromStatus`, `toStatus` and `changedAt`; the first entry has no `fromStatus` and records the application entering the pipeline as `APPLIED`. Rejected moves are not recorded, and deleting an application deletes its history. Applications created before history was added start their history at their next change.

```bash
curl localhost:8080/api/v1/applications/1/history
```

Recruiters can leave notes on an application. `POST /api/v1/applications/{id}/notes` takes a `body` (required, up to 2000 characters) and an optional `author` (up to 100 characters), trims both and returns the note with its `id` and `createdAt`. `GET` on the same path lists the notes oldest first. Notes cannot be edited, and deleting an application deletes its notes.

```bash
curl -X POST localhost:8080/api/v1/applications/1/notes \
  -H 'Content-Type: application/json' \
  -d '{"body":"Strong Kafka background, ask about on-call","author":"Priya"}'
curl localhost:8080/api/v1/applications/1/notes
```

`GET /api/v1/jobs/{jobId}/pipeline` summarises a job's pipeline: the `total` number of applications and, under `stages`, the count in every stage in pipeline order, with `0` for empty stages. An unknown job returns `404`.

```bash
curl localhost:8080/api/v1/jobs/1/pipeline
# {"jobId":1,"jobTitle":"Backend Engineer","total":3,"stages":{"APPLIED":1,"SCREENING":2,"INTERVIEW":0,...}}
```

Application lists take the same `page`, `size` and `sort` parameters as the other lists and sort by `id`, `status`, `createdAt` or `updatedAt`.

Jobs and candidates can be turned into embedding vectors for semantic matching. A job's embedding covers its title, location, minimum experience and description; a candidate's covers the parsed headline, experience and skills plus the resume text (cut to the first 8000 characters). Vectors are stored in the `job_embeddings` and `candidate_embeddings` tables as pgvector `vector` columns and are replaced each time the endpoint is called. A candidate with neither a resume nor a parsed profile returns `409`, and a model failure returns `503`. Deleting a job or candidate deletes its embedding.

```bash
curl -X POST localhost:8080/api/v1/jobs/1/embedding
curl -X POST localhost:8080/api/v1/candidates/1/embedding
```

`POST /api/v1/candidates/embeddings` embeds every candidate that has no embedding yet, so a freshly imported pool can be prepared in one request. The candidates' texts are sent to the embedding model together, up to 100 per model call, and the response gives the number `embedded`, the `candidateIds` that were embedded, the number `skipped` because they have neither a resume nor a parsed profile, and `embeddedAt`. Candidates that already have an embedding are left alone (use the single-candidate endpoint to refresh one). If any model call fails the endpoint returns `503` and stores nothing, so it can simply be called again.

```bash
curl -X POST localhost:8080/api/v1/candidates/embeddings
```

Once a job and some candidates have embeddings, `GET /api/v1/jobs/{jobId}/matches` ranks the candidates by the cosine similarity of their embedding to the job's, best first. Each match has its `rank`, `candidateId`, `fullName`, `headline` and a `score` from -1 to 1 (rounded to four decimals); `candidatesConsidered` counts every candidate that could be compared. `limit` (1-50, default 10) caps the number of matches. Candidates without an embedding, or whose vector has a different size from the job's (for example after changing `OPENAI_EMBEDDING_MODEL`), are left out until they are embedded again. A job without an embedding returns `409`. Ranking reads stored vectors only and never calls the model.

```bash
curl 'localhost:8080/api/v1/jobs/1/matches?limit=5'
```

`GET /api/v1/jobs/{jobId}/matches/export` returns the same ranking as a `text/csv` download named `job-{jobId}-ranked-candidates.csv`, with the columns `rank`, `candidateId`, `fullName`, `headline` and `score`. `limit` works as above but defaults to the maximum of 50, and the errors are the same as for the JSON ranking. Values containing commas, quotes or line breaks are quoted, and text starting with `=`, `+`, `-` or `@` is prefixed with an apostrophe so spreadsheets do not run it as a formula.

```bash
curl -OJ localhost:8080/api/v1/jobs/1/matches/export
```

`GET /api/v1/jobs/{jobId}/matches/{candidateId}/explanation` asks the chat model why a candidate fits a job. It compares the same job and candidate texts used for embeddings and returns a short `summary`, the job requirements the candidate meets (`strengths`) and those they are missing or only partly meet (`gaps`). The response also carries the candidate's `fullName` and the ranking `score`, which is `null` unless both the job and the candidate have comparable embeddings; the explanation itself does not need embeddings. A candidate with neither a resume nor a parsed profile returns `409`, and a model failure returns `503`. Explanations are cached (see below), so repeating the request returns the same text without asking the model again.

```bash
curl localhost:8080/api/v1/jobs/1/matches/3/explanation
```

`GET /api/v1/jobs/{jobId}/matches/{candidateId}/skill-gap` lists the skills the job requires (`requiredSkills`, extracted from the job by the chat model) and splits them into those on the candidate's parsed profile (`matchedSkills`) and those that are not (`missingSkills`). Skills the candidate has that the job does not ask for are in `additionalSkills`. Skills are compared ignoring case, spaces, hyphens, underscores and dots, so `Spring-Boot` matches `SpringBoot` and `Node.js` matches `NodeJS`, while `C`, `C++` and `C#` stay distinct. `coverage` is the share of required skills the candidate has, from 0 to 1, or `null` when the job names none. The report also compares the job's `requiredExperienceYears` with the candidate's `candidateExperienceYears`; `meetsExperience` is `null` when either is unknown. The candidate's resume must be parsed first, otherwise the endpoint returns `409`; a model failure returns `503`. The required skills are extracted once per job and cached, so reports for other candidates on the same job reuse them until the job is updated.

```bash
curl localhost:8080/api/v1/jobs/1/matches/3/skill-gap
```

`POST /api/v1/assistant/chat` answers a recruiter's `question` (up to 2000 characters) about the candidate pool with retrieval-augmented generation. The question is embedded with the embedding model, the `topK` candidates (1-20, default 5) whose stored embeddings are closest to it are retrieved, and their profiles (headline, experience, skills and the start of the resume) are given to the chat model, which answers from those profiles only. The response has the `answer` and the `sources` it was based on, each with `candidateId`, `fullName`, `headline` and similarity `score`. Only candidates with an embedding are searched; if none have one the endpoint returns `409`, and an embedding model failure returns `503`. If the chat model fails after the candidates were retrieved, the endpoint still answers `200` with the `sources`, an empty `answer`, `fallback: true` and a `notice` saying no answer could be written, so the recruiter can work from the closest candidates; otherwise `fallback` is `false` and `notice` is `null`.

```bash
curl -X POST localhost:8080/api/v1/assistant/chat \
  -H 'Content-Type: application/json' \
  -d '{"question":"Who has run Kafka in production?","topK":3}'
```

`POST /api/v1/assistant/interview-questions` asks the chat model for `count` interview questions (1-15, default 5) tailored to one candidate and one job. It reads the same job and candidate texts used for embeddings and match explanations, so it does not need embeddings, and returns the candidate's `fullName`, the `jobTitle` and the `questions`, each with the `question` to ask, its `focus` (the job requirement or candidate claim it tests) and a one-line `rationale`. The questions verify the experience the candidate claims and probe the requirements their profile does not show. Unknown job or candidate ids return `404`, a candidate with neither a resume nor a parsed profile returns `409`, and a model failure returns `503`. Each call asks the model again, so the questions can vary.

```bash
curl -X POST localhost:8080/api/v1/assistant/interview-questions \
  -H 'Content-Type: application/json' \
  -d '{"jobId":1,"candidateId":3,"count":4}'
```

`POST /api/v1/assistant/emails` drafts an email to the candidate on one application. `type` is `REJECTION` or `NEXT_ROUND`, and an optional `note` (up to 500 characters) passes the recruiter's instructions to the model. The agent calls the `applicationDetails` tool (candidate name, job, current stage and the stage they are invited to) and the `candidateProfile` tool, then returns a `subject` and `body`. The response adds the recipient `to` (from the candidate record, never from the model), `candidateName`, `jobTitle`, `nextStage` (null for a rejection) and the `toolCalls` the agent made. A rejection can be drafted unless the application is `HIRED` or `WITHDRAWN`; a next-round email only from `APPLIED`, `SCREENING` or `INTERVIEW`, inviting the candidate to the following stage. Other stages return `409`, an unknown application `404`, and a model failure or an empty body `503`. The draft is not sent and the application status is not changed.

```bash
curl -X POST localhost:8080/api/v1/assistant/emails \
  -H 'Content-Type: application/json' \
  -d '{"applicationId":1,"type":"NEXT_ROUND","note":"The technical interview is remote"}'
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

After `LOGIN_MAX_ATTEMPTS` failed logins in a row for one email (default 5), that email is locked out for `LOGIN_LOCKOUT` (default `15m`): every login for it, even with the right password, returns `429` with a `Retry-After` header giving the seconds left. Unknown emails are counted the same way, failures older than the lockout period are forgotten, and a successful login clears the count. The counts are kept in memory per instance and reset on restart. Set `LOGIN_MAX_ATTEMPTS=0` to turn the throttle off.

Every endpoint that calls the model or the embedding store (`/api/v1/assistant/**`, matches, explanations, skill gaps, the ranking export, single and batch embedding, and resume parsing) is rate limited per client, keyed by the remote address. A client may make `AI_RATE_LIMIT_REQUESTS` such calls (default 30) per `AI_RATE_LIMIT_WINDOW` (default `1m`); further calls in that window return `429` with a `Retry-After` header giving the seconds until it ends. Other endpoints are not limited. The counts are kept in memory per instance and reset on restart; behind a reverse proxy, let the proxy pass the client address (for example with `server.forward-headers-strategy=native`). Set `AI_RATE_LIMIT_REQUESTS=0` to turn the limit off.

Calls to the model provider (chat and embeddings) are retried when they hit a rate limit, a server error, a connection failure or a timeout. A call is tried up to `AI_RETRY_MAX_ATTEMPTS` times in total (default 3), waiting `AI_RETRY_INITIAL_BACKOFF` (default `2s`) before the first retry and multiplying the wait by `AI_RETRY_MULTIPLIER` (default `2.0`) each time, up to `AI_RETRY_MAX_BACKOFF` (default `30s`). Client errors such as an invalid API key are not retried. Each attempt may take `AI_CONNECT_TIMEOUT` (default `10s`) to connect and `AI_READ_TIMEOUT` (default `60s`) to answer. Every failed attempt is logged as a warning; when all attempts fail, the endpoint answers `503` as before. Set `AI_RETRY_MAX_ATTEMPTS=1` to turn retries off.

`PUT /api/v1/auth/password` changes the signed-in user's password and returns `204`. It needs the bearer token and the `currentPassword`; a wrong current password, or a `newPassword` equal to the current one, returns `400`, and the new password must be 8 to 72 characters like at registration. Tokens issued before the change stay valid until they expire.

```bash
curl -X PUT localhost:8080/api/v1/auth/password \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"currentPassword":"a-long-passphrase","newPassword":"another-passphrase"}'
```

Every account has a `role`: `ADMIN`, `RECRUITER` or `CANDIDATE`. It is returned with the user and written into access tokens as the `role` claim. Registration always creates a `CANDIDATE`, except for the account registered with `ADMIN_EMAIL`, which becomes the first `ADMIN`. An admin changes roles with `PUT /api/v1/users/{id}/role`; without a token the endpoint returns `401`, with a non-admin token `403`, and demoting the last remaining admin returns `409`. Roles are checked against the database, so a change applies straight away, even to tokens issued before it.

```bash
curl -X PUT localhost:8080/api/v1/users/2/role \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"role":"RECRUITER"}'
```

Creating, updating, deleting and restoring a job, changing its status and moving an application to another stage each write an audit event in the same transaction as the change, so a rejected change leaves no entry. An event records the `action`, the `entityType` (`JOB` or `APPLICATION`) and `entityId`, short `details` such as `OPEN -> CLOSED`, the time, and the acting user's `actorId` and `actorEmail` taken from the bearer token; both are `null` when the request carried no valid token, which can happen while `AUTH_ENFORCED` is off. Admins read the log with `GET /api/v1/audit-events`, filtered by `entityType` and optionally `entityId`, paged and sortable by `id`, `action` or `occurredAt`; without a token it returns `401`, with a non-admin token `403`.

```bash
curl "localhost:8080/api/v1/audit-events?entityType=JOB&entityId=1" -H "Authorization: Bearer $ADMIN_TOKEN"
```

Access control is off by default so existing clients keep working. Set `AUTH_ENFORCED=true` and every `POST`, `PUT`, `PATCH` or `DELETE` under `/api/v1`, plus the AI endpoints (`/api/v1/assistant/**` and `/api/v1/jobs/{jobId}/matches/**`, reads included), needs a `RECRUITER` or `ADMIN` bearer token: no or an invalid token returns `401`, a `CANDIDATE` token `403`. Logging in, registering (`POST /api/v1/users`) and other reads stay open.

Single jobs (`GET /api/v1/jobs/{id}`), candidate rankings (per job and `limit`), match explanations (per job and candidate) and the required skills the model extracts for skill-gap reports (per job) are cached. `CACHE_TYPE` picks the backend: `simple` (the default) keeps entries in memory on each instance, `redis` stores them in Redis so every instance shares them, and `none` turns caching off. Redis entries expire after `CACHE_TTL` and are stored under keys starting with `talentlens:`. Updating or deleting a job, changing a candidate (details, resume or parsed profile) or computing a new embedding clears the affected entries, so a cached result never outlives the data it was built from. `docker compose up` starts Redis and runs the application with `CACHE_TYPE=redis`; set `REDIS_HEALTH_ENABLED=true` with Redis so `/actuator/health` reports its status.

Interactive documentation is generated from the code: Swagger UI at `http://localhost:8080/swagger-ui.html` and the OpenAPI document at `/v3/api-docs` (JSON) or `/v3/api-docs.yaml`. Set `API_DOCS_ENABLED=false` to turn both off.

The database schema lives in `src/main/resources/db/migration` and is applied by Flyway on startup; Hibernate only validates that the entities match it. To change the schema, add a new `V<n>__description.sql` file rather than editing an existing one. Migrations that only work on one database live in `src/main/resources/db/vendor/<database>`; the pgvector tables are created there, so PostgreSQL needs the `vector` extension available (the `pgvector/pgvector` image in `docker-compose.yml` has it). A database created before migrations were introduced is baselined at version 1 automatically.

## Project layout

```
src/main/java/com/talentlens
├── job/         Job postings
├── candidate/   Candidates and their resume text
├── application/ Candidates applying to jobs, and the hiring pipeline
├── ai/          Model-backed features (resume parsing, match explanations, skill extraction, assistant answers, interview questions)
├── matching/    Embeddings in pgvector, semantic candidate ranking, match explanations and skill gaps
├── assistant/   Recruiter assistant: chat over the candidate pool, interview questions, shortlisting and email agents
├── user/        User accounts with hashed passwords and roles
├── auth/        JWT login and access tokens
├── audit/       Audit log of recruiter actions
├── config/      Application configuration (OpenAPI, password hashing, JWT settings, caching, model retries and timeouts)
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
| `AUTH_ENFORCED` | `false` | Require a `RECRUITER` or `ADMIN` token for write and AI endpoints |
| `LOGIN_MAX_ATTEMPTS` | `5` | Failed logins in a row before an email is locked out (`0` turns it off) |
| `LOGIN_LOCKOUT` | `15m` | How long a locked-out email has to wait |
| `AI_RATE_LIMIT_REQUESTS` | `30` | AI endpoint calls one client may make per window (`0` turns it off) |
| `AI_RATE_LIMIT_WINDOW` | `1m` | Length of the AI rate limit window |
| `AI_RETRY_MAX_ATTEMPTS` | `3` | Tries per model call, including the first (`1` turns retries off) |
| `AI_RETRY_INITIAL_BACKOFF` | `2s` | Wait before the first retry of a model call |
| `AI_RETRY_MULTIPLIER` | `2.0` | Factor the wait grows by on each further retry |
| `AI_RETRY_MAX_BACKOFF` | `30s` | Longest wait between retries |
| `AI_CONNECT_TIMEOUT` | `10s` | Time allowed to connect to the model provider |
| `AI_READ_TIMEOUT` | `60s` | Time allowed for the model provider to answer |
| `CACHE_TYPE` | `simple` | Cache backend: `redis`, `simple` (in memory) or `none` |
| `CACHE_TTL` | `10m` | How long Redis keeps a cached entry |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Redis server used when `CACHE_TYPE=redis` |
| `REDIS_PASSWORD` | none | Redis password, if the server needs one |
| `REDIS_HEALTH_ENABLED` | `false` | Include Redis in `/actuator/health` |
| `ACTUATOR_ENDPOINTS` | `health,info,metrics,prometheus` | Actuator endpoints served over HTTP |
| `LOG_FORMAT` | `text` | Console log format: `text`, `json` (Elastic Common Schema) or `logstash` |
