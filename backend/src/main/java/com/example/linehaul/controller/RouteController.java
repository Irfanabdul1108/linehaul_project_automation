package com.example.linehaul.controller;

import com.example.linehaul.model.Order;
import com.example.linehaul.model.Route;
import com.example.linehaul.service.RouteService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/routes")
public class RouteController {

    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    @GetMapping
    public List<Route> all(@RequestParam(required = false) String search,
                           @RequestParam(required = false) String warehouseId) {
        return routeService.findAll(search, warehouseId);
    }

    @GetMapping("/{routeId}")
    public Route one(@PathVariable String routeId) {
        return routeService.findByRouteId(routeId);
    }

    @GetMapping("/{routeId}/orders")
    public List<Order> orders(@PathVariable String routeId) {
        return routeService.findOrders(routeId);
    }

    @PostMapping
    public Route create(@Valid @RequestBody Route route,
                        @RequestParam(required = false) String warehouseId) {
        return routeService.create(route, warehouseId);
    }

    @PutMapping("/{routeId}")
    public Route update(@PathVariable String routeId, @RequestBody Route route) {
        return routeService.update(routeId, route);
    }

    @PostMapping("/{routeId}/orders/{orderId}")
    public Route assignOrder(@PathVariable String routeId, @PathVariable String orderId) {
        return routeService.assignOrder(routeId, orderId);
    }

    @DeleteMapping("/{routeId}/orders/{orderId}")
    public Route unassignOrder(@PathVariable String routeId, @PathVariable String orderId) {
        return routeService.unassignOrder(routeId, orderId);
    }

    @PutMapping("/{routeId}/truck")
    public Route assignTruck(@PathVariable String routeId, @RequestParam String truckId) {
        return routeService.assignTruck(routeId, truckId);
    }

    @DeleteMapping("/{routeId}/truck")
    public Route unassignTruck(@PathVariable String routeId) {
        return routeService.unassignTruck(routeId);
    }

    @PutMapping("/{routeId}/driver")
    public Route assignDriver(@PathVariable String routeId, @RequestParam String driverId) {
        return routeService.assignDriver(routeId, driverId);
    }

    @DeleteMapping("/{routeId}/driver")
    public Route unassignDriver(@PathVariable String routeId) {
        return routeService.unassignDriver(routeId);
    }

    @PostMapping("/{routeId}/dispatch")
    public Route dispatch(@PathVariable String routeId) {
        return routeService.dispatch(routeId);
    }
}
