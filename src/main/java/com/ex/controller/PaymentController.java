package com.ex.controller;

import com.ex.dto.OrderResponse;
import com.ex.dto.PaymentConfigResponse;
import com.ex.dto.PortOnePaymentCompleteRequest;
import com.ex.service.PaymentService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * 브라우저에는 고객사 식별코드와 채널 키만 전달합니다.
     * REST API Key/Secret은 절대 응답에 포함하지 않습니다.
     */
    @GetMapping("/config")
    public PaymentConfigResponse config(HttpSession session) {
        return paymentService.paymentConfig(requireMemberId(session));
    }

    /** PC 결제창 콜백 결과를 서버가 포트원 REST API로 다시 조회하고 검증합니다. */
    @PostMapping("/portone/complete")
    public OrderResponse completePortOne(
            @Valid @RequestBody PortOnePaymentCompleteRequest request,
            HttpSession session
    ) {
        return paymentService.completePortOne(
                request.impUid(),
                request.merchantUid(),
                request.paymentToken(),
                requireMemberId(session)
        );
    }

    /** 결제 승인 후 DB 반영이 실패한 본인 주문을 PortOne 주문번호로 재검증합니다. */
    @PostMapping("/portone/reconcile/{orderNumber}")
    public OrderResponse reconcilePortOne(
            @PathVariable(name = "orderNumber") String orderNumber,
            HttpSession session
    ) {
        return paymentService.reconcilePortOne(
                orderNumber,
                requireMemberId(session)
        );
    }

    /** 결제창 취소 등으로 거래번호가 발급되지 않은 결제대기 주문만 안전하게 해제합니다. */
    @PostMapping("/portone/fail")
    public void failPortOne(
            @RequestParam(name = "orderNumber") String orderNumber,
            @RequestParam(name = "token") String token,
            HttpSession session
    ) {
        paymentService.failPendingPayment(
                orderNumber,
                token,
                requireMemberId(session)
        );
    }

    /**
     * 포트원 콘솔의 V1 웹훅 주소를 /api/payments/portone/webhook 으로 설정합니다.
     * 수신값을 그대로 믿지 않고 포트원 결제 단건조회 API로 다시 검증합니다.
     */
    @PostMapping("/portone/webhook")
    public Map<String, String> portOneWebhook(@RequestBody JsonNode payload) {
        paymentService.handlePortOneWebhook(payload);
        return Map.of("result", "ok");
    }

    private Long requireMemberId(HttpSession session) {
        Long memberId = (Long) session.getAttribute("memberId");
        if (memberId == null) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }
        return memberId;
    }
}
