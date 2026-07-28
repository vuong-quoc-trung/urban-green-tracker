package com.urbangreen.urban_green_tracker.comment.entity;


import com.urbangreen.urban_green_tracker.post.entity.Post;
import com.urbangreen.urban_green_tracker.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "comments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Comment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    // THÊM MỚI: Đính kèm ảnh/video vào bình luận
    @Column(name = "media_url", length = 500)
    private String mediaUrl;

    @Column(name = "media_type", length = 20)
    private String mediaType; // IMAGE, VIDEO
}