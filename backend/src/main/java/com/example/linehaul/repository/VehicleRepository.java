package com.example.linehaul.repository;

import com.example.linehaul.model.Vehicle;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface VehicleRepository extends MongoRepository<Vehicle, String> {

    Optional<Vehicle> findByTruckId(String truckId);

    boolean existsByTruckId(String truckId);
}
