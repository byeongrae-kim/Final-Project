package com.ex.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaleZonePolicyTest {

    @Test
    void 남은기간별할인율과판매중지를판정한다() {
        LocalDate today = LocalDate.now();

        assertEquals(0, SaleZonePolicy.discountRate(today.plusDays(46), 100));
        assertEquals(10, SaleZonePolicy.discountRate(today.plusDays(45), 50));
        assertEquals(0, SaleZonePolicy.discountRate(today.plusDays(45), 49));
        assertEquals(20, SaleZonePolicy.discountRate(today.plusDays(29), 1));
        assertEquals(30, SaleZonePolicy.discountRate(today.plusDays(14), 1));
        assertEquals(40, SaleZonePolicy.discountRate(today.plusDays(7), 1));

        assertTrue(SaleZonePolicy.isSaleZone(today.plusDays(4), 1));
        assertTrue(SaleZonePolicy.isSaleZone(today.plusDays(45), 50));
        assertFalse(SaleZonePolicy.isSaleZone(today.plusDays(45), 49));
        assertFalse(SaleZonePolicy.isSaleZone(today.plusDays(46), 100));
        assertFalse(SaleZonePolicy.isSellable(today.plusDays(3)));
    }
}
