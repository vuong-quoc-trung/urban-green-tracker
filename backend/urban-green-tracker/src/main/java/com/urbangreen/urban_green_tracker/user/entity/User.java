package com.urbangreen.urban_green_tracker.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(length = 255)
    private String password;

    @Column(nullable = false, length = 20)
    private String provider; 

    @Column(name = "provider_id", length = 255)
    private String providerId;

    @Column(nullable = false, length = 20)
    private String role; 

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "total_points", nullable = false)
    private Integer totalPoints = 0;

    @Column(name = "monthly_points", nullable = false)
    private Integer monthlyPoints = 0;

    @Column(name = "current_title", length = 50)
    private String currentTitle;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
