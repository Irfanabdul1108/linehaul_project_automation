package com.example.linehaul.model;

import jakarta.validation.constraints.NotBlank;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A physical depot that owns orders, routes, drivers and trucks.
 *
 * <p>Every operational document in this project carries a {@code warehouseId}. Two names matter and
 * they are deliberately different:</p>
 * <ul>
 *   <li>{@link #name} is the <b>designation</b> ("Warehouse A"). It exists for the warehouse
 *       switching interface, where an operator picks the depot to work in - nowhere else.</li>
 *   <li>{@link #hubLocation} is the <b>place</b> ("Bengaluru"). This is the token written into order
 *       and route lanes and shown on every screen, which is what lets the assignment engine check
 *       whether a route passes through a depot, and lets the lane memory know how long a drive
 *       between two places takes.</li>
 * </ul>
 */
@Document(collection = "warehouses")
public class Warehouse {

    @Id
    private String id;

    @NotBlank(message = "Warehouse ID is required.")
    @Indexed(unique = true)
    private String warehouseId;

    @NotBlank(message = "Warehouse name is required.")
    private String name;

    /** Short code shown in the UI, for example {@code WH-A}. */
    private String code;

    /** The lane token used as an origin/destination on orders and routes - the city, e.g. {@code Bengaluru}. */
    private String hubLocation;

    private String city;

    private String state;

    private String description;

    public Warehouse() {
    }

    public Warehouse(String warehouseId, String name, String code, String hubLocation,
                      String city, String state, String description) {
        this.warehouseId = warehouseId;
        this.name = name;
        this.code = code;
        this.hubLocation = hubLocation;
        this.city = city;
        this.state = state;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(String warehouseId) {
        this.warehouseId = warehouseId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getHubLocation() {
        return hubLocation;
    }

    public void setHubLocation(String hubLocation) {
        this.hubLocation = hubLocation;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
