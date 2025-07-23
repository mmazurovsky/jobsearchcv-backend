package com.jobsearchcv.backend.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

@Service
class TranslationService(
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()
        
    private val languageDetectionCache = mutableMapOf<String, String>()
    
    suspend fun translateToEnglish(text: String): String {
        if (text.isBlank()) return text
        
        try {
            // Simple heuristic language detection
            val language = detectLanguage(text)
            
            if (language == "en") {
                return text
            }
            
            // Use Google Translate API (free version through public endpoint)
            return translateText(text, language, "en")
            
        } catch (e: Exception) {
            logger.warn("Translation failed for text: ${text.take(50)}..., error: ${e.message}")
            return text // Return original if translation fails
        }
    }
    
    suspend fun translateMultipleToEnglish(texts: List<String>): List<String> {
        if (texts.isEmpty()) return emptyList()
        
        logger.info("🔤 Starting batch translation for ${texts.size} texts")
        
        try {
            // Combine texts with separator for batch translation
            val combinedText = texts.joinToString(" ||| ")
            logger.info("🔤 Combined text length: ${combinedText.length} characters")
            val translatedCombined = translateToEnglish(combinedText)
            
            // Split back into individual translations
            val translatedTexts = translatedCombined.split(" ||| ")
            logger.info("🔤 Split result: ${translatedTexts.size} texts")
            
            // Return original texts if split doesn't match
            return if (translatedTexts.size == texts.size) {
                logger.info("✅ Batch translation successful")
                translatedTexts
            } else {
                logger.warn("⚠️ Batch translation split mismatch, falling back to individual translations")
                // Fallback to individual translations
                texts.map { translateToEnglish(it) }
            }
            
        } catch (e: Exception) {
            logger.warn("❌ Batch translation failed, falling back to individual translations: ${e.message}")
            return texts.map { translateToEnglish(it) }
        }
    }
    
    private fun detectLanguage(text: String): String {
        // Simple language detection based on character patterns
        val cleanText = text.lowercase().trim()
        
        // Cache check
        val cacheKey = cleanText.take(100)
        languageDetectionCache[cacheKey]?.let { return it }
        
        val language = when {
            // English patterns
            cleanText.contains(Regex("\\b(the|and|for|are|with|that|this|will|from|they|been|have|were|said|each|which|their|time|but|its|who|did|get|may|him|old|see|now)\\b")) -> "en"
            
            // German patterns
            cleanText.contains(Regex("\\b(der|die|das|und|für|sind|mit|dass|dies|wird|von|sie|gewesen|haben|waren|gesagt|jeder|welche|ihre|zeit|aber|seine|wer|tat|bekommen|kann|ihm|alt|sehen|jetzt)\\b")) -> "de"
            
            // French patterns
            cleanText.contains(Regex("\\b(le|la|les|et|pour|sont|avec|que|ce|sera|de|ils|été|avoir|étaient|dit|chaque|qui|leur|temps|mais|son|qui|fait|obtenir|peut|lui|vieux|voir|maintenant)\\b")) -> "fr"
            
            // Spanish patterns
            cleanText.contains(Regex("\\b(el|la|los|las|y|para|son|con|que|esto|será|de|ellos|sido|haber|eran|dicho|cada|cual|su|tiempo|pero|quien|hizo|obtener|puede|él|viejo|ver|ahora)\\b")) -> "es"
            
            // Polish patterns
            cleanText.contains(Regex("\\b(i|w|na|z|do|że|to|jest|będzie|od|oni|byli|mieć|były|powiedział|każdy|które|ich|czas|ale|jego|kto|zrobił|dostać|może|mu|stary|widzieć|teraz)\\b")) -> "pl"
            
            // Default to English if uncertain
            else -> "en"
        }
        
        languageDetectionCache[cacheKey] = language
        return language
    }
    
    private suspend fun translateText(text: String, fromLang: String, toLang: String): String = withContext(Dispatchers.IO) {
        if (fromLang == toLang) return@withContext text
        
        try {
            val encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8)
            val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$fromLang&tl=$toLang&dt=t&q=$encodedText"
            
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .GET()
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() == 200) {
                return@withContext parseTranslationResponse(response.body())
            } else {
                logger.warn("Translation API returned status: ${response.statusCode()}")
                return@withContext text
            }
            
        } catch (e: Exception) {
            logger.warn("Translation request failed: ${e.message}")
            return@withContext text
        }
    }
    
    private fun parseTranslationResponse(responseBody: String): String {
        try {
            // Parse Google Translate response format
            val jsonArray = objectMapper.readTree(responseBody)
            val translations = jsonArray.get(0)
            
            val result = StringBuilder()
            for (translation in translations) {
                if (translation.isArray && translation.size() > 0) {
                    result.append(translation.get(0).asText())
                }
            }
            
            return result.toString().ifBlank { responseBody }
            
        } catch (e: Exception) {
            logger.warn("Failed to parse translation response: ${e.message}")
            return responseBody
        }
    }
}