package com.jargoyle;

import com.jargoyle.dto.ProcessingStatusEvent;
import com.jargoyle.service.DocumentStatusNotifier;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Maintains a registry of SSE emitters that are interested in receiving
 * document processing updates.
 */
@Component
public class SseEmitterRegistry implements DocumentStatusNotifier {

    private static final Duration DEFAULT_HEARTBEAT_INTERVAL = Duration.ofSeconds(15);
    private static final Logger log = LoggerFactory.getLogger(SseEmitterRegistry.class);
    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>> emitterRegistry = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeatExecutor;
    private final Duration heartbeatInterval;
    private final boolean ownsExecutor;

    public SseEmitterRegistry() {
        this(Executors.newSingleThreadScheduledExecutor(runnable -> {
            var thread = new Thread(runnable, "sse-heartbeat");
            thread.setDaemon(true);
            return thread;
        }), DEFAULT_HEARTBEAT_INTERVAL, true);
    }

    SseEmitterRegistry(ScheduledExecutorService heartbeatExecutor, Duration heartbeatInterval) {
        this(heartbeatExecutor, heartbeatInterval, false);
    }

    private SseEmitterRegistry(
            ScheduledExecutorService heartbeatExecutor,
            Duration heartbeatInterval,
            boolean ownsExecutor) {
        this.heartbeatExecutor = heartbeatExecutor;
        this.heartbeatInterval = heartbeatInterval;
        this.ownsExecutor = ownsExecutor;
    }

    public void register(UUID documentId, SseEmitter emitter) {
        var registeredEmittersForDoc = emitterRegistry.computeIfAbsent(documentId, key -> new CopyOnWriteArrayList<>());
        registeredEmittersForDoc.add(emitter);
        var heartbeatTaskRef = new AtomicReference<ScheduledFuture<?>>();

        // Clean up the emitter when the client disconnects, times out, or
        // an error occurs. Without these callbacks, dead emitters linger in
        // the registry and blow up when notify() tries to send to them.
        Runnable removeEmitter = () -> {
            registeredEmittersForDoc.remove(emitter);
            var heartbeatTask = heartbeatTaskRef.getAndSet(null);
            if (heartbeatTask != null) {
                heartbeatTask.cancel(true);
            }
            log.debug("Emitter removed from registry for document {}", documentId);
        };
        emitter.onCompletion(removeEmitter);
        emitter.onTimeout(removeEmitter);
        emitter.onError(e -> removeEmitter.run());

        heartbeatTaskRef.set(heartbeatExecutor.scheduleAtFixedRate(
                () -> sendHeartbeat(documentId, emitter, removeEmitter),
                heartbeatInterval.toMillis(),
                heartbeatInterval.toMillis(),
                TimeUnit.MILLISECONDS));

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

    @PreDestroy
    public void destroy() {
        if (ownsExecutor) {
            heartbeatExecutor.shutdownNow();
        }
    }

    private void sendHeartbeat(UUID documentId, SseEmitter emitter, Runnable removeEmitter) {
        try {
            emitter.send(SseEmitter.event().comment("heartbeat"));
        } catch (Exception ex) {
            log.debug("Heartbeat send failed for document {}, removing: {}", documentId, ex.getMessage());
            removeEmitter.run();
        }
    }
}
