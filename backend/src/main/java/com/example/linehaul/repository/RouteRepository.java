package com.example.linehaul.repository;

import com.example.linehaul.model.Route;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface RouteRepository extends MongoRepository<Route, String> {

    Optional<Route> findByRouteId(String routeId);

    boolean existsByRouteId(String routeId);

    List<Route> findByStatus(String status);

    List<Route> findByStatusIn(List<String> statuses);
}
