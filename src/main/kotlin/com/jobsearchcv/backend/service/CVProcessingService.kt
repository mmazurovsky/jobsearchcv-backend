package com.jobsearchcv.backend.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.jobsearchcv.backend.domain.model.*
import com.jobsearchcv.backend.service.client.DeepSeekClient
import com.jobsearchcv.backend.service.client.DeepSeekRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.extractor.WordExtractor
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.extractor.XWPFWordExtractor
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayInputStream

@Service
class CVProcessingService(
    private val deepSeekClient: DeepSeekClient
) {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(CVProcessingService::class.java)
    }

    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    /**
     * Extract text from uploaded CV file
     */
    suspend fun extractTextFromCV(file: MultipartFile): String = withContext(Dispatchers.IO) {
        try {
            logger.info("Extracting text from CV: ${file.originalFilename}")
            val contentType = file.contentType ?: ""
            val fileName = file.originalFilename ?: ""
            
            val extractedText = when {
                contentType.contains("pdf") || fileName.endsWith(".pdf", ignoreCase = true) -> {
                    extractTextFromPDF(file.bytes)
                }
                contentType.contains("msword") || fileName.endsWith(".doc", ignoreCase = true) -> {
                    extractTextFromDOC(file.bytes)
                }
                contentType.contains("wordprocessingml") || fileName.endsWith(".docx", ignoreCase = true) -> {
                    extractTextFromDOCX(file.bytes)
                }
                else -> {
                    throw IllegalArgumentException("Unsupported file format: $contentType")
                }
            }
            
            logger.info("Successfully extracted ${extractedText.length} characters from CV")
            extractedText
        } catch (e: Exception) {
            logger.error("Failed to extract text from CV: ${e.message}", e)
            throw RuntimeException("Failed to extract text from CV: ${e.message}", e)
        }
    }

    /**
     * Process CV text with DeepSeek AI to extract structured data and generate job searches
     */
    suspend fun processCVWithAI(request: CVProcessingRequest): CVProcessingResult {
        try {
            logger.info("Processing CV with AI for user: ${request.userId}")
            
            if (!deepSeekClient.isAvailable()) {
                return CVProcessingResult(
                    success = false,
                    analysisResult = null,
                    errorMessage = "DeepSeek API is not available"
                )
            }

            val systemPrompt = buildCVProcessingPrompt(request.extractedText)
            
            val deepSeekRequest = DeepSeekRequest(
                prompt = systemPrompt,
                temperature = 0.3,
                maxTokens = 3000,
                model = "deepseek-chat"
            )

            val response = deepSeekClient.chat(deepSeekRequest)

            if (!response.success || response.content.isNullOrBlank()) {
                logger.error("DeepSeek API failed: ${response.errorMessage}")
                return CVProcessingResult(
                    success = false,
                    analysisResult = null,
                    errorMessage = response.errorMessage ?: "Failed to process CV with AI"
                )
            }

            // Parse AI response as JSON
            val analysisResult = try {
                parseAIResponse(response.content!!, request.userId)
            } catch (e: Exception) {
                logger.error("Failed to parse AI response: ${e.message}", e)
                logger.debug("AI response that failed to parse: ${response.content}")
                return CVProcessingResult(
                    success = false,
                    analysisResult = null,
                    errorMessage = "Failed to parse AI response: ${e.message}"
                )
            }

            logger.info("Successfully processed CV with AI for user: ${request.userId}")
            return CVProcessingResult(
                success = true,
                analysisResult = analysisResult
            )

        } catch (e: Exception) {
            logger.error("Error processing CV with AI: ${e.message}", e)
            return CVProcessingResult(
                success = false,
                analysisResult = null,
                errorMessage = e.message ?: "Unknown error during CV processing"
            )
        }
    }

    private fun extractTextFromPDF(bytes: ByteArray): String {
        return Loader.loadPDF(bytes).use { document ->
            val stripper = PDFTextStripper()
            stripper.getText(document)
        }
    }

    private fun extractTextFromDOC(bytes: ByteArray): String {
        return HWPFDocument(ByteArrayInputStream(bytes)).use { document ->
            val extractor = WordExtractor(document)
            extractor.text
        }
    }

    private fun extractTextFromDOCX(bytes: ByteArray): String {
        return XWPFDocument(ByteArrayInputStream(bytes)).use { document ->
            val extractor = XWPFWordExtractor(document)
            extractor.text
        }
    }

    private fun buildCVProcessingPrompt(extractedText: String): String {
        return """
You are a senior recruiter with extensive experience in talent acquisition and CV analysis. You need to process the following CV text and extract structured information, then propose relevant job searches.

**STEP 1: Extract Information**
From the CV text below, extract:
1. Current or desired position (the job title the person is seeking or currently has)
2. All previous positions/job titles the person has held
3. List of skills and technologies with experience weights (0-100, where 100 = expert level)
4. Education background
5. Location (city, country if specified)

**STEP 2: Propose Job Searches**
Based on the extracted information, create 3-5 job search recommendations following this logic:
- Primary search: Based on current/desired position
- Alternative searches: Based on previous experience and transferable skills
- Consider the person's skill level and experience when suggesting job types
- Use appropriate job types: Full-time, Part-time, Contract, Temporary, Internship
- Use appropriate remote types: On-site, Remote, Hybrid
- Use appropriate time periods: 1 hour, 4 hours, 24 hours (for active job searching)

**IMPORTANT**: Return ONLY a valid JSON object with this exact structure:

```json
{
  "current_or_desired_position": "Software Engineer",
  "previous_positions": ["Junior Developer", "Intern"],
  "skills_with_weights": [
    {"skill": "JavaScript", "weight": 85},
    {"skill": "React", "weight": 90},
    {"skill": "Node.js", "weight": 75}
  ],
  "education": ["Bachelor's in Computer Science"],
  "location": "San Francisco, CA",
  "recommended_searches": [
    {
      "jobTitle": "Senior Software Engineer",
      "location": "San Francisco, CA",
      "jobTypes": ["Full-time"],
      "remoteTypes": ["Remote", "Hybrid"],
      "timePeriod": "4 hours",
      "userId": 0,
      "filterText": "React JavaScript Node.js"
    }
  ]
}
```

**CV Text to Process:**
${extractedText}

Remember: Return ONLY the JSON object, no additional text or formatting.
        """.trimIndent()
    }

    private fun parseAIResponse(aiResponse: String, userId: String): CVAnalysisResult {
        // Clean the response to extract JSON
        val jsonStart = aiResponse.indexOf("{")
        val jsonEnd = aiResponse.lastIndexOf("}") + 1
        
        if (jsonStart == -1 || jsonEnd <= jsonStart) {
            throw IllegalArgumentException("No valid JSON found in AI response")
        }
        
        val jsonContent = aiResponse.substring(jsonStart, jsonEnd)
        
        // Parse the JSON response
        val parsedResponse: Map<String, Any> = objectMapper.readValue(jsonContent)
        
        // Extract and validate data
        val currentOrDesiredPosition = parsedResponse["current_or_desired_position"] as? String
        val previousPositions = (parsedResponse["previous_positions"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        val education = (parsedResponse["education"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        val location = parsedResponse["location"] as? String
        
        // Parse skills with weights
        val skillsWithWeights = (parsedResponse["skills_with_weights"] as? List<*>)?.mapNotNull { skillMap ->
            (skillMap as? Map<*, *>)?.let { skill ->
                val skillName = skill["skill"] as? String
                val weight = (skill["weight"] as? Number)?.toInt()
                if (skillName != null && weight != null) {
                    SkillWithWeight(skillName, weight.coerceIn(0, 100))
                } else null
            }
        } ?: emptyList()
        
        // Parse recommended searches
        val recommendedSearches = (parsedResponse["recommended_searches"] as? List<*>)?.mapNotNull { searchMap ->
            (searchMap as? Map<*, *>)?.let { search ->
                try {
                    val jobTitle = search["jobTitle"] as? String ?: return@mapNotNull null
                    val jobLocation = search["location"] as? String ?: "Remote"
                    val jobTypesRaw = search["jobTypes"] as? List<*> ?: listOf("Full-time")
                    val remoteTypesRaw = search["remoteTypes"] as? List<*> ?: listOf("Remote")
                    val timePeriodRaw = search["timePeriod"] as? String ?: "1 hour"
                    val filterText = search["filterText"] as? String
                    
                    val jobTypes = jobTypesRaw.mapNotNull { JobType.fromLabel(it.toString()) }
                    val remoteTypes = remoteTypesRaw.mapNotNull { RemoteType.fromLabel(it.toString()) }
                    val timePeriod = TimePeriod.fromDisplayName(timePeriodRaw) ?: TimePeriod.getDefault()
                    
                    JobSearchIn(
                        jobTitle = jobTitle,
                        location = jobLocation,
                        jobTypes = jobTypes.ifEmpty { listOf(JobType.getDefault()) },
                        remoteTypes = remoteTypes.ifEmpty { listOf(RemoteType.getDefault()) },
                        timePeriod = timePeriod,

                        filterText = filterText
                    )
                } catch (e: Exception) {
                    logger.warn("Failed to parse job search recommendation: ${e.message}")
                    null
                }
            }
        } ?: emptyList()
        
        return CVAnalysisResult(
            currentOrDesiredPosition = currentOrDesiredPosition,
            previousPositions = previousPositions,
            skillsWithWeights = skillsWithWeights,
            education = education,
            location = location,
            recommendedSearches = recommendedSearches
        )
    }
}