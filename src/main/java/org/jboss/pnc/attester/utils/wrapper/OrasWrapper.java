package org.jboss.pnc.attester.utils.wrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.pnc.attester.utils.configuration.AttesterConfig;
import org.jboss.pnc.attester.utils.hash.DigestGenerator;

import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class OrasWrapper {

    public static final String SIGSTORE_BUNDLE_MEDIA_TYPE = "application/vnd.dev.sigstore.bundle.v0.3+json";
    public static final String IN_TOTO_STATEMENT_MEDIA_TYPE = "application/vnd.in-toto+json";
    public static final String HASH_MEDIA_TYPE = "text/plain";

    private static final Pattern DIGEST_PATTERN = Pattern.compile("(?m)^Digest:\\s+sha256:([0-9a-fA-F]{64})\\s*$");

    @Inject
    AttesterConfig config;

    @Inject
    DigestGenerator digestGenerator;

    /**
     * Publishes the complete provenance statement together with its Sigstore bundle.
     * The bundle itself also embeds the signed statement in its DSSE payload, while
     * the separate statement layer makes direct inspection and comparison simpler.
     *
     * @return the OCI manifest SHA-256 without the {@code sha256:} prefix
     */
    public String pushBuildAttestation(
            String imageNameTag,
            Path statement,
            Path bundle) throws IOException, InterruptedException {

        Path parent = requireSameParent(statement, bundle);

        // NCL-9852: generate shas for the statement and bundle
        List<Path> hashPaths = digestGenerator.generateDigests(statement);
        List<Path> hashBundlePaths = digestGenerator.generateDigests(bundle);

        String statementLayer = statement.getFileName() + ":" + IN_TOTO_STATEMENT_MEDIA_TYPE;
        String bundleLayer = bundle.getFileName() + ":" + SIGSTORE_BUNDLE_MEDIA_TYPE;

        List<String> commands = new ArrayList<>(
                List.of(
                        "oras",
                        "push",
                        "--username",
                        config.getContainerRegistryUsername(),
                        "--password-stdin",
                        "--artifact-type",
                        SIGSTORE_BUNDLE_MEDIA_TYPE,
                        imageNameTag,
                        statementLayer,
                        bundleLayer));

        List<Path> combinedHashs = Stream.concat(hashPaths.stream(), hashBundlePaths.stream()).toList();
        for (Path path : combinedHashs) {
            commands.add(path.getFileName().toString() + ":" + HASH_MEDIA_TYPE);
        }

        log.info("Running oras command: {} for image tag: {}", commands, imageNameTag);
        ProcessBuilder processBuilder = new ProcessBuilder(commands);
        processBuilder.directory(parent.toFile());
        Process process = processBuilder.start();

        try (var stdin = process.getOutputStream()) {
            stdin.write(config.getContainerRegistryPassword().getBytes(StandardCharsets.UTF_8));
            stdin.write('\n');
        }

        StreamGobbler stdout = new StreamGobbler(process.getInputStream(), "oras-out");
        StreamGobbler stderr = new StreamGobbler(process.getErrorStream(), "oras-err");
        stdout.start();
        stderr.start();

        int exitCode = process.waitFor();
        stdout.join();
        stderr.join();

        String output = Files.readString(stdout.capturedFile) + System.lineSeparator()
                + Files.readString(stderr.capturedFile);
        if (exitCode != 0) {
            throw new RuntimeException("oras failed: " + output);
        }

        Matcher matcher = DIGEST_PATTERN.matcher(output);
        if (!matcher.find()) {
            throw new RuntimeException("Failed to extract manifest digest from oras output: " + output);
        }
        return matcher.group(1).toLowerCase();
    }

    private static Path requireSameParent(Path first, Path second) {
        if (!Files.isRegularFile(first)) {
            throw new IllegalStateException("Provenance statement does not exist: " + first);
        }
        if (!Files.isRegularFile(second)) {
            throw new IllegalStateException("Sigstore bundle does not exist: " + second);
        }

        Path firstParent = first.toAbsolutePath().getParent();
        Path secondParent = second.toAbsolutePath().getParent();
        if (!firstParent.equals(secondParent)) {
            throw new IllegalArgumentException("Provenance statement and bundle must be in the same directory");
        }
        return firstParent;
    }
}
