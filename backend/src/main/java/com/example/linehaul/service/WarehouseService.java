package com.example.linehaul.service;

import com.example.linehaul.exception.BusinessException;
import com.example.linehaul.exception.NotFoundException;
import com.example.linehaul.model.Warehouse;
import com.example.linehaul.repository.WarehouseRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;

    public WarehouseService(WarehouseRepository warehouseRepository) {
        this.warehouseRepository = warehouseRepository;
    }

    public List<Warehouse> findAll() {
        return warehouseRepository.findAll().stream()
                .sorted((a, b) -> a.getWarehouseId().compareTo(b.getWarehouseId()))
                .toList();
    }

    public Optional<Warehouse> find(String warehouseId) {
        String wanted = LinehaulUtil.clean(warehouseId);
        if (wanted.isEmpty()) {
            return Optional.empty();
        }
        return warehouseRepository.findAll().stream()
                .filter(w -> wanted.equalsIgnoreCase(w.getWarehouseId()) || wanted.equalsIgnoreCase(w.getCode()))
                .findFirst();
    }

    public Warehouse findByWarehouseId(String warehouseId) {
        return find(warehouseId)
                .orElseThrow(() -> new NotFoundException("Warehouse not found."));
    }

    public Warehouse create(Warehouse warehouse) {
        warehouse.setWarehouseId(LinehaulUtil.clean(warehouse.getWarehouseId()));

        if (warehouse.getWarehouseId().isEmpty()) {
            throw new BusinessException("Warehouse ID is required.");
        }
        if (warehouseRepository.existsByWarehouseId(warehouse.getWarehouseId())) {
            throw new BusinessException("Warehouse ID " + warehouse.getWarehouseId() + " already exists.");
        }
        return warehouseRepository.save(warehouse);
    }
}
