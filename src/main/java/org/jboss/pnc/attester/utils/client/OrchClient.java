package org.jboss.pnc.attester.utils.client;

import java.net.URI;
import java.util.Collection;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.pnc.api.slsa.dto.provenance.v1.Predicate;
import org.jboss.pnc.api.slsa.dto.provenance.v1.Provenance;
import org.jboss.pnc.attester.utils.configuration.AttesterConfig;
import org.jboss.pnc.client.BuildClient;
import org.jboss.pnc.client.Configuration;
import org.jboss.pnc.client.RemoteResourceException;
import org.jboss.pnc.client.SlsaProvenanceV1Client;
import org.jboss.pnc.dto.Artifact;

import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class OrchClient {

    @Inject
    AttesterConfig attesterConfig;

    Configuration configuration;
    BuildClient buildClient;
    SlsaProvenanceV1Client slsaClient;

    @PostConstruct
    public void init() {
        String orchUrl = attesterConfig.getOrchUrl();
        URI uri = URI.create(orchUrl);
        configuration = Configuration.builder()
                .protocol(uri.getScheme())
                .host(uri.getHost())
                .pageSize(50)
                .addDefaultMdcToHeadersMappings()
                .build();

        buildClient = new BuildClient(configuration);
        slsaClient = new SlsaProvenanceV1Client(configuration);
    }

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

    public String getAnyArtifactIdForBuild(String buildId) throws RemoteResourceException {
        BuildClient buildClient = new BuildClient(configuration);
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
}