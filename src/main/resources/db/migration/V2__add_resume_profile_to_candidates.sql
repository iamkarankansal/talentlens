alter table candidates add column headline varchar(255);
alter table candidates add column experience_years double precision;
alter table candidates add column profile_parsed_at timestamp(6) with time zone;

create table candidate_skills (
    candidate_id bigint not null,
    skill varchar(255) not null,
    constraint fk_candidate_skills_candidate foreign key (candidate_id) references candidates (id) on delete cascade
);

create index idx_candidate_skills_candidate on candidate_skills (candidate_id);
