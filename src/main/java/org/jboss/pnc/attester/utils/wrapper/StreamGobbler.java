package org.jboss.pnc.attester.utils.wrapper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;

import lombok.extern.slf4j.Slf4j;

/**
 * Author: Andrea Vibelli avibelli@redhat.com via the sentinel repository
 */
@Slf4j
public class StreamGobbler extends Thread {
    private final InputStream is;
    private final String name;
    final Path capturedFile;

    StreamGobbler(InputStream is, String name) throws IOException {
        this.is = is;
        this.name = name;
        this.capturedFile = Files.createTempFile(name, ".log");
    }

    @Override
    public void run() {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is));
                BufferedWriter writer = Files.newBufferedWriter(capturedFile)) {
            String line;
            while ((line = br.readLine()) != null) {
                writer.write(line);
                writer.newLine();
            }
        } catch (IOException e) {
            log.error("Exception while draining the ouput log!", e);
        }
    }
}
