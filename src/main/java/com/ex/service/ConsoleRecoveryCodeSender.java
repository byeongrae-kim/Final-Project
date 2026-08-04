package com.ex.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ConsoleRecoveryCodeSender implements RecoveryCodeSender {

    @Value("${feedflow.security.password-recovery.console-enabled:true}")
    private boolean consoleEnabled;

    @Override
    public void send(
            String username,
            String maskedDestination,
            String verificationCode
    ) {
        if (!consoleEnabled) {
            throw new IllegalStateException(
                    "운영용 이메일 또는 문자 인증번호 발송기를 설정해주세요."
            );
        }

        log.warn(
                "[FEED FLOW 개발용 비밀번호 인증번호] 아이디={}, 수신처={}, 인증번호={} (5분간 유효)",
                username,
                maskedDestination,
                verificationCode
        );
    }
}
