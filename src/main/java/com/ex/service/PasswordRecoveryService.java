package com.ex.service;

import com.ex.dto.PasswordResetCodeRequest;
import com.ex.dto.ResetPasswordRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class PasswordRecoveryService {

    private static final Duration CODE_LIFETIME = Duration.ofMinutes(5);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(30);
    private static final int MAX_FAILED_ATTEMPTS = 5;

    private final MemberService memberService;
    private final RecoveryCodeSender recoveryCodeSender;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, Challenge> challenges = new ConcurrentHashMap<>();

    public synchronized void issueCode(PasswordResetCodeRequest request) {
        String username = normalizeUsername(request.username());
        Instant now = Instant.now();
        Challenge existing = challenges.get(username);

        if (existing != null
                && existing.expiresAt().isAfter(now)
                && existing.issuedAt().plus(RESEND_COOLDOWN).isAfter(now)) {
            throw new IllegalArgumentException(
                    "인증번호는 30초 후 다시 요청할 수 있습니다."
            );
        }

        boolean matches = memberService.matchesPasswordRecoveryIdentity(
                username,
                request.email(),
                request.phone()
        );

        // 회원 존재 여부를 API 응답으로 노출하지 않습니다.
        if (!matches) {
            challenges.remove(username);
            return;
        }

        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        byte[] salt = new byte[16];
        secureRandom.nextBytes(salt);
        recoveryCodeSender.send(
                username,
                request.phone().trim(),
                code
        );
        challenges.put(
                username,
                new Challenge(
                        hash(username, code, salt),
                        salt,
                        now,
                        now.plus(CODE_LIFETIME),
                        0
                )
        );
    }

    public synchronized void resetPassword(ResetPasswordRequest request) {
        String username = normalizeUsername(request.username());
        Instant now = Instant.now();
        Challenge challenge = challenges.get(username);

        if (challenge == null || !challenge.expiresAt().isAfter(now)) {
            challenges.remove(username);
            throw invalidCode();
        }

        if (!MessageDigest.isEqual(
                challenge.codeHash(),
                hash(username, request.verificationCode(), challenge.salt())
        )) {
            int failedAttempts = challenge.failedAttempts() + 1;
            if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                challenges.remove(username);
            } else {
                challenges.put(
                        username,
                        new Challenge(
                                challenge.codeHash(),
                                challenge.salt(),
                                challenge.issuedAt(),
                                challenge.expiresAt(),
                                failedAttempts
                        )
                );
            }
            throw invalidCode();
        }

        memberService.updatePasswordAfterVerification(
                username,
                request.newPassword()
        );
        challenges.remove(username);
    }

    private byte[] hash(String username, String code, byte[] salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            return digest.digest(
                    (username + ':' + code).getBytes(StandardCharsets.UTF_8)
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("인증번호 보안 처리에 실패했습니다.", exception);
        }
    }

    private IllegalArgumentException invalidCode() {
        return new IllegalArgumentException(
                "인증번호가 올바르지 않거나 만료되었습니다. 다시 발급해주세요."
        );
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }

    private record Challenge(
            byte[] codeHash,
            byte[] salt,
            Instant issuedAt,
            Instant expiresAt,
            int failedAttempts
    ) {
    }
}
