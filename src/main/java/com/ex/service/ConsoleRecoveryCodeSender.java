package com.ex.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ConsoleRecoveryCodeSender {

    @Value("${feedflow.security.password-recovery.console-enabled:true}")
    private boolean consoleEnabled;

    public void send(
            String username,
            String destinationPhone,
            String verificationCode
    ) {
        if (!consoleEnabled) {
            return;
        }

        log.warn(
                "[FEED FLOW 개발용 비밀번호 인증번호] 아이디={}, 수신처={}, 인증번호={} (5분간 유효)",
                username,
                maskPhone(destinationPhone),
                verificationCode
        );
    }

    private String maskPhone(String phone) {
        String digits = phone == null ? "" : phone.replaceAll("[^0-9]", "");
        if (digits.length() < 10) {
            return "등록 휴대전화";
        }
        return digits.substring(0, 3)
                + "-****-"
                + digits.substring(digits.length() - 4);
    }
}
