package com.talentlens.migration;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class FlywayMigrationTests {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void appliesEveryMigrationOnStartup() {
        MigrationInfoService info = flyway.info();

        assertThat(info.applied()).isNotEmpty();
        assertThat(info.pending()).isEmpty();
        assertThat(info.current().getVersion().getVersion()).isEqualTo("2");
    }

    @Test
    void createsTheTablesTheEntitiesMapTo() {
        assertThat(jdbcTemplate.queryForObject("select count(*) from jobs", Integer.class)).isNotNull();
        assertThat(jdbcTemplate.queryForObject("select count(*) from candidates", Integer.class)).isNotNull();
        assertThat(jdbcTemplate.queryForObject("select count(*) from candidate_skills", Integer.class)).isNotNull();
    }
}
