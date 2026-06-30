package org.jboss.pnc.attester.utils.client;

import java.net.URI;
import java.util.Collection;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.faulttolerance.Retry;
import org.jboss.pnc.api.enums.AttachmentType;
import org.jboss.pnc.api.slsa.dto.provenance.v1.Predicate;
import org.jboss.pnc.api.slsa.dto.provenance.v1.Provenance;
import org.jboss.pnc.attester.utils.configuration.AttesterConfig;
import org.jboss.pnc.client.AttachmentClient;
import org.jboss.pnc.client.BuildClient;
import org.jboss.pnc.client.Configuration;
import org.jboss.pnc.client.RemoteResourceException;
import org.jboss.pnc.client.SlsaProvenanceV1Client;
import org.jboss.pnc.dto.Artifact;
import org.jboss.pnc.dto.Attachment;
import org.jboss.pnc.dto.Build;
import org.jboss.pnc.quarkus.client.auth.runtime.PNCClientAuth;

import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class OrchClient {

    @Inject
    PNCClientAuth pncClientAuth;

    @Inject
    AttesterConfig attesterConfig;

    BuildClient buildClient;
    SlsaProvenanceV1Client slsaClient;
    AttachmentClient attachmentClient;

    @PostConstruct
    public void init() {

        String orchUrl = attesterConfig.getOrchUrl();
        URI uri = URI.create(orchUrl);

        Configuration configuration;
        configuration = Configuration.builder()
                .protocol(uri.getScheme())
                .host(uri.getHost())
                .pageSize(50)
                .addDefaultMdcToHeadersMappings()
                .build();

        Configuration configurationAuth;
        configurationAuth = Configuration.builder()
                .protocol(uri.getScheme())
                .host(uri.getHost())
                .pageSize(50)
                .addDefaultMdcToHeadersMappings()
                .bearerTokenSupplier(() -> pncClientAuth.getAuthToken())
                .build();

        buildClient = new BuildClient(configurationAuth);
        slsaClient = new SlsaProvenanceV1Client(configuration);
        attachmentClient = new AttachmentClient(configurationAuth);
    }

    @Retry(maxRetries = 3, delay = 1000, jitter = 500)
    public Predicate getProvenancePredicate(String buildId) throws RemoteResourceException {

        String artifactId = getAnyArtifactIdForBuild(buildId);
        if (artifactId == null) {
            throw new RuntimeException("No artifact for build: " + buildId + ". Aborting!");
        }
        Provenance provenance = slsaClient.getFromArtifactId(artifactId);
        if (provenance == null) {
            throw new RuntimeException(
                    "Aborting! No provenance for artifactId: " + artifactId + " for build: " + buildId);
        }
        return provenance.getPredicate();
    }

    @Retry(maxRetries = 3, delay = 1000, jitter = 500)
    public String getAnyArtifactIdForBuild(String buildId) throws RemoteResourceException {
        Collection<Artifact> artifacts = buildClient.getBuiltArtifacts(buildId).getAll();

        if (artifacts != null && !artifacts.isEmpty()) {

            for (Artifact artifact : artifacts) {
                // just return the first one
                return artifact.getId();
            }
        }

        // if we're here, no artifact was found
        return null;
    }

    public void createAttachmentProvenance(String buildId, String url, String sha256) throws RemoteResourceException {

        Build build = buildClient.getSpecific(buildId);

        Attachment attachment = Attachment.builder()
                .name("provenance-attachment")
                .description("Provenance Attachment")
                .url(url)
                .sha256(sha256)
                .type(AttachmentType.PROVENANCE)
                .build(build)
                .build();

        attachmentClient.create(attachment);
    }

    public void addProvenanceAttestationToBuildAttribute(String buildId, String imageSha)
            throws RemoteResourceException {
        buildClient.addAttribute(buildId, "PROVENANCE_ATTESTATION", imageSha);
    }
}