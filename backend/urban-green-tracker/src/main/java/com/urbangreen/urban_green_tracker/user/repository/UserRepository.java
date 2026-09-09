package com.urbangreen.urban_green_tracker.user.repository;



import org.springframework.stereotype.Repository;

import com.urbangreen.urban_green_tracker.user.entity.User;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;


import java.util.List;
import java.util.UUID;
import java.util.Optional;


@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    Optional<User> findByEmailOrUsername(String email, String username);
    Optional<User> findByEmailAndStatus(String email, String status);

    @Transactional
    @Modifying
    @Query("UPDATE User u SET u.status = :status WHERE u.id = :id")
    int updateUserStatus(UUID id, String status);

    List<User> findByRole(String role);

    List<User> findTop10ByStatusOrderByMonthlyPointsDesc(String status);

    // Cộng điểm cho User khi họ đăng bài sự cố được duyệt (Cộng nguyên tử bằng DB)
    @Transactional
    @Modifying
    @Query("UPDATE User u SET u.totalPoints = u.totalPoints + :points, u.monthlyPoints = u.monthlyPoints + :points WHERE u.id = :id")
    void addPointsToUser(UUID id, int points);



     
}
