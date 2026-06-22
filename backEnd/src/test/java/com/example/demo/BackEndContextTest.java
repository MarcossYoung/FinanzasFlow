package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "jwt.secret=context-test-secret-that-is-long-and-not-a-default",
        "telegram.admin.chat-ids=",
        "app.seed.demo-data=false"
})
class BackEndContextTest {
    @Test
    void contextLoadsWithMigrationsAndIngestionWiring() {
    }
}
