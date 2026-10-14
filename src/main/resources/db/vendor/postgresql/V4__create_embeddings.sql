-- Needs the pgvector extension (the pgvector/pgvector image in docker-compose ships it).
create extension if not exists vector;

create table job_embeddings (
    job_id bigint primary key,
    embedding vector not null,
    dimensions integer not null,
    embedded_at timestamp(6) with time zone not null,
    constraint fk_job_embeddings_job foreign key (job_id) references jobs (id) on delete cascade
);

create table candidate_embeddings (
    candidate_id bigint primary key,
    embedding vector not null,
    dimensions integer not null,
    embedded_at timestamp(6) with time zone not null,
    constraint fk_candidate_embeddings_candidate foreign key (candidate_id) references candidates (id) on delete cascade
);
