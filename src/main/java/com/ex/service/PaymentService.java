package com.ex.service;

import com.ex.config.PaymentProperties;
import com.ex.dto.OrderResponse;
import com.ex.dto.PaymentConfigResponse;
import com.ex.entity.*;
import com.ex.repository.PurchaseOrderRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
public class PaymentService {

    private static final String PORTONE_TOKEN_URL =
            "https://api.iamport.kr/users/getToken";
    private static final String PORTONE_PAYMENT_URL =
            "https://api.iamport.kr/payments/";
    private static final String PORTONE_PAYMENT_BY_MERCHANT_URL =
            "https://api.iamport.kr/payments/find/";
    private static final String PORTONE_CANCEL_URL =
            "https://api.iamport.kr/payments/cancel";
    private static final String PORTONE_VBANK_URL =
            "https://api.iamport.kr/vbanks/";
    private static final DateTimeFormatter VBANK_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    .withZone(ZoneId.of("Asia/Seoul"));

    private final RestClient restClient;
    private final PaymentProperties properties;
    private final PurchaseOrderRepository purchaseOrderRepository;

    public PaymentService(
            RestClient.Builder restClientBuilder,
            PaymentProperties properties,
            PurchaseOrderRepository purchaseOrderRepository
    ) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
        this.purchaseOrderRepository = purchaseOrderRepository;
    }

    /**
     * 고객사 식별코드와 채널 키는 결제창 호출에 필요한 공개 설정입니다.
     * REST API Key와 Secret은 이 응답에 절대 포함하지 않습니다.
     */
    public PaymentConfigResponse paymentConfig(Long memberId) {
        if (memberId == null) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }

        PaymentProperties.Portone portone = properties.getPortone();
        boolean serverConfigured = StringUtils.hasText(portone.getCustomerCode())
                && StringUtils.hasText(portone.getApiKey())
                && StringUtils.hasText(portone.getApiSecret());

        return new PaymentConfigResponse(
                serverConfigured,
                portone.getCustomerCode(),
                serverConfigured && StringUtils.hasText(portone.getCardChannelKey()),
                portone.getCardChannelKey(),
                serverConfigured && StringUtils.hasText(portone.getKakaoChannelKey()),
                portone.getKakaoChannelKey(),
                serverConfigured && StringUtils.hasText(portone.getVirtualAccountChannelKey()),
                portone.getVirtualAccountChannelKey()
        );
    }

    /** PC 결제창의 콜백을 로그인 회원과 일치하는 주문인지 확인한 뒤 검증합니다. */
    @Transactional
    public OrderResponse completePortOne(
            String impUid,
            String merchantUid,
            String callbackToken,
            Long memberId
    ) {
        PurchaseOrder order = requireMemberOrder(merchantUid, memberId);
        requireCallbackToken(order, callbackToken);
        return verifyAndApplyPortOnePayment(order, impUid);
    }

    /** 모바일 리다이렉트는 주문별 임의 토큰으로 주문을 확인한 뒤 검증합니다. */
    @Transactional
    public OrderResponse completePortOneByCallback(
            String impUid,
            String merchantUid,
            String callbackToken
    ) {
        PurchaseOrder order = requireCallbackOrder(merchantUid, callbackToken);
        return verifyAndApplyPortOnePayment(order, impUid);
    }

    /**
     * 외부 결제는 승인됐지만 로컬 DB 반영이 실패한 회원 주문을 주문번호로 재검증합니다.
     * 브라우저 값을 신뢰하지 않고 PortOne REST 응답의 결제번호·금액·상태를 확인합니다.
     */
    @Transactional
    public OrderResponse reconcilePortOne(
            String merchantUid,
            Long memberId
    ) {
        PurchaseOrder order = requireMemberOrder(merchantUid, memberId);
        requirePortOneConfiguration();

        String accessToken = getPortOneAccessToken();
        JsonNode payment = getPortOnePaymentByMerchantUid(
                accessToken,
                order.getOrderNumber()
        );
        String impUid = requiredText(payment, "imp_uid", "포트원 결제번호");

        return applyVerifiedPortOnePayment(order, impUid, payment);
    }

    /**
     * 결제창에서 거래번호가 발급되기 전에 취소된 주문만 재고를 복원합니다.
     * 이미 외부 거래번호가 저장된 주문은 자동으로 실패 처리하지 않습니다.
     */
    @Transactional
    public void failPendingPayment(
            String orderNumber,
            String callbackToken,
            Long memberId
    ) {
        PurchaseOrder order = requireMemberOrder(orderNumber, memberId);
        requireCallbackToken(order, callbackToken);
        failUnstartedPayment(order);
    }

    @Transactional
    public void failPendingPaymentByCallback(
            String orderNumber,
            String callbackToken
    ) {
        failUnstartedPayment(requireCallbackOrder(orderNumber, callbackToken));
    }

    /**
     * 포트원 웹훅 본문을 그대로 신뢰하지 않습니다.
     * imp_uid로 포트원 REST API를 다시 조회해 주문번호, 금액, 상태를 검증합니다.
     */
    @Transactional
    public void handlePortOneWebhook(JsonNode payload) {
        String impUid = payload.path("imp_uid").asText("");
        String merchantUid = payload.path("merchant_uid").asText("");

        if (!StringUtils.hasText(impUid) || !StringUtils.hasText(merchantUid)) {
            throw new IllegalArgumentException("포트원 웹훅의 결제번호 또는 주문번호가 없습니다.");
        }

        PurchaseOrder order = purchaseOrderRepository.findByOrderNumber(merchantUid)
                .orElseThrow(() -> new IllegalArgumentException("웹훅 주문을 찾을 수 없습니다."));
        verifyAndApplyPortOnePayment(order, impUid);
    }

    @Transactional
    public OrderResponse cancelOrder(String orderNumber, Long memberId) {
        PurchaseOrder order = requireMemberOrder(orderNumber, memberId);

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalArgumentException("이미 취소된 주문입니다.");
        }
        if (order.getStatus() == OrderStatus.SHIPPING
                || order.getStatus() == OrderStatus.DELIVERED) {
            throw new IllegalArgumentException("배송이 시작된 주문은 취소할 수 없습니다.");
        }

        if (StringUtils.hasText(order.getProviderTransactionId())) {
            if (order.getPaymentProvider() != PaymentProvider.PORTONE) {
                throw new IllegalArgumentException(
                        "이전 직접 연동 결제입니다. 기존 결제사 관리자에서 취소 상태를 확인해주세요."
                );
            }
            if (order.getPaymentStatus() == PaymentStatus.WAITING_FOR_DEPOSIT) {
                cancelPortOneVirtualAccount(order.getProviderTransactionId());
            } else {
                cancelPortOneTransaction(
                        order.getProviderTransactionId(),
                        order.getOrderNumber(),
                        order.getTotalAmount(),
                        "고객 주문 취소"
                );
            }
        }

        order.markPaymentCancelled();
        cancelLocally(order);
        return OrderResponse.from(order);
    }

    private OrderResponse verifyAndApplyPortOnePayment(
            PurchaseOrder order,
            String impUid
    ) {
        requirePortOneConfiguration();
        JsonNode payment = getPortOnePayment(impUid, order.getOrderNumber());

        return applyVerifiedPortOnePayment(order, impUid, payment);
    }

    private OrderResponse applyVerifiedPortOnePayment(
            PurchaseOrder order,
            String impUid,
            JsonNode payment
    ) {

        String verifiedImpUid = requiredText(payment, "imp_uid", "포트원 결제번호");
        String merchantUid = requiredText(payment, "merchant_uid", "포트원 주문번호");
        int amount = payment.path("amount").asInt(-1);
        String status = payment.path("status").asText("");

        if (!secureEquals(impUid, verifiedImpUid)) {
            throw new IllegalArgumentException("포트원 결제번호가 일치하지 않습니다.");
        }
        if (!order.getOrderNumber().equals(merchantUid)) {
            throw new IllegalArgumentException("포트원 결제 주문번호가 일치하지 않습니다.");
        }
        if (amount != order.getTotalAmount()) {
            throw new IllegalArgumentException("포트원 결제 금액이 주문 금액과 일치하지 않습니다.");
        }

        if (order.getPaymentStatus() == PaymentStatus.DONE) {
            if (!secureEquals(order.getProviderTransactionId(), verifiedImpUid)) {
                throw new IllegalArgumentException("이미 다른 결제번호로 완료된 주문입니다.");
            }
            return OrderResponse.from(order);
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            if ("paid".equals(status)) {
                cancelPortOneTransaction(
                        verifiedImpUid,
                        order.getOrderNumber(),
                        order.getTotalAmount(),
                        "이미 취소된 주문의 결제 자동 환불"
                );
            }
            return OrderResponse.from(order);
        }

        order.preparePayment(PaymentProvider.PORTONE, verifiedImpUid);

        switch (status) {
            case "paid" -> order.completePayment(
                    verifiedImpUid,
                    payment.path("receipt_url").asText(null)
            );
            case "ready" -> applyVirtualAccount(order, payment, verifiedImpUid);
            case "failed" -> {
                order.failPayment();
                cancelLocally(order);
            }
            case "cancelled", "canceled" -> {
                order.markPaymentCancelled();
                cancelLocally(order);
            }
            default -> throw new IllegalArgumentException(
                    "아직 완료되지 않은 포트원 결제 상태입니다: " + status
            );
        }

        return OrderResponse.from(order);
    }

    private void applyVirtualAccount(
            PurchaseOrder order,
            JsonNode payment,
            String impUid
    ) {
        if (order.getPaymentMethod() != PaymentMethod.BANK_TRANSFER
                || !"vbank".equals(payment.path("pay_method").asText())) {
            throw new IllegalArgumentException("가상계좌 주문이 아닌데 미입금 상태가 반환되었습니다.");
        }

        order.waitForDeposit(
                impUid,
                null,
                payment.path("vbank_name").asText(null),
                payment.path("vbank_num").asText(null),
                formatVirtualAccountDueDate(payment.path("vbank_date").asLong(0))
        );
    }

    private JsonNode getPortOnePayment(String impUid, String merchantUid) {
        String accessToken = getPortOneAccessToken();
        String encodedImpUid = UriUtils.encodePathSegment(impUid, StandardCharsets.UTF_8);

        try {
            JsonNode wrapper = restClient.get()
                    .uri(PORTONE_PAYMENT_URL + encodedImpUid)
                    .header(HttpHeaders.AUTHORIZATION, accessToken)
                    .retrieve()
                    .body(JsonNode.class);
            return requirePortOneResponse(wrapper, "포트원 결제 조회");
        } catch (RestClientResponseException exception) {
            /*
             * 일부 PortOne V1 테스트 결제는 결제 목록과 주문번호 조회에는
             * 즉시 나타나지만 imp_uid 단건조회가 404를 반환할 수 있습니다.
             * 이 경우에만 고유한 merchant_uid로 다시 조회하고, 호출부에서
             * 반환된 imp_uid까지 원래 콜백 값과 교차 검증합니다.
             */
            if (exception.getStatusCode().value() == 404) {
                log.info("포트원 결제번호 조회가 404를 반환해 주문번호로 다시 조회합니다.");
                return getPortOnePaymentByMerchantUid(accessToken, merchantUid);
            }
            log.warn("포트원 결제 조회 실패: status={}, body={}",
                    exception.getStatusCode(), exception.getResponseBodyAsString());
            throw new IllegalArgumentException("포트원 결제 조회에 실패했습니다.");
        } catch (RuntimeException exception) {
            log.warn("포트원 결제 조회 통신 실패", exception);
            throw new IllegalArgumentException("포트원 결제 조회 서버에 연결하지 못했습니다.");
        }
    }

    private JsonNode getPortOnePaymentByMerchantUid(
            String accessToken,
            String merchantUid
    ) {
        String encodedMerchantUid = UriUtils.encodePathSegment(
                merchantUid,
                StandardCharsets.UTF_8
        );

        try {
            JsonNode wrapper = restClient.get()
                    .uri(PORTONE_PAYMENT_BY_MERCHANT_URL + encodedMerchantUid)
                    .header(HttpHeaders.AUTHORIZATION, accessToken)
                    .retrieve()
                    .body(JsonNode.class);
            return requirePortOneResponse(wrapper, "포트원 주문번호 결제 조회");
        } catch (RestClientResponseException exception) {
            log.warn("포트원 주문번호 결제 조회 실패: status={}, body={}",
                    exception.getStatusCode(), exception.getResponseBodyAsString());
            throw new IllegalArgumentException("포트원 결제 조회에 실패했습니다.");
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalArgumentException) {
                throw exception;
            }
            log.warn("포트원 주문번호 결제 조회 통신 실패", exception);
            throw new IllegalArgumentException("포트원 결제 조회 서버에 연결하지 못했습니다.");
        }
    }

    private String getPortOneAccessToken() {
        requirePortOneConfiguration();

        Map<String, Object> body = Map.of(
                "imp_key", properties.getPortone().getApiKey(),
                "imp_secret", properties.getPortone().getApiSecret()
        );

        JsonNode response = postPortOneJson(
                PORTONE_TOKEN_URL,
                null,
                body,
                "포트원 액세스 토큰 발급"
        );
        return requiredText(response, "access_token", "포트원 액세스 토큰");
    }

    private void cancelPortOneTransaction(
            String impUid,
            String merchantUid,
            int amount,
            String reason
    ) {
        String accessToken = getPortOneAccessToken();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("imp_uid", impUid);
        body.put("merchant_uid", merchantUid);
        body.put("amount", amount);
        body.put("reason", reason);

        postPortOneJson(
                PORTONE_CANCEL_URL,
                accessToken,
                body,
                "포트원 결제 취소"
        );
    }

    /** 입금 전 가상계좌는 환불이 아니라 발급취소(말소) API로 닫습니다. */
    private void cancelPortOneVirtualAccount(String impUid) {
        String accessToken = getPortOneAccessToken();
        String encodedImpUid = UriUtils.encodePathSegment(
                impUid,
                StandardCharsets.UTF_8
        );

        try {
            JsonNode wrapper = restClient.delete()
                    .uri(PORTONE_VBANK_URL + encodedImpUid)
                    .header(HttpHeaders.AUTHORIZATION, accessToken)
                    .retrieve()
                    .body(JsonNode.class);
            requirePortOneResponse(wrapper, "포트원 가상계좌 발급취소");
        } catch (RestClientResponseException exception) {
            log.warn("포트원 가상계좌 발급취소 실패: status={}, body={}",
                    exception.getStatusCode(), exception.getResponseBodyAsString());
            throw new IllegalArgumentException("가상계좌 발급취소에 실패했습니다.");
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalArgumentException) {
                throw exception;
            }
            log.warn("포트원 가상계좌 발급취소 통신 실패", exception);
            throw new IllegalArgumentException("가상계좌 발급취소 서버에 연결하지 못했습니다.");
        }
    }

    private JsonNode postPortOneJson(
            String url,
            String authorization,
            Object body,
            String operation
    ) {
        try {
            RestClient.RequestBodySpec request = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON);
            if (StringUtils.hasText(authorization)) {
                request.header(HttpHeaders.AUTHORIZATION, authorization);
            }
            JsonNode wrapper = request.body(body)
                    .retrieve()
                    .body(JsonNode.class);
            return requirePortOneResponse(wrapper, operation);
        } catch (RestClientResponseException exception) {
            log.warn("{} 실패: status={}, body={}",
                    operation, exception.getStatusCode(), exception.getResponseBodyAsString());
            throw new IllegalArgumentException(operation + "에 실패했습니다. 포트원 설정을 확인해주세요.");
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalArgumentException) {
                throw exception;
            }
            log.warn("{} 통신 실패", operation, exception);
            throw new IllegalArgumentException(operation + " 서버에 연결하지 못했습니다.");
        }
    }

    private JsonNode requirePortOneResponse(JsonNode wrapper, String operation) {
        if (wrapper == null) {
            throw new IllegalArgumentException(operation + " 응답이 없습니다.");
        }
        if (wrapper.path("code").asInt(-1) != 0 || wrapper.path("response").isMissingNode()
                || wrapper.path("response").isNull()) {
            String message = wrapper.path("message").asText("알 수 없는 오류");
            throw new IllegalArgumentException(operation + "에 실패했습니다: " + message);
        }
        return wrapper.path("response");
    }

    private PurchaseOrder requireMemberOrder(String orderNumber, Long memberId) {
        if (memberId == null) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }
        PurchaseOrder order = purchaseOrderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));
        if (order.getMember() == null || !memberId.equals(order.getMember().getId())) {
            throw new IllegalArgumentException("본인의 주문만 결제할 수 있습니다.");
        }
        return order;
    }

    private PurchaseOrder requireCallbackOrder(
            String orderNumber,
            String callbackToken
    ) {
        PurchaseOrder order = purchaseOrderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));
        requireCallbackToken(order, callbackToken);
        return order;
    }

    private void requireCallbackToken(PurchaseOrder order, String callbackToken) {
        if (!secureEquals(order.getPaymentCallbackToken(), callbackToken)) {
            throw new IllegalArgumentException("결제 콜백 검증에 실패했습니다.");
        }
    }

    private void failUnstartedPayment(PurchaseOrder order) {
        if (order.getStatus() == OrderStatus.PAID
                || order.getStatus() == OrderStatus.CANCELLED
                || StringUtils.hasText(order.getProviderTransactionId())) {
            return;
        }
        if (order.getPaymentStatus() == PaymentStatus.READY) {
            order.failPayment();
            cancelLocally(order);
        }
    }

    private void cancelLocally(PurchaseOrder order) {
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return;
        }
        order.cancel();
        order.getItems().stream()
                .flatMap(item -> item.getLotAllocations().stream())
                .forEach(allocation -> allocation.getProductLot().increase(allocation.getQuantity()));
    }

    private String formatVirtualAccountDueDate(long epochSeconds) {
        return epochSeconds > 0
                ? VBANK_DATE_FORMAT.format(Instant.ofEpochSecond(epochSeconds))
                : null;
    }

    private String requiredText(JsonNode response, String field, String label) {
        if (response == null || !StringUtils.hasText(response.path(field).asText())) {
            throw new IllegalArgumentException(label + "가 포트원 응답에 없습니다.");
        }
        return response.path(field).asText();
    }

    private void requirePortOneConfiguration() {
        PaymentProperties.Portone portone = properties.getPortone();
        if (!StringUtils.hasText(portone.getCustomerCode())
                || !StringUtils.hasText(portone.getApiKey())
                || !StringUtils.hasText(portone.getApiSecret())) {
            throw new IllegalArgumentException(
                    "포트원 고객사 식별코드 또는 REST API Key/Secret이 설정되지 않았습니다."
            );
        }
    }

    private boolean secureEquals(String expected, String actual) {
        if (!StringUtils.hasText(expected) || !StringUtils.hasText(actual)) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }
}
