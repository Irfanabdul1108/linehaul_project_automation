package com.example.linehaul.controller;

import com.example.linehaul.model.Vehicle;
import com.example.linehaul.service.VehicleService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {

    private final VehicleService vehicleService;

    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @GetMapping
    public List<Vehicle> all(@RequestParam(required = false) String warehouseId) {
        return vehicleService.findAll(warehouseId);
    }

    @PostMapping
    public Vehicle create(@Valid @RequestBody Vehicle vehicle,
                          @RequestParam(required = false) String warehouseId) {
        return vehicleService.create(vehicle, warehouseId);
    }
}
