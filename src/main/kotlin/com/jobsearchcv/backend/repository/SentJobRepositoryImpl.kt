package com.jobsearchcv.backend.repository

import com.jobsearchcv.backend.domain.model.SentJobOut
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
class SentJobRepositoryImpl(
    private val mongoTemplate: MongoTemplate
) : SentJobRepositoryCustom {
    
    override fun findByDestination(destination: String): List<SentJobOut> {
        val query = Query()
        query.addCriteria(Criteria.where("destination").`is`(destination))
        
        return mongoTemplate.find(query, SentJobOut::class.java)
    }
    
    override fun countByDestinationAndSentAtAfter(destination: String, sentAtAfter: OffsetDateTime): Long {
        val query = Query()
        query.addCriteria(
            Criteria.where("destination").`is`(destination)
                .and("sent_at").gte(sentAtAfter)
        )
        
        return mongoTemplate.count(query, SentJobOut::class.java)
    }
}