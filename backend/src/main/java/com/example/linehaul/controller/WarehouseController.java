package com.example.linehaul.controller;

import com.example.linehaul.model.Route;
import com.example.linehaul.model.Warehouse;
import com.example.linehaul.service.DashboardService;
import com.example.linehaul.service.RouteService;
import com.example.linehaul.service.WarehouseService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/warehouses")
public class WarehouseController {

    private final WarehouseService warehouseService;
    private final DashboardService dashboardService;
    private final RouteService routeService;

    public WarehouseController(WarehouseService warehouseService,
                               DashboardService dashboardService,
                               RouteService routeService) {
        this.warehouseService = warehouseService;
        this.dashboardService = dashboardService;
        this.routeService = routeService;
    }

    /** Used by the start screen: which depot am I working in today? */
    @GetMapping
    public List<Warehouse> all() {
        return warehouseService.findAll();
    }

    @GetMapping("/{warehouseId}")
    public Warehouse one(@PathVariable String warehouseId) {
        return warehouseService.findByWarehouseId(warehouseId);
    }

    /** Operational numbers of one depot, plus the size of the network it can hand freight to. */
    @GetMapping("/{warehouseId}/dashboard")
    public DashboardService.Summary dashboard(@PathVariable String warehouseId) {
        warehouseService.findByWarehouseId(warehouseId);
        return dashboardService.summary(warehouseId);
    }

    /**
     * The whole route network, every warehouse. The assignment engine needs this view, and it is also
     * what makes a cross-warehouse recommendation explainable on screen.
     */
    @GetMapping("/{warehouseId}/route-network")
    public List<Route> routeNetwork(@PathVariable String warehouseId) {
        warehouseService.findByWarehouseId(warehouseId);
        return routeService.findAll(null);
    }

    @PostMapping
    public Warehouse create(@Valid @RequestBody Warehouse warehouse) {
        return warehouseService.create(warehouse);
    }
}
