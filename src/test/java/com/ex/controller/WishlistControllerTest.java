package com.ex.controller;

import com.ex.entity.Product;
import com.ex.repository.ProductRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class WishlistControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void 비회원등록은거부하고회원관심상품만저장한다() throws Exception {
        Product product = productRepository
                .findAllByActiveTrueOrderByIdAsc()
                .getFirst();

        mockMvc.perform(post("/api/wishlist/{productId}", product.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."));

        MockHttpSession session = signupAndLogin();

        mockMvc.perform(post("/api/wishlist/{productId}", product.getId())
                        .session(session))
                .andExpect(status().isCreated());

        // 같은 상품을 다시 눌러도 중복 행을 만들지 않습니다.
        mockMvc.perform(post("/api/wishlist/{productId}", product.getId())
                        .session(session))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/wishlist").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0]").value(product.getId()));

        mockMvc.perform(delete("/api/wishlist/{productId}", product.getId())
                        .session(session))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/wishlist").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    private MockHttpSession signupAndLogin() throws Exception {
        mockMvc.perform(post("/api/members/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "wishlistfarm",
                                  "email": "wishlist@example.com",
                                  "password": "Feedflow!123",
                                  "name": "관심농부",
                                  "farmName": "관심농장",
                                  "phone": "010-5555-1111",
                                  "businessNumber": "321-54-98765",
                                  "homeAddress": {
                                    "addressType": "HOME",
                                    "recipientName": "관심농부",
                                    "phone": "010-5555-1111",
                                    "baseAddress": "충남 천안시",
                                    "detailAddress": "201호",
                                    "defaultAddress": true
                                  },
                                  "farmAddress": {
                                    "addressType": "FARM",
                                    "recipientName": "관심농부",
                                    "phone": "010-5555-1111",
                                    "baseAddress": "충남 천안시 농장로 55",
                                    "unloadingLocation": "사료창고 앞",
                                    "defaultAddress": false
                                  }
                                }
                                """))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(post("/api/members/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "wishlistfarm",
                                  "password": "Feedflow!123"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
