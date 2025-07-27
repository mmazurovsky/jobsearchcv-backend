package com.jobsearchcv.backend.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.jobsearchcv.backend.domain.model.*
import com.jobsearchcv.backend.service.client.DeepSeekClient
import com.jobsearchcv.backend.service.client.DeepSeekRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

data class JobSearchParseResult(
    val success: Boolean,
    val jobSearchIn: JobSearchIn? = null,
    val errorMessage: String? = null,
    val missingFields: List<String> = emptyList()
)

@Service
class JobSearchParserService(
    private val deepSeekClient: DeepSeekClient
) {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(JobSearchParserService::class.java)
    }

    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    suspend fun parseUserInput(userInput: String, userId: String): JobSearchParseResult {
        return try {
            if (!deepSeekClient.isAvailable()) {
                logger.warn("DeepSeek API not available, falling back to basic parsing")
                return parseBasicInput(userInput, userId)
            }

            val prompt = buildPrompt(userInput)
            val response = deepSeekClient.chat(DeepSeekRequest(prompt))
            
            if (response.success && response.content != null) {
                parseDeepSeekResponse(response.content, userId)
            } else {
                logger.error("DeepSeek API failed: {}", response.errorMessage)
                JobSearchParseResult(
                    success = false,
                    errorMessage = "Sorry, I couldn't parse your job search request. Please try again with a clearer description."
                )
            }
        } catch (e: Exception) {
            logger.error("Error parsing user input: {}", userInput, e)
            JobSearchParseResult(
                success = false,
                errorMessage = "Sorry, I couldn't parse your job search request. Please try again with a clearer description."
            )
        }
    }

    private fun buildPrompt(userInput: String): String {
        return """
        Parse the following job search description into JSON format. Extract all relevant job search criteria.

        Required fields:
        - jobTitle: string (the job position/role)
        - location: string (where the user wants to work, should be a country, a city or town or village or Worldwide)

        Optional fields:
        - jobTypes: array of strings (employment types like "Full-time", "Part-time", "Contract", "Temporary", "Internship")
        - remoteTypes: array of strings (work arrangements like "Remote", "On-site", "Hybrid") 
        - timePeriod: string (frequency for alerts like "1 hour", "4 hours", "24 hours", "1 week", "1 month")
        - filterText: string (additional requirements, exclusions, salary, technologies, languages etc.)

        Available job types: ${JobType.getAllLabels().joinToString(", ")}
        Available remote types: ${RemoteType.getAllLabels().joinToString(", ")}
        Available time periods: ${TimePeriod.getAllLabels().joinToString(", ")}

        Rules:
        1. Only include timePeriod if the user explicitly mentions frequency (e.g., "hourly", "daily", "weekly", "every hour", "check every day")
        2. Put salary requirements, technology preferences, company preferences, and other requirements in filterText
        3. Return only valid JSON, no explanations
        4. Use exact values from the available options listed above

        User input: "$userInput"

        Examples:
            Input: "Senior Software Engineer in San Francisco, full-time, remote, check hourly"
            Output: {"jobTitle": "Senior Software Engineer", "location": "San Francisco", "jobTypes": ["Full-time"], "remoteTypes": ["Remote"], "timePeriod": "1 hour"}

            Input: "Data Scientist role in Berlin, contract work, only English language required, avoid startups"
            Output: {"jobTitle": "Data Scientist", "location": "Berlin", "jobTypes": ["Contract"], "remoteTypes": ["Remote"], "filterText": "only English language required, avoid startups"}
            
            Input: "Product Manager in NYC, search weekly, no travel"
            Output: {"jobTitle": "Product Manager", "location": "NYC", "timePeriod": "1 week", "filterText": "no travel"}
        """.trimIndent()
    }

    private fun parseDeepSeekResponse(response: String, userId: String): JobSearchParseResult {
        return try {
            // Extract JSON from response (remove any markdown formatting)
            val jsonString = response.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val parsedData = objectMapper.readValue<Map<String, Any>>(jsonString)
            
            val jobTitle = parsedData["jobTitle"] as? String
            val location = parsedData["location"] as? String
            val jobTypeLabels = parsedData["jobTypes"] as? List<*>
            val remoteTypeLabels = parsedData["remoteTypes"] as? List<*>
            val timePeriodName = parsedData["timePeriod"] as? String
            val filterText = parsedData["filterText"] as? String

            // Validate required fields
            val missingFields = mutableListOf<String>()
            if (jobTitle.isNullOrBlank()) missingFields.add("Job Title")
            if (location.isNullOrBlank()) missingFields.add("Location")

            if (missingFields.isNotEmpty()) {
                return JobSearchParseResult(
                    success = false,
                    errorMessage = "Missing required information: ${missingFields.joinToString(", ")}",
                    missingFields = missingFields
                )
            }

            // Parse job types
            val jobTypes = jobTypeLabels?.mapNotNull { label ->
                JobType.fromLabel(label.toString())
            }?.takeIf { it.isNotEmpty() } ?: listOf(JobType.getDefault())

            // Parse remote types  
            val remoteTypes = remoteTypeLabels?.mapNotNull { label ->
                RemoteType.fromLabel(label.toString())
            }?.takeIf { it.isNotEmpty() } ?: listOf(RemoteType.getDefault())

            // Parse time period with robust fallback
            val timePeriod = parseTimePeriodFromResponse(timePeriodName)
            
            logger.debug("Parsed timePeriod: {} from input: '{}'", timePeriod, timePeriodName)

            val jobSearchIn = JobSearchIn(
                id = UUID.randomUUID().toString(),
                jobTitle = jobTitle!!,
                location = location!!,
                jobTypes = jobTypes,
                remoteTypes = remoteTypes,
                timePeriod = timePeriod,
                filterText = if (filterText.isNullOrBlank()) null else filterText
            )

            JobSearchParseResult(success = true, jobSearchIn = jobSearchIn)

        } catch (e: Exception) {
            logger.error("Error parsing DeepSeek response: {}", response, e)
            JobSearchParseResult(
                success = false,
                errorMessage = "I couldn't understand your job search description. Please provide clearer details about the job title and location."
            )
        }
    }

    private fun parseBasicInput(userInput: String, userId: String): JobSearchParseResult {
        // Fallback basic parsing when DeepSeek is not available
        val words = userInput.lowercase().split("\\s+".toRegex())
        
        // Try to extract basic information
        val hasLocation = words.any { word ->
            listOf("in", "at", "from", "located", "based").any { userInput.lowercase().contains("$it $word") }
        }
        
        if (!hasLocation) {
            return JobSearchParseResult(
                success = false,
                errorMessage = "Please specify both a job title and location. For example: 'Software Engineer in San Francisco'",
                missingFields = listOf("Job Title", "Location")
            )
        }

        // This is a very basic fallback - in production, you might want more sophisticated parsing
        return JobSearchParseResult(
            success = false,
            errorMessage = "Advanced parsing is not available. Please provide your search in this format:\n" +
                    "Job Title: [your job title]\n" +
                    "Location: [your location]\n" +
                    "Job Type: [Full-time/Part-time/Contract/etc.]\n" +
                    "Remote: [Remote/On-site/Hybrid]"
        )
    }
    
    /**
     * Parses time period from LLM response with robust fallback logic
     */
    private fun parseTimePeriodFromResponse(timePeriodName: String?): TimePeriod {
        if (timePeriodName.isNullOrBlank()) {
            logger.debug("No timePeriod specified, using default: {}", TimePeriod.getDefault().displayName)
            return TimePeriod.getDefault()
        }
        
        // Try exact match first
        TimePeriod.fromDisplayName(timePeriodName)?.let { 
            logger.debug("Found exact match for timePeriod: {} -> {}", timePeriodName, it.displayName)
            return it 
        }
        
        // Try case-insensitive match
        TimePeriod.values().find { 
            it.displayName.equals(timePeriodName, ignoreCase = true) 
        }?.let { 
            logger.debug("Found case-insensitive match for timePeriod: {} -> {}", timePeriodName, it.displayName)
            return it 
        }
        
        // Try partial matches for common user input variations
        val lowerInput = timePeriodName.lowercase().trim()
        val match = when {
            lowerInput.contains("5 min") || lowerInput.contains("5min") -> TimePeriod.`5 minutes`
            lowerInput.contains("10 min") || lowerInput.contains("10min") -> TimePeriod.`10 minutes`
            lowerInput.contains("15 min") || lowerInput.contains("15min") -> TimePeriod.`15 minutes`
            lowerInput.contains("20 min") || lowerInput.contains("20min") -> TimePeriod.`20 minutes`
            lowerInput.contains("30 min") || lowerInput.contains("30min") -> TimePeriod.`30 minutes`
            lowerInput.contains("1 hour") || lowerInput.contains("hour") && lowerInput.contains("1") -> TimePeriod.`1 hour`
            lowerInput.contains("4 hour") || lowerInput.contains("4hour") -> TimePeriod.`4 hours`
            lowerInput.contains("24 hour") || lowerInput.contains("daily") || lowerInput.contains("day") -> TimePeriod.`24 hours`
            lowerInput.contains("1 week") || lowerInput.contains("week") -> TimePeriod.`1 week`
            lowerInput.contains("1 month") || lowerInput.contains("month") -> TimePeriod.`1 month`
            else -> null
        }
        
        if (match != null) {
            logger.debug("Found partial match for timePeriod: {} -> {}", timePeriodName, match.displayName)
            return match
        }
        
        logger.warn("Could not parse timePeriod: '{}', using default: {}", timePeriodName, TimePeriod.getDefault().displayName)
        return TimePeriod.getDefault()
    }
} 