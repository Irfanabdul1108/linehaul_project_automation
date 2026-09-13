package com.example.linehaul.automation;

import com.example.linehaul.model.Route;
import com.example.linehaul.service.LinehaulUtil;

import java.time.LocalTime;
import java.util.List;

/**
 * Reuses the project's existing ETA model (departure time plus travel duration, see
 * {@link LinehaulUtil#calculateEta(String, int)}) and only adds the one thing the assignment engine
 * needs: the time a <em>specific order</em> is delivered, which depends on where it is loaded and
 * unloaded along the route.
 *
 * <p>The route's total travel time is shared evenly across its legs. When a route does not carry a
 * departure time, nothing is invented: the estimate comes back as "unknown" and the caller says so.</p>
 */
public final class EtaEstimator {

    public static final class Estimate {
        private final boolean known;
        private final double travelHours;
        private final int arrivalMinutes;
        private final String arrivalClock;
        private final String durationText;

        Estimate(boolean known, double travelHours, int arrivalMinutes, String arrivalClock, String durationText) {
            this.known = known;
            this.travelHours = travelHours;
            this.arrivalMinutes = arrivalMinutes;
            this.arrivalClock = arrivalClock;
            this.durationText = durationText;
        }

        public boolean isKnown() {
            return known;
        }

        public double getTravelHours() {
            return travelHours;
        }

        /** Travel minutes from the route departure until this order is unloaded; used for ranking. */
        public int getArrivalMinutes() {
            return arrivalMinutes;
        }

        public String getArrivalClock() {
            return arrivalClock;
        }

        public String getDurationText() {
            return durationText;
        }
    }

    private static final Estimate UNKNOWN = new Estimate(false, 0, Integer.MAX_VALUE, null, null);

    private EtaEstimator() {
    }

    /**
     * @param sequence origin, stops and destination in travel order
     * @param pickup   index in the sequence where the order is loaded
     * @param dropoff  index in the sequence where the order is unloaded
     */
    public static Estimate estimate(Route route, List<String> sequence, int pickup, int dropoff) {
        String departure = LinehaulUtil.clean(route.getDepartureTime());
        if (departure.isEmpty() || sequence == null || sequence.size() < 2 || dropoff <= pickup) {
            return UNKNOWN;
        }

        LocalTime start;
        try {
            start = LocalTime.parse(departure.trim(), LinehaulUtil.INPUT);
        } catch (Exception invalidTime) {
            return UNKNOWN;
        }

        int legs = sequence.size() - 1;
        double perLeg = Math.max(0, route.getTravelDuration()) / (double) legs;
        double travelHours = perLeg * (dropoff - pickup);
        int offsetMinutes = (int) Math.round(travelHours * 60);

        LocalTime arrival = start.plusHours(offsetMinutes / 60L).plusMinutes(offsetMinutes % 60);
        int dayOffset = (start.getHour() * 60 + start.getMinute() + offsetMinutes) / (24 * 60);

        return new Estimate(true, travelHours, offsetMinutes, arrival.format(LinehaulUtil.DISPLAY),
                format(travelHours, dayOffset));
    }

    private static String format(double hours, int extraDays) {
        int totalMinutes = (int) Math.round(hours * 60);
        int shownHours = totalMinutes / 60;
        int minutes = totalMinutes % 60;
        StringBuilder text = new StringBuilder();
        if (shownHours > 0) {
            text.append(shownHours).append("h ");
        }
        text.append(minutes).append("m");
        if (extraDays == 1) {
            text.append(" (+1 day)");
        } else if (extraDays > 1) {
            text.append(" (+").append(extraDays).append(" days)");
        }
        return text.toString();
    }
}
