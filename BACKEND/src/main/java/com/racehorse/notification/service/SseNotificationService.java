package com.racehorse.notification.service;

import com.racehorse.notification.dto.NotificationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class SseNotificationService {
    private static final Logger log = LoggerFactory.getLogger(SseNotificationService.class);
    
    // Lưu các emitter (kết nối mở) của mỗi user
    private final Map<UUID, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID userId) {
        // Timeout 30 phút để giữ kết nối lâu dài, có thể cấu hình
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        
        emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(() -> removeEmitter(userId, emitter));
        emitter.onError(e -> removeEmitter(userId, emitter));

        try {
            // Gửi event "ping" đầu tiên để kết nối không bị đóng bởi proxy
            emitter.send(SseEmitter.event().name("ping").data("connected"));
        } catch (IOException e) {
            removeEmitter(userId, emitter);
        }

        return emitter;
    }

    public void pushNotification(UUID recipientId, NotificationResponse notification) {
        CopyOnWriteArrayList<SseEmitter> userEmitters = emitters.get(recipientId);
        if (userEmitters != null) {
            for (SseEmitter emitter : userEmitters) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("notification")
                            .data(notification));
                } catch (IOException e) {
                    emitter.completeWithError(e);
                    removeEmitter(recipientId, emitter);
                }
            }
        }
    }

    private void removeEmitter(UUID userId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters != null) {
            userEmitters.remove(emitter);
            if (userEmitters.isEmpty()) {
                emitters.remove(userId);
            }
        }
    }
}
