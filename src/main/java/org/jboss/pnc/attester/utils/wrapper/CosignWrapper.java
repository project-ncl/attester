package org.jboss.pnc.attester.utils.wrapper;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.pnc.attester.utils.configuration.AttesterConfig;

/**
 * Wrapper around the Cosign CLI for signing complete in-toto provenance statements.
 * Author: Andrea Vibelli avibelli@redhat.com via the sentinel repository
 */
@ApplicationScoped
public class CosignWrapper {

    @Inject
    AttesterConfig config;

    /**
     * Creates a Sigstore bundle equivalent to the pipeline command:
     *
     * <pre>
     * cosign attest-blob \
     *   --yes \
     *   --key cosign.key \
     *   --statement provenance.json \
     *   --bundle provenance.sigstore.json
     * </pre>
     *
     * The statement already contains its in-toto subject. Therefore this method
     * deliberately does not use --predicate, --type, --hash, or a positional blob.
     */
    public void attestStatement(
            Path statement,
            Path outputBundle) throws IOException, InterruptedException {

        requireRegularFile(statement, "provenance statement");
        Path bundleParent = outputBundle.toAbsolutePath().getParent();
        if (bundleParent != null) {
            Files.createDirectories(bundleParent);
        }

        List<String> commands = new ArrayList<>();
        commands.add("cosign");
        commands.add("attest-blob");
        commands.add("--yes");
        commands.add("--key");
        commands.add(config.getCosignPrivateKeyPath().toString());
        commands.add("--statement");
        commands.add(statement.toString());
        commands.add("--bundle");
        commands.add(outputBundle.toString());

        if (config.getCosignSigningConfigPath().isPresent()) {
            Path signingConfig = config.getCosignSigningConfigPath().get();
            requireRegularFile(signingConfig, "Cosign signing config");
            commands.add("--signing-config");
            commands.add(signingConfig.toString());
        }

        CommandResult result = runCosign(commands);
        if (result.exitCode() != 0) {
            throw new RuntimeException("cosign failed: " + result.stderr());
        }

        if (!Files.isRegularFile(outputBundle) || Files.size(outputBundle) == 0) {
            throw new IllegalStateException("Cosign did not create bundle: " + outputBundle);
        }
    }

    /**
     * Verifies a signed provenance bundle using the configured Cosign public key.
     *
     * @param bundlePath path to the {@code provenance.sigstore.json} bundle
     * @return {@code true} when Cosign successfully verifies the bundle
     * @throws IOException if the bundle cannot be read or Cosign cannot be started
     * @throws InterruptedException if the verification process is interrupted
     */
    public boolean verifyAttestation(Path bundlePath)
            throws IOException, InterruptedException {

        Objects.requireNonNull(bundlePath, "bundlePath must not be null");

        if (!Files.isRegularFile(bundlePath)) {
            throw new IOException("Sigstore bundle does not exist: " + bundlePath);
        }

        List<String> commands = List.of(
                "cosign",
                "verify-blob-attestation",
                "--insecure-ignore-tlog=true",
                "--check-claims=false",
                "--key",
                config.getCosignPublicKeyPath().toString(),
                "--type",
                "slsaprovenance1",
                "--bundle",
                bundlePath.toString());

        ProcessBuilder pb = new ProcessBuilder(commands);
        Process p = pb.start();

        StreamGobbler outGobbler = new StreamGobbler(p.getInputStream(), "cosign-out");
        StreamGobbler errGobbler = new StreamGobbler(p.getErrorStream(), "cosign-err");

        outGobbler.start();
        errGobbler.start();

        int exitCode = p.waitFor();

        outGobbler.join();
        errGobbler.join();

        return exitCode == 0;
    }

    private static void requireRegularFile(Path path, String description) {
        if (path == null || !Files.isRegularFile(path)) {
            throw new IllegalStateException(description + " does not exist: " + path);
        }
    }

    private CommandResult runCosign(List<String> commands) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(commands);
        String password = config.getCosignPrivateKeyPassword();
        pb.environment().put("COSIGN_PASSWORD", password != null ? password : "");

        Process process = pb.start();
        StreamGobbler stdout = new StreamGobbler(process.getInputStream(), "cosign-out");
        StreamGobbler stderr = new StreamGobbler(process.getErrorStream(), "cosign-err");
        stdout.start();
        stderr.start();

        int exitCode = process.waitFor();
        stdout.join();
        stderr.join();

        return new CommandResult(
                exitCode,
                Files.readString(stdout.capturedFile),
                Files.readString(stderr.capturedFile));
    }

    private record CommandResult(int exitCode, String stdout, String stderr) {
    }

}