package com.ex.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 개발 확인용 Console 출력과 실제 이메일 발송을 한 번의 요청으로 처리합니다.
 */
@Component
@RequiredArgsConstructor
public class CompositeRecoveryCodeSender implements RecoveryCodeSender {

    private final ConsoleRecoveryCodeSender consoleSender;
    private final NaverSensRecoveryCodeSender smsSender;

    @Override
    public void send(
            String username,
            String destinationPhone,
            String verificationCode
    ) {
        // 로컬 시연 중에도 인증번호를 확인할 수 있도록 Console에 먼저 출력합니다.
        consoleSender.send(username, destinationPhone, verificationCode);

        // Naver Cloud SENS 설정을 켠 경우 동일한 번호를 회원 휴대전화로 전송합니다.
        smsSender.send(username, destinationPhone, verificationCode);
    }
}
