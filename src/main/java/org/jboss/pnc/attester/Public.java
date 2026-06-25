package org.jboss.pnc.attester;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
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

    @GET
    @Path("/attest/{buildId}")
    @Produces(APPLICATION_JSON)
    public Predicate attest(String buildId) throws Exception {

        log.info("Fetching predicate for build: {}", buildId);

        Predicate predicate = orchClient.getProvenancePredicate(buildId);

        String image = config.getContainerImage() + ":build-" + buildId;
        File tempFile = File.createTempFile("pnc-build-", buildId);
        Files.writeString(tempFile.toPath(), buildId, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        log.info("Creating image {} for build: {}", image, buildId);
        orasWrapper.createContainerImage(image, tempFile.toPath());
        tempFile.delete();

        log.info("Cosigning image {} for build: {}", image, buildId);

        File tempFileProvenance = File.createTempFile("pnc-provenance-", buildId);
        objectMapper.writeValue(tempFileProvenance, predicate);
        cosignWrapper.attestPredicate(tempFileProvenance.toPath(), image);

        log.info("Done for build: {}", buildId);

        return predicate;
    }
}
