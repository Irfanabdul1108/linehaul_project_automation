package com.example.linehaul.automation;

import com.example.linehaul.dto.RouteRecommendation;
import com.example.linehaul.model.Route;


/**
 * Hard business rules for load capacity. These checks are deterministic: they only ever look at the
 * numbers that are stored in MongoDB and they never accept a route that would be overloaded.
 */
public final class CapacityRules {

    private CapacityRules() {
    }

    /**
     * @return {@code null} when the order fits, otherwise the reason it does not.
     */
    public static String rejectRouteCapacity(Route route, int orderWeight) {
        int room = route.getMaxCapacity() - route.getCurrentWeight();
        if (orderWeight <= room) {
            return null;
        }
        return "Not enough room: " + number(route.getMaxCapacity() - room) + " kg of "
                + number(route.getMaxCapacity()) + " kg is already loaded, so only " + number(room)
                + " kg is free and this order needs " + number(orderWeight) + " kg.";
    }

    /**
     * Vehicle check. {@code vehicleCapacity} is {@code null} when the route has no truck assigned or
     * the truck's capacity is not recorded - in that case nothing is assumed, the caller only shows
     * a warning.
     */
    public static String rejectVehicleCapacity(Route route, int orderWeight, Integer vehicleCapacity) {
        if (vehicleCapacity == null || vehicleCapacity <= 0) {
            return null;
        }
        int newLoad = route.getCurrentWeight() + orderWeight;
        if (newLoad <= vehicleCapacity) {
            return null;
        }
        return "The assigned truck can carry " + number(vehicleCapacity) + " kg, but this route would be at "
                + number(newLoad) + " kg.";
    }

    public static String number(int value) {
        return String.format(java.util.Locale.ROOT, "%,d", value);
    }

    /** Fill percentage of the route after the order has been added. */
    public static int fillPercentAfter(Route route, int orderWeight) {
        if (route.getMaxCapacity() <= 0) {
            return 0;
        }
        return (int) Math.round(((route.getCurrentWeight() + orderWeight) * 100.0) / route.getMaxCapacity());
    }

    static void applyCapacity(Route route, int orderWeight, RouteRecommendation recommendation) {
        recommendation.setAvailableCapacity(Math.max(0, route.getMaxCapacity() - route.getCurrentWeight()));
        recommendation.setLoadAfter(route.getCurrentWeight() + orderWeight);
        recommendation.setFillPercentAfter(fillPercentAfter(route, orderWeight));
    }
}
