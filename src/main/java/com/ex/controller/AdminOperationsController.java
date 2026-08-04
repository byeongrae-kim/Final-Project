package com.ex.controller;

import com.ex.dto.AdminOrderStatusRequest;
import com.ex.dto.InventoryAlertResponse;
import com.ex.dto.OrderDetailResponse;
import com.ex.service.AdminOperationsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminOperationsController {

    private final AdminOperationsService adminOperationsService;

    @GetMapping("/orders")
    public List<OrderDetailResponse> orders() {
        return adminOperationsService.findOrders();
    }

    @PatchMapping("/orders/{orderNumber}/status")
    public OrderDetailResponse updateStatus(
            @PathVariable(name = "orderNumber") String orderNumber,
            @Valid @RequestBody AdminOrderStatusRequest request
    ) {
        return adminOperationsService.updateOrderStatus(orderNumber, request);
    }

    @GetMapping("/inventory/alerts")
    public List<InventoryAlertResponse> inventoryAlerts() {
        return adminOperationsService.findInventoryAlerts();
    }
}
