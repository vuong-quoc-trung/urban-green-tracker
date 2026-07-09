package com.urbangreen.urban_green_tracker.post.entity;


import com.urbangreen.urban_green_tracker.image.entity.PostImage;         // Lấy từ module image
import com.urbangreen.urban_green_tracker.image.entity.ResolutionImage;   // Lấy từ module image
import com.urbangreen.urban_green_tracker.user.entity.User;               // Lấy từ module user
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.locationtech.jts.geom.Point; // BẮT BUỘC dùng JTS Point cho PostGIS

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "posts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Post {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, length = 50)
    private String type; 

    @Column(nullable = false, length = 50)
    private String status; 

    @Column(nullable = false, columnDefinition = "geometry(Point, 4326)")
    private Point location; 

    @Column(name = "resolution_note", columnDefinition = "TEXT")
    private String resolutionNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PostImage> images;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ResolutionImage> resolutionImages;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}