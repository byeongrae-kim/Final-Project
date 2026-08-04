package com.ex.repository;

import com.ex.entity.ProductLot;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrencyLockConfigurationTest {

    @Test
    void orderAndLotMutationQueriesUsePessimisticWriteLocks() throws Exception {
        Lock orderLock = PurchaseOrderRepository.class
                .getMethod("findByOrderNumberForUpdate", String.class)
                .getAnnotation(Lock.class);
        Lock lotLock = ProductLotRepository.class
                .getMethod(
                        "findByProductIdAndQuantityGreaterThanAndExpirationDateGreaterThanEqualOrderByExpirationDateAsc",
                        Long.class,
                        int.class,
                        LocalDate.class
                )
                .getAnnotation(Lock.class);

        assertThat(orderLock).isNotNull();
        assertThat(orderLock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(lotLock).isNotNull();
        assertThat(lotLock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(ProductLot.class).isNotNull();
    }
}
