package com.garment.repository;

import com.garment.model.InventoryAlert;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryAlertRepository extends MongoRepository<InventoryAlert, String> {
}
