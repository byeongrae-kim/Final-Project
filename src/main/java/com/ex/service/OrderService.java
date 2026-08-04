package com.ex.service;

import com.ex.dto.CreateOrderRequest;
import com.ex.dto.OrderDetailResponse;
import com.ex.dto.OrderResponse;
import com.ex.entity.*;
import com.ex.repository.ProductLotRepository;
import com.ex.repository.ProductRepository;
import com.ex.repository.PurchaseOrderRepository;
import com.ex.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final int FREE_DELIVERY_THRESHOLD = 150_000;
    private static final int DELIVERY_FEE = 5_000;

    private final ProductRepository productRepository;
    private final ProductLotRepository productLotRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request, Long memberId) {
        if (memberId == null) {
            throw new IllegalArgumentException("로그인 후 주문할 수 있습니다.");
        }

        Member member = memberRepository.findById(memberId)
                .filter(Member::isActive)
                .orElseThrow(() -> new IllegalArgumentException("로그인 회원 정보를 찾을 수 없습니다."));

        PurchaseOrder order = PurchaseOrder.builder()
                .orderNumber(createOrderNumber())
                .customerName(request.customerName())
                .phone(request.phone())
                .postalCode(request.postalCode())
                .address(request.address())
                .detailAddress(request.detailAddress())
                .unloadingLocation(request.unloadingLocation())
                .deliveryRequest(request.deliveryRequest())
                .paymentMethod(request.paymentMethod())
                // 외부 결제 승인 전에는 결제대기 상태로 보관합니다.
                .status(OrderStatus.PAYMENT_PENDING)
                .paymentStatus(PaymentStatus.READY)
                .paymentCallbackToken(UUID.randomUUID().toString())
                .member(member)
                .build();

        int productAmount = 0;

        for (CreateOrderRequest.OrderLineRequest line : request.items()) {
            Product product = productRepository.findById(line.productId())
                    .filter(Product::isActive)
                    .orElseThrow(() -> new IllegalArgumentException("주문할 수 없는 상품입니다: " + line.productId()));

            int lineAmount = Math.multiplyExact(product.getPrice(), line.quantity());
            productAmount = Math.addExact(productAmount, lineAmount);

            OrderItem orderItem = OrderItem.builder()
                    .product(product)
                    .productName(product.getName())
                    .quantity(line.quantity())
                    .unitPrice(product.getPrice())
                    .lineAmount(lineAmount)
                    .build();
            decreaseStock(product.getId(), line.quantity(), orderItem);
            order.addItem(orderItem);
        }

        int deliveryFee = productAmount >= FREE_DELIVERY_THRESHOLD ? 0 : DELIVERY_FEE;
        int totalAmount = productAmount + deliveryFee;

        order.setProductAmount(productAmount);
        order.setDeliveryFee(deliveryFee);
        order.setDiscountAmount(0);
        order.setTotalAmount(totalAmount);

        PurchaseOrder savedOrder = purchaseOrderRepository.save(order);

        return OrderResponse.from(savedOrder);
    }

    @Transactional
    public OrderResponse cancelOrder(String orderNumber, Long memberId) {
        if (memberId == null) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }
        PurchaseOrder order = purchaseOrderRepository.findByOrderNumberForUpdate(orderNumber)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));
        if (order.getMember() == null || !order.getMember().getId().equals(memberId)) {
            throw new IllegalArgumentException("본인의 주문만 취소할 수 있습니다.");
        }
        order.cancel();
        lockAllocatedLots(order);
        order.getItems().stream()
                .flatMap(item -> item.getLotAllocations().stream())
                .forEach(allocation -> allocation.getProductLot().increase(allocation.getQuantity()));
        return toResponse(order);
    }

    private void lockAllocatedLots(PurchaseOrder order) {
        List<Long> lotIds = order.getItems().stream()
                .flatMap(item -> item.getLotAllocations().stream())
                .map(allocation -> allocation.getProductLot().getId())
                .distinct()
                .toList();
        if (!lotIds.isEmpty()) {
            productLotRepository.findAllByIdForUpdate(lotIds);
        }
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> findMemberOrders(Long memberId) {
        if (memberId == null) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }
        return purchaseOrderRepository
                .findByMember_IdAndCreatedAtAfterOrderByCreatedAtDesc(memberId, LocalDateTime.now().minusMonths(6))
                .stream()
                .map(OrderResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse findMemberOrder(String orderNumber, Long memberId) {
        if (memberId == null) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }
        PurchaseOrder order = purchaseOrderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));

        if (order.getMember() == null || !order.getMember().getId().equals(memberId)) {
            throw new IllegalArgumentException("본인의 주문만 확인할 수 있습니다.");
        }

        return OrderDetailResponse.from(order);
    }

    private void decreaseStock(Long productId, int requestedQuantity, OrderItem orderItem) {
        List<ProductLot> lots = productLotRepository
                .findByProductIdAndQuantityGreaterThanAndExpirationDateGreaterThanEqualOrderByExpirationDateAsc(
                        productId,
                        0,
                        LocalDate.now()
                );

        int totalStock = lots.stream().mapToInt(ProductLot::getQuantity).sum();
        if (totalStock < requestedQuantity) {
            throw new IllegalArgumentException("상품 재고가 부족합니다.");
        }

        int remaining = requestedQuantity;
        for (ProductLot lot : lots) {
            if (remaining == 0) {
                break;
            }
            int deduction = Math.min(lot.getQuantity(), remaining);
            lot.decrease(deduction);
            orderItem.addLotAllocation(OrderLotAllocation.builder()
                    .productLot(lot)
                    .quantity(deduction)
                    .build());
            remaining -= deduction;
        }
    }

    private String createOrderNumber() {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        return "FF-" + date + "-" + random;
    }

    private OrderResponse toResponse(PurchaseOrder order) {
        return OrderResponse.from(order);
    }
}
