package com.example.linehaul.controller;

import com.example.linehaul.automation.AssignmentService;
import com.example.linehaul.dto.AssignRequest;
import com.example.linehaul.dto.AssignmentAdvice;
import com.example.linehaul.dto.AssignmentResult;
import com.example.linehaul.dto.BatchAssignmentReport;
import com.example.linehaul.dto.CreateAndAssignRequest;
import com.example.linehaul.dto.RecommendRequest;
import com.example.linehaul.exception.BusinessException;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The endpoints of the smart assignment feature.
 *
 * <p>{@code recommend-routes} only reads. {@code assign}, {@code assign-all} and
 * {@code create-and-assign} write, and every one of them re-validates the business rules on the server
 * before a single document is saved.</p>
 */
@RestController
@RequestMapping("/api")
public class AssignmentController {

    private final AssignmentService assignmentService;

    public AssignmentController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    /** Step 1 of the flow: analyse one order and return the top three routes (or a new-route plan). */
    @PostMapping("/orders/{orderId}/recommend-routes")
    public AssignmentAdvice recommend(@PathVariable String orderId,
                                      @RequestBody(required = false) RecommendRequest request,
                                      @RequestParam(required = false) String warehouseId) {
        RecommendRequest safe = request == null ? new RecommendRequest() : request;
        String depot = depot(safe.getWarehouseId(), warehouseId);
        return assignmentService.advise(orderId, depot, safe.isUseAi());
    }

    /** Step 2: load the order onto the route the dispatcher picked. */
    @PostMapping("/orders/{orderId}/assign")
    public AssignmentResult assign(@PathVariable String orderId,
                                   @Valid @RequestBody AssignRequest request,
                                   @RequestParam(required = false) String warehouseId) {
        if (request.getWarehouseId() == null || request.getWarehouseId().isBlank()) {
            request.setWarehouseId(warehouseId);
        }
        String bodyOrder = request.getOrderId() == null ? "" : request.getOrderId().trim();
        if (!bodyOrder.isEmpty() && !orderId.equals(bodyOrder)) {
            throw new BusinessException("The order in the address (" + orderId + ") and in the body ("
                    + bodyOrder + ") do not match.");
        }
        request.setOrderId(orderId);
        return assignmentService.assign(orderId, request);
    }

    /** One order of a bulk run, so the UI can show the progress list while it works. */
    @PostMapping("/orders/{orderId}/auto-assign")
    public BatchAssignmentReport.Outcome autoAssign(@PathVariable String orderId,
                                                    @RequestParam(required = false) String warehouseId) {
        return assignmentService.autoAssignOne(orderId, warehouseId);
    }

    /** The bulk run: the same engine over every unassigned order of the depot. */
    @PostMapping("/orders/assign-all")
    public BatchAssignmentReport assignAll(@RequestBody(required = false) RecommendRequest request,
                                           @RequestParam(required = false) String warehouseId) {
        RecommendRequest safe = request == null ? new RecommendRequest() : request;
        return assignmentService.assignAll(depot(safe.getWarehouseId(), warehouseId), safe.isUseAi());
    }

    /** No route fitted: create the recommended one, put the chosen driver and truck on it, and load the order. */
    @PostMapping("/routes/create-and-assign")
    public AssignmentResult createAndAssign(@Valid @RequestBody CreateAndAssignRequest request,
                                            @RequestParam(required = false) String warehouseId) {
        if (request.getWarehouseId() == null || request.getWarehouseId().isBlank()) {
            request.setWarehouseId(warehouseId);
        }
        return assignmentService.createAndAssign(request);
    }

    /** The body wins over the query parameter, which the frontend always sends for convenience. */
    private static String depot(String fromBody, String fromQuery) {
        if (fromBody != null && !fromBody.isBlank()) {
            return fromBody.trim();
        }
        return fromQuery == null ? "" : fromQuery.trim();
    }
}
