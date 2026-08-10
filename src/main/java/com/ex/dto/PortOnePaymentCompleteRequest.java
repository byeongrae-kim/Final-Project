package com.ex.dto;

import jakarta.validation.constraints.NotBlank;

public record PortOnePaymentCompleteRequest(
        @NotBlank(message = "포트원 결제번호가 필요합니다.")
        String impUid,

        @NotBlank(message = "주문번호가 필요합니다.")
        String merchantUid,

        @NotBlank(message = "결제 확인 토큰이 필요합니다.")
        String paymentToken
) {
}
