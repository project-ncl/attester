package org.jboss.pnc.attester;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

import org.jboss.pnc.api.slsa.dto.provenance.v1.Predicate;
import org.jboss.pnc.attester.utils.client.OrchClient;
import org.jboss.pnc.attester.utils.configuration.AttesterConfig;
import org.jboss.pnc.attester.utils.wrapper.CosignWrapper;
import org.jboss.pnc.attester.utils.wrapper.OrasWrapper;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Path("/")
@Slf4j
public class Public {

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

        log.info("Fetching predicate for build: {}", buildId);

        Predicate predicate = orchClient.getProvenancePredicate(buildId);

        String image = config.getContainerImage();
        String imageTag = image + ":build-" + buildId;

        File tempFile = File.createTempFile("pnc-build-", buildId);
        Files.writeString(tempFile.toPath(), buildId, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        log.info("Creating image {} for build: {}", imageTag, buildId);
        String sha256 = orasWrapper.createContainerImage(imageTag, tempFile.toPath());

        // digest url of image: more precise than tags
        String imageSha = image + "@sha256:" + sha256;

        tempFile.delete();

        log.info("Cosigning image {} for build: {}", imageTag, buildId);

        File tempFileProvenance = File.createTempFile("pnc-provenance-", buildId);
        objectMapper.writeValue(tempFileProvenance, predicate);
        cosignWrapper.attestPredicate(tempFileProvenance.toPath(), imageTag);

        log.info("Creating provenance-attestation attachment to PNC-Orch for build: {}", buildId);
        orchClient.createAttachmentProvenance(buildId, imageTag, sha256);

        if (config.isAddBuildAttribute()) {
            log.info("Creating build attribute for provenance-attestation for build: {}", buildId);
            orchClient.addProvenanceAttestationToBuildAttribute(buildId, imageSha);
        }

        log.info("Done for build: {}", buildId);

        return predicate;
    }
}
