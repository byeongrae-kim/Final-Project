package com.ex.dto;

import com.ex.entity.OrderStatus;
import com.ex.entity.PaymentMethod;
import com.ex.entity.PaymentProvider;
import com.ex.entity.PaymentStatus;
import com.ex.entity.PurchaseOrder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record OrderDetailResponse(
        Long id,
        String orderNumber,
        OrderStatus status,
        PaymentStatus paymentStatus,
        PaymentMethod paymentMethod,
        PaymentProvider paymentProvider,
        String memberUsername,
        String farmName,
        String customerName,
        String phone,
        String postalCode,
        String address,
        String detailAddress,
        String unloadingLocation,
        String deliveryRequest,
        int productAmount,
        int deliveryFee,
        int discountAmount,
        int totalAmount,
        String receiptUrl,
        String virtualAccountBank,
        String virtualAccountNumber,
        String virtualAccountDueDate,
        String trackingCarrier,
        String trackingNumber,
        LocalDateTime orderedAt,
        LocalDateTime paymentApprovedAt,
        LocalDateTime preparingAt,
        LocalDateTime shippedAt,
        LocalDateTime deliveredAt,
        LocalDateTime cancelledAt,
        List<OrderLineDetailResponse> items
) {
    public static OrderDetailResponse from(PurchaseOrder order) {
        return new OrderDetailResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getPaymentStatus(),
                order.getPaymentMethod(),
                order.getPaymentProvider(),
                order.getMember() == null ? null : order.getMember().getUsername(),
                order.getMember() == null ? null : order.getMember().getFarmName(),
                order.getCustomerName(),
                order.getPhone(),
                order.getPostalCode(),
                order.getAddress(),
                order.getDetailAddress(),
                order.getUnloadingLocation(),
                order.getDeliveryRequest(),
                order.getProductAmount(),
                order.getDeliveryFee(),
                order.getDiscountAmount(),
                order.getTotalAmount(),
                order.getPaymentReceiptUrl(),
                order.getVirtualAccountBank(),
                order.getVirtualAccountNumber(),
                order.getVirtualAccountDueDate(),
                order.getTrackingCarrier(),
                order.getTrackingNumber(),
                order.getCreatedAt(),
                order.getPaymentApprovedAt(),
                order.getPreparingAt(),
                order.getShippedAt(),
                order.getDeliveredAt(),
                order.getCancelledAt(),
                order.getItems().stream()
                        .map(item -> new OrderLineDetailResponse(
                                item.getProduct().getId(),
                                item.getProductName(),
                                item.getQuantity(),
                                item.getUnitPrice(),
                                item.getLineAmount(),
                                item.getProduct().getWeightKg().doubleValue()
                                        * item.getQuantity(),
                                item.getLotAllocations().stream()
                                        .map(allocation -> new LotAllocationResponse(
                                                allocation.getProductLot().getLotNumber(),
                                                allocation.getProductLot().getExpirationDate(),
                                                allocation.getQuantity()
                                        ))
                                        .toList()
                        ))
                        .toList()
        );
    }

    public record OrderLineDetailResponse(
            Long productId,
            String productName,
            int quantity,
            int unitPrice,
            int lineAmount,
            double totalWeightKg,
            List<LotAllocationResponse> lots
    ) {
    }

    public record LotAllocationResponse(
            String lotNumber,
            LocalDate expirationDate,
            int quantity
    ) {
    }
}
