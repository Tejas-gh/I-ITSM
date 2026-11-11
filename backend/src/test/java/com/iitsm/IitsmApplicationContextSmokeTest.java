package com.iitsm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class IitsmApplicationContextSmokeTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Flyway flyway;

    @Autowired
    private Environment environment;

    @Test
    void springContext_testProfile_loads() {
        assertNotNull(dataSource);
        assertNotNull(flyway);

        assertEquals(
                "iitsm",
                environment.getProperty("spring.application.name")
        );

        assertEquals(
                "none",
                environment.getProperty("spring.jpa.hibernate.ddl-auto")
        );

        assertEquals(
                "false",
                environment.getProperty("spring.jpa.open-in-view")
        );
    }
}