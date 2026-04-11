package com.garment.repository;

import com.garment.model.SalesRecord;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Repository
public interface SalesRecordRepository extends MongoRepository<SalesRecord, String> {

    List<SalesRecord> findByCustomerId(String customerId);

    List<SalesRecord> findBySaleDateBetween(Date startDate, Date endDate);
}
