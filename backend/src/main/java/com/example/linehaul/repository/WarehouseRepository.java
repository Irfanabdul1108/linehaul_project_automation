package com.example.linehaul.repository;

import com.example.linehaul.model.Warehouse;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface WarehouseRepository extends MongoRepository<Warehouse, String> {

    Optional<Warehouse> findByWarehouseId(String warehouseId);

    boolean existsByWarehouseId(String warehouseId);
}
