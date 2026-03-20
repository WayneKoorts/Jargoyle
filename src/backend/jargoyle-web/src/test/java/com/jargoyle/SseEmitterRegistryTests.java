package com.jargoyle;


import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jargoyle.dto.ProcessingStatusEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class SseEmitterRegistryTests {

    private SseEmitter mockEmitter;
    private SseEmitterRegistry sut;
    private Logger logger;
    private ListAppender<ILoggingEvent> listAppender;
    private ScheduledExecutorService heartbeatExecutor;

    @BeforeEach
    void setUp() {
        mockEmitter = mock(SseEmitter.class);
        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
        sut = new SseEmitterRegistry(heartbeatExecutor, Duration.ofMillis(25));

        // In-memory log for verification.
        logger = (Logger) LoggerFactory.getLogger(SseEmitterRegistry.class);
        logger.setLevel(Level.DEBUG);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        heartbeatExecutor.shutdownNow();
        logger.detachAppender(listAppender);
    }

    @Test
    void notify_notifiesEmitters() throws IOException {
        var docId = UUID.randomUUID();

        sut.register(docId, mockEmitter);
        sut.notify(docId, new ProcessingStatusEvent("PROCESSING", "Step 1", null));

        verify(mockEmitter).send(any(ProcessingStatusEvent.class));
    }

    @Test
    void notify_withNoEmitters_logsWarning() {
        // Not registering any emitters
        // ...

        sut.notify(UUID.randomUUID(), new ProcessingStatusEvent("", "", ""));

        assertThat(listAppender.list)
                .anyMatch(event ->
                        event.getFormattedMessage().contains("no registered emitters")
                        && event.getLevel() == Level.WARN);
    }

    @Test
    void notify_sendException_removesEmitterAndContinues() throws IOException {
        var docId = UUID.randomUUID();
        var faultyEmitter = mock(SseEmitter.class);
        doThrow(new IOException())
                .when(faultyEmitter).send(any(ProcessingStatusEvent.class));

        sut.register(docId, faultyEmitter);
        sut.notify(docId, new ProcessingStatusEvent("", "", ""));

        // The failed emitter should be silently removed — no exception propagated
        assertThat(listAppender.list)
                .anyMatch(event ->
                        event.getFormattedMessage().contains("Emitter send failed")
                        && event.getLevel() == Level.DEBUG);
    }

    @Test
    void notify_illegalStateException_removesEmitterAndContinues() throws IOException {
        var docId = UUID.randomUUID();
        var deadEmitter = mock(SseEmitter.class);
        doThrow(new IllegalStateException("ResponseBodyEmitter has already completed"))
                .when(deadEmitter).send(any(ProcessingStatusEvent.class));

        sut.register(docId, deadEmitter);
        sut.notify(docId, new ProcessingStatusEvent("PROCESSING", "Saving results...", null));

        assertThat(listAppender.list)
                .anyMatch(event ->
                        event.getFormattedMessage().contains("Emitter send failed")
                        && event.getLevel() == Level.DEBUG);
    }

    @Test
    void complete_completesEmitters() {
        var docId = UUID.randomUUID();

        sut.register(docId, mockEmitter);
        sut.complete(docId);

        verify(mockEmitter).complete();
    }

    @Test
    void register_sendsHeartbeatComments() throws Exception {
        var docId = UUID.randomUUID();
        var heartbeatSent = new CountDownLatch(1);

        doAnswer(invocation -> {
            heartbeatSent.countDown();
            return null;
        }).when(mockEmitter).send(any(SseEmitter.SseEventBuilder.class));

        sut.register(docId, mockEmitter);

        assertThat(heartbeatSent.await(500, TimeUnit.MILLISECONDS)).isTrue();
        verify(mockEmitter, atLeastOnce()).send(any(SseEmitter.SseEventBuilder.class));
    }
}
