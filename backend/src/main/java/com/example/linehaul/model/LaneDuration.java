package com.example.linehaul.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * How long one lane takes, remembered so nobody has to type it twice.
 *
 * <p>A lane is a pair of places - two warehouse cities ({@code Bengaluru} to {@code Hyderabad}), or a
 * warehouse city and any other destination the network serves. The key is built from the normalised
 * tokens of both ends, sorted, so {@code Bengaluru to Hyderabad} and {@code Hyderabad to Bengaluru}
 * are the same lane and are answered by one another.</p>
 *
 * <p>Rows are written from two places only: the demo/reference table loaded at startup, and every
 * route a dispatcher actually creates or edits. {@link #travelDuration} is the running average of
 * everything that was observed, so the estimate improves with use instead of drifting.</p>
 */
@Document(collection = "laneDurations")
public class LaneDuration {

    public static final String SOURCE_SEED = "SEED";
    public static final String SOURCE_ROUTE = "ROUTE";

    @Id
    private String id;

    /** Normalised, direction independent key, for example {@code bengaluru|hyderabad}. */
    @Indexed(unique = true)
    private String laneKey;

    /** Readable end points, kept for the UI. */
    private String origin;

    private String destination;

    /** Average travel time of this lane, in whole hours. */
    private int travelDuration;

    /** How many observations the average is built from. */
    private int samples;

    /** {@code SEED} for the reference table, {@code ROUTE} once a real route confirmed it. */
    private String source = SOURCE_SEED;

    public LaneDuration() {
    }

    public LaneDuration(String laneKey, String origin, String destination, int travelDuration,
                        int samples, String source) {
        this.laneKey = laneKey;
        this.origin = origin;
        this.destination = destination;
        this.travelDuration = travelDuration;
        this.samples = samples;
        this.source = source;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLaneKey() {
        return laneKey;
    }

    public void setLaneKey(String laneKey) {
        this.laneKey = laneKey;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public int getTravelDuration() {
        return travelDuration;
    }

    public void setTravelDuration(int travelDuration) {
        this.travelDuration = travelDuration;
    }

    public int getSamples() {
        return samples;
    }

    public void setSamples(int samples) {
        this.samples = samples;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
