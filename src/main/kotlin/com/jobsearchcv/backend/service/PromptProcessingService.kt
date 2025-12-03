package com.jobsearchcv.backend.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.jobsearchcv.backend.domain.model.*
import com.jobsearchcv.backend.service.client.OpenRouterClient
import com.jobsearchcv.backend.service.client.LLMRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class PromptProcessingService(
    private val openRouterClient: OpenRouterClient
) {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(PromptProcessingService::class.java)
    }

    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    /**
     * Process free-form prompt with GPT-4 Turbo to generate job searches
     */
    suspend fun processPromptWithAI(prompt: String, userId: String): PromptProcessingResult {
        try {
            logger.info("Processing prompt with AI for user: $userId")
            logger.debug("Prompt text: $prompt")

            if (!openRouterClient.isAvailable()) {
                return PromptProcessingResult(
                    success = false,
                    recommendedSearches = null,
                    errorMessage = "OpenRouter API is not available"
                )
            }

            val systemPrompt = buildPromptProcessingPrompt(prompt)

            val llmRequest = LLMRequest(
                prompt = systemPrompt,
                temperature = 0.3,
                maxTokens = 12000,
                model = "openai/gpt-4-turbo"
            )

            val response = openRouterClient.chat(llmRequest)

            if (!response.success || response.content.isNullOrBlank()) {
                logger.error("OpenRouter API failed: ${response.errorMessage}")
                return PromptProcessingResult(
                    success = false,
                    recommendedSearches = null,
                    errorMessage = response.errorMessage ?: "Failed to process prompt with AI"
                )
            }

            // Parse AI response as JSON
            val searches = try {
                parseAIResponse(response.content, userId)
            } catch (e: Exception) {
                logger.error("Failed to parse AI response: ${e.message}, content: ${response.content}", e)
                return PromptProcessingResult(
                    success = false,
                    recommendedSearches = null,
                    errorMessage = "Failed to parse AI response: ${e.message}"
                )
            }

            logger.info("Successfully processed prompt with AI: generated ${searches.size} searches for user: $userId")
            return PromptProcessingResult(
                success = true,
                recommendedSearches = searches
            )

        } catch (e: Exception) {
            logger.error("Error processing prompt with AI: ${e.message}", e)
            return PromptProcessingResult(
                success = false,
                recommendedSearches = null,
                errorMessage = e.message ?: "Unknown error during prompt processing"
            )
        }
    }

    /**
     * Build the system prompt for LLM
     */
    private fun buildPromptProcessingPrompt(userPrompt: String): String {
        return """
Role and Objective
- You are a senior recruiter and job search specialist. Your objective is to analyze a user's free-form job search request and generate 2-3 tailored job search configurations.

Instructions
- Carefully extract job search parameters from the user's text: job titles, locations, job types, remote preferences, time periods, and additional requirements.
- Extract any specific skills, technologies, requirements, or keywords mentioned by the user and place them in the filterText field.
- Apply the default values specified below for any parameters NOT mentioned by the user.
- Generate 2-3 diverse job searches that fulfill the user's intent.
- Output only a valid JSON object matching the defined schema. Do not include any extra commentary or text outside the JSON object.

Default Values (use when NOT specified in prompt)
- jobTitle: "" (empty string if not specified)
- location: "" (empty string if not specified)
- jobTypes: ["Full-time"] (if not specified)
- remoteTypes: ["Hybrid"] (if not specified)
- timePeriod: "20 minutes" (if not specified)
- filterText: null (if no specific requirements mentioned)

Acceptable Values (ONLY use these)
- jobTypes: Full-time, Part-time, Contract, Temporary, Internship
- remoteTypes: Remote, On-site, Hybrid
- timePeriods (frequencies): 5 minutes, 10 minutes, 15 minutes, 20 minutes, 30 minutes, 1 hour, 4 hours, 24 hours, 1 week, 1 month

Filter Text Extraction Rules
Extract and consolidate ALL relevant requirements into filterText as natural language sentences. Include:

1. Salary expectations (e.g., minimum salary, salary range, compensation requirements)
2. Communication language preference (e.g., only speaking English, only speaking Spanish)
3. Visa sponsorship needs (e.g., requires visa sponsorship, needs H1B support)
4. Preferred technologies, frameworks, and tools (e.g., only positions with React and TypeScript requirement)
5. Experience level and years (e.g., only senior level position)
6. Commute preferences (e.g., require public transport access)
7. Travel willingness (e.g., willing to travel up to 25%, no travelling is required)
8. Relocation preferences (e.g., open to relocation, no relocation wanted, willing to relocate to Europe)
9. Other specific requirements (e.g., startups only, job in healthcare domain)

Format Requirements:
- Write in concise natural language sentences, NOT comma-separated keywords
- Use clear, specific statements (e.g., "Requires visa sponsorship" not just "visa")
- Group related requirements into coherent points
- Each requirement should be a complete, understandable statement
- Separate multiple requirements with semicolons
- Keep it concise but informative - aim for 1-4 sentences total

Examples:
* "Python developer with Django, need visa sponsorship and at least 80k salary"
  → filterText: "I want to work with Python and Django. Require visa sponsorship. Minimum salary expectation 80k."

* "Frontend engineer React and TypeScript, German speaking, willing to relocate"
  → filterText: "I want to work with React and TypeScript. I speak German. Open to relocation."

* "Senior backend developer with 5+ years AWS, remote only, no travel"
  → filterText: "I want to work with AWS. I prefer no travel."

* "Data scientist with ML experience, 100k+ salary, needs H1B support"
  → filterText: "I want to work with Machine learning. My minimum salary is 100k. I require H1B visa sponsorship."

* "Marketing manager with SEO, flexible about relocation, Spanish preferred"
  → filterText: "I want to work with SEO. Spanish language skills preferred. Flexible about relocation."

* "Software engineer, willing to travel monthly"
  → filterText: "Willing to travel monthly for meetings."

Search Generation Rules
- Generate 2-3 searches (aim for 2-3 based on prompt complexity)
- First search: Primary job title/location from prompt with Hybrid remote type (or user-specified)
- Second search: Broader or alternative title with Remote remote type
- Additional search (optional): Variation in location, job type, or time period for diversity
- Remote remoteTypes: Use ONLY with country-level location (e.g., "United States", "Canada")
- Hybrid/On-site remoteTypes: Use ONLY with city or state (e.g., "San Francisco, CA", "California")
- jobTitle: Must contain ONLY ONE job title, never combine multiple roles
  Good examples: "Frontend Software Developer", "Product Manager", "Data Scientist"
  Bad examples: "Software Developer and Scrum Master", "Frontend or Backend Developer"
- location: Use only city, state, or country granularity (never more specific, never more broad)
- filterText: Should be consistent across all searches for the same prompt (same requirements apply to all searches)

Handling Vague Prompts
- If prompt is very vague (e.g., "find me jobs"): Create searches with empty jobTitle and location, use default values
- If only title specified: Use title, empty location, defaults for rest
- If only location specified: Empty title, use location, defaults for rest

JSON Schema to Output
{
  "recommended_searches": [
    {
      "jobTitle": string,
      "location": string,
      "jobTypes": string[],
      "remoteTypes": string[],
      "timePeriod": string,
      "filterText": string | null
    }
  ]
}

User's Job Search Request:
${userPrompt}

Remember: Return ONLY the JSON object, no additional text or formatting.
        """.trimIndent()
    }

    /**
     * Parse AI response JSON into JobSearchIn objects
     */
    private fun parseAIResponse(aiResponse: String, userId: String): List<JobSearchIn> {
        // Clean the response to extract JSON
        val jsonStart = aiResponse.indexOf("{")
        val jsonEnd = aiResponse.lastIndexOf("}") + 1

        if (jsonStart == -1 || jsonEnd <= jsonStart) {
            throw IllegalArgumentException("No valid JSON found in AI response")
        }

        val jsonContent = aiResponse.substring(jsonStart, jsonEnd)

        // Parse the JSON response
        val parsedResponse: Map<String, Any> = objectMapper.readValue(jsonContent)

        // Extract recommended searches
        val recommendedSearches = (parsedResponse["recommended_searches"] as? List<*>)?.mapNotNull { searchMap ->
            (searchMap as? Map<*, *>)?.let { search ->
                try {
                    val jobTitle = search["jobTitle"] as? String ?: ""
                    val jobLocation = search["location"] as? String ?: ""
                    val jobTypesRaw = search["jobTypes"] as? List<*> ?: listOf("Full-time")
                    val remoteTypesRaw = search["remoteTypes"] as? List<*> ?: listOf("Hybrid")
                    val timePeriodRaw = search["timePeriod"] as? String ?: "20 minutes"
                    val filterTextRaw = search["filterText"] as? String

                    val jobTypes = jobTypesRaw.mapNotNull { JobType.fromLabel(it.toString()) }
                    val remoteTypes = remoteTypesRaw.mapNotNull { RemoteType.fromLabel(it.toString()) }
                    val timePeriod = TimePeriod.fromDisplayName(timePeriodRaw)
                        ?: TimePeriod.fromDisplayName("20 minutes")!!

                    // Clean up filterText: if empty or whitespace, set to null
                    val filterText = filterTextRaw?.trim()?.takeIf { it.isNotBlank() }

                    JobSearchIn(
                        id = UUID.randomUUID().toString(),
                        jobTitle = jobTitle,
                        location = jobLocation,
                        jobTypes = jobTypes.ifEmpty { listOf(JobType.getDefault()) },
                        remoteTypes = remoteTypes.ifEmpty { listOf(RemoteType.fromLabel("Hybrid")!!) },
                        timePeriod = timePeriod,
                        filterText = filterText
                    )
                } catch (e: Exception) {
                    logger.warn("Failed to parse job search recommendation: ${e.message}")
                    null
                }
            }
        } ?: emptyList()

        if (recommendedSearches.isEmpty()) {
            throw IllegalArgumentException("No valid job searches found in AI response")
        }

        return recommendedSearches
    }
}
