package com.example.linehaul.controller;

import com.example.linehaul.model.Driver;
import com.example.linehaul.service.DriverService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/drivers")
public class DriverController {

    private final DriverService driverService;

    public DriverController(DriverService driverService) {
        this.driverService = driverService;
    }

    @GetMapping
    public List<Driver> all(@RequestParam(required = false) String warehouseId) {
        return driverService.findAll(warehouseId);
    }

    @PostMapping
    public Driver create(@Valid @RequestBody Driver driver,
                         @RequestParam(required = false) String warehouseId) {
        return driverService.create(driver, warehouseId);
    }
}
