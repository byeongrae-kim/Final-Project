package com.ex.controller;

import com.ex.dto.AdminOrderStatusRequest;
import com.ex.dto.AdminActivityResponse;
import com.ex.dto.AdminDashboardResponse;
import com.ex.dto.InventoryAlertResponse;
import com.ex.dto.OrderDetailResponse;
import com.ex.service.AdminOperationsService;
import com.ex.service.AdminActivityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminOperationsController {

    private final AdminOperationsService adminOperationsService;
    private final AdminActivityService adminActivityService;

    @GetMapping("/dashboard")
    public AdminDashboardResponse dashboard() {
        return adminOperationsService.dashboard();
    }

    @GetMapping("/activities")
    public List<AdminActivityResponse> activities() {
        return adminActivityService.findRecent();
    }

    @GetMapping("/orders")
    public List<OrderDetailResponse> orders() {
        return adminOperationsService.findOrders();
    }

    @PatchMapping("/orders/{orderNumber}/status")
    public OrderDetailResponse updateStatus(
            @PathVariable(name = "orderNumber") String orderNumber,
            @Valid @RequestBody AdminOrderStatusRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest
    ) {
        OrderDetailResponse result =
                adminOperationsService.updateOrderStatus(orderNumber, request);
        adminActivityService.record(
                authentication.getName(),
                "ORDER_STATUS_CHANGED",
                "ORDER",
                orderNumber,
                "주문 상태를 " + request.status().name() + "(으)로 변경",
                servletRequest.getRemoteAddr()
        );
        return result;
    }

    @GetMapping("/inventory/alerts")
    public List<InventoryAlertResponse> inventoryAlerts() {
        return adminOperationsService.findInventoryAlerts();
    }
}
