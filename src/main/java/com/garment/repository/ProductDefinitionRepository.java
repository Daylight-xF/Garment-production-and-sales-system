package com.garment.repository;

import com.garment.model.ProductDefinition;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductDefinitionRepository extends MongoRepository<ProductDefinition, String> {

    Optional<ProductDefinition> findByProductCode(String productCode);

    boolean existsByProductCode(String productCode);
}
