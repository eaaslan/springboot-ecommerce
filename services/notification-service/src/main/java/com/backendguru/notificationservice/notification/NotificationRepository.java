package com.backendguru.notificationservice.notification;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

  List<Notification> findByOrderIdOrderByIdAsc(Long orderId);

  List<Notification> findByUserIdOrderByIdDesc(Long userId);
}
