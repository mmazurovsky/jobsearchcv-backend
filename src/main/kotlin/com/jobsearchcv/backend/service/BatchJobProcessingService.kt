package com.jobsearchcv.backend.service

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.jobsearchcv.backend.domain.model.*
import com.jobsearchcv.backend.repository.ProcessedJobRepository
import com.jobsearchcv.backend.service.client.OpenRouterClient
import com.jobsearchcv.backend.service.client.LLMRequest
import com.jobsearchcv.backend.service.client.LLMConfig
import com.jobsearchcv.backend.service.batch.JobBatchCalculator
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class BatchEnrichmentRequest(
    val jobId: String,
    val title: String,
    val description: String,
    val company: String
)

data class BatchEnrichmentResult(
    @JsonProperty("job_id") val jobId: String,
    @JsonProperty("techstack") val techstack: List<String>,
    @JsonProperty("tags") val tags: List<String> = emptyList(),
    @JsonProperty("salary") val salary: String?
)

data class BatchCompatibilityRequest(
    val jobId: String,
    val title: String,
    val description: String,
    val company: String,
    val techstack: List<String>,
    val tags: List<String>,
    val salary: String?,
    val applicants: String?
)

data class BatchCompatibilityResult(
    @JsonProperty("job_id") val jobId: String,
    @JsonProperty("compatibility_score") val compatibilityScore: Int,
    @JsonProperty("filter_reason") val filterReason: String?
)

