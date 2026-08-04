package com.ex.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;

@Entity
@Table(name = "purchase_order", indexes = {
        @Index(name = "idx_order_number", columnList = "order_number", unique = true)
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PurchaseOrder extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "purchase_order_id")
    private Long id;

    @Column(name = "order_number", nullable = false, unique = true, length = 40)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @Column(name = "customer_name", nullable = false, length = 40)
    private String customerName;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false, length = 200)
    private String address;

    @Column(name = "postal_code", length = 10)
    private String postalCode;

    @Column(name = "detail_address", length = 200)
    private String detailAddress;

    @Column(name = "unloading_location", length = 200)
    private String unloadingLocation;

    @Column(name = "delivery_request", length = 300)
    private String deliveryRequest;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 30)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_provider", length = 20)
    private PaymentProvider paymentProvider;

    @Enumerated(EnumType.STRING)
    // 기존 H2 주문 데이터가 있는 프로젝트도 ddl-auto=update로 마이그레이션되도록 nullable로 둡니다.
    @Column(name = "payment_status", length = 30)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.READY;

    @Column(name = "provider_transaction_id", length = 200)
    private String providerTransactionId;

    @Column(name = "payment_callback_token", length = 36)
    private String paymentCallbackToken;

    @Column(name = "payment_webhook_secret", length = 200)
    private String paymentWebhookSecret;

    @Column(name = "payment_receipt_url", length = 500)
    private String paymentReceiptUrl;

    @Column(name = "virtual_account_bank", length = 40)
    private String virtualAccountBank;

    @Column(name = "virtual_account_number", length = 80)
    private String virtualAccountNumber;

    @Column(name = "virtual_account_due_date", length = 40)
    private String virtualAccountDueDate;

    @Column(name = "payment_approved_at")
    private LocalDateTime paymentApprovedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Column(name = "product_amount", nullable = false)
    private int productAmount;

    @Column(name = "delivery_fee", nullable = false)
    private int deliveryFee;

    @Column(name = "discount_amount", nullable = false)
    private int discountAmount;

    @Column(name = "total_amount", nullable = false)
    private int totalAmount;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    public void addItem(OrderItem item) {
        items.add(item);
        item.assignOrder(this);
    }

    public void preparePayment(PaymentProvider provider, String transactionId) {
        this.paymentProvider = provider;
        this.providerTransactionId = transactionId;
        this.paymentStatus = PaymentStatus.READY;
    }

    public void completePayment(String transactionId, String receiptUrl) {
        this.providerTransactionId = transactionId;
        this.paymentReceiptUrl = receiptUrl;
        this.paymentStatus = PaymentStatus.DONE;
        this.paymentApprovedAt = LocalDateTime.now();
        this.status = OrderStatus.PAID;
    }

    public void waitForDeposit(
            String transactionId,
            String webhookSecret,
            String bank,
            String accountNumber,
            String dueDate
    ) {
        this.providerTransactionId = transactionId;
        this.paymentWebhookSecret = webhookSecret;
        this.virtualAccountBank = bank;
        this.virtualAccountNumber = accountNumber;
        this.virtualAccountDueDate = dueDate;
        this.paymentStatus = PaymentStatus.WAITING_FOR_DEPOSIT;
        this.status = OrderStatus.PAYMENT_PENDING;
    }

    public void failPayment() {
        this.paymentStatus = PaymentStatus.FAILED;
    }

    public void markPaymentCancelled() {
        this.paymentStatus = PaymentStatus.CANCELLED;
    }

    public void cancel() {
        if (status == OrderStatus.SHIPPING || status == OrderStatus.DELIVERED) {
            throw new IllegalArgumentException("배송이 시작된 주문은 취소할 수 없습니다.");
        }
        if (status == OrderStatus.CANCELLED) {
            throw new IllegalArgumentException("이미 취소된 주문입니다.");
        }
        status = OrderStatus.CANCELLED;
    }
}
