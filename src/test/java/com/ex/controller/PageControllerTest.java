package com.ex.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 구매화면을Thymeleaf로렌더링한다() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("products"))
                .andExpect(content().string(containsString("FEED FLOW")))
                .andExpect(content().string(not(containsString("H2 연동됨"))))
                .andExpect(content().string(containsString("아이디")))
                .andExpect(content().string(containsString("중복확인")))
                .andExpect(content().string(containsString("/images/feedflow-logo.png")))
                .andExpect(content().string(containsString("/images/feedflow-farm-hero.png")))
                .andExpect(content().string(containsString("/images/feed-bag-warehouse.png")))
                .andExpect(content().string(containsString("/js/feedflow.js")))
                .andExpect(content().string(not(containsString("전문가에게 상담받기"))))
                .andExpect(content().string(containsString("https://cdn.iamport.kr/v1/iamport.js")));

        mockMvc.perform(get("/images/feedflow-logo.png"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));
        mockMvc.perform(get("/images/feed-bag-warehouse.png"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));
        mockMvc.perform(get("/images/feedflow-farm-hero.png"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));
    }

    @Test
    void SaleZone전용화면과할인기준을렌더링한다() throws Exception {
        mockMvc.perform(get("/sale-zone"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("products", "saleZonePage"))
                .andExpect(content().string(containsString("SALE ZONE")))
                .andExpect(content().string(containsString("30~45일")))
                .andExpect(content().string(containsString("판매 중지")))
                .andExpect(content().string(not(containsString("전문가에게 상담받기"))));
    }

    @Test
    void 관리자화면을Thymeleaf로렌더링한다() throws Exception {
        MockHttpSession adminSession = new MockHttpSession();
        adminSession.setAttribute("isAdmin", true);

        mockMvc.perform(get("/admin").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(view().name("admin"))
                .andExpect(content().string(containsString("상품 운영 대시보드")))
                .andExpect(content().string(containsString("/images/feedflow-logo.png")))
                .andExpect(content().string(containsString("/js/admin.js")));
    }
}
