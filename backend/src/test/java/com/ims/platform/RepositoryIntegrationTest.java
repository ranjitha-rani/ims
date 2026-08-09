package com.ims.platform;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class RepositoryIntegrationTest {
    @Autowired UserRepository users;
    @Autowired PlanRepository plans;

    @Test
    void persistsAndQueriesCoreEntities() {
        users.save(new UserAccount("customer@example.com","hash","Customer",Role.CUSTOMER));
        plans.save(new Plan("STANDARD","Standard",new BigDecimal("49.99")));
        assertThat(users.findByEmailIgnoreCase("CUSTOMER@example.com")).isPresent();
        assertThat(plans.findByActiveTrue()).extracting(p -> p.code).containsExactly("STANDARD");
    }
}
