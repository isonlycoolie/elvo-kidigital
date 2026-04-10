package com.elvo.accountmanagement;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(classes = ElvoAccountManagementServiceApplication.class)
class ApplicationContextTest {

    @Test
    void contextLoads() {
    }
}
