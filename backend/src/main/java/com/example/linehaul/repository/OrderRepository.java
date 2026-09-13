package com.example.linehaul.repository;

import com.example.linehaul.model.Order;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends MongoRepository<Order, String> {

    Optional<Order> findByOrderId(String orderId);

    boolean existsByOrderId(String orderId);

    List<Order> findByRouteId(String routeId);

    List<Order> findByStatus(String status);

    List<Order> findByStatusIn(List<String> statuses);
}
