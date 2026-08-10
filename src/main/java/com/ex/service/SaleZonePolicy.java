package com.ex.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * LOT의 남은 유통기한을 기준으로 판매 가능 여부와 자동 할인율을 계산합니다.
 */
public final class SaleZonePolicy {

    public static final int OVERSTOCK_QUANTITY = 50;

    private SaleZonePolicy() {
    }

    public static long daysRemaining(LocalDate expirationDate) {
        return ChronoUnit.DAYS.between(LocalDate.now(), expirationDate);
    }

    public static boolean isSellable(LocalDate expirationDate) {
        return daysRemaining(expirationDate) >= 4;
    }

    public static boolean isSaleZone(
            LocalDate expirationDate,
            int lotQuantity
    ) {
        long days = daysRemaining(expirationDate);
        return days >= 4
                && (days <= 29
                || (days <= 45 && lotQuantity >= OVERSTOCK_QUANTITY));
    }

    public static int discountRate(
            LocalDate expirationDate,
            int lotQuantity
    ) {
        long days = daysRemaining(expirationDate);
        if (days <= 3) {
            return 0;
        }
        if (days <= 7) {
            return 40;
        }
        if (days <= 14) {
            return 30;
        }
        if (days <= 29) {
            return 20;
        }
        if (days <= 45 && lotQuantity >= OVERSTOCK_QUANTITY) {
            return 10;
        }
        return 0;
    }

    public static int discountAmount(
            int baseUnitPrice,
            int quantity,
            LocalDate expirationDate,
            int lotQuantity
    ) {
        return Math.multiplyExact(
                Math.floorDiv(
                        Math.multiplyExact(
                                baseUnitPrice,
                                discountRate(expirationDate, lotQuantity)
                        ),
                        100
                ),
                quantity
        );
    }

    public static int effectiveUnitPrice(
            int baseUnitPrice,
            LocalDate expirationDate,
            int lotQuantity
    ) {
        return baseUnitPrice - discountAmount(
                baseUnitPrice,
                1,
                expirationDate,
                lotQuantity
        );
    }

    public static String treatmentLabel(
            LocalDate expirationDate,
            int lotQuantity
    ) {
        long days = daysRemaining(expirationDate);
        if (days <= 3) {
            return "판매 중지";
        }
        if (days <= 7) {
            return "40% · 당일/익일 배송만";
        }
        if (days <= 14) {
            return "30% · 빠른 배송 지역 중심";
        }
        if (days <= 29) {
            return "20% · 임박 특가";
        }
        if (days <= 45 && lotQuantity >= OVERSTOCK_QUANTITY) {
            return "10% · 과잉 재고 할인";
        }
        return "정상가 · 일반 판매";
    }
}
