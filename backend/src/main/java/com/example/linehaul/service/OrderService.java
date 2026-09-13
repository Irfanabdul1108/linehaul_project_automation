package com.example.linehaul.service;

import com.example.linehaul.exception.BusinessException;
import com.example.linehaul.exception.NotFoundException;
import com.example.linehaul.model.Order;
import com.example.linehaul.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public List<Order> findAll(String search, String status) {
        return findAll(search, status, null);
    }

    /**
     * @param warehouseId when set, only orders that live in this warehouse are returned.
     */
    public List<Order> findAll(String search, String status, String warehouseId) {
        List<Order> orders = orderRepository.findAll();
        String needle = LinehaulUtil.clean(search).toLowerCase();
        String wanted = LinehaulUtil.clean(status).toUpperCase();
        String depot = LinehaulUtil.clean(warehouseId).toUpperCase();

        return orders.stream()
                .filter(o -> needle.isEmpty() || o.getOrderId().toLowerCase().contains(needle))
                .filter(o -> wanted.isEmpty() || wanted.equalsIgnoreCase(o.getStatus()))
                .filter(o -> depot.isEmpty() || depot.equalsIgnoreCase(LinehaulUtil.clean(o.getWarehouseId()).toUpperCase()))
                .sorted((a, b) -> a.getOrderId().compareTo(b.getOrderId()))
                .toList();
    }

    /** Orders in this warehouse that are not on any route yet. */
    public List<Order> findUnassigned(String warehouseId) {
        return findAll(null, null, warehouseId).stream()
                .filter(o -> LinehaulUtil.clean(o.getRouteId()).isEmpty())
                .filter(o -> {
                    String status = LinehaulUtil.clean(o.getStatus()).toUpperCase();
                    return status.isEmpty() || "CREATED".equals(status) || "READY".equals(status)
                            || "DRAFT".equals(status) || "MANIFESTED".equals(status) || "BLOCKED".equals(status);
                })
                .toList();
    }

    public Order findByOrderId(String orderId) {
        return orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found."));
    }

    public Order create(Order order) {
        return create(order, null);
    }

    /** @param warehouseId depot the new order belongs to, when the body does not say so itself. */
    public Order create(Order order, String warehouseId) {
        order.setOrderId(LinehaulUtil.clean(order.getOrderId()));

        if (LinehaulUtil.clean(order.getWarehouseId()).isEmpty()) {
            order.setWarehouseId(WarehouseScope.key(warehouseId).isEmpty() ? null : WarehouseScope.key(warehouseId));
        } else {
            order.setWarehouseId(WarehouseScope.key(order.getWarehouseId()));
        }

        if (order.getOrderId().isEmpty()) {
            throw new BusinessException("Order ID is required.");
        }
        if (orderRepository.existsByOrderId(order.getOrderId())) {
            throw new BusinessException("Order ID " + order.getOrderId() + " already exists.");
        }
        if (order.getStatus() == null || order.getStatus().isBlank()) {
            order.setStatus("READY");
        }
        order.setStatus(order.getStatus().toUpperCase());
        order.setRouteId(null);
        order.setEta(null);
        return orderRepository.save(order);
    }

    public Order update(String orderId, Order changes) {
        Order order = findByOrderId(orderId);

        if (changes.getCustomer() != null) order.setCustomer(changes.getCustomer());
        if (changes.getOrigin() != null) order.setOrigin(changes.getOrigin());
        if (changes.getDestination() != null) order.setDestination(changes.getDestination());
        if (changes.getServiceDate() != null) order.setServiceDate(changes.getServiceDate());
        order.setWeight(changes.getWeight());
        order.setPieces(changes.getPieces());
        if (changes.getStatus() != null && !changes.getStatus().isBlank()) {
            order.setStatus(changes.getStatus().toUpperCase());
        }
        return orderRepository.save(order);
    }
}
