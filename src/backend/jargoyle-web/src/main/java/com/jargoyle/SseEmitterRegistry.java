package com.jargoyle;

import com.jargoyle.dto.ProcessingStatusEvent;
import com.jargoyle.service.DocumentStatusNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Maintains a registry of SSE emitters that are interested in receiving
 * document processing updates.
 */
@Component
public class SseEmitterRegistry implements DocumentStatusNotifier {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterRegistry.class);
    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>> emitterRegistry = new ConcurrentHashMap<>();

    public void register(UUID documentId, SseEmitter emitter) {
        var registeredEmittersForDoc = emitterRegistry.computeIfAbsent(documentId, key -> new CopyOnWriteArrayList<>());
        registeredEmittersForDoc.add(emitter);
        log.info("Registered emitter \"{}\" for document ID \"{}\"", emitter, documentId);
    }

    @Override
    public void notify(UUID documentId, ProcessingStatusEvent event) {
        var registeredEmittersForDoc = emitterRegistry.get(documentId);
        if (registeredEmittersForDoc == null) {
            log.warn("Trying to notify for document ID {} but there are no registered emitters.", documentId);
            return;
        }

        for (var emitter : registeredEmittersForDoc) {
            try {
                emitter.send(event);
            } catch (IOException ex) {
                log.error("Failed to notify emitter", ex);
                registeredEmittersForDoc.remove(emitter);
            }
        }
    }

    @Override
    public void complete(UUID documentId) {
        var registeredEmittersForDoc = emitterRegistry.get(documentId);
        if (registeredEmittersForDoc != null) {
            for (var emitter : registeredEmittersForDoc) {
                emitter.complete();
            }
        }

        emitterRegistry.remove(documentId);
        log.info("Removed document {} from emitter registry.", documentId);
    }
}
