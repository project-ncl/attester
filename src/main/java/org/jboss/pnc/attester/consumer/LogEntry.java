package org.jboss.pnc.attester.consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@AllArgsConstructor
public class LogEntry {
    String buildId;
    boolean temporaryBuild;
}
