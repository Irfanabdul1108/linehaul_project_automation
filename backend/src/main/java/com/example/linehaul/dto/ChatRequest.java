package com.example.linehaul.dto;


public class ChatRequest {

    private String message;

    /** Optional depot scope. When it is empty the assistant answers from the whole network. */
    private String warehouseId;

    public ChatRequest() {
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(String warehouseId) {
        this.warehouseId = warehouseId;
    }
}
