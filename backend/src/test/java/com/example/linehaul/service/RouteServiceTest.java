package com.example.linehaul.service;

import com.example.linehaul.exception.BusinessException;
import com.example.linehaul.exception.NotFoundException;
import com.example.linehaul.model.Driver;
import com.example.linehaul.model.Order;
import com.example.linehaul.model.Route;
import com.example.linehaul.model.Vehicle;
import com.example.linehaul.repository.DriverRepository;
import com.example.linehaul.repository.OrderRepository;
import com.example.linehaul.repository.RouteRepository;
import com.example.linehaul.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RouteServiceTest {

    private RouteRepository routeRepository;
    private OrderRepository orderRepository;
    private VehicleRepository vehicleRepository;
    private DriverRepository driverRepository;
    private RouteService routeService;

    private Route route;
    private Order order;

    @BeforeEach
    void setUp() {
        routeRepository = mock(RouteRepository.class);
        orderRepository = mock(OrderRepository.class);
        vehicleRepository = mock(VehicleRepository.class);
        driverRepository = mock(DriverRepository.class);
        routeService = new RouteService(routeRepository, orderRepository, vehicleRepository,
                driverRepository, new ResourceLookup(vehicleRepository, driverRepository));

        route = new Route();
        route.setRouteId("LH-1029");
        route.setOrigin("EXPINTL");
        route.setDestination("ATLTEST");
        route.setDepartureTime("20:00");
        route.setTravelDuration(9);
        route.setMaxCapacity(10000);
        route.setCurrentWeight(0);
        route.setOrderIds(new ArrayList<>());
        route.setStatus("DRAFT");

        order = new Order();
        order.setOrderId("LH-1001");
        order.setCustomer("ABC Logistics");
        order.setWeight(850);
        order.setStatus("READY");

        when(routeRepository.save(any(Route.class))).thenAnswer(i -> i.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
    }

    private void givenRouteExists() {
        when(routeRepository.findByRouteId("LH-1029")).thenReturn(Optional.of(route));
    }

    private void givenOrderExists() {
        when(orderRepository.findByOrderId("LH-1001")).thenReturn(Optional.of(order));
    }

    @Test
    void assigningAnOrderUpdatesWeightEtaAndOrderStatus() {
        givenRouteExists();
        givenOrderExists();

        Route result = routeService.assignOrder("LH-1029", "LH-1001");

        assertEquals(850, result.getCurrentWeight());
        assertEquals(9, result.getCapacityPercent());
        assertEquals("05:00 AM (+1 day)", result.getEta());
        assertEquals(1, result.getOrderIds().size());
        assertEquals("BLOCKED", result.getStatus());
        assertEquals("Truck not assigned", result.getReadinessReason());

        assertEquals("LH-1029", order.getRouteId());
        assertEquals("ASSIGNED", order.getStatus());
        assertEquals("05:00 AM (+1 day)", order.getEta());
    }

    @Test
    void routeIsReadyOnceItHasOrdersTruckAndDriver() {
        givenRouteExists();
        givenOrderExists();
        route.setTruckId("T-182");
        route.setDriverId("D-101");

        Route result = routeService.assignOrder("LH-1029", "LH-1001");

        assertEquals("READY", result.getReadiness());
        assertEquals("READY", result.getStatus());
        assertEquals("Ready to dispatch", result.getReadinessReason());
    }

    @Test
    void capacityExceededIsRejectedAndNothingIsSaved() {
        givenRouteExists();
        givenOrderExists();
        route.setCurrentWeight(9500);

        BusinessException error = assertThrows(BusinessException.class,
                () -> routeService.assignOrder("LH-1029", "LH-1001"));

        assertEquals("Route capacity exceeded.", error.getMessage());
        assertEquals(9500, route.getCurrentWeight());
        assertEquals(0, route.getOrderIds().size());
        verify(routeRepository, never()).save(any(Route.class));
        verify(orderRepository, never()).save(any(Order.class));
        assertNull(order.getRouteId());
    }

    @Test
    void anOrderCannotBelongToTwoRoutes() {
        givenRouteExists();
        givenOrderExists();
        order.setRouteId("LH-1030");

        BusinessException error = assertThrows(BusinessException.class,
                () -> routeService.assignOrder("LH-1029", "LH-1001"));

        assertEquals("Order LH-1001 is already assigned to route LH-1030.", error.getMessage());
        verify(routeRepository, never()).save(any(Route.class));
    }

    @Test
    void removingAnOrderFreesTheWeightAndTheOrder() {
        givenRouteExists();
        givenOrderExists();
        route.getOrderIds().add("LH-1001");
        route.setCurrentWeight(850);
        order.setRouteId("LH-1029");
        order.setStatus("ASSIGNED");

        Route result = routeService.unassignOrder("LH-1029", "LH-1001");

        assertEquals(0, result.getCurrentWeight());
        assertEquals(0, result.getOrderIds().size());
        assertNull(order.getRouteId());
        assertEquals("READY", order.getStatus());
        assertNull(order.getEta());
    }

    @Test
    void aTruckThatIsNotAvailableCannotBeAssigned() {
        givenRouteExists();
        Vehicle truck = new Vehicle();
        truck.setTruckId("T-509");
        truck.setStatus("MAINTENANCE");
        when(vehicleRepository.findByTruckId("T-509")).thenReturn(Optional.of(truck));

        BusinessException error = assertThrows(BusinessException.class,
                () -> routeService.assignTruck("LH-1029", "T-509"));

        assertEquals("Truck is not available.", error.getMessage());
    }

    @Test
    void aTruckAlreadyOnAnotherRouteIsRejected() {
        givenRouteExists();
        Vehicle truck = new Vehicle();
        truck.setTruckId("T-182");
        truck.setStatus("ASSIGNED");
        truck.setRouteId("LH-1030");
        when(vehicleRepository.findByTruckId("T-182")).thenReturn(Optional.of(truck));

        BusinessException error = assertThrows(BusinessException.class,
                () -> routeService.assignTruck("LH-1029", "T-182"));

        assertEquals("Truck is already assigned.", error.getMessage());
    }

    @Test
    void dispatchIsRefusedWhileTheRouteIsBlocked() {
        givenRouteExists();
        route.getOrderIds().add("LH-1001");
        route.setCurrentWeight(850);
        route.setTruckId("T-182");

        BusinessException error = assertThrows(BusinessException.class,
                () -> routeService.dispatch("LH-1029"));

        assertEquals("Route cannot be dispatched. Reason: driver not assigned.", error.getMessage());
        assertEquals("DRAFT", route.getStatus());
    }

    @Test
    void dispatchMovesTheRouteAndItsOrdersOn() {
        givenRouteExists();
        givenOrderExists();
        route.getOrderIds().add("LH-1001");
        route.setCurrentWeight(850);
        route.setTruckId("T-182");
        route.setDriverId("D-101");

        Vehicle truck = new Vehicle();
        truck.setTruckId("T-182");
        truck.setStatus("ASSIGNED");
        Driver driver = new Driver();
        driver.setDriverId("D-101");
        driver.setStatus("ASSIGNED");
        when(vehicleRepository.findByTruckId("T-182")).thenReturn(Optional.of(truck));
        when(driverRepository.findByDriverId("D-101")).thenReturn(Optional.of(driver));

        Route result = routeService.dispatch("LH-1029");

        assertEquals("DISPATCHED", result.getStatus());
        assertEquals("DISPATCHED", order.getStatus());
        assertEquals("IN_TRANSIT", truck.getStatus());
        assertEquals("IN_TRANSIT", driver.getStatus());
    }

    @Test
    void anUnknownRouteIsNotFound() {
        when(routeRepository.findByRouteId("NOPE")).thenReturn(Optional.empty());

        NotFoundException error = assertThrows(NotFoundException.class,
                () -> routeService.findByRouteId("NOPE"));

        assertEquals("Route not found.", error.getMessage());
    }

    @Test
    void aDuplicateRouteIdIsRejected() {
        when(routeRepository.existsByRouteId("LH-1029")).thenReturn(true);

        Route newRoute = new Route();
        newRoute.setRouteId("LH-1029");
        newRoute.setOrigin("A");
        newRoute.setDestination("B");
        newRoute.setMaxCapacity(1000);

        BusinessException error = assertThrows(BusinessException.class,
                () -> routeService.create(newRoute));

        assertEquals("Route ID LH-1029 already exists.", error.getMessage());
        verify(routeRepository, never()).save(any(Route.class));
    }

    @Test
    void createCalculatesTheEtaAndStartsAsDraft() {
        Route newRoute = new Route();
        newRoute.setRouteId("LH-1040");
        newRoute.setOrigin("Austin");
        newRoute.setDestination("Atlanta");
        newRoute.setDepartureTime("20:00");
        newRoute.setTravelDuration(9);
        newRoute.setMaxCapacity(10000);

        Route saved = routeService.create(newRoute);

        assertEquals("05:00 AM (+1 day)", saved.getEta());
        assertEquals("DRAFT", saved.getStatus());
        assertEquals(0, saved.getCurrentWeight());
        assertEquals("BLOCKED", saved.getReadiness());
        assertEquals("No orders assigned", saved.getReadinessReason());
    }

    // ---------------------------------------------------------------- destination rule

    /**
     * The drag-and-drop board offers every editable route of the warehouse, so the rule that a route
     * must actually reach the order's destination has to live here, in the service. Without it a Vizag
     * order could be dropped onto the Chennai run and would simply never arrive.
     */
    @Test
    void anOrderCannotBeDroppedOnARouteThatNeverReachesItsDestination() {
        givenRouteExists();
        givenOrderExists();
        route.setOrigin("Bengaluru");
        route.setDestination("Chennai");
        order.setDestination("Vizag");

        BusinessException error = assertThrows(BusinessException.class,
                () -> routeService.assignOrder("LH-1029", "LH-1001"));

        assertEquals("Destination mismatch: order LH-1001 is going to Vizag, but route LH-1029 runs "
                + "Bengaluru > Chennai and never reaches Vizag.", error.getMessage());
        assertEquals(0, route.getCurrentWeight());
        assertEquals(0, route.getOrderIds().size());
        assertNull(order.getRouteId());
        verify(routeRepository, never()).save(any(Route.class));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void aRouteThatEndsAtTheDestinationAcceptsTheOrder() {
        givenRouteExists();
        givenOrderExists();
        route.setOrigin("Bengaluru");
        route.setDestination("Vizag");
        order.setDestination("Vizag");

        Route result = routeService.assignOrder("LH-1029", "LH-1001");

        assertEquals(850, result.getCurrentWeight());
        assertEquals("LH-1029", order.getRouteId());
    }

    /** Delivering at an intermediate stop is a valid match - the truck passes the door anyway. */
    @Test
    void aRouteThatStopsAtTheDestinationOnTheWayAlsoAcceptsTheOrder() {
        givenRouteExists();
        givenOrderExists();
        route.setOrigin("Bengaluru");
        route.setDestination("Vizag");
        route.setStops(new ArrayList<>(List.of("Warangal", "Hyderabad")));
        order.setDestination("Hyderabad");

        Route result = routeService.assignOrder("LH-1029", "LH-1001");

        assertEquals(850, result.getCurrentWeight());
        assertEquals("LH-1029", order.getRouteId());
    }

    /** The origin is where freight is loaded, so it can never double as the drop-off point. */
    @Test
    void theOriginOfARouteIsNotAValidDestination() {
        givenRouteExists();
        givenOrderExists();
        route.setOrigin("Bengaluru");
        route.setDestination("Chennai");
        order.setDestination("Bengaluru");

        BusinessException error = assertThrows(BusinessException.class,
                () -> routeService.assignOrder("LH-1029", "LH-1001"));

        assertTrue(error.getMessage().startsWith("Destination mismatch"), error.getMessage());
    }

    /** Place names are free text, so "Hyderabad Hub" and "Hyderabad" must not be treated as two cities. */
    @Test
    void theDestinationCheckIgnoresCaseAndGenericWords() {
        givenRouteExists();
        givenOrderExists();
        route.setOrigin("Bengaluru");
        route.setDestination("Hyderabad Hub");
        order.setDestination("hyderabad");

        Route result = routeService.assignOrder("LH-1029", "LH-1001");

        assertEquals("LH-1029", order.getRouteId());
        assertEquals(850, result.getCurrentWeight());
    }
}
