package com.ims.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class PostgresMigrationIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ims")
            .withUsername("ims")
            .withPassword("ims-test");

    @Test
    void appliesFlywaySchemaToPostgres() throws Exception {
        Flyway.configure()
            .dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword())
            .locations("classpath:db/migration")
            .load()
            .migrate();

        try (var connection=DriverManager.getConnection(
                postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword());
             var statement=connection.createStatement();
             var result=statement.executeQuery("""
                 select count(*)
                 from information_schema.tables
                 where table_schema = 'public'
                   and table_name in ('app_user','plan','policy','payment','claim',
                                      'outbox_event','consumed_event','audit_event','notification_log')
                 """)) {
            result.next();
            assertEquals(9,result.getInt(1));
        }
    }
}
