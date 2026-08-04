package com.ex.dto;

import com.ex.entity.ProductLot;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record InventoryAlertResponse(
        Long productId,
        String productName,
        String lotNumber,
        LocalDate expirationDate,
        int quantity,
        long daysRemaining,
        boolean expired,
        boolean expiringSoon,
        boolean lowStock,
        String severity,
        String status
) {
    public static InventoryAlertResponse from(ProductLot lot) {
        LocalDate today = LocalDate.now();
        long days = ChronoUnit.DAYS.between(today, lot.getExpirationDate());
        boolean expired = days < 0;
        boolean expiringSoon = !expired && days <= 30;
        boolean lowStock = lot.getQuantity() <= 10;

        String severity = expired || lot.getQuantity() == 0
                ? "DANGER"
                : days <= 7 || lot.getQuantity() <= 5
                ? "WARNING"
                : "NOTICE";

        String status = expired
                ? "유통기한 만료"
                : expiringSoon
                ? "유통기한 임박"
                : lot.getQuantity() == 0
                ? "품절"
                : "재고 부족";

        return new InventoryAlertResponse(
                lot.getProduct().getId(),
                lot.getProduct().getName(),
                lot.getLotNumber(),
                lot.getExpirationDate(),
                lot.getQuantity(),
                days,
                expired,
                expiringSoon,
                lowStock,
                severity,
                status
        );
    }
}
