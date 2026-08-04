package com.ex.dto;

import com.ex.entity.OrderStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminOrderStatusRequest(
        @NotNull(message = "변경할 주문 상태를 선택해주세요.")
        OrderStatus status,

        @Size(max = 40, message = "배송사명은 40자 이하로 입력해주세요.")
        String carrier,

        @Size(max = 80, message = "송장번호는 80자 이하로 입력해주세요.")
        String trackingNumber
) {
}
