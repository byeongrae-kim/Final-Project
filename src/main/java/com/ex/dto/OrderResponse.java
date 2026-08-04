package com.ex.dto;

import com.ex.entity.OrderStatus;
import com.ex.entity.PaymentMethod;
import com.ex.entity.PaymentProvider;
import com.ex.entity.PaymentStatus;
import com.ex.entity.PurchaseOrder;

import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        String orderNumber,
        OrderStatus status,
        int productAmount,
        int deliveryFee,
        int discountAmount,
        int totalAmount,
        PaymentMethod paymentMethod,
        PaymentProvider paymentProvider,
        PaymentStatus paymentStatus,
        String paymentToken,
        String receiptUrl,
        String virtualAccountBank,
        String virtualAccountNumber,
        String virtualAccountDueDate,
        LocalDateTime orderedAt,
        List<OrderLineResponse> items
) {
    public static OrderResponse from(PurchaseOrder order) {
        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getProductAmount(),
                order.getDeliveryFee(),
                order.getDiscountAmount(),
                order.getTotalAmount(),
                order.getPaymentMethod(),
                order.getPaymentProvider(),
                order.getPaymentStatus(),
                order.getPaymentCallbackToken(),
                order.getPaymentReceiptUrl(),
                order.getVirtualAccountBank(),
                order.getVirtualAccountNumber(),
                order.getVirtualAccountDueDate(),
                order.getCreatedAt(),
                order.getItems().stream()
                        .map(item -> new OrderLineResponse(
                                item.getProduct().getId(),
                                item.getProductName(),
                                item.getQuantity(),
                                item.getUnitPrice(),
                                item.getLineAmount(),
                                item.getProduct().getWeightKg().doubleValue() * item.getQuantity()
                        ))
                        .toList()
        );
    }

    public record OrderLineResponse(
            Long productId,
            String productName,
            int quantity,
            int unitPrice,
            int lineAmount,
            double totalWeightKg
    ) {
    }
}
