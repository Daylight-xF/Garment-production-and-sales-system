package com.garment.repository;

import com.garment.model.ProductionPlan;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductionPlanRepository extends MongoRepository<ProductionPlan, String> {

    List<ProductionPlan> findByStatus(String status);

    boolean existsByBatchNo(String batchNo);
}
