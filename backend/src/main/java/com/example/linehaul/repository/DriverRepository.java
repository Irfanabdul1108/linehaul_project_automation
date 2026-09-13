package com.example.linehaul.repository;

import com.example.linehaul.model.Driver;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface DriverRepository extends MongoRepository<Driver, String> {

    Optional<Driver> findByDriverId(String driverId);

    boolean existsByDriverId(String driverId);
}
