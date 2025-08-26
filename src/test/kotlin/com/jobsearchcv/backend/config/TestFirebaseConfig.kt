package com.jobsearchcv.backend.config

import com.google.firebase.FirebaseApp
import jakarta.annotation.PostConstruct
import org.mockito.Mockito.mock
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@TestConfiguration
class TestFirebaseConfig {
    
    @PostConstruct
    fun initializeTestFirebase() {
        if (FirebaseApp.getApps().isEmpty()) {
            val mockApp = mock(FirebaseApp::class.java)
            FirebaseApp.getApps().clear()
        }
    }
}