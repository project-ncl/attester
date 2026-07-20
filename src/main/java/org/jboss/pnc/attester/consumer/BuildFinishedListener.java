package org.jboss.pnc.attester.consumer;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.reactive.messaging.Incoming;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.annotation.Timed;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.smallrye.common.annotation.Blocking;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class BuildFinishedListener {

    private final static String LOGGER_NAME_TO_LISTEN = "org.jboss.pnc._userlog_.build-result";
    private final static String LOG_MESSAGE_TO_LISTEN = "Successfully completed";

    @Inject
    ObjectMapper mapper;

    @Timed
    @Blocking
    @Incoming("logs")
    public void consume(@SpanAttribute(value = "json") List<String> batchJsonLines) {
        log.info("Received {} messages per batch", batchJsonLines.size());

        for (String json : batchJsonLines) {
            consumeSingleMessage(json);
        }
    }

    private void consumeSingleMessage(String json) {

        try {
            LogLine logLine = mapper.readValue(json, LogLine.class);

            if (!logLine.getLoggerName().trim().equals(LOGGER_NAME_TO_LISTEN)) {
                return;
            }

            if (!logLine.getLine().contains(LOG_MESSAGE_TO_LISTEN)) {
                return;
            }

            String buildIdSucceeded = logLine.getLogEntry().getBuildId();
            log.info("Build {} succeeded!");

        } catch (Exception e) {
            log.error("Error while reading and saving the data", e);
            throw new RuntimeException(e);
        }
    }
}
