package com.ex.dto;

import com.ex.entity.Product;
import com.ex.entity.ProductLot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

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
        LocalDate today = LocalDate.now();
        List<ProductLot> availableLots = product.getLots().stream()
                .filter(lot -> lot.getQuantity() > 0)
                .filter(lot -> !lot.getExpirationDate().isBefore(today))
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
                availableLots.stream().map(LotResponse::from).toList()
        );
    }

    public record LotResponse(
            String lotNumber,
            LocalDate manufacturedDate,
            LocalDate expirationDate,
            int quantity,
            long daysRemaining,
            String status
    ) {
        static LotResponse from(ProductLot lot) {
            long daysRemaining = ChronoUnit.DAYS.between(LocalDate.now(), lot.getExpirationDate());
            String status = daysRemaining <= 30 ? "유통기한 임박" : lot.getQuantity() <= 10 ? "재고 부족" : "판매 가능";
            return new LotResponse(
                    lot.getLotNumber(),
                    lot.getManufacturedDate(),
                    lot.getExpirationDate(),
                    lot.getQuantity(),
                    daysRemaining,
                    status
            );
        }
    }
}
