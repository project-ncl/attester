package org.jboss.pnc.attester;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.ZonedDateTime;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.pnc.api.dto.ComponentVersion;
import org.jboss.pnc.api.slsa.dto.provenance.v1.Predicate;
import org.jboss.pnc.attester.utils.client.OrchClient;
import org.jboss.pnc.attester.utils.configuration.AttesterConfig;
import org.jboss.pnc.attester.utils.wrapper.CosignWrapper;
import org.jboss.pnc.attester.utils.wrapper.OrasWrapper;
import org.jboss.pnc.common.log.LogSanitizer;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.security.Authenticated;
import lombok.extern.slf4j.Slf4j;

@Path("/")
@Slf4j
public class Public {

    @ConfigProperty(name = "quarkus.application.name")
    String name;

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

    @POST
    @Path("/attest/{buildId}")
    @Produces(APPLICATION_JSON)
    @Authenticated
    public Predicate attest(String buildId) throws Exception {

        var sanitizedBuildId = LogSanitizer.clean(buildId);

        log.info("Fetching predicate for build: {}", sanitizedBuildId);

        Predicate predicate = orchClient.getProvenancePredicate(sanitizedBuildId);

        String image = config.getContainerImage();
        String imageTag = image + ":build-" + sanitizedBuildId;

        File tempFile = File.createTempFile("pnc-build-", sanitizedBuildId);
        Files.writeString(
                tempFile.toPath(),
                sanitizedBuildId,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);

        log.info("Creating image {} for build: {}", imageTag, sanitizedBuildId);
        String sha256 = orasWrapper.createContainerImage(imageTag, tempFile.toPath());

        // digest url of image: more precise than tags
        String imageSha = image + "@sha256:" + sha256;

        tempFile.delete();

        log.info("Cosigning image {} for build: {}", imageTag, sanitizedBuildId);

        File tempFileProvenance = File.createTempFile("pnc-provenance-", sanitizedBuildId);
        objectMapper.writeValue(tempFileProvenance, predicate);
        cosignWrapper.attestPredicate(tempFileProvenance.toPath(), imageTag);

        log.info("Creating provenance-attestation attachment to PNC-Orch for build: {}", sanitizedBuildId);
        orchClient.createAttachmentProvenance(sanitizedBuildId, imageTag, sha256);

        if (config.isAddBuildAttribute()) {
            log.info("Creating build attribute for provenance-attestation for build: {}", sanitizedBuildId);
            orchClient.addProvenanceAttestationToBuildAttribute(sanitizedBuildId, imageSha);
        }

        log.info("Done for build: {}", sanitizedBuildId);

        return predicate;
    }

    @GET
    @Path("/version")
    public ComponentVersion getVersion() {
        return ComponentVersion.builder()
                .name(name)
                .version(Constants.ATTESTER_VERSION)
                .commit(Constants.COMMIT_HASH)
                .builtOn(ZonedDateTime.parse(Constants.BUILD_TIME))
                .build();
    }
}
