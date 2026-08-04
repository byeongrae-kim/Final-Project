package com.ex.service;

import com.ex.dto.AdminOrderStatusRequest;
import com.ex.dto.InventoryAlertResponse;
import com.ex.dto.OrderDetailResponse;
import com.ex.entity.OrderStatus;
import com.ex.entity.ProductLot;
import com.ex.entity.PurchaseOrder;
import com.ex.repository.ProductLotRepository;
import com.ex.repository.PurchaseOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminOperationsService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final ProductLotRepository productLotRepository;

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
