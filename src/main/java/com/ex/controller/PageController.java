package com.ex.controller;

import com.ex.service.MemberService;
import com.ex.service.ProductCatalogService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@RequiredArgsConstructor
public class PageController {

    private final ProductCatalogService productCatalogService;
    private final MemberService memberService;

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("siteName", "FEED FLOW");
        model.addAttribute("saleZonePage", false);
        model.addAttribute(
                "products",
                productCatalogService.findProducts(null, null)
        );

        return "index";
    }

    @GetMapping("/sale-zone")
    public String saleZone(Model model) {
        model.addAttribute("siteName", "FEED FLOW SALE ZONE");
        model.addAttribute("saleZonePage", true);
        model.addAttribute(
                "products",
                productCatalogService.findSaleZoneProducts(null, null)
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

        // 관리자가 회원 마이페이지 주소로 접근하면 판매자센터로 이동합니다.
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

    @GetMapping("/mypage/orders/{orderNumber}")
    public String orderDetail(
            @PathVariable(name = "orderNumber") String orderNumber,
            HttpSession session,
            Model model
    ) {
        if (Boolean.TRUE.equals(session.getAttribute("isAdmin"))) {
            return "redirect:/admin";
        }

        Long memberId = (Long) session.getAttribute("memberId");
        if (memberId == null) {
            return "redirect:/";
        }

        model.addAttribute("siteName", "FEED FLOW 주문 상세");
        model.addAttribute("member", memberService.findById(memberId));
        model.addAttribute("orderNumber", orderNumber);
        return "order-detail";
    }

    @GetMapping("/mypage/orders/{orderNumber}/receipt")
    public String testReceipt(
            @PathVariable(name = "orderNumber") String orderNumber,
            HttpSession session,
            Model model
    ) {
        if (Boolean.TRUE.equals(session.getAttribute("isAdmin"))) {
            return "redirect:/admin";
        }

        Long memberId = (Long) session.getAttribute("memberId");
        if (memberId == null) {
            return "redirect:/";
        }

        model.addAttribute("siteName", "FEED FLOW 테스트 결제 확인서");
        model.addAttribute("orderNumber", orderNumber);
        return "test-receipt";
    }
}