@Service
class BatchJobProcessingService(
    private val translationService: TranslationService,
    private val openRouterClient: OpenRouterClient,
    private val objectMapper: ObjectMapper,
    private val jobDataConverter: JobDataConverter,
    private val processedJobRepository: ProcessedJobRepository,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    // Semaphore to limit parallel LLM requests
    private val llmSemaphore = Semaphore(3)

    /**
     * Full LLM processing pipeline for ScrapedJobData.
     * Steps: Translation → Enrichment → Scoring → Storage
     */
    suspend fun processAndSaveJobsDataBatch(
        jobsData: List<ScrapedJobData>,
        jobSearch: JobSearchOut?,
        enrichmentConfig: LLMConfig = LLMConfig.forEnrichment(),
        scoringConfig: LLMConfig = LLMConfig.forScoring()
    ): List<ScoredJobData> = withContext(Dispatchers.IO) {
        val jobSearchId = jobSearch?.id ?: "unknown"
        try {
            logger.info("[JobSearch: $jobSearchId] Processing ${jobsData.size} jobs using full LLM pipeline")

            // Step 1: Translation
            val translatedJobs = translateJobsDataBatch(jobsData, jobSearchId)
            logger.info("[JobSearch: $jobSearchId] Translated ${translatedJobs.size} jobs")

            // Step 2: Enrichment (techstack and salary extraction)
            val enrichedJobs = enrichJobsDataBatch(translatedJobs, jobSearchId, enrichmentConfig)
            logger.info("[JobSearch: $jobSearchId] Enriched ${enrichedJobs.size} jobs")
            enrichedJobs.filter { it.techstack.isEmpty() }.forEach { job ->
                logger.warn("[JobSearch: $jobSearchId] Didn't get techstack for job ${job.id}, link: ${job.link}")
            }

            // Step 3: Save processed jobs
            val processedJobs = enrichedJobs.map { job ->
                jobDataConverter.enrichedToProcessedJobData(job)
            }
            processedJobRepository.bulkSaveOrUpdate(processedJobs)

            // Step 4: Compatibility Scoring
            val scoredJobs = scoreJobsDataBatch(
                processedJobs,
                enrichedJobs,
                jobSearch,
                scoringConfig
            ).sortedByDescending { it.compatibilityScore }
            logger.info("[JobSearch: $jobSearchId] Scored ${scoredJobs.size} jobs")
            logger.info("[JobSearch: $jobSearchId] Scores of scored jobs: ${scoredJobs.map { it.compatibilityScore }}")

            logger.info("[JobSearch: $jobSearchId] Successfully processed ${scoredJobs.size} jobs through full LLM pipeline")
            return@withContext scoredJobs
        } catch (e: Exception) {
            logger.error("[JobSearch: $jobSearchId] Error in full job processing pipeline", e)
            io.sentry.Sentry.captureException(e)
            // Return minimal ScoredJobData for failed jobs
            return@withContext emptyList()
        }
    }

    /**
     * Partial LLM processing pipeline for XCOM/PAGE channels.
     * Steps: Translation → Enrichment → Storage (NO scoring)
     * Returns enriched jobs as ScoredJobData with default compatibility score for consistency.
     */
    suspend fun processJobsForXcomOrPageChannel(
        jobsData: List<ScrapedJobData>,
        jobSearch: JobSearchOut?,
        enrichmentConfig: LLMConfig = LLMConfig.forEnrichment()
    ): List<ScoredJobData> = withContext(Dispatchers.IO) {
        val jobSearchId = jobSearch?.id ?: "unknown"
        try {
            logger.info("[JobSearch: $jobSearchId] Processing ${jobsData.size} jobs for XCOM/PAGE channel (no scoring)")

            // Step 1: Translation
            val translatedJobs = translateJobsDataBatch(jobsData, jobSearchId)
            logger.info("[JobSearch: $jobSearchId] Translated ${translatedJobs.size} jobs")

            // Step 2: Enrichment (techstack, tags, salary extraction)
            val enrichedJobs = enrichJobsDataBatch(translatedJobs, jobSearchId, enrichmentConfig)
            logger.info("[JobSearch: $jobSearchId] Enriched ${enrichedJobs.size} jobs")
            enrichedJobs.filter { it.techstack.isEmpty() }.forEach { job ->
                logger.warn("[JobSearch: $jobSearchId] No techstack for job ${job.id}, link: ${job.link}")
            }

            // Step 3: Save to processed_jobs
            val processedJobs = enrichedJobs.map { job ->
                jobDataConverter.enrichedToProcessedJobData(job)
            }
            processedJobRepository.bulkSaveOrUpdate(processedJobs)
            logger.info("[JobSearch: $jobSearchId] Saved ${processedJobs.size} jobs to processed_jobs")

            // Step 4: Convert to ScoredJobData with default score (100 = no filtering)
            val scoredJobs = enrichedJobs.mapNotNull { enrichedJob ->
                val processedJob = processedJobs.find { it.id == enrichedJob.id }
                if (processedJob != null) {
                    jobDataConverter.toScoredJobData(
                        processedJob = processedJob,
                        createdAgo = enrichedJob.createdAgo,
                        scrapedAt = enrichedJob.scrapedAt,
                        userId = enrichedJob.userId,
                        jobSearchId = enrichedJob.jobSearchId,
                        keywords = enrichedJob.keywords,
                        compatibilityScore = 100, // Default: send all jobs
                        filterReason = null
                    )
                } else {
                    logger.error("[JobSearch: $jobSearchId] Missing processed job for ${enrichedJob.id}")
                    null
                }
            }

            logger.info("[JobSearch: $jobSearchId] Successfully processed ${scoredJobs.size} jobs for XCOM/PAGE")
            return@withContext scoredJobs

        } catch (e: Exception) {
            logger.error("[JobSearch: $jobSearchId] Error in XCOM/PAGE processing pipeline", e)
            io.sentry.Sentry.captureException(e)
            return@withContext emptyList()
        }
    }

    /**
     * Translates job titles and descriptions to English.
     * Restored from legacy translateJobsBatch method.
     */
    private suspend fun translateJobsDataBatch(
        jobsData: List<ScrapedJobData>,
        jobSearchId: String
    ): List<TranslatedJobData> {
        return try {
            // Extract all text that needs translation
            val textsToTranslate = jobsData.flatMap { job ->
                listOf(job.title, job.description)
            }

            // Translate in batch
            val translatedTexts = translationService.translateMultipleToEnglish(textsToTranslate)

            // Apply translations back to jobs
            jobsData.mapIndexed { index, job ->
                val titleIndex = index * 2
                val descriptionIndex = index * 2 + 1

                jobDataConverter.toTranslatedJobData(
                    scrapedJob = job,
                    translatedTitle = if (titleIndex < translatedTexts.size) translatedTexts[titleIndex] else job.title,
                    translatedDescription = if (descriptionIndex < translatedTexts.size) translatedTexts[descriptionIndex] else job.description
                )
            }

        } catch (e: Exception) {
            logger.error("[JobSearch: $jobSearchId] Error in batch translation", e)
            // Return original jobs as TranslatedJobData if translation fails
            jobsData.map { job ->
                jobDataConverter.toTranslatedJobData(
                    scrapedJob = job,
                    translatedTitle = job.title,
                    translatedDescription = job.description
                )
            }
        }
    }

    /**
     * Enriches jobs with techstack and salary information using LLM.
     * Restored from legacy enrichJobsBatch method.
     */
    private suspend fun enrichJobsDataBatch(
        translatedJobs: List<TranslatedJobData>,
        jobSearchId: String,
        enrichmentConfig: LLMConfig
    ): List<EnrichedJobData> {
        try {
            // Step 1: Split into batches based on content length
            val enrichmentPromptTemplate = buildEnrichmentPromptTemplate()
            val enrichmentCalculator = JobBatchCalculator<TranslatedJobData, BatchEnrichmentRequest>(
                llmConfig = enrichmentConfig,
                jobSearchId = jobSearchId
            )

            val enrichmentBatches = enrichmentCalculator.createBatches(
                jobs = translatedJobs,
                promptTemplate = enrichmentPromptTemplate,
                contentExtractor = { job ->
                    "${job.title} ${job.description} ${job.company}"
                },
                requestBuilder = { job ->
                    BatchEnrichmentRequest(
                        jobId = job.id,
                        title = job.title,
                        description = job.description,
                        company = job.company
                    )
                }
            )
            logger.info("[JobSearch: $jobSearchId] Split ${translatedJobs.size} jobs into ${enrichmentBatches.size} enrichment batches")

            // Step 2: Process enrichment batches in parallel
            val enrichmentResults = coroutineScope {
                enrichmentBatches.mapIndexed { index, batch ->
                    async {
                        llmSemaphore.withPermit {
                            processEnrichmentBatch(
                                batch,
                                index + 1,
                                enrichmentBatches.size,
                                jobSearchId,
                                enrichmentConfig
                            )
                        }
                    }
                }.awaitAll().flatten()
            }

            // Step 3: Apply enrichment results to original jobs
            val enrichmentMap = enrichmentResults.associateBy { it.jobId }

            return translatedJobs.map { job ->
                val enrichment = enrichmentMap[job.id]
                jobDataConverter.toEnrichedJobData(
                    translatedJob = job,
                    techstack = enrichment?.techstack ?: emptyList(),
                    tags = enrichment?.tags ?: emptyList(),
                    salary = enrichment?.salary
                )
            }

        } catch (e: Exception) {
            logger.error("[JobSearch: $jobSearchId] Error in batch enrichment", e)
            return translatedJobs.map { job ->
                jobDataConverter.toEnrichedJobData(
                    translatedJob = job,
                    techstack = emptyList(),
                    tags = emptyList(),
                    salary = null
                )
            }
        }
    }

    /**
     * Scores jobs for compatibility using LLM.
     * Restored from legacy scoreJobsCompatibilityBatch method.
     */
    private suspend fun scoreJobsDataBatch(
        processedJobs: List<ProcessedJobData>,
        enrichedJobs: List<EnrichedJobData>,
        jobSearch: JobSearchOut?,
        scoringConfig: LLMConfig
    ): List<ScoredJobData> {
        val jobSearchId = jobSearch?.id ?: "unknown"
        try {
            // Split into batches for compatibility scoring
            val scoringPromptTemplate = buildCompatibilityPromptTemplate(jobSearch)
            val scoringCalculator = JobBatchCalculator<EnrichedJobData, BatchCompatibilityRequest>(
                llmConfig = scoringConfig,
                jobSearchId = jobSearchId
            )

            val compatibilityBatches = scoringCalculator.createBatches(
                jobs = enrichedJobs,
                promptTemplate = scoringPromptTemplate,
                contentExtractor = { job ->
                    val techstack = job.techstack.joinToString(", ")
                    val tags = job.tags.joinToString(", ")
                    "${job.title} ${job.description} ${job.company} $techstack $tags ${job.salary ?: ""}"
                },
                requestBuilder = { job ->
                    BatchCompatibilityRequest(
                        jobId = job.id,
                        title = job.title,
                        description = job.description,
                        company = job.company,
                        techstack = job.techstack,
                        tags = job.tags,
                        salary = job.salary,
                        applicants = job.applicants
                    )
                }
            )
            logger.info("[JobSearch: $jobSearchId] Split ${enrichedJobs.size} jobs into ${compatibilityBatches.size} compatibility batches")

            // Process compatibility batches in parallel
            val compatibilityResults = coroutineScope {
                compatibilityBatches.mapIndexed { index, batch ->
                    async {
                        llmSemaphore.withPermit {
                            processCompatibilityBatch(
                                batch,
                                jobSearch,
                                index + 1,
                                compatibilityBatches.size,
                                scoringConfig
                            )
                        }
                    }
                }.awaitAll().flatten()
            }

            // Apply compatibility results to jobs
            val compatibilityMap = compatibilityResults.associateBy { it.jobId }
            val enrichedJobMap = enrichedJobs.associateBy { it.id }

            return processedJobs.mapNotNull { processedJob ->
                val enrichedJob = enrichedJobMap[processedJob.id]
                val compatibility = compatibilityMap[processedJob.id]
                val compatibilityScore = compatibility?.compatibilityScore

                if (compatibilityScore == null || enrichedJob == null) {
                    logger.error("[JobSearch: $jobSearchId] Missing data for ${processedJob.title} ${processedJob.link}")
                    null
                } else {
                    jobDataConverter.toScoredJobData(
                        processedJob = processedJob,
                        createdAgo = enrichedJob.createdAgo,
                        scrapedAt = enrichedJob.scrapedAt,
                        userId = enrichedJob.userId,
                        jobSearchId = enrichedJob.jobSearchId,
                        keywords = enrichedJob.keywords,
                        compatibilityScore = compatibility.compatibilityScore,
                        filterReason = compatibility.filterReason
                    )
                }

            }

        } catch (e: Exception) {
            logger.error(
                "[JobSearch: $jobSearchId] Exception caught in batch compatibility scoring",
                e
            )
            val enrichedJobMap = enrichedJobs.associateBy { it.id }
            return processedJobs.mapNotNull { processedJob ->
                val enrichedJob = enrichedJobMap[processedJob.id]
                if (enrichedJob != null) {
                    jobDataConverter.toScoredJobData(
                        processedJob = processedJob,
                        createdAgo = enrichedJob.createdAgo,
                        scrapedAt = enrichedJob.scrapedAt,
                        userId = enrichedJob.userId,
                        jobSearchId = enrichedJob.jobSearchId,
                        keywords = enrichedJob.keywords,
                        compatibilityScore = 0,
                        filterReason = "Compatibility scoring failed: ${e.message}"
                    )
                } else {
                    null
                }
            }
        }
    }

    /**
     * Processes a batch of enrichment requests using LLM.
     * Restored from legacy processEnrichmentBatch method.
     */
    private suspend fun processEnrichmentBatch(
        batch: List<BatchEnrichmentRequest>,
        batchIndex: Int,
        batchesSize: Int,
        jobSearchId: String,
        config: LLMConfig
    ): List<BatchEnrichmentResult> {
        try {
            logger.info("[JobSearch: $jobSearchId] Processing enrichment batch $batchIndex/$batchesSize with ${batch.size} jobs")

            val prompt = buildEnrichmentPrompt(batch)
            logger.info("[JobSearch: $jobSearchId] 🤖 Enrichment batch $batchIndex/$batchesSize - About to call OpenRouter API")
            val request = LLMRequest(
                prompt = prompt,
                temperature = config.temperature,
                maxTokens = config.maxOutputTokens,
                model = config.model
            )
            val response = openRouterClient.chat(request)
            logger.info("[JobSearch: $jobSearchId] 🤖 Enrichment batch $batchIndex/$batchesSize - OpenRouter API call completed, success: ${response.success}")

            if (response.success && response.content != null) {
                logger.debug(
                    "[JobSearch: $jobSearchId] Enrichment batch $batchIndex/$batchesSize - OpenRouter response: ${
                        response.content.take(
                            200
                        )
                    }..."
                )
                val results = parseEnrichmentResponse(response.content, batch)
                logger.info("[JobSearch: $jobSearchId] Enrichment batch $batchIndex/$batchesSize - Successfully parsed ${results.size} results: ${results.map { it.techstack }}")
                return results
            } else {
                logger.error("[JobSearch: $jobSearchId] Enrichment batch $batchIndex/$batchesSize - OpenRouter API failed: ${response.errorMessage}")
                return batch.map { request ->
                    BatchEnrichmentResult(
                        jobId = request.jobId,
                        techstack = emptyList(),
                        tags = emptyList(),
                        salary = null
                    )
                }
            }

        } catch (e: Exception) {
            logger.error(
                "[JobSearch: $jobSearchId] Error processing enrichment batch $batchIndex/$batchesSize with ${batch.size} jobs",
                e
            )
            return batch.map { request ->
                BatchEnrichmentResult(
                    jobId = request.jobId,
                    techstack = emptyList(),
                    tags = emptyList(),
                    salary = null
                )
            }
        }
    }

    /**
     * Processes a batch of compatibility requests using LLM.
     * Restored from legacy processCompatibilityBatch method.
     */
    private suspend fun processCompatibilityBatch(
        batch: List<BatchCompatibilityRequest>,
        jobSearch: JobSearchOut?,
        batchIndex: Int,
        batchesSize: Int,
        config: LLMConfig
    ): List<BatchCompatibilityResult> {
        val jobSearchId = jobSearch?.id ?: "unknown"
        try {
            logger.info("[JobSearch: $jobSearchId] Processing compatibility batch $batchIndex/$batchesSize with ${batch.size} jobs")

            val prompt = buildCompatibilityPrompt(batch, jobSearch)
            logger.info("[JobSearch: $jobSearchId] 🤖 Compatibility batch $batchIndex/$batchesSize - About to call OpenRouter API")
            val request = LLMRequest(
                prompt = prompt,
                temperature = config.temperature,
                maxTokens = config.maxOutputTokens,
                model = config.model
            )
            val response = openRouterClient.chat(request)
            logger.info("[JobSearch: $jobSearchId] 🤖 Compatibility batch $batchIndex/$batchesSize - OpenRouter API call completed, success: ${response.success}")

            if (response.success && response.content != null) {
                logger.debug(
                    "[JobSearch: $jobSearchId] Compatibility batch $batchIndex/$batchesSize - OpenRouter response: ${
                        response.content.take(
                            200
                        )
                    }..."
                )
                val results = parseCompatibilityResponse(response.content, batch)
                logger.info("[JobSearch: $jobSearchId] Compatibility batch $batchIndex/$batchesSize - Successfully parsed ${results.size} results : ${results.map { it.compatibilityScore }}")

                return results
            } else {
                logger.error("[JobSearch: $jobSearchId] Compatibility batch $batchIndex/$batchesSize - OpenRouter API failed: ${response.errorMessage}")
                return batch.map { request ->
                    BatchCompatibilityResult(
                        jobId = request.jobId,
                        compatibilityScore = 0,
                        filterReason = "OpenRouter API failed: ${response.errorMessage}"
                    )
                }
            }

        } catch (e: Exception) {
            logger.error(
                "[JobSearch: $jobSearchId] Error processing compatibility batch $batchIndex/$batchesSize with ${batch.size} jobs",
                e
            )
            return batch.map { request ->
                BatchCompatibilityResult(
                    jobId = request.jobId,
                    compatibilityScore = 0,
                    filterReason = "Processing failed: ${e.message}"
                )
            }
        }
    }

    /**
     * Builds the enrichment prompt template WITHOUT jobs data.
     * Used to estimate base prompt token count for batching.
     */
    private fun buildEnrichmentPromptTemplate(): String {
        return """You are a technical recruiter specializing in job analysis. You act like a JSON-only API.

Extract technology stack and salary information from the following job postings.

JOBS TO ANALYZE:
[JOB DATA WILL BE INSERTED HERE]

For each job, identify:
1. TECHSTACK: List of technologies, programming languages, frameworks, tools mentioned in title and description, ordered by importance (most important first)
2. TAGS: Relevant tags like communication language with level needed, seniority level, travel expectations, standby/on-call requirements, soft skills, certifications, visa requirements, or other important non-technical requirements
3. SALARY: Any salary information like ranges, fixed amounts, hourly/daily rates (null if not mentioned)

Return ONLY a valid JSON array with this exact structure (no markdown, no extra text):
[
  {
    "job_id": "job-123",
    "techstack": ["Python", "React", "AWS", "Docker"],
    "tags": ["English C1", "German B1", "Senior", "Requires 80% travel"],
    "salary": "80k-100k"
  }
]

REQUIREMENTS:
- job_id: string matching the Job ID from the input above
- techstack: array of technology/skill strings from job title and job description
- tags: array of non-tech requirement strings such as communication languages, seniority, travel, standby, visa, soft skills
- salary: string with salary info or null if not mentioned
- NO markdown formatting in response
- NO additional text or explanations"""
    }

    /**
     * Builds the compatibility prompt template WITHOUT jobs data.
     * Used to estimate base prompt token count for batching.
     */
    private fun buildCompatibilityPromptTemplate(jobSearch: JobSearchOut?): String {
        // Build search criteria (same logic as buildCompatibilityPrompt)
        val criteriaLines = mutableListOf<String>()
        jobSearch?.let { search ->
            if (search.jobTitle.isNotBlank()) {
                criteriaLines.add("Position Title/Keywords: ${search.jobTitle}")
            }
            search.jobTypes?.let { types ->
                if (types.isNotEmpty()) {
                    criteriaLines.add("Job Types: ${types.joinToString(", ") { it.label }}")
                }
            }
            search.remoteTypes?.let { types ->
                if (types.isNotEmpty()) {
                    criteriaLines.add("Remote Work Types: ${types.joinToString(", ") { it.label }}")
                }
            }
            if (!search.location.isNullOrBlank()) {
                criteriaLines.add("Location: ${search.location}")
            }
            if (!search.filterText.isNullOrBlank()) {
                criteriaLines.add("Filter text: ${search.filterText}")
            }
        }

        val searchCriteria = if (criteriaLines.isNotEmpty()) {
            criteriaLines.joinToString("\n")
        } else {
            "No specific criteria provided"
        }

        return """You are a senior technical recruiter specializing in job matching. You act like a JSON-only API.

Evaluate jobs against search criteria with focus on accuracy and relevance.

SEARCH CRITERIA:
$searchCriteria

EVALUATION PRIORITY (in order of importance):
1. TITLE & KEYWORDS MATCH: Job title similarity to provided keywords, seniority level match
2. TECHSTACK & KEYWORDS MATCH: Job techstack similarity to provided keywords
3. REMOTE WORK TYPE: Match between job's remote policy and required remote type
4. JOB TYPE: Match between job type (full-time, contract, etc.) and requirements
5. NUMBER OF APPLICANTS: Filter out jobs with more than 70 applicants
6. DESCRIPTION KEYWORDS: How well job description matches search keywords
7. SALARY: Match for salary if specified in requirements
8. TAGS & TECHSTACK ALIGNING WITH FILTER TEXT: Tags (languages, seniority, travel, standby) and techstack should be compared to filter text, if some tags or techstack imply the job should be filtered out according to filter text, do so

SCORING GUIDELINES:
- 90-100: Perfect match (title + techstack + tags are not conflicting with filter text at all)
- 70-89: Strong match (partial title/techstack match, tags are not conflicting with filter text at all)
- 50-69: Good match (some title/techstack/description match, most of tags are not conflicting with filter text)
- 30-49: Weak match (weak alignment overall)
- 0-29: Poor/no match (no significant alignment)

JOBS TO EVALUATE:
[JOB DATA WILL BE INSERTED HERE]

Return ONLY a valid JSON array with this exact structure (no markdown, no extra text):
[
  {
    "job_id": "job-123",
    "compatibility_score": 85,
    "filter_reason": null
  }
]

REQUIREMENTS:
- job_id: string matching the Job ID from the input above
- compatibility_score: integer 0-100 based on evaluation criteria
- filter_reason: null if job passes, otherwise short explanation why filtered out
- If compatibility_score is 0, filter_reason MUST explain why
- NO markdown formatting in response
- NO additional text or explanations"""
    }

    /**
     * Builds the enrichment prompt for LLM.
     * Restored from legacy buildEnrichmentPrompt method.
     */
    private fun buildEnrichmentPrompt(batch: List<BatchEnrichmentRequest>): String {
        val jobsText = batch.map { job ->
            """
Job ID: ${job.jobId}
Title: ${job.title}
Company: ${job.company}
Description: ${job.description}
---"""
        }.joinToString("\n")

        return """You are a technical recruiter specializing in job analysis. You act like a JSON-only API.

Extract technology stack and salary information from the following job postings.

JOBS TO ANALYZE:
$jobsText

For each job, identify:
1. TECHSTACK: List of technologies, programming languages, frameworks, tools mentioned in title and description, ordered by importance (most important first)
2. TAGS: Relevant tags like communication language with level needed, seniority level, travel expectations, standby/on-call requirements, soft skills, certifications, visa requirements, or other important non-technical requirements
3. SALARY: Any salary information like ranges, fixed amounts, hourly/daily rates (null if not mentioned)

Return ONLY a valid JSON array with this exact structure (no markdown, no extra text):
[
  {
    "job_id": "job-123",
    "techstack": ["Python", "React", "AWS", "Docker"],
    "tags": ["English C1", "German B1", "Senior", "Requires 80% travel"],
    "salary": "80k-100k"
  },
  {
    "job_id": "job-456", 
    "techstack": ["Java", "Spring Boot", "Kubernetes"],
    "tags": ["Native German", "Requires standby on weekends"],
    "salary": null
  }
]

REQUIREMENTS:
- job_id: string matching the Job ID from the input above
- techstack: array of technology/skill strings from job title and job description
- tags: array of non-tech requirement strings such as communication languages, seniority, travel, standby, visa, soft skills
- salary: string with salary info or null if not mentioned
- NO markdown formatting in response
- NO additional text or explanations"""
    }

    /**
     * Builds the compatibility prompt for LLM.
     * Restored from legacy buildCompatibilityPrompt method.
     */
    private fun buildCompatibilityPrompt(
        batch: List<BatchCompatibilityRequest>,
        jobSearch: JobSearchOut?
    ): String {
        // Build search criteria
        val criteriaLines = mutableListOf<String>()
        jobSearch?.let { search ->
            if (search.jobTitle.isNotBlank()) {
                criteriaLines.add("Position Title/Keywords: ${search.jobTitle}")
            }
            search.jobTypes?.let { types ->
                if (types.isNotEmpty()) {
                    criteriaLines.add("Job Types: ${types.joinToString(", ") { it.label }}")
                }
            }
            search.remoteTypes?.let { types ->
                if (types.isNotEmpty()) {
                    criteriaLines.add("Remote Work Types: ${types.joinToString(", ") { it.label }}")
                }
            }
            if (!search.location.isNullOrBlank()) {
                criteriaLines.add("Location: ${search.location}")
            }
            if (!search.filterText.isNullOrBlank()) {
                criteriaLines.add("Filter text: ${search.filterText}")
            }
        }

        val searchCriteria = if (criteriaLines.isNotEmpty()) {
            criteriaLines.joinToString("\n")
        } else {
            "No specific criteria provided"
        }

        val jobsText = batch.map { job ->
            val techstackText = if (job.techstack.isNotEmpty()) {
                job.techstack.joinToString(", ")
            } else {
                "Not specified"
            }
            val tagsText = if (job.tags.isNotEmpty()) {
                job.tags.joinToString(", ")
            } else {
                "Not specified"
            }

            """
Job ID: ${job.jobId}
Title: ${job.title}
Company: ${job.company}
Techstack: $techstackText
Tags: $tagsText
Salary: ${job.salary ?: "Not specified"}
Description: ${job.description}
Applicants: ${job.applicants}
---"""
        }.joinToString("\n")

        return """You are a senior technical recruiter specializing in job matching. You act like a JSON-only API.

Evaluate jobs against search criteria with focus on accuracy and relevance.

SEARCH CRITERIA:
$searchCriteria

EVALUATION PRIORITY (in order of importance):
1. TITLE & KEYWORDS MATCH: Job title similarity to provided keywords, seniority level match
2. TECHSTACK & KEYWORDS MATCH: Job techstack similarity to provided keywords  
3. REMOTE WORK TYPE: Match between job's remote policy and required remote type
4. JOB TYPE: Match between job type (full-time, contract, etc.) and requirements
5. NUMBER OF APPLICANTS: Filter out jobs with more than 70 applicants
6. DESCRIPTION KEYWORDS: How well job description matches search keywords
7. SALARY: Match for salary if specified in requirements
8. TAGS & TECHSTACK ALIGNING WITH FILTER TEXT: Tags (languages, seniority, travel, standby) and techstack should be compared to filter text, if some tags or techstack imply the job should be filtered out according to filter text, do so

SCORING GUIDELINES:
- 90-100: Perfect match (title + techstack + tags are not conflicting with filter text at all)
- 70-89: Strong match (partial title/techstack match, tags are not conflicting with filter text at all)
- 50-69: Good match (some title/techstack/description match, most of tags are not conflicting with filter text)
- 30-49: Weak match (weak alignment overall)
- 0-29: Poor/no match (no significant alignment)

JOBS TO EVALUATE:
$jobsText

Return ONLY a valid JSON array with this exact structure (no markdown, no extra text):
[
  {
    "job_id": "job-123",
    "compatibility_score": 85,
    "filter_reason": null
  },
  {
    "job_id": "job-456", 
    "compatibility_score": 0,
    "filter_reason": "Requires German language"
  }
]

REQUIREMENTS:
- job_id: string matching the Job ID from the input above
- compatibility_score: integer 0-100 based on evaluation criteria
- filter_reason: null if job passes, otherwise short explanation why filtered out
- If compatibility_score is 0, filter_reason MUST explain why
- NO markdown formatting in response
- NO additional text or explanations"""
    }

    /**
     * Parses the enrichment response from LLM.
     * Restored from legacy parseEnrichmentResponse method.
     */
    private fun parseEnrichmentResponse(
        content: String,
        originalBatch: List<BatchEnrichmentRequest>
    ): List<BatchEnrichmentResult> {
        return try {
            val cleanedContent = cleanJsonResponse(content)
            val results: List<BatchEnrichmentResult> = objectMapper.readValue(cleanedContent)

            // Create map by job_id for lookup
            val resultsMap = results.associateBy { it.jobId }

            // Ensure we have results for all jobs in original order
            originalBatch.map { request ->
                resultsMap[request.jobId] ?: BatchEnrichmentResult(
                    jobId = request.jobId,
                    techstack = emptyList(),
                    tags = emptyList(),
                    salary = null
                )
            }

        } catch (e: Exception) {
            logger.error("Failed to parse enrichment response: ${e.message}", e)
            logger.error("Response content: ${content.take(1000)}")

            // Return fallback results
            originalBatch.map { request ->
                BatchEnrichmentResult(
                    jobId = request.jobId,
                    techstack = emptyList(),
                    tags = emptyList(),
                    salary = null
                )
            }
        }
    }

    /**
     * Parses the compatibility response from LLM.
     * Restored from legacy parseCompatibilityResponse method.
     */
    private fun parseCompatibilityResponse(
        content: String,
        originalBatch: List<BatchCompatibilityRequest>
    ): List<BatchCompatibilityResult> {
        return try {
            val cleanedContent = cleanJsonResponse(content)
            val results: List<BatchCompatibilityResult> = objectMapper.readValue(cleanedContent)

            // Create map by job_id for lookup
            val resultsMap = results.associateBy { it.jobId }

            // Ensure we have results for all jobs in original order
            originalBatch.map { request ->
                resultsMap[request.jobId] ?: BatchCompatibilityResult(
                    jobId = request.jobId,
                    compatibilityScore = 0,
                    filterReason = "No response from LLM"
                )
            }

        } catch (e: Exception) {
            logger.error("Failed to parse compatibility response: ${e.message}", e)
            logger.error("Response content: ${content.take(1000)}")

            // Return fallback results
            originalBatch.map { request ->
                BatchCompatibilityResult(
                    jobId = request.jobId,
                    compatibilityScore = 0,
                    filterReason = "Response parsing failed"
                )
            }
        }
    }

    /**
     * Cleans JSON response from LLM by removing markdown formatting.
     * Restored from legacy cleanJsonResponse method.
     */
    private fun cleanJsonResponse(content: String): String {
        var cleaned = content.trim()

        // Remove markdown code blocks
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7)
        }
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3)
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length - 3)
        }

        // Extract JSON array
        val startIdx = cleaned.indexOf('[')
        val endIdx = cleaned.lastIndexOf(']')

        if (startIdx != -1 && endIdx != -1 && endIdx > startIdx) {
            cleaned = cleaned.substring(startIdx, endIdx + 1)
        }

        return cleaned.trim()
    }
}
