package com.ex.repository;

import com.ex.entity.ProductLot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface ProductLotRepository extends JpaRepository<ProductLot, Long> {

    List<ProductLot> findByProduct_ActiveTrueOrderByExpirationDateAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@jakarta.persistence.QueryHint(
            name = "jakarta.persistence.lock.timeout",
            value = "5000"
    ))
    @Query("""
            select lot
            from ProductLot lot
            where lot.product.id = :productId
              and lot.quantity > :quantity
              and lot.expirationDate >= :expirationDate
            order by lot.expirationDate asc
            """)
    List<ProductLot> findByProductIdAndQuantityGreaterThanAndExpirationDateGreaterThanEqualOrderByExpirationDateAsc(
            @Param("productId") Long productId,
            @Param("quantity") int quantity,
            @Param("expirationDate") LocalDate expirationDate
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@jakarta.persistence.QueryHint(
            name = "jakarta.persistence.lock.timeout",
            value = "5000"
    ))
    @Query("select lot from ProductLot lot where lot.id in :lotIds")
    List<ProductLot> findAllByIdForUpdate(
            @Param("lotIds") Collection<Long> lotIds
    );
}
