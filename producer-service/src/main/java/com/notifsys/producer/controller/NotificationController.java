package com.notifsys.producer.controller;

import com.notifsys.producer.dto.NotificationEvent;
import com.notifsys.producer.dto.NotificationRequest;
import com.notifsys.producer.service.NotificationProducerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@CrossOrigin(origins = "*")
public class NotificationController {

    private final NotificationProducerService producerService;

    public NotificationController(NotificationProducerService producerService) {
        this.producerService = producerService;
    }

    /**
     * Generic entry point: any upstream system (orders, billing, social graph...)
     * calls this to fire a notification. The request is accepted immediately and
     * handed to Kafka - the caller does NOT wait on delivery, persistence, or the
     * WebSocket push. That decoupling is the whole point of the architecture.
     */
    @PostMapping
    public ResponseEntity<NotificationEvent> send(@Valid @RequestBody NotificationRequest request) {
        NotificationEvent event = NotificationEvent.from(request);
        producerService.publish(event);
        return ResponseEntity.accepted().body(event);
    }

    /** Convenience endpoint used by the demo frontend's "Order Shipped" button. */
    @PostMapping("/demo/order-shipped/{userId}")
    public ResponseEntity<NotificationEvent> demoOrderShipped(@PathVariable String userId) {
        return send(new NotificationRequest(userId, "ORDER_SHIPPED", "Your order has shipped! 📦",
                "Order #" + (1000 + (int) (Math.random() * 9000)) + " is on its way."));
    }

    /** Convenience endpoint used by the demo frontend's "Friend Request" button. */
    @PostMapping("/demo/friend-request/{userId}")
    public ResponseEntity<NotificationEvent> demoFriendRequest(@PathVariable String userId) {
        return send(new NotificationRequest(userId, "FRIEND_REQUEST", "New friend request 👋",
                "Someone wants to connect with you."));
    }

    /** Convenience endpoint used by the demo frontend's "Payment Received" button. */
    @PostMapping("/demo/payment-received/{userId}")
    public ResponseEntity<NotificationEvent> demoPaymentReceived(@PathVariable String userId) {
        return send(new NotificationRequest(userId, "PAYMENT_RECEIVED", "Payment received 💸",
                "You received $" + (10 + (int) (Math.random() * 490)) + ".00"));
    }

    @GetMapping("/health")
    @ResponseStatus(HttpStatus.OK)
    public String health() {
        return "producer-service is up";
    }
}
