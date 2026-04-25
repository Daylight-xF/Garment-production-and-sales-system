package com.garment.repository;

import com.garment.model.FinishedProduct;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FinishedProductRepository extends MongoRepository<FinishedProduct, String> {

    Optional<FinishedProduct> findFirstByProductCodeAndNameAndColorAndSizeAndBatchNo(
            String productCode, String name, String color, String size, String batchNo);
}
