package org.jboss.pnc.attester.utils.client;

import java.net.URI;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.faulttolerance.Retry;
import org.jboss.pnc.api.enums.AttachmentType;
import org.jboss.pnc.api.slsa.dto.provenance.v1.Provenance;
import org.jboss.pnc.attester.utils.configuration.AttesterConfig;
import org.jboss.pnc.client.AttachmentClient;
import org.jboss.pnc.client.BuildClient;
import org.jboss.pnc.client.Configuration;
import org.jboss.pnc.client.RemoteResourceException;
import org.jboss.pnc.client.SlsaProvenanceV1Client;
import org.jboss.pnc.dto.Attachment;
import org.jboss.pnc.dto.Build;
import org.jboss.pnc.quarkus.client.auth.runtime.PNCClientAuth;

import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class OrchClient {

    private static final String FULL_ATTESTATION_ATTRIBUTE = "SLSA_FULL_PROVENANCE_ATTESTATION";
    private static final String REDACTED_ATTESTATION_ATTRIBUTE = "SLSA_PROVENANCE_ATTESTATION";
    private static final String PUBLIC_KEY_OCI_REF_ATTRIBUTE = "SLSA_ATTESTATION_PUBLIC_KEY";

    @Inject
    PNCClientAuth pncClientAuth;

    @Inject
    AttesterConfig attesterConfig;

    BuildClient buildClient;
    SlsaProvenanceV1Client slsaClient;
    AttachmentClient attachmentClient;

    @PostConstruct
    public void init() {

        URI uri = URI.create(attesterConfig.getOrchUrl());

        Configuration configuration = Configuration.builder()
                .protocol(uri.getScheme())
                .host(uri.getHost())
                .pageSize(50)
                .addDefaultMdcToHeadersMappings()
                .build();

        Configuration configurationAuth = Configuration.builder()
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

    /**
     * Returns the complete non-redacted in-toto provenance statement from the
     * build-level endpoint. The caller must sign this entire object.
     */
    @Retry(maxRetries = 3, delay = 1000, jitter = 500)
    public Provenance getProvenance(String buildId) throws RemoteResourceException {
        return requireProvenance(slsaClient.getFromBuildId(buildId), buildId, false);
    }

    /**
     * Returns the complete redacted in-toto provenance statement from the
     * build-level redacted endpoint. The caller must sign this entire object.
     */
    @Retry(maxRetries = 3, delay = 1000, jitter = 500)
    public Provenance getRedactedProvenance(String buildId) throws RemoteResourceException {
        return requireProvenance(slsaClient.getFromBuildIdRedacted(buildId), buildId, true);
    }

    /**
     * Creates exactly two build-scoped provenance attachments: one containing
     * the signed full statement and one containing the signed redacted statement.
     */
    public void createProvenanceAttachments(
            String buildId,
            String fullReference,
            String fullManifestSha256,
            String redactedReference,
            String redactedManifestSha256) throws RemoteResourceException {

        Build build = buildClient.getSpecific(buildId);

        Attachment fullAttachment = Attachment.builder()
                .name("full-provenance.sigstore.json")
                .description("Sigstore bundle containing the signed full SLSA provenance for build " + buildId)
                .url(fullReference)
                .sha256(fullManifestSha256)
                .type(AttachmentType.PROVENANCE)
                .build(build)
                .build();

        Attachment redactedAttachment = Attachment.builder()
                .name("provenance.sigstore.json")
                .description("Sigstore bundle containing the signed redacted SLSA provenance for build " + buildId)
                .url(redactedReference)
                .sha256(redactedManifestSha256)
                .type(AttachmentType.PROVENANCE)
                .build(build)
                .build();

        attachmentClient.create(fullAttachment);
        attachmentClient.create(redactedAttachment);

        if (attesterConfig.isPublicKeyOciRefSpecified()) {
            Attachment publicKeyAttachment = Attachment.builder()
                    .name("slsa-attestation-public-key")
                    .description("SLSA Attestation Public Key OCI Ref for " + buildId)
                    .url(attesterConfig.getPublicKeyOciRef())
                    .sha256(attesterConfig.getPublicKeyOciSha256())
                    .type(AttachmentType.PROVENANCE)
                    .build(build)
                    .build();
            attachmentClient.create(publicKeyAttachment);
        }
    }

    public void addProvenanceAttestationBuildAttributes(
            String buildId,
            String fullReference,
            String redactedReference) throws RemoteResourceException {

        buildClient.addAttribute(buildId, FULL_ATTESTATION_ATTRIBUTE, fullReference);
        buildClient.addAttribute(buildId, REDACTED_ATTESTATION_ATTRIBUTE, redactedReference);

        if (attesterConfig.isPublicKeyOciRefSpecified()) {
            // NCL-9852: add to build attributes the public key oci ref
            buildClient.addAttribute(buildId, PUBLIC_KEY_OCI_REF_ATTRIBUTE, attesterConfig.getPublicKeyOciRef());
        }
    }

    /**
     * Check if buildId is present on PNC DB. It might not be present yet since it's still being stored in the DB
     * transaction.
     */
    public boolean isBuildIdAvailable(String buildId) {
        try {
            buildClient.getSpecific(buildId);
            return true;
        } catch (RemoteResourceException e) {
            return false;
        }
    }

    public boolean isBuildIdSuccessful(String buildId) {
        try {
            Build build = buildClient.getSpecific(buildId);
            return build.getStatus().completedSuccessfully();
        } catch (RemoteResourceException e) {
            throw new RuntimeException(e);
        }
    }

    private static Provenance requireProvenance(Provenance provenance, String buildId, boolean redacted) {
        if (provenance == null) {
            String variant = redacted ? "redacted" : "full";
            throw new IllegalStateException("No " + variant + " provenance found for build: " + buildId);
        }
        return provenance;
    }
}
