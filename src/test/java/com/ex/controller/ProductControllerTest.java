package com.ex.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 전체상품과Lot재고를조회한다() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8))
                .andExpect(jsonPath("$[0].name").value("한우 마스터 700"))
                .andExpect(jsonPath("$[0].lot").value("FF-HB-260721"))
                .andExpect(jsonPath("$[0].stock").value(84));
    }

    @Test
    void 축종별로상품을필터링한다() throws Exception {
        mockMvc.perform(get("/api/products").param("animalType", "PIG"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].animal").value("돼지"));
    }

    @Test
    void SaleZone은남은기간별할인Lot만조회한다() throws Exception {
        mockMvc.perform(get("/api/products/sale-zone"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].lots[0].discountRate").value(10))
                .andExpect(jsonPath("$[1].lots[0].discountRate").value(20))
                .andExpect(jsonPath("$[2].lots[0].discountRate").value(30))
                .andExpect(jsonPath("$[3].lots[0].discountRate").value(40))
                .andExpect(jsonPath("$[0].lots[0].saleZone").value(true));
    }
}
