package com.ex.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class NaverSensRecoveryCodeSender {

    private static final String API_BASE_URL = "https://sens.apigw.ntruss.com";

    @Value("${feedflow.security.password-recovery.sms-enabled:false}")
    private boolean smsEnabled;

    @Value("${feedflow.sms.naver.service-id:}")
    private String serviceId;

    @Value("${feedflow.sms.naver.access-key:}")
    private String accessKey;

    @Value("${feedflow.sms.naver.secret-key:}")
    private String secretKey;

    @Value("${feedflow.sms.naver.sender-phone:}")
    private String senderPhone;

    public void send(
            String username,
            String destinationPhone,
            String verificationCode
    ) {
        if (!smsEnabled) {
            log.info("휴대전화 SMS 인증번호 발송이 비활성화되어 있습니다.");
            return;
        }

        validateConfiguration();

        String timestamp = String.valueOf(System.currentTimeMillis());
        String requestPath = "/sms/v2/services/" + serviceId.trim() + "/messages";
        String recipient = digitsOnly(destinationPhone);
        String content = "[FEED FLOW] 비밀번호 재설정 인증번호는 "
                + verificationCode + "입니다. 5분 안에 입력해주세요.";

        Map<String, Object> body = Map.of(
                "type", "SMS",
                "contentType", "COMM",
                "countryCode", "82",
                "from", digitsOnly(senderPhone),
                "content", content,
                "messages", List.of(Map.of("to", recipient))
        );

        try {
            RestClient.create(API_BASE_URL)
                    .post()
                    .uri(requestPath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("x-ncp-apigw-timestamp", timestamp)
                    .header("x-ncp-iam-access-key", accessKey.trim())
                    .header(
                            "x-ncp-apigw-signature-v2",
                            createSignature(requestPath, timestamp)
                    )
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.info("휴대전화 인증번호를 발송했습니다. 아이디={}, 수신처={}",
                    username, maskPhone(destinationPhone));
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "인증번호 문자 발송에 실패했습니다. Naver Cloud SENS 설정을 확인해주세요.",
                    exception
            );
        }
    }

    private String createSignature(String requestPath, String timestamp) {
        String message = "POST " + requestPath + '\n'
                + timestamp + '\n'
                + accessKey.trim();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    secretKey.trim().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            ));
            return Base64.getEncoder().encodeToString(
                    mac.doFinal(message.getBytes(StandardCharsets.UTF_8))
            );
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SMS API 서명 생성에 실패했습니다.", exception);
        }
    }

    private void validateConfiguration() {
        if (isBlank(serviceId)
                || isBlank(accessKey)
                || isBlank(secretKey)
                || isBlank(senderPhone)) {
            throw new IllegalStateException(
                    "SMS 발송 설정이 없습니다. sms.properties를 확인해주세요."
            );
        }
    }

    private String digitsOnly(String value) {
        return value == null ? "" : value.replaceAll("[^0-9]", "");
    }

    private String maskPhone(String phone) {
        String digits = digitsOnly(phone);
        if (digits.length() < 10) {
            return "등록 휴대전화";
        }
        return digits.substring(0, 3)
                + "-****-"
                + digits.substring(digits.length() - 4);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
