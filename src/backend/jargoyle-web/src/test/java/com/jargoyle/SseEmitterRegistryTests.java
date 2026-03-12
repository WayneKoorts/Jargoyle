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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class SseEmitterRegistryTests {

    private SseEmitter mockEmitter;
    private SseEmitterRegistry sut;
    private Logger logger;
    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        mockEmitter = mock(SseEmitter.class);
        sut = new SseEmitterRegistry();

        // In-memory log for verification.
        logger = (Logger) LoggerFactory.getLogger(SseEmitterRegistry.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
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
    void notify_sendException_throwsIOException() throws IOException {
        var docId = UUID.randomUUID();
        var faultyEmitter = mock(SseEmitter.class);
        doThrow(new IOException())
                .when(faultyEmitter).send(any(ProcessingStatusEvent.class));

        sut.register(docId, faultyEmitter);
        sut.notify(docId, new ProcessingStatusEvent("", "", ""));

        assertThat(listAppender.list)
                .anyMatch(event ->
                        event.getFormattedMessage().contains("Failed to notify emitter")
                        && event.getLevel() == Level.ERROR);
    }

    @Test
    void complete_completesEmitters() {
        var docId = UUID.randomUUID();

        sut.register(docId, mockEmitter);
        sut.complete(docId);

        verify(mockEmitter).complete();
    }
}
