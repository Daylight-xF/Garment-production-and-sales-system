package com.garment.repository;

import com.garment.model.Order;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends MongoRepository<Order, String> {

    List<Order> findByStatus(String status);

    List<Order> findByCustomerId(String customerId);

    Optional<Order> findByOrderNo(String orderNo);

    long countByOrderNoStartingWith(String prefix);
}
