package com.ex.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "admin_activity_log", indexes = {
        @Index(name = "idx_admin_activity_created_at", columnList = "created_at"),
        @Index(name = "idx_admin_activity_target", columnList = "target_type,target_identifier")
})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class AdminActivityLog extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "admin_activity_log_id")
    private Long id;

    @Column(name = "admin_username", nullable = false, length = 40)
    private String adminUsername;

    @Column(name = "action_type", nullable = false, length = 40)
    private String actionType;

    @Column(name = "target_type", nullable = false, length = 30)
    private String targetType;

    @Column(name = "target_identifier", nullable = false, length = 100)
    private String targetIdentifier;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;
}
