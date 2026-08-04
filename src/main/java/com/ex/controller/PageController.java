package com.ex.controller;

import com.ex.service.MemberService;
import com.ex.service.ProductCatalogService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class PageController {

    private final ProductCatalogService productCatalogService;
    private final MemberService memberService;

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("siteName", "FEED FLOW");
        model.addAttribute(
                "products",
                productCatalogService.findProducts(null, null)
        );

        return "index";
    }

    @GetMapping("/admin")
    public String admin(HttpSession session, Model model) {
        boolean isAdmin =
                Boolean.TRUE.equals(session.getAttribute("isAdmin"));

        if (!isAdmin) {
            return "redirect:/";
        }

        model.addAttribute(
                "siteName",
                "FEED FLOW 판매자센터"
        );

        return "admin";
    }

    @GetMapping("/mypage")
    public String myPage(HttpSession session, Model model) {
        boolean isAdmin =
                Boolean.TRUE.equals(session.getAttribute("isAdmin"));

        /*
         * 일반 로그인 화면에서 관리자 계정으로 로그인하면
         * 기존 JavaScript가 /mypage로 이동합니다.
         * 이때 관리자는 /admin으로 다시 이동시킵니다.
         */
        if (isAdmin) {
            return "redirect:/admin";
        }

        Long memberId =
                (Long) session.getAttribute("memberId");

        if (memberId == null) {
            return "redirect:/";
        }

        model.addAttribute(
                "siteName",
                "FEED FLOW 마이페이지"
        );

        model.addAttribute(
                "member",
                memberService.findById(memberId)
        );

        return "mypage";
    }
}