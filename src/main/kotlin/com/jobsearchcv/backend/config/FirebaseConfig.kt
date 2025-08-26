package com.jobsearchcv.backend.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import jakarta.annotation.PostConstruct
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.Resource
import java.io.FileNotFoundException

@Configuration
@ConditionalOnProperty(name = ["firebase.enabled"], havingValue = "true", matchIfMissing = false)
class FirebaseConfig {
    
    private val log: Logger = LoggerFactory.getLogger(FirebaseConfig::class.java)
    
    @Value("\${firebase.project-id:applyfirst-b9c69}")
    private lateinit var firebaseProjectId: String
    
    @Value("\${firebase.credentials.path:}")
    private var credentialsPath: String? = null
    
    @Value("classpath:firebase-service-account.json")
    private lateinit var credentialsResource: Resource
    
    @PostConstruct
    fun initialize() {
        try {
            log.debug("Firebase configuration check: project-id=$firebaseProjectId, has-private-key=${!privateKey.isNullOrBlank()}, has-client-email=${!clientEmail.isNullOrBlank()}, credentials-path=$credentialsPath")
            
            if (FirebaseApp.getApps().isEmpty()) {
                val credentials = getCredentials()
                val options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .setProjectId(firebaseProjectId)
                    .build()
                
                FirebaseApp.initializeApp(options)
                log.info("Firebase Admin SDK initialized successfully for project: $firebaseProjectId")
            } else {
                log.info("Firebase Admin SDK already initialized")
            }
        } catch (e: Exception) {
            log.error("Failed to initialize Firebase Admin SDK. Make sure your Firebase credentials are properly configured.", e)
            log.error("Current configuration: project-id=$firebaseProjectId, has-private-key=${!privateKey.isNullOrBlank()}, has-client-email=${!clientEmail.isNullOrBlank()}, credentials-path=$credentialsPath")
            throw RuntimeException("Failed to initialize Firebase Admin SDK. Check your Firebase configuration.", e)
        }
    }
    
    @Value("\${firebase.credentials.private-key:}")
    private var privateKey: String? = null
    
    @Value("\${firebase.credentials.client-email:}")
    private var clientEmail: String? = null
    
    @Value("\${firebase.credentials.client-id:}")
    private var clientId: String? = null
    
    @Value("\${firebase.credentials.private-key-id:}")
    private var privateKeyId: String? = null
    
    private fun getCredentials(): GoogleCredentials {
        return when {
            // Option 1: Use individual environment variables (deployment-friendly)
            !privateKey.isNullOrBlank() && !clientEmail.isNullOrBlank() -> {
                log.info("Loading Firebase credentials from environment variables")
                createCredentialsFromEnvVars()
            }
            // Option 2: Use credentials file path
            !credentialsPath.isNullOrBlank() -> {
                log.info("Loading Firebase credentials from path: $credentialsPath")
                GoogleCredentials.fromStream(java.io.FileInputStream(credentialsPath))
            }
            // Option 3: Use classpath resource
            credentialsResource.exists() -> {
                log.info("Loading Firebase credentials from classpath")
                GoogleCredentials.fromStream(credentialsResource.inputStream)
            }
            // Option 4: Use Application Default Credentials
            else -> {
                log.info("Using Application Default Credentials for Firebase")
                GoogleCredentials.getApplicationDefault()
            }
        }
    }
    
    private fun createCredentialsFromEnvVars(): GoogleCredentials {
        try {
            // Validate required fields
            requireNotNull(privateKey?.takeIf { it.isNotBlank() }) { "FIREBASE_PRIVATE_KEY is required" }
            requireNotNull(clientEmail?.takeIf { it.isNotBlank() }) { "FIREBASE_CLIENT_EMAIL is required" }
            requireNotNull(firebaseProjectId.takeIf { it.isNotBlank() }) { "FIREBASE_PROJECT_ID is required" }
            
            // Create service account JSON with required fields
            val serviceAccountJson = buildString {
                append("{\n")
                append("  \"type\": \"service_account\",\n")
                append("  \"project_id\": \"$firebaseProjectId\",\n")
                append("  \"private_key_id\": \"${privateKeyId ?: "dummy-key-id"}\",\n")  // Use dummy if not provided
                append("  \"private_key\": \"${privateKey?.replace("\\n", "\n")}\",\n")
                append("  \"client_email\": \"$clientEmail\",\n")
                append("  \"client_id\": \"${clientId ?: ""}\",\n")
                append("  \"auth_uri\": \"https://accounts.google.com/o/oauth2/auth\",\n")
                append("  \"token_uri\": \"https://oauth2.googleapis.com/token\",\n")
                append("  \"auth_provider_x509_cert_url\": \"https://www.googleapis.com/oauth2/v1/certs\",\n")
                append("  \"client_x509_cert_url\": \"https://www.googleapis.com/robot/v1/metadata/x509/$clientEmail\"\n")
                append("}")
            }
            
            log.debug("Creating Firebase credentials from environment variables for project: $firebaseProjectId")
            return GoogleCredentials.fromStream(serviceAccountJson.byteInputStream())
        } catch (e: Exception) {
            log.error("Failed to create Firebase credentials from environment variables", e)
            throw IllegalStateException("Invalid Firebase environment variable configuration", e)
        }
    }
}