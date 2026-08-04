package com.ex.service;

import com.ex.config.PaymentProperties;
import com.ex.dto.OrderResponse;
import com.ex.entity.Member;
import com.ex.entity.OrderStatus;
import com.ex.entity.PaymentMethod;
import com.ex.entity.PaymentProvider;
import com.ex.entity.PaymentStatus;
import com.ex.entity.PurchaseOrder;
import com.ex.repository.PurchaseOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PaymentServiceTest {

    private static final String ORDER_NUMBER = "FF-20260803-CA7434";
    private static final String IMP_UID = "imp_test_123456";
    private static final String CALLBACK_TOKEN = "payment-callback-token";
    private static final String ACCESS_TOKEN = "portone-access-token";

    private MockRestServiceServer server;
    private PurchaseOrderRepository purchaseOrderRepository;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        purchaseOrderRepository = mock(PurchaseOrderRepository.class);

        PaymentProperties properties = new PaymentProperties();
        properties.getPortone().setCustomerCode("imp00000000");
        properties.getPortone().setApiKey("test-api-key");
        properties.getPortone().setApiSecret("test-api-secret");

        paymentService = new PaymentService(
                builder,
                properties,
                purchaseOrderRepository
        );
    }

    @Test
    void fallsBackToMerchantUidWhenImpUidLookupReturnsNotFound() {
        PurchaseOrder order = PurchaseOrder.builder()
                .orderNumber(ORDER_NUMBER)
                .customerName("테스트 농장")
                .phone("010-0000-0000")
                .address("테스트 주소")
                .paymentMethod(PaymentMethod.KAKAO_PAY)
                .paymentStatus(PaymentStatus.READY)
                .paymentCallbackToken(CALLBACK_TOKEN)
                .status(OrderStatus.PAYMENT_PENDING)
                .productAmount(52_800)
                .deliveryFee(5_000)
                .discountAmount(0)
                .totalAmount(57_800)
                .build();
        when(purchaseOrderRepository.findByOrderNumber(ORDER_NUMBER))
                .thenReturn(Optional.of(order));

        server.expect(once(), requestTo("https://api.iamport.kr/users/getToken"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        """
                        {
                          "code": 0,
                          "response": {
                            "access_token": "portone-access-token"
                          }
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));
        server.expect(once(), requestTo(
                        "https://api.iamport.kr/payments/" + IMP_UID
                ))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", ACCESS_TOKEN))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "code": -1,
                                  "message": "존재하지 않는 결제정보입니다.",
                                  "response": null
                                }
                                """));
        server.expect(once(), requestTo(
                        "https://api.iamport.kr/payments/find/" + ORDER_NUMBER
                ))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", ACCESS_TOKEN))
                .andRespond(withSuccess(
                        """
                        {
                          "code": 0,
                          "response": {
                            "imp_uid": "imp_test_123456",
                            "merchant_uid": "FF-20260803-CA7434",
                            "amount": 57800,
                            "status": "paid",
                            "receipt_url": "https://example.test/receipt"
                          }
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        OrderResponse result = paymentService.completePortOneByCallback(
                IMP_UID,
                ORDER_NUMBER,
                CALLBACK_TOKEN
        );

        assertThat(result.status()).isEqualTo(OrderStatus.PAID);
        assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.DONE);
        assertThat(result.paymentProvider()).isEqualTo(PaymentProvider.PORTONE);
        assertThat(result.totalAmount()).isEqualTo(57_800);
        assertThat(result.receiptUrl()).isEqualTo("https://example.test/receipt");
        assertThat(order.getProviderTransactionId()).isEqualTo(IMP_UID);
        server.verify();
    }

    @Test
    void cancelsVirtualAccountBeforeDepositByDeletingVbank() {
        Member member = Member.builder()
                .id(7L)
                .username("vbank-member")
                .email("vbank@example.com")
                .password("encoded")
                .name("가상계좌 회원")
                .phone("010-0000-0000")
                .build();
        PurchaseOrder order = PurchaseOrder.builder()
                .orderNumber("FF-VBANK-001")
                .member(member)
                .customerName("가상계좌 회원")
                .phone("010-0000-0000")
                .address("테스트 주소")
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .paymentProvider(PaymentProvider.PORTONE)
                .paymentStatus(PaymentStatus.WAITING_FOR_DEPOSIT)
                .providerTransactionId("imp_vbank_123456")
                .status(OrderStatus.PAYMENT_PENDING)
                .productAmount(10_000)
                .deliveryFee(5_000)
                .discountAmount(0)
                .totalAmount(15_000)
                .build();
        when(purchaseOrderRepository.findByOrderNumber("FF-VBANK-001"))
                .thenReturn(Optional.of(order));

        server.expect(once(), requestTo("https://api.iamport.kr/users/getToken"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        """
                        {
                          "code": 0,
                          "response": {
                            "access_token": "portone-access-token"
                          }
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));
        server.expect(once(), requestTo(
                        "https://api.iamport.kr/vbanks/imp_vbank_123456"
                ))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("Authorization", ACCESS_TOKEN))
                .andRespond(withSuccess(
                        """
                        {
                          "code": 0,
                          "response": {
                            "imp_uid": "imp_vbank_123456"
                          }
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        OrderResponse result = paymentService.cancelOrder("FF-VBANK-001", 7L);

        assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.CANCELLED);
        server.verify();
    }
}
