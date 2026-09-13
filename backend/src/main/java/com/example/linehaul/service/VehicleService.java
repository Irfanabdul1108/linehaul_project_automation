package com.example.linehaul.service;

import com.example.linehaul.exception.BusinessException;
import com.example.linehaul.exception.NotFoundException;
import com.example.linehaul.model.Vehicle;
import com.example.linehaul.model.Status;
import com.example.linehaul.repository.VehicleRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VehicleService {

    private final VehicleRepository vehicleRepository;

    public VehicleService(VehicleRepository vehicleRepository) {
        this.vehicleRepository = vehicleRepository;
    }

    public List<Vehicle> findAll() {
        return findAll(null);
    }

    /** @param warehouseId when set, only trucks that belong to this depot are returned. */
    public List<Vehicle> findAll(String warehouseId) {
        String depot = LinehaulUtil.clean(warehouseId).toUpperCase();
        return vehicleRepository.findAll().stream()
                .filter(v -> depot.isEmpty() || depot.equalsIgnoreCase(LinehaulUtil.clean(v.getWarehouseId()).toUpperCase()))
                .sorted((a, b) -> a.getTruckId().compareTo(b.getTruckId()))
                .toList();
    }

    /** Trucks of this depot that are free and can carry at least {@code requiredWeight}. */
    public List<Vehicle> findAvailable(int requiredWeight, String warehouseId) {
        return findAll(warehouseId).stream()
                .filter(v -> Status.AVAILABLE.equalsIgnoreCase(LinehaulUtil.clean(v.getStatus())))
                .filter(v -> LinehaulUtil.clean(v.getRouteId()).isEmpty())
                .filter(v -> v.getCapacity() >= requiredWeight)
                .toList();
    }

    public Vehicle findByTruckId(String truckId) {
        return vehicleRepository.findByTruckId(truckId)
                .orElseThrow(() -> new NotFoundException("Truck not found."));
    }

    public Vehicle create(Vehicle vehicle) {
        return create(vehicle, null);
    }

    /** @param warehouseId depot the new truck is parked at. */
    public Vehicle create(Vehicle vehicle, String warehouseId) {
        vehicle.setTruckId(LinehaulUtil.clean(vehicle.getTruckId()));

        vehicle.setWarehouseId(LinehaulUtil.clean(vehicle.getWarehouseId()).isEmpty()
                ? (WarehouseScope.key(warehouseId).isEmpty() ? null : WarehouseScope.key(warehouseId))
                : WarehouseScope.key(vehicle.getWarehouseId()));

        if (vehicle.getTruckId().isEmpty()) {
            throw new BusinessException("Truck ID is required.");
        }
        if (vehicleRepository.existsByTruckId(vehicle.getTruckId())) {
            throw new BusinessException("Truck ID " + vehicle.getTruckId() + " already exists.");
        }
        if (vehicle.getCapacity() <= 0) {
            throw new BusinessException("Capacity must be greater than zero.");
        }
        if (vehicle.getType() == null || vehicle.getType().isBlank()) {
            vehicle.setType("Truck");
        }
        if (vehicle.getStatus() == null || vehicle.getStatus().isBlank()) {
            vehicle.setStatus("AVAILABLE");
        }
        vehicle.setStatus(vehicle.getStatus().toUpperCase());
        vehicle.setRouteId(null);
        return vehicleRepository.save(vehicle);
    }
}
