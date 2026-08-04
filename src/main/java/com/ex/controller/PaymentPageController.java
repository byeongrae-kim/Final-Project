package com.ex.controller;

import com.ex.dto.OrderResponse;
import com.ex.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
@RequiredArgsConstructor
public class PaymentPageController {

    private final PaymentService paymentService;

    /** 모바일 결제 완료 후 포트원이 다시 보내는 리다이렉트 주소입니다. */
    @GetMapping("/payments/portone/redirect")
    public String portOneRedirect(
            @RequestParam(name = "imp_uid", required = false) String impUid,
            @RequestParam(name = "merchant_uid") String merchantUid,
            @RequestParam String token,
            @RequestParam(name = "error_code", required = false) String errorCode,
            @RequestParam(name = "error_msg", required = false) String errorMessage
    ) {
        /*
         * 최신 PortOne V1 SDK의 success/error_code 값만으로 결제 결과를
         * 확정하지 않습니다. imp_uid가 있으면 PortOne REST API로
         * 실제 승인 상태와 금액을 조회한 결과를 사용합니다.
         */
        if (impUid == null || impUid.isBlank()) {
            paymentService.failPendingPaymentByCallback(merchantUid, token);
            String message = errorMessage == null || errorMessage.isBlank()
                    ? "포트원 결제번호가 전달되지 않았습니다."
                    : errorMessage;
            return resultRedirect("fail", merchantUid, message);
        }

        try {
            OrderResponse order = paymentService.completePortOneByCallback(
                    impUid,
                    merchantUid,
                    token
            );
            String paymentStatus = order.paymentStatus().name();
            String result = paymentStatus.equals("WAITING_FOR_DEPOSIT")
                    ? "waiting"
                    : paymentStatus.equals("DONE")
                            ? "success"
                            : "fail";
            String message = result.equals("fail")
                    ? "결제 승인이 완료되지 않았습니다. 주문 내역을 확인해주세요."
                    : null;
            return resultRedirect(result, order.orderNumber(), message);
        } catch (IllegalArgumentException exception) {
            return resultRedirect("fail", merchantUid, exception.getMessage());
        }
    }

    private String resultRedirect(String payment, String orderNumber, String message) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/")
                .queryParam("payment", payment)
                .queryParam("orderNumber", orderNumber);
        if (message != null && !message.isBlank()) {
            builder.queryParam("message", message);
        }
        return "redirect:" + builder.build().encode().toUriString();
    }
}
