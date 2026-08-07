package com.ex.controller;

import com.ex.entity.OrderStatus;
import com.ex.entity.PaymentMethod;
import com.ex.entity.PaymentProvider;
import com.ex.entity.PaymentStatus;
import com.ex.entity.Product;
import com.ex.entity.ProductLot;
import com.ex.entity.PurchaseOrder;
import com.ex.repository.ProductLotRepository;
import com.ex.repository.ProductRepository;
import com.ex.repository.PurchaseOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "feedflow.admin.username=admin",
        "feedflow.admin.password=Admin!1234"
})
@Transactional
class AdminOperationsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductLotRepository productLotRepository;

    @Test
    void 관리자가주문상태와송장번호를순서대로변경한다() throws Exception {
        String orderNumber = "FF-ADMIN-TRACE-001";
        purchaseOrderRepository.saveAndFlush(PurchaseOrder.builder()
                .orderNumber(orderNumber)
                .customerName("관리자 주문 테스트")
                .phone("010-1111-2222")
                .address("충남 테스트시 농장로 1")
                .paymentMethod(PaymentMethod.CARD)
                .paymentProvider(PaymentProvider.PORTONE)
                .paymentStatus(PaymentStatus.DONE)
                .status(OrderStatus.PAID)
                .productAmount(50_000)
                .deliveryFee(5_000)
                .discountAmount(0)
                .totalAmount(55_000)
                .build());

        mockMvc.perform(get("/api/admin/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.totalRevenue", greaterThanOrEqualTo(55_000)))
                .andExpect(jsonPath("$.dailySales.length()").value(7));

        mockMvc.perform(get("/api/admin/orders"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch(
                        "/api/admin/orders/{orderNumber}/status",
                        orderNumber
                )
                        .header(HttpHeaders.AUTHORIZATION, basicAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PREPARING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PREPARING"))
                .andExpect(jsonPath("$.preparingAt").isNotEmpty());

        mockMvc.perform(patch(
                        "/api/admin/orders/{orderNumber}/status",
                        orderNumber
                )
                        .header(HttpHeaders.AUTHORIZATION, basicAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SHIPPING\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch(
                        "/api/admin/orders/{orderNumber}/status",
                        orderNumber
                )
                        .header(HttpHeaders.AUTHORIZATION, basicAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "SHIPPING",
                                  "carrier": "CJ대한통운",
                                  "trackingNumber": "1234567890"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPING"))
                .andExpect(jsonPath("$.trackingCarrier").value("CJ대한통운"))
                .andExpect(jsonPath("$.trackingNumber").value("1234567890"))
                .andExpect(jsonPath("$.shippedAt").isNotEmpty());

        mockMvc.perform(patch(
                        "/api/admin/orders/{orderNumber}/status",
                        orderNumber
                )
                        .header(HttpHeaders.AUTHORIZATION, basicAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DELIVERED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.deliveredAt").isNotEmpty());

        mockMvc.perform(get("/api/admin/activities")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].actionType", hasItem("ORDER_STATUS_CHANGED")))
                .andExpect(jsonPath("$[*].targetIdentifier", hasItem(orderNumber)));
    }

    @Test
    void 재고부족과유통기한임박Lot을경고한다() throws Exception {
        Product product = productRepository.findAllByActiveTrueOrderByIdAsc().getFirst();
        String lotNumber = "ALERT-LOT-" + System.nanoTime();
        productLotRepository.saveAndFlush(ProductLot.builder()
                .product(product)
                .lotNumber(lotNumber)
                .manufacturedDate(LocalDate.now().minusMonths(6))
                .expirationDate(LocalDate.now().plusDays(5))
                .quantity(3)
                .build());

        mockMvc.perform(get("/api/admin/inventory/alerts")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].lotNumber", hasItem(lotNumber)))
                .andExpect(jsonPath("$[?(@.lotNumber == '%s')].expiringSoon".formatted(lotNumber), hasItem(true)))
                .andExpect(jsonPath("$[?(@.lotNumber == '%s')].lowStock".formatted(lotNumber), hasItem(true)));
    }

    private String basicAuth() {
        return "Basic " + Base64.getEncoder().encodeToString(
                "admin:Admin!1234".getBytes(StandardCharsets.UTF_8)
        );
    }
}
