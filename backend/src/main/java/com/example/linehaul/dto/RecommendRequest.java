package com.example.linehaul.dto;

/** Body of the recommendation call. Both fields are optional for backwards compatibility. */
public class RecommendRequest {

    private String warehouseId;
    /** Ask Gemini to re-order and explain the candidates the backend already validated. */
    private boolean useAi = true;
    /** When true, also explain the orders the engine rejected (used by the details view). */
    private boolean includeRejected = true;

    public RecommendRequest() {
    }

    public String getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(String warehouseId) {
        this.warehouseId = warehouseId;
    }

    public boolean isUseAi() {
        return useAi;
    }

    public void setUseAi(boolean useAi) {
        this.useAi = useAi;
    }

    public boolean isIncludeRejected() {
        return includeRejected;
    }

    public void setIncludeRejected(boolean includeRejected) {
        this.includeRejected = includeRejected;
    }
}
