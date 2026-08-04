package com.ex.repository;

import com.ex.entity.PurchaseOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    Optional<PurchaseOrder> findByOrderNumber(String orderNumber);
    Optional<PurchaseOrder> findByProviderTransactionId(String providerTransactionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@jakarta.persistence.QueryHint(
            name = "jakarta.persistence.lock.timeout",
            value = "5000"
    ))
    @Query("select purchaseOrder from PurchaseOrder purchaseOrder where purchaseOrder.orderNumber = :orderNumber")
    Optional<PurchaseOrder> findByOrderNumberForUpdate(
            @Param("orderNumber") String orderNumber
    );

    List<PurchaseOrder> findByMember_IdAndCreatedAtAfterOrderByCreatedAtDesc(Long memberId, LocalDateTime since);
    List<PurchaseOrder> findAllByOrderByCreatedAtDesc();
}
