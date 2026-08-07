package com.ex.service;

import com.ex.dto.AdminActivityResponse;
import com.ex.entity.AdminActivityLog;
import com.ex.repository.AdminActivityLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminActivityService {

    private final AdminActivityLogRepository adminActivityLogRepository;

    @Transactional
    public void record(
            String adminUsername,
            String actionType,
            String targetType,
            String targetIdentifier,
            String description,
            String ipAddress
    ) {
        adminActivityLogRepository.save(AdminActivityLog.builder()
                .adminUsername(limit(adminUsername, 40, "admin"))
                .actionType(limit(actionType, 40, "UNKNOWN"))
                .targetType(limit(targetType, 30, "SYSTEM"))
                .targetIdentifier(limit(targetIdentifier, 100, "-"))
                .description(limit(description, 500, "관리자 작업"))
                .ipAddress(limit(ipAddress, 64, null))
                .build());
    }

    @Transactional(readOnly = true)
    public List<AdminActivityResponse> findRecent() {
        return adminActivityLogRepository.findTop100ByOrderByCreatedAtDesc()
                .stream()
                .map(AdminActivityResponse::from)
                .toList();
    }

    private String limit(String value, int maxLength, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength
                ? trimmed
                : trimmed.substring(0, maxLength);
    }
}
