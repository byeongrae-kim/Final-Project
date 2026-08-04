package com.ex.controller;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 아이디중복검사후회원가입하고아이디로로그인한다() throws Exception {
        String body = signupBody("feedfarm", "farm@example.com");

        mockMvc.perform(get("/api/members/check-username")
                        .param("username", "feedfarm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));

        mockMvc.perform(post("/api/members/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("feedfarm"))
                .andExpect(jsonPath("$.email").value("farm@example.com"))
                .andExpect(jsonPath("$.farmName").value("행복농장"));

        mockMvc.perform(get("/api/members/check-username")
                        .param("username", "feedfarm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));

        MvcResult loginResult = mockMvc.perform(post("/api/members/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "feedfarm",
                                  "password": "Feedflow!123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("김농부"))
                .andReturn();

        MockHttpSession session =
                (MockHttpSession) loginResult.getRequest().getSession(false);

        mockMvc.perform(get("/mypage").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("mypage"))
                .andExpect(model().attributeExists("member"))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("로그아웃")
                ));

        mockMvc.perform(get("/api/members/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("feedfarm"))
                .andExpect(jsonPath("$.farmName").value("행복농장"));

        mockMvc.perform(post("/api/members/logout").session(session))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/members/me"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."));

        mockMvc.perform(post("/api/members/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "feedfarm",
                                  "password": "wrong-password"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("아이디 또는 비밀번호가 일치하지 않습니다."));

        mockMvc.perform(post("/api/members/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("이미 사용 중인 아이디입니다."));
    }

    @Test
    void 잘못된아이디비밀번호이메일형식을거부한다() throws Exception {
        String invalidBody = signupBody("1", "잘못된이메일")
                .replace("Feedflow!123", "1234");

        mockMvc.perform(post("/api/members/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 성명과이메일로아이디를찾고회원정보확인후비밀번호를재설정한다() throws Exception {
        mockMvc.perform(post("/api/members/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody("findfarm", "find@example.com")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/members/find-username")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "김농부",
                                  "email": "find@example.com"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("findfarm"))
                .andExpect(jsonPath("$.message").value("가입한 아이디를 찾았습니다."));

        mockMvc.perform(post("/api/members/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "findfarm",
                                  "email": "find@example.com",
                                  "phone": "01042705271",
                                  "newPassword": "Changed!456"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                        "비밀번호가 변경되었습니다. 새 비밀번호로 로그인해주세요."
                ));

        mockMvc.perform(post("/api/members/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "findfarm",
                                  "password": "Feedflow!123"
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/members/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "findfarm",
                                  "password": "Changed!456"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("findfarm"));
    }

    private String signupBody(String username, String email) {
        return """
                {
                  "username": "%s",
                  "email": "%s",
                  "password": "Feedflow!123",
                  "name": "김농부",
                  "farmName": "행복농장",
                  "phone": "010-4270-5271",
                  "businessNumber": "123-45-67890",
                  "homeAddress": {
                    "addressType": "HOME",
                    "recipientName": "김농부",
                    "phone": "010-4270-5271",
                    "postalCode": "31000",
                    "baseAddress": "충남 천안시",
                    "detailAddress": "101호",
                    "defaultAddress": true
                  },
                  "farmAddress": {
                    "addressType": "FARM",
                    "recipientName": "김농부",
                    "phone": "010-4270-5271",
                    "postalCode": "31000",
                    "baseAddress": "충남 천안시 농장로 24",
                    "unloadingLocation": "제2축사 앞",
                    "defaultAddress": false
                  }
                }
                """.formatted(username, email);
    }
}
