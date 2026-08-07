package com.ex.dto;

import java.time.LocalDate;
import java.util.List;

public record AdminDashboardResponse(
        long totalRevenue,
        long todayRevenue,
        long totalOrders,
        long todayOrders,
        long paymentCompletedOrders,
        long shippingOrders,
        long cancelledOrders,
        long totalProducts,
        long soldOutProducts,
        long lowStockLots,
        long expiringLots,
        List<DailySales> dailySales
) {
    public record DailySales(
            LocalDate date,
            long orderCount,
            long revenue
    ) {
    }
}
