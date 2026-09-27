package com.springtest.product_store;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

// The context starts on a fresh Postgres: Flyway applies the migrations and
// Hibernate's ddl-auto=validate accepts the resulting schema
class ProductStoreApplicationIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoadsAndFlywayAppliedMigrations() {
        Integer applied = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version = '1' and success", Integer.class);

        assertThat(applied).isEqualTo(1);
    }
}
