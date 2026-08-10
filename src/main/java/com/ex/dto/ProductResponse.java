package com.ex.dto;

import com.ex.entity.Product;
import com.ex.entity.ProductLot;
import com.ex.service.SaleZonePolicy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

public record ProductResponse(
        Long id,
        String productCode,
        String name,
        String animal,
        String animalType,
        String stage,
        String description,
        BigDecimal weight,
        int price,
        Integer originalPrice,
        BigDecimal protein,
        BigDecimal fat,
        BigDecimal fiber,
        BigDecimal calcium,
        String lot,
        LocalDate manufacturedDate,
        LocalDate expiry,
        int stock,
        String tone,
        String badge,
        String shape,
        String imageUrl,
        String manufacturer,
        List<LotResponse> lots
) {
    public static ProductResponse from(Product product) {
        return from(product, lot -> true);
    }

    /**
     * 특정 판매 구역에서 사용할 LOT 조건을 적용해 상품 재고를 계산합니다.
     * SALE ZONE에서는 남은 일수와 과잉 재고 기준을 만족하는 LOT만 포함됩니다.
     */
    public static ProductResponse from(
            Product product,
            Predicate<ProductLot> lotFilter
    ) {
        List<ProductLot> availableLots = product.getLots().stream()
                .filter(lot -> lot.getQuantity() > 0)
                // 남은 기간 3일 이하는 폐기·반품 검토 대상으로 판매하지 않습니다.
                .filter(lot -> SaleZonePolicy.isSellable(lot.getExpirationDate()))
                .filter(lotFilter)
                .sorted(Comparator.comparing(ProductLot::getExpirationDate))
                .toList();

        ProductLot firstAvailableLot = availableLots.stream()
                .min(Comparator.comparing(ProductLot::getExpirationDate))
                .orElse(null);

        int stock = availableLots.stream()
                .mapToInt(ProductLot::getQuantity)
                .sum();

        return new ProductResponse(
                product.getId(),
                "FF-P" + String.format("%05d", product.getId()),
                product.getName(),
                product.getAnimalType().getLabel(),
                product.getAnimalType().name(),
                product.getFeedStage(),
                product.getDescription(),
                product.getWeightKg(),
                product.getPrice(),
                product.getOriginalPrice(),
                product.getProteinPercent(),
                product.getFatPercent(),
                product.getFiberPercent(),
                product.getCalciumPercent(),
                firstAvailableLot == null ? null : firstAvailableLot.getLotNumber(),
                firstAvailableLot == null ? null : firstAvailableLot.getManufacturedDate(),
                firstAvailableLot == null ? null : firstAvailableLot.getExpirationDate(),
                stock,
                product.getDisplayTone(),
                product.getBadge(),
                product.getDisplayShape(),
                product.getImageUrl(),
                product.getManufacturer().getName(),
                availableLots.stream()
                        .map(lot -> LotResponse.from(lot, product.getPrice()))
                        .toList()
        );
    }

    public record LotResponse(
            String lotNumber,
            LocalDate manufacturedDate,
            LocalDate expirationDate,
            int quantity,
            long daysRemaining,
            String status,
            int discountRate,
            int effectiveUnitPrice,
            boolean saleZone,
            String treatment
    ) {
        static LotResponse from(ProductLot lot, int baseUnitPrice) {
            long daysRemaining = ChronoUnit.DAYS.between(LocalDate.now(), lot.getExpirationDate());
            int discountRate = SaleZonePolicy.discountRate(
                    lot.getExpirationDate(),
                    lot.getQuantity()
            );
            String status = discountRate > 0
                    ? "SALE " + discountRate + "%"
                    : lot.getQuantity() <= 10 ? "재고 부족" : "판매 가능";
            return new LotResponse(
                    lot.getLotNumber(),
                    lot.getManufacturedDate(),
                    lot.getExpirationDate(),
                    lot.getQuantity(),
                    daysRemaining,
                    status,
                    discountRate,
                    SaleZonePolicy.effectiveUnitPrice(
                            baseUnitPrice,
                            lot.getExpirationDate(),
                            lot.getQuantity()
                    ),
                    SaleZonePolicy.isSaleZone(
                            lot.getExpirationDate(),
                            lot.getQuantity()
                    ),
                    SaleZonePolicy.treatmentLabel(
                            lot.getExpirationDate(),
                            lot.getQuantity()
                    )
            );
        }
    }
}
