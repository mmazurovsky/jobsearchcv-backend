package com.jobsearchcv.backend

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class SimpleContextLoadTest {

    @Test
    fun `context loads`() {
        // This test simply checks if the Spring application context loads successfully
    }
}
