package com.ex.controller;

import com.ex.dto.OrderResponse;
import com.ex.entity.OrderStatus;
import com.ex.entity.PaymentMethod;
import com.ex.entity.PaymentProvider;
import com.ex.entity.PaymentStatus;
import com.ex.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentPageControllerTest {

    private PaymentService paymentService;
    private PaymentPageController controller;

    @BeforeEach
    void setUp() {
        paymentService = mock(PaymentService.class);
        controller = new PaymentPageController(paymentService);
    }

    @Test
    void errorCode가있어도결제번호가있으면서버에서실제상태를검증한다() {
        OrderResponse paidOrder = order(PaymentStatus.DONE, OrderStatus.PAID);
        when(paymentService.completePortOneByCallback(
                "imp-test-payment",
                "FF-TEST-001",
                "callback-token"
        )).thenReturn(paidOrder);

        String redirect = controller.portOneRedirect(
                "imp-test-payment",
                "FF-TEST-001",
                "callback-token",
                "TEST_NOTICE",
                "테스트 결제 안내"
        );

        assertEquals(
                "redirect:/?payment=success&orderNumber=FF-TEST-001",
                redirect
        );
        verify(paymentService).completePortOneByCallback(
                "imp-test-payment",
                "FF-TEST-001",
                "callback-token"
        );
        verify(paymentService, never()).failPendingPaymentByCallback(
                "FF-TEST-001",
                "callback-token"
        );
    }

    @Test
    void 결제번호가없을때만대기주문을실패처리한다() {
        String redirect = controller.portOneRedirect(
                null,
                "FF-TEST-001",
                "callback-token",
                "PAYMENT_CANCELLED",
                "결제가 취소되었습니다."
        );

        assertEquals(
                "redirect:/?payment=fail&orderNumber=FF-TEST-001"
                        + "&message=%EA%B2%B0%EC%A0%9C%EA%B0%80%20%EC%B7%A8%EC%86%8C%EB%90%98%EC%97%88%EC%8A%B5%EB%8B%88%EB%8B%A4.",
                redirect
        );
        verify(paymentService).failPendingPaymentByCallback(
                "FF-TEST-001",
                "callback-token"
        );
        verify(paymentService, never()).completePortOneByCallback(
                "imp-test-payment",
                "FF-TEST-001",
                "callback-token"
        );
    }

    private OrderResponse order(PaymentStatus paymentStatus, OrderStatus orderStatus) {
        return new OrderResponse(
                1L,
                "FF-TEST-001",
                orderStatus,
                1000,
                0,
                0,
                1000,
                PaymentMethod.KAKAO_PAY,
                PaymentProvider.PORTONE,
                paymentStatus,
                "callback-token",
                null,
                null,
                null,
                null,
                LocalDateTime.now(),
                List.of()
        );
    }
}
