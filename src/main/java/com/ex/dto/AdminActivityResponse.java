package com.ex.dto;

import com.ex.entity.AdminActivityLog;

import java.time.LocalDateTime;

public record AdminActivityResponse(
        Long id,
        String adminUsername,
        String actionType,
        String targetType,
        String targetIdentifier,
        String description,
        String ipAddress,
        LocalDateTime createdAt
) {
    public static AdminActivityResponse from(AdminActivityLog log) {
        return new AdminActivityResponse(
                log.getId(),
                log.getAdminUsername(),
                log.getActionType(),
                log.getTargetType(),
                log.getTargetIdentifier(),
                log.getDescription(),
                log.getIpAddress(),
                log.getCreatedAt()
        );
    }
}
