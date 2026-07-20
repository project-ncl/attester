package org.jboss.pnc.attester.consumer;

import java.io.IOException;
import java.util.Optional;

import org.jboss.pnc.common.Json;
import org.jboss.pnc.common.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

public class LogLineDeserializer extends StdDeserializer<LogLine> {

    private final Logger logger = LoggerFactory.getLogger(LogLineDeserializer.class);

    public LogLineDeserializer() {
        super(LogLine.class);
    }

    protected LogLineDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public LogLine deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
            throws IOException {

        JsonNode node = jsonParser.getCodec().readTree(jsonParser);

        String loggerName = Json.getText(node, "/loggerName").orElse(null);
        Boolean temp = Boolean.parseBoolean(Json.getText(node, "/mdc/tmp").orElse("false"));
        String buildId = Json.getText(node, "/mdc/buildId").orElse(null);

        String message = Json.getText(node, "/message").orElse(null);
        String logLine;
        if (!Strings.isEmpty(message)) {
            logLine = message;
        } else {
            Optional<String> stackTrace = Json.getText(node, "/stackTrace");
            if (stackTrace.isPresent()) {
                logLine = stackTrace.get().lines().findFirst().orElse("");
            } else {
                logLine = "";
            }
        }

        LogEntry logEntry = new LogEntry(buildId, temp);
        return new LogLine(
                logEntry,
                loggerName,
                logLine);
    }
}
