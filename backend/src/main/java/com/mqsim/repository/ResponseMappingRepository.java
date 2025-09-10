package com.mqsim.repository;

import com.mqsim.model.ResponseMapping;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResponseMappingRepository extends MongoRepository<ResponseMapping, String> {
    
    List<ResponseMapping> findByQueueNameAndEnabledTrueOrderByPriorityAsc(String queueName);
    
    List<ResponseMapping> findByQueueName(String queueName);
    
    List<ResponseMapping> findAllByOrderByQueueNameAscPriorityAsc();
    
    long countByQueueName(String queueName);
}