package com.urbangreen.urban_green_tracker.image.entity;


import com.urbangreen.urban_green_tracker.post.entity.Post; // Import chéo từ module post
import com.urbangreen.urban_green_tracker.user.entity.User; // Import chéo từ module user
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "resolution_images")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ResolutionImage {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User uploadedBy;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}