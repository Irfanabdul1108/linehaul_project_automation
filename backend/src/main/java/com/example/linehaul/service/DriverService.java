package com.example.linehaul.service;

import com.example.linehaul.exception.BusinessException;
import com.example.linehaul.exception.NotFoundException;
import com.example.linehaul.model.Driver;
import com.example.linehaul.model.Status;
import com.example.linehaul.repository.DriverRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DriverService {

    private final DriverRepository driverRepository;

    public DriverService(DriverRepository driverRepository) {
        this.driverRepository = driverRepository;
    }

    public List<Driver> findAll() {
        return findAll(null);
    }

    /** @param warehouseId when set, only drivers that belong to this depot are returned. */
    public List<Driver> findAll(String warehouseId) {
        String depot = LinehaulUtil.clean(warehouseId).toUpperCase();
        return driverRepository.findAll().stream()
                .filter(d -> depot.isEmpty() || depot.equalsIgnoreCase(LinehaulUtil.clean(d.getWarehouseId()).toUpperCase()))
                .sorted((a, b) -> a.getDriverId().compareTo(b.getDriverId()))
                .toList();
    }

    /** Drivers of this depot that can still be put behind a wheel. */
    public List<Driver> findAvailable(String warehouseId) {
        return findAll(warehouseId).stream()
                .filter(d -> Status.AVAILABLE.equalsIgnoreCase(LinehaulUtil.clean(d.getStatus())))
                .filter(d -> LinehaulUtil.clean(d.getRouteId()).isEmpty())
                .toList();
    }

    public Driver findByDriverId(String driverId) {
        return driverRepository.findByDriverId(driverId)
                .orElseThrow(() -> new NotFoundException("Driver not found."));
    }

    public Driver create(Driver driver) {
        return create(driver, null);
    }

    /** @param warehouseId depot the new driver is hired into. */
    public Driver create(Driver driver, String warehouseId) {
        driver.setDriverId(LinehaulUtil.clean(driver.getDriverId()));

        driver.setWarehouseId(LinehaulUtil.clean(driver.getWarehouseId()).isEmpty()
                ? (WarehouseScope.key(warehouseId).isEmpty() ? null : WarehouseScope.key(warehouseId))
                : WarehouseScope.key(driver.getWarehouseId()));

        if (driver.getDriverId().isEmpty()) {
            throw new BusinessException("Driver ID is required.");
        }
        if (driverRepository.existsByDriverId(driver.getDriverId())) {
            throw new BusinessException("Driver ID " + driver.getDriverId() + " already exists.");
        }
        if (driver.getName() == null || driver.getName().isBlank()) {
            throw new BusinessException("Driver name is required.");
        }
        if (driver.getStatus() == null || driver.getStatus().isBlank()) {
            driver.setStatus("AVAILABLE");
        }
        driver.setStatus(driver.getStatus().toUpperCase());
        driver.setRouteId(null);
        return driverRepository.save(driver);
    }
}
