package org.jboss.pnc.attester.utils.wrapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class OrasWrapper {

    private final String username;
    private final String password; // can be "" if key is unencrypted

    public OrasWrapper(String username, String password) {
        this.username = username;
        this.password = password;
    }

    /**
     * Create a container image tag
     */
    public void createContainerImage(String imageNameTag, Path path) throws IOException, InterruptedException {

        List<String> commands = List.of(
                "oras",
                "push",
                imageNameTag,
                path.toString(),
                "--disable-path-validation",
                "--username",
                username,
                "--password",
                password);
        System.out.println(commands);

        ProcessBuilder pb = new ProcessBuilder(commands);

        Process p = pb.start();

        // Drain stdout/stderr in separate threads to avoid blocking
        StreamGobbler outGobbler = new StreamGobbler(p.getInputStream(), "oras-out");
        StreamGobbler errGobbler = new StreamGobbler(p.getErrorStream(), "oras-err");
        outGobbler.start();
        errGobbler.start();

        // 1. Wait for process to finish
        int exitCode = p.waitFor();

        if (exitCode != 0) {
            String err = Files.readString(errGobbler.capturedFile);
            throw new RuntimeException("oras failed: " + err);
        }
    }
}
