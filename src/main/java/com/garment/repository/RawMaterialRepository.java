package com.garment.repository;

import com.garment.model.RawMaterial;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RawMaterialRepository extends MongoRepository<RawMaterial, String> {
}
