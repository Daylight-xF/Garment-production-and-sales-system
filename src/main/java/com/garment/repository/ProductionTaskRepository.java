package com.garment.repository;

import com.garment.model.ProductionTask;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductionTaskRepository extends MongoRepository<ProductionTask, String> {

    List<ProductionTask> findByPlanId(String planId);

    List<ProductionTask> findByAssignee(String assignee);
}
