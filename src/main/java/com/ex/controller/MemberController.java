package com.ex.controller;

import com.ex.dto.FindUsernameRequest;
import com.ex.dto.FindUsernameResponse;
import com.ex.dto.LoginRequest;
import com.ex.dto.MemberResponse;
import com.ex.dto.MemberUpdateRequest;
import com.ex.dto.PasswordResetCodeRequest;
import com.ex.dto.ResetPasswordRequest;
import com.ex.dto.SignupRequest;
import com.ex.service.MemberService;
import com.ex.service.PasswordRecoveryService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final PasswordRecoveryService passwordRecoveryService;

    @Value("${feedflow.admin.username:}")
    private String adminUsername;

    @Value("${feedflow.admin.password:}")
    private String adminPassword;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public MemberResponse signup(@Valid @RequestBody SignupRequest request) {
        return memberService.signup(request);
    }

    @GetMapping("/check-username")
    public Map<String, Boolean> checkUsername(
            @RequestParam(name = "username") String username
    ) {
        boolean isAdminUsername =
                adminUsername != null
                && !adminUsername.isBlank()
                && adminUsername.equalsIgnoreCase(username.trim());

        boolean available =
                !isAdminUsername
                && memberService.isUsernameAvailable(username);

        return Map.of("available", available);
    }

    @PostMapping("/login")
    public MemberResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpSession session
    ) {
        if (isAdminLogin(request)) {
            session.removeAttribute("memberId");
            session.setAttribute("isAdmin", true);

            /*
             * 기존 화면 JavaScript가 회원 응답을 받도록 되어 있으므로
             * 관리자 화면 이동에 사용할 관리자 응답을 반환합니다.
             */
            return new MemberResponse(
                    0L,
                    adminUsername,
                    "",
                    "관리자",
                    "FEED FLOW",
                    "",
                    null,
                    LocalDateTime.now(),
                    List.of()
            );
        }

        session.removeAttribute("isAdmin");

        MemberResponse member = memberService.login(request);
        session.setAttribute("memberId", member.id());

        return member;
    }

    @PostMapping("/find-username")
    public FindUsernameResponse findUsername(
            @Valid @RequestBody FindUsernameRequest request
    ) {
        return memberService.findUsername(request);
    }

    @PostMapping("/password-reset/code")
    public Map<String, String> issuePasswordResetCode(
            @Valid @RequestBody PasswordResetCodeRequest request
    ) {
        passwordRecoveryService.issueCode(request);
        return Map.of(
                "message",
                "회원정보가 일치하면 등록 휴대전화로 인증번호를 발송했습니다. 개발 환경에서는 STS Console에도 표시됩니다."
        );
    }

    @PostMapping("/reset-password")
    public Map<String, String> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        passwordRecoveryService.resetPassword(request);
        return Map.of(
                "message",
                "비밀번호가 변경되었습니다. 새 비밀번호로 로그인해주세요."
        );
    }

    @GetMapping("/me")
    public MemberResponse me(HttpSession session) {
        Long memberId = (Long) session.getAttribute("memberId");

        if (memberId == null) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }

        return memberService.findById(memberId);
    }

    @PutMapping("/me")
    public MemberResponse updateMe(
            @Valid @RequestBody MemberUpdateRequest request,
            HttpSession session
    ) {
        Long memberId = (Long) session.getAttribute("memberId");

        if (memberId == null) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }

        return memberService.update(memberId, request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpSession session) {
        session.invalidate();
    }

    private boolean isAdminLogin(LoginRequest request) {
        if (adminUsername == null
                || adminPassword == null
                || adminUsername.isBlank()
                || adminPassword.isBlank()) {
            return false;
        }

        return adminUsername.equalsIgnoreCase(request.username().trim())
                && adminPassword.equals(request.password());
    }
}
