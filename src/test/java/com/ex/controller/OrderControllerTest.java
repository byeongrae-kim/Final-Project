package com.ex.controller;

import com.ex.entity.Product;
import com.ex.repository.ProductRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void 비회원주문을거부하고회원주문과취소를처리한다() throws Exception {
        Product product = productRepository
                .findAllByActiveTrueOrderByIdAsc()
                .getFirst();

        int initialStock = product.getLots().stream()
                .mapToInt(lot -> lot.getQuantity())
                .sum();

        String orderBody = """
                {
                  "customerName": "김농부",
                  "phone": "010-4270-5271",
                  "address": "충남 천안시 서북구 농장로 24",
                  "detailAddress": "제2축사",
                  "unloadingLocation": "사료창고 앞",
                  "deliveryRequest": "도착 전 연락",
                  "paymentMethod": "CARD",
                  "items": [{"productId": %d, "quantity": 2}]
                }
                """.formatted(product.getId());

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."));

        MockHttpSession session = signupAndLogin();

        mockMvc.perform(get("/api/payments/config").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portOneEnabled").value(false))
                .andExpect(jsonPath("$.cardEnabled").value(false))
                .andExpect(jsonPath("$.kakaoEnabled").value(false))
                .andExpect(jsonPath("$.virtualAccountEnabled").value(false));

        String response = mockMvc.perform(post("/api/orders")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PAYMENT_PENDING"))
                .andExpect(jsonPath("$.paymentStatus").value("READY"))
                .andExpect(jsonPath("$.paymentToken").isNotEmpty())
                .andExpect(jsonPath("$.discountAmount").value(0))
                .andExpect(jsonPath("$.orderNumber").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertEquals(
                initialStock - 2,
                product.getLots().stream()
                        .mapToInt(lot -> lot.getQuantity())
                        .sum()
        );

        JsonNode json = objectMapper.readTree(response);
        String orderNumber = json.get("orderNumber").asText();

        mockMvc.perform(patch(
                        "/api/orders/{orderNumber}/cancel",
                        orderNumber
                ).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertEquals(
                initialStock,
                product.getLots().stream()
                        .mapToInt(lot -> lot.getQuantity())
                        .sum()
        );
    }

    private MockHttpSession signupAndLogin() throws Exception {
        mockMvc.perform(post("/api/members/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "orderfarm",
                                  "email": "order@example.com",
                                  "password": "Feedflow!123",
                                  "name": "김농부",
                                  "farmName": "행복농장",
                                  "phone": "010-4270-5271",
                                  "businessNumber": "123-45-67890",
                                  "homeAddress": {
                                    "addressType": "HOME",
                                    "recipientName": "김농부",
                                    "phone": "010-4270-5271",
                                    "baseAddress": "충남 천안시",
                                    "detailAddress": "101호",
                                    "defaultAddress": true
                                  },
                                  "farmAddress": {
                                    "addressType": "FARM",
                                    "recipientName": "김농부",
                                    "phone": "010-4270-5271",
                                    "baseAddress": "충남 천안시 농장로 24",
                                    "unloadingLocation": "제2축사 앞",
                                    "defaultAddress": false
                                  }
                                }
                                """))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(post("/api/members/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "orderfarm",
                                  "password": "Feedflow!123"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
