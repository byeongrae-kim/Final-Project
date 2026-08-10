package com.ex.service;

import com.ex.dto.AdminOrderStatusRequest;
import com.ex.dto.AdminDashboardResponse;
import com.ex.dto.InventoryAlertResponse;
import com.ex.dto.OrderDetailResponse;
import com.ex.entity.OrderStatus;
import com.ex.entity.PaymentStatus;
import com.ex.entity.Product;
import com.ex.entity.ProductLot;
import com.ex.entity.PurchaseOrder;
import com.ex.repository.ProductLotRepository;
import com.ex.repository.ProductRepository;
import com.ex.repository.PurchaseOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminOperationsService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final ProductLotRepository productLotRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public AdminDashboardResponse dashboard() {
        LocalDate today = LocalDate.now();
        LocalDate firstDay = today.minusDays(6);
        List<PurchaseOrder> orders = purchaseOrderRepository.findAll();
        List<Product> products = productRepository.findAllByActiveTrueOrderByIdAsc();
        List<ProductLot> lots = productLotRepository
                .findByProduct_ActiveTrueOrderByExpirationDateAsc();

        long totalRevenue = orders.stream()
                .filter(order -> order.getPaymentStatus() == PaymentStatus.DONE)
                .mapToLong(PurchaseOrder::getTotalAmount)
                .sum();
        long todayRevenue = orders.stream()
                .filter(order -> order.getPaymentStatus() == PaymentStatus.DONE)
                .filter(order -> order.getCreatedAt().toLocalDate().equals(today))
                .mapToLong(PurchaseOrder::getTotalAmount)
                .sum();

        Map<LocalDate, long[]> daily = new LinkedHashMap<>();
        for (int day = 0; day < 7; day++) {
            daily.put(firstDay.plusDays(day), new long[2]);
        }
        orders.stream()
                .filter(order -> order.getPaymentStatus() == PaymentStatus.DONE)
                .filter(order -> !order.getCreatedAt().toLocalDate().isBefore(firstDay))
                .filter(order -> !order.getCreatedAt().toLocalDate().isAfter(today))
                .forEach(order -> {
                    long[] point = daily.get(order.getCreatedAt().toLocalDate());
                    point[0]++;
                    point[1] += order.getTotalAmount();
                });

        List<AdminDashboardResponse.DailySales> dailySales = daily.entrySet()
                .stream()
                .map(entry -> new AdminDashboardResponse.DailySales(
                        entry.getKey(), entry.getValue()[0], entry.getValue()[1]
                ))
                .toList();

        return new AdminDashboardResponse(
                totalRevenue,
                todayRevenue,
                orders.size(),
                orders.stream().filter(order ->
                        order.getCreatedAt().toLocalDate().equals(today)).count(),
                orders.stream().filter(order ->
                        order.getPaymentStatus() == PaymentStatus.DONE).count(),
                orders.stream().filter(order ->
                        order.getStatus() == OrderStatus.PREPARING
                                || order.getStatus() == OrderStatus.SHIPPING).count(),
                orders.stream().filter(order ->
                        order.getStatus() == OrderStatus.CANCELLED).count(),
                products.size(),
                products.stream().filter(product -> product.getLots().stream()
                        .mapToInt(ProductLot::getQuantity).sum() == 0).count(),
                lots.stream().filter(lot -> lot.getQuantity() <= 10).count(),
                lots.stream().filter(lot -> !lot.getExpirationDate()
                        .isAfter(today.plusDays(30))).count(),
                dailySales
        );
    }

    @Transactional(readOnly = true)
    public List<OrderDetailResponse> findOrders() {
        return purchaseOrderRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(OrderDetailResponse::from)
                .toList();
    }

    @Transactional
    public OrderDetailResponse updateOrderStatus(
            String orderNumber,
            AdminOrderStatusRequest request
    ) {
        PurchaseOrder order = purchaseOrderRepository.findByOrderNumberForUpdate(orderNumber)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));

        if (order.getStatus() == request.status()) {
            return OrderDetailResponse.from(order);
        }

        if (request.status() == OrderStatus.PREPARING) {
            order.startPreparing();
        } else if (request.status() == OrderStatus.SHIPPING) {
            order.startShipping(request.carrier(), request.trackingNumber());
        } else if (request.status() == OrderStatus.DELIVERED) {
            order.completeDelivery();
        } else {
            throw new IllegalArgumentException(
                    "관리자 화면에서는 상품준비중, 배송중, 배송완료 순서로만 변경할 수 있습니다."
            );
        }

        return OrderDetailResponse.from(order);
    }

    @Transactional(readOnly = true)
    public List<InventoryAlertResponse> findInventoryAlerts() {
        return productLotRepository
                .findByProduct_ActiveTrueOrderByExpirationDateAsc()
                .stream()
                .filter(this::requiresAlert)
                .map(InventoryAlertResponse::from)
                .toList();
    }

    private boolean requiresAlert(ProductLot lot) {
        return lot.getQuantity() <= 10
                || !lot.getExpirationDate().isAfter(
                        java.time.LocalDate.now().plusDays(30)
                );
    }
}
