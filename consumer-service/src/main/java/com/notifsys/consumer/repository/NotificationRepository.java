package com.notifsys.consumer.repository;

import com.notifsys.consumer.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    @Modifying
    @Query("UPDATE Notification n SET n.readStatus = true WHERE n.userId = :userId AND n.readStatus = false")
    int markAllAsReadForUser(@Param("userId") String userId);
}
