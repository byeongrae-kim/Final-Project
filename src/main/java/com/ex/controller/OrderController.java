package com.ex.controller;

import com.ex.dto.CreateOrderRequest;
import com.ex.dto.OrderResponse;
import com.ex.service.OrderService;
import com.ex.service.PaymentService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final PaymentService paymentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createOrder(@Valid @RequestBody CreateOrderRequest request, HttpSession session) {
        Long memberId = requireMemberId(session);
        return orderService.createOrder(request, memberId);
    }

    @PatchMapping("/{orderNumber}/cancel")
    public OrderResponse cancelOrder(
            @PathVariable(name = "orderNumber") String orderNumber,
            HttpSession session
    ) {
        return paymentService.cancelOrder(orderNumber, requireMemberId(session));
    }

    @GetMapping("/mine")
    public List<OrderResponse> findMyOrders(HttpSession session) {
        return orderService.findMemberOrders(requireMemberId(session));
    }

    @GetMapping("/{orderNumber}")
    public OrderResponse findMyOrder(
            @PathVariable(name = "orderNumber") String orderNumber,
            HttpSession session
    ) {
        return orderService.findMemberOrder(orderNumber, requireMemberId(session));
    }

    private Long requireMemberId(HttpSession session) {
        Long memberId = (Long) session.getAttribute("memberId");
        if (memberId == null) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }
        return memberId;
    }
}
