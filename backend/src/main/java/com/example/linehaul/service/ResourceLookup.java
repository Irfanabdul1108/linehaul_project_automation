package com.example.linehaul.service;

import com.example.linehaul.model.Driver;
import com.example.linehaul.model.Vehicle;
import com.example.linehaul.repository.DriverRepository;
import com.example.linehaul.repository.VehicleRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ResourceLookup {

    private final VehicleRepository vehicleRepository;
    private final DriverRepository driverRepository;

    public ResourceLookup(VehicleRepository vehicleRepository, DriverRepository driverRepository) {
        this.vehicleRepository = vehicleRepository;
        this.driverRepository = driverRepository;
    }

    public Optional<Vehicle> findTruck(String truckId) {
        if (truckId == null || truckId.isBlank()) {
            return Optional.empty();
        }
        return vehicleRepository.findByTruckId(truckId);
    }

    public Optional<Driver> findDriver(String driverId) {
        if (driverId == null || driverId.isBlank()) {
            return Optional.empty();
        }
        return driverRepository.findByDriverId(driverId);
    }
}
