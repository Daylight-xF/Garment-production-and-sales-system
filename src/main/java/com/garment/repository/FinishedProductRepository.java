package com.garment.repository;

import com.garment.model.FinishedProduct;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FinishedProductRepository extends MongoRepository<FinishedProduct, String> {
}
