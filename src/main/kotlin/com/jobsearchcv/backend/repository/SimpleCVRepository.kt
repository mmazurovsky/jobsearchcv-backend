package com.jobsearchcv.backend.repository

import com.jobsearchcv.backend.domain.model.SimpleUserCV
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Repository

interface SimpleCVRepository {
    suspend fun findByUserId(userId: String): Flow<SimpleUserCV>
    suspend fun findByUserIdAndId(userId: String, id: String): SimpleUserCV?
    suspend fun save(cv: SimpleUserCV): SimpleUserCV
    suspend fun deleteById(id: String): Boolean
}

@Repository
class SimpleCVRepositoryImpl(
    private val mongoTemplate: ReactiveMongoTemplate,
) : SimpleCVRepository {
    
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(SimpleCVRepositoryImpl::class.java)
    }
    
    override suspend fun findByUserId(userId: String): Flow<SimpleUserCV> {
        logger.debug("Finding CVs for user: $userId")
        
        val query = Query(Criteria.where("user_id").`is`(userId))
            .with(Sort.by(Sort.Direction.DESC, "created_at"))
            
        return mongoTemplate.find(query, SimpleUserCV::class.java).asFlow()
    }
    
    override suspend fun findByUserIdAndId(userId: String, id: String): SimpleUserCV? {
        logger.debug("Finding CV: id=$id for user: $userId")
        
        val query = Query(
            Criteria.where("user_id").`is`(userId)
                .and("_id").`is`(id)
        )
        
        return mongoTemplate.findOne(query, SimpleUserCV::class.java).awaitFirstOrNull()
    }
    
    override suspend fun save(cv: SimpleUserCV): SimpleUserCV {
        logger.info("Saving CV record: userId=${cv.userId}, cvId=${cv.id}")
        
        return mongoTemplate.save(cv).awaitSingle()
    }
    
    override suspend fun deleteById(id: String): Boolean {
        logger.info("Deleting CV record: cvId=$id")
        
        val query = Query(Criteria.where("_id").`is`(id))
        val result = mongoTemplate.remove(query, SimpleUserCV::class.java).awaitSingle()
        
        return result.deletedCount > 0
    }
}