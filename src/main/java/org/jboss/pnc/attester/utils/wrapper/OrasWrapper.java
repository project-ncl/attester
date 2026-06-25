package org.jboss.pnc.attester.utils.wrapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.pnc.attester.utils.configuration.AttesterConfig;

@ApplicationScoped
public class OrasWrapper {

    @Inject
    AttesterConfig config;

    /**
     * Create a container image tag
     *
     * @return the SHA256 digest hash (without the "sha256:" prefix)
     */
    public String createContainerImage(String imageNameTag, Path path) throws IOException, InterruptedException {

        List<String> commands = List.of(
                "oras",
                "push",
                imageNameTag,
                path.toString(),
                "--disable-path-validation",
                "--username",
                config.getContainerRegistryUsername(),
                "--password",
                config.getContainerRegistryPassword());

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

        String output = Files.readString(outGobbler.capturedFile);

        // Extract SHA256 digest from output
        String digest = null;
        for (String line : output.split("\n")) {
            if (line.startsWith("Digest: sha256:")) {
                digest = line.substring("Digest: sha256:".length()).trim();
                break;
            }
        }

        if (digest == null) {
            throw new RuntimeException("Failed to extract digest from oras output");
        }

        return digest;
    }
}
