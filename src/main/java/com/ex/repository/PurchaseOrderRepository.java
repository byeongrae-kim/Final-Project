package com.ex.repository;

import com.ex.entity.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    Optional<PurchaseOrder> findByOrderNumber(String orderNumber);
    List<PurchaseOrder> findByMember_IdAndCreatedAtAfterOrderByCreatedAtDesc(Long memberId, LocalDateTime since);
}
