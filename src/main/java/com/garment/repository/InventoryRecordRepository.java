package com.garment.repository;

import com.garment.model.InventoryRecord;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryRecordRepository extends MongoRepository<InventoryRecord, String> {
}
