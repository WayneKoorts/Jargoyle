package com.jargoyle;

import com.jargoyle.dto.ProcessingStatusEvent;
import com.jargoyle.service.DocumentStatusNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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

        // Clean up the emitter when the client disconnects, times out, or
        // an error occurs. Without these callbacks, dead emitters linger in
        // the registry and blow up when notify() tries to send to them.
        Runnable removeEmitter = () -> {
            registeredEmittersForDoc.remove(emitter);
            log.debug("Emitter removed from registry for document {}", documentId);
        };
        emitter.onCompletion(removeEmitter);
        emitter.onTimeout(removeEmitter);
        emitter.onError(e -> removeEmitter.run());

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
            } catch (Exception ex) {
                // SseEmitter.send() throws IOException on network failures and
                // IllegalStateException when the emitter is already completed
                // (e.g. client disconnected). Either way, the emitter is dead —
                // remove it and carry on. This must never abort document processing.
                log.debug("Emitter send failed for document {}, removing: {}", documentId, ex.getMessage());
                registeredEmittersForDoc.remove(emitter);
            }
        }
    }

    @Override
    public void complete(UUID documentId) {
        var registeredEmittersForDoc = emitterRegistry.remove(documentId);
        if (registeredEmittersForDoc != null) {
            for (var emitter : registeredEmittersForDoc) {
                try {
                    emitter.complete();
                } catch (Exception ex) {
                    // The emitter may already be completed if the client disconnected;
                    // safe to ignore.
                    log.debug("Emitter already completed for document {}: {}", documentId, ex.getMessage());
                }
            }
        }

        log.info("Removed document {} from emitter registry.", documentId);
    }
}
