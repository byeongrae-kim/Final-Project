package com.ex.service;

import com.ex.dto.ProductResponse;
import com.ex.entity.AnimalType;
import com.ex.entity.Product;
import com.ex.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductCatalogService {

    private final ProductRepository productRepository;

    public List<ProductResponse> findProducts(AnimalType animalType, String query) {
        return loadProducts(animalType).stream()
                .filter(product -> matchesQuery(product, query))
                .map(ProductResponse::from)
                .toList();
    }

    /**
     * 4~29일 LOT과 재고 50포 이상인 30~45일 LOT만 조회합니다.
     * 매 요청마다 현재 날짜를 계산하므로 시간이 지나 기준 구간에 들어온
     * 상품은 별도 관리자 작업 없이 SALE ZONE에 자동으로 노출됩니다.
     */
    public List<ProductResponse> findSaleZoneProducts(
            AnimalType animalType,
            String query
    ) {
        Predicate<com.ex.entity.ProductLot> saleZoneLot = lot ->
                lot.getQuantity() > 0
                        && SaleZonePolicy.isSaleZone(
                                lot.getExpirationDate(),
                                lot.getQuantity()
                        );

        return loadProducts(animalType).stream()
                .filter(product -> matchesQuery(product, query))
                .filter(product -> product.getLots().stream().anyMatch(saleZoneLot))
                .map(product -> ProductResponse.from(product, saleZoneLot))
                .toList();
    }

    public ProductResponse findProduct(Long productId) {
        Product product = productRepository.findByIdAndActiveTrue(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
        return ProductResponse.from(product);
    }

    private List<Product> loadProducts(AnimalType animalType) {
        return animalType == null
                ? productRepository.findAllByActiveTrueOrderByIdAsc()
                : productRepository.findAllByActiveTrueAndAnimalTypeOrderByIdAsc(animalType);
    }

    private boolean matchesQuery(Product product, String query) {
        String normalizedQuery = query == null
                ? ""
                : query.trim().toLowerCase(Locale.ROOT);

        return normalizedQuery.isEmpty()
                || product.getName().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                || product.getDescription().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                || product.getFeedStage().toLowerCase(Locale.ROOT).contains(normalizedQuery);
    }
}
