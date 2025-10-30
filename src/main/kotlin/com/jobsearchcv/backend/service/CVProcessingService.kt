package com.jobsearchcv.backend.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.jobsearchcv.backend.domain.model.*
import com.jobsearchcv.backend.service.client.OpenRouterClient
import com.jobsearchcv.backend.service.client.LLMRequest
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
import java.util.UUID

@Service
class CVProcessingService(
    private val openRouterClient: OpenRouterClient
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
            
            if (!openRouterClient.isAvailable()) {
                return CVProcessingResult(
                    success = false,
                    analysisResult = null,
                    errorMessage = "OpenRouter API is not available"
                )
            }

            val systemPrompt = buildCVProcessingPrompt(request.extractedText)
            
            val LLMRequest = LLMRequest(
                prompt = systemPrompt,
                temperature = 0.3,
                maxTokens = 12000,
                model = "openai/gpt-4-turbo"
            )

            val response = openRouterClient.chat(LLMRequest)

            if (!response.success || response.content.isNullOrBlank()) {
                logger.error("OpenRouter API failed: ${response.errorMessage}")
                return CVProcessingResult(
                    success = false,
                    analysisResult = null,
                    errorMessage = response.errorMessage ?: "Failed to process CV with AI"
                )
            }

            // Parse AI response as JSON
            val analysisResult = try {
                parseAIResponse(response.content, request.userId)
            } catch (e: Exception) {
                logger.error("Failed to parse AI response: ${e.message}, content: ${response.content}", e)
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
Role and Objective
- You are a senior recruiter highly skilled in talent acquisition and CV analysis. Your objective is to process a provided CV, extract structured data, and generate tailored job search recommendations.

Instructions
- Begin with a concise checklist (3–7 bullets) of what you will do; keep items conceptual, not implementation-level.
- Carefully extract information from the CV text and map it according to the data requirements below.
- Apply logic for weight assignment, chronology, and location as described.
- Recommend job searches using the extracted data, adhering to the prescribed business rules and requirements below.
- Output only a valid JSON object matching the defined schema. Do not include any extra commentary or text outside the JSON object.

Job Search Recommendation Specific Rules
- Use only acceptable job types when constructing "jobTypes": Full-time, Part-time, Contract, Temporary, Internship. Default to Full-time.
- Use only these remote types in "remoteTypes": Remote, On-site, Hybrid. Default to Remote.
- Only use the following time periods for "timePeriod": 20 minutes, 30 minutes, 1 hour, 4 hours, 24 hours. Default to 20 minutes for the primary search, 30 minutes for the secondary search, and 4 hours for any additional searches.

Sub-categories / Special Rules
- Output 3-5 recommended_searches
- Remote remoteTypes can be used ONLY with country
- Hybrid and On-site remoteTypes can be used ONLY with city or state
- For first search use same title as latest title in CV or title specified first in CV. For secondary search use title one level higher than first one. Additional titles should not contain levels in them and should be different from primary and secondary
- Use only city, state, or country granularity for location—never more specific, never more broad.
- jobTitle must contain only ONE job title, never combine multiple roles.
  Good examples: "Frontend Software Developer", "Product Manager", "Data Scientist"
  Bad examples: "Software Developer and Scrum Master", "Frontend or Backend Developer", "Developer/Designer"
- At least one job search must use Hybrid remoteType and a specific city or state if the user's location is certain and sufficiently granular.
- Skill weights: 80+ for frequently mentioned, specified earlier in CV; 40 for specified later in CV, specified only once; 60-79 for others.

Context
- The input is plain CV text. Extraction accuracy for jobs, skills, location, and languages is essential.
- Use given chronology or, if missing, use the order encountered.

Planning and Verification
- After mapping and formatting, validate the output: ensure all required fields are present, types match the schema, and handle all missing/ambiguous data per rules. Make a final schema compliance pass before output.

Output Format
- Output is concise and limited to JSON object. Return only valid JSON as described below. No additional explanations. Don't include prompt in your output.

Stop Conditions
- End when a fully valid JSON object strictly adhering to the schema and instructions is ready.

JSON Schema to output:
{
  "current_or_desired_position": string | null,
  "previous_positions": string[],
  "skills_with_weights": [
    {"skill": string, "weight": integer}
  ],
  "location": string | null,
  "languages": string[] | [],
  "recommended_searches": [
    {
      "jobTitle": string,
      "location": string,
      "jobTypes": string[],
      "remoteTypes": string[],
      "timePeriod": string
    }
  ]
}

Handle all missing or ambiguous data as instructed: use null for strings and empty arrays for lists.

CV TEXT:
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

                    val jobTypes = jobTypesRaw.mapNotNull { JobType.fromLabel(it.toString()) }
                    val remoteTypes = remoteTypesRaw.mapNotNull { RemoteType.fromLabel(it.toString()) }
                    val timePeriod = TimePeriod.fromDisplayName(timePeriodRaw) ?: TimePeriod.getDefault()

                    JobSearchIn(
                        id = UUID.randomUUID().toString(),
                        jobTitle = jobTitle,
                        location = jobLocation,
                        jobTypes = jobTypes.ifEmpty { listOf(JobType.getDefault()) },
                        remoteTypes = remoteTypes.ifEmpty { listOf(RemoteType.getDefault()) },
                        timePeriod = timePeriod,
                        filterText = null
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