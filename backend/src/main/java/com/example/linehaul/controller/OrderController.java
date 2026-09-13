package com.example.linehaul.controller;

import com.example.linehaul.model.Order;
import com.example.linehaul.service.OrderService;
import jakarta.validation.Valid;
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
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public List<Order> all(@RequestParam(required = false) String search,
                           @RequestParam(required = false) String status,
                           @RequestParam(required = false) String warehouseId) {
        return orderService.findAll(search, status, warehouseId);
    }

    @GetMapping("/{orderId}")
    public Order one(@PathVariable String orderId) {
        return orderService.findByOrderId(orderId);
    }

    @PostMapping
    public Order create(@Valid @RequestBody Order order,
                        @RequestParam(required = false) String warehouseId) {
        return orderService.create(order, warehouseId);
    }

    @PutMapping("/{orderId}")
    public Order update(@PathVariable String orderId, @RequestBody Order order) {
        return orderService.update(orderId, order);
    }
}
