package org.jboss.pnc.attester;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Comparator;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.pnc.api.slsa.dto.provenance.v1.Provenance;
import org.jboss.pnc.attester.utils.client.OrchClient;
import org.jboss.pnc.attester.utils.configuration.AttesterConfig;
import org.jboss.pnc.attester.utils.wrapper.CosignWrapper;
import org.jboss.pnc.attester.utils.wrapper.OrasWrapper;
import org.jboss.pnc.common.log.LogSanitizer;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class Attester {

    private static final String FULL_STATEMENT_FILENAME = "full-provenance.json";
    private static final String REDACTED_STATEMENT_FILENAME = "provenance.json";
    private static final String FULL_BUNDLE_FILENAME = "full-provenance.sigstore.json";
    private static final String REDACTED_BUNDLE_FILENAME = "provenance.sigstore.json";

    @Inject
    OrchClient orchClient;

    @Inject
    OrasWrapper orasWrapper;

    @Inject
    CosignWrapper cosignWrapper;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    AttesterConfig config;

    public Provenance attest(String buildId) throws Exception {
        String sanitizedBuildId = LogSanitizer.clean(buildId);
        String tagSafeBuildId = toTagPart(sanitizedBuildId);

        log.info("Fetching full provenance statement for build: {}", sanitizedBuildId);
        Provenance fullProvenance = orchClient.getProvenance(sanitizedBuildId);

        log.info("Fetching redacted provenance statement for build: {}", sanitizedBuildId);
        Provenance redactedProvenance = orchClient.getRedactedProvenance(sanitizedBuildId);

        java.nio.file.Path workDirectory = Files.createTempDirectory("pnc-attester-" + tagSafeBuildId + "-");
        try {
            java.nio.file.Path fullStatement = workDirectory.resolve(FULL_STATEMENT_FILENAME);
            java.nio.file.Path fullBundle = workDirectory.resolve(FULL_BUNDLE_FILENAME);
            java.nio.file.Path redactedStatement = workDirectory.resolve(REDACTED_STATEMENT_FILENAME);
            java.nio.file.Path redactedBundle = workDirectory.resolve(REDACTED_BUNDLE_FILENAME);

            // Write the complete in-toto statements returned by PNC. Do not extract
            // provenance.getPredicate(): cosign --statement must receive the full
            // statement, including _type, subject, predicateType, and predicate.
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(fullStatement.toFile(), fullProvenance);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(redactedStatement.toFile(), redactedProvenance);

            log.info("Signing complete full provenance statement for build: {}", sanitizedBuildId);
            cosignWrapper.attestStatement(fullStatement, fullBundle);

            log.info("Signing complete redacted provenance statement for build: {}", sanitizedBuildId);
            cosignWrapper.attestStatement(redactedStatement, redactedBundle);

            String image = config.getContainerImage();
            String fullTag = image + ":build-" + tagSafeBuildId + "-full-provenance";
            String redactedTag = image + ":build-" + tagSafeBuildId + "-provenance";

            log.info("Publishing full provenance statement and signed bundle to {}", fullTag);
            String fullManifestSha256 = orasWrapper.pushBuildAttestation(fullTag, fullStatement, fullBundle);

            log.info("Publishing redacted provenance statement and signed bundle to {}", redactedTag);
            String redactedManifestSha256 = orasWrapper.pushBuildAttestation(
                    redactedTag,
                    redactedStatement,
                    redactedBundle);

            String fullReference = image + "@sha256:" + fullManifestSha256;
            String redactedReference = image + "@sha256:" + redactedManifestSha256;

            log.info("Creating full and redacted provenance attachments for build: {}", sanitizedBuildId);
            orchClient.createProvenanceAttachments(
                    sanitizedBuildId,
                    fullReference,
                    fullManifestSha256,
                    redactedReference,
                    redactedManifestSha256);

            if (config.isAddBuildAttribute()) {
                log.info(
                        "Adding full and redacted provenance references to build attributes for build: {}",
                        sanitizedBuildId);
                orchClient.addProvenanceAttestationBuildAttributes(
                        sanitizedBuildId,
                        fullReference,
                        redactedReference);
            }

            log.info("Created two signed full-statement provenance bundles for build: {}", sanitizedBuildId);

            // Preserve the useful endpoint response by returning the complete,
            // non-redacted provenance statement.
            return fullProvenance;
        } catch (Exception e) {
            log.error("Error while attesting build: {}", buildId, e);
            throw e;
        } finally {
            deleteRecursively(workDirectory);
        }
    }

    private static String toTagPart(String value) {
        String safe = value == null ? "" : value.replaceAll("[^A-Za-z0-9_.-]", "-");
        safe = safe.replaceAll("-+", "-").replaceAll("^[.-]+|[.-]+$", "");
        if (safe.isBlank()) {
            throw new IllegalArgumentException("Build ID cannot be converted to a safe OCI tag component: " + value);
        }
        return safe;
    }

    private static void deleteRecursively(java.nio.file.Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.warn("Could not delete temporary path: {}", path, e);
                }
            });
        } catch (IOException e) {
            log.warn("Could not clean temporary directory: {}", directory, e);
        }
    }
}
