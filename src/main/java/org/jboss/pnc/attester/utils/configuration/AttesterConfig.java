package org.jboss.pnc.attester.utils.configuration;

import java.nio.file.Path;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import lombok.Getter;

@ApplicationScoped
@Getter
public class AttesterConfig {

    @ConfigProperty(name = "attester.orchUrl")
    String orchUrl;

    @ConfigProperty(name = "attester.container-registry.username")
    String containerRegistryUsername;

    @ConfigProperty(name = "attester.container-registry.password")
    String containerRegistryPassword;

    @ConfigProperty(name = "attester.container-registry.image")
    String containerImage;

    @ConfigProperty(name = "attester.cosign.privateKeyPath")
    Path cosignPrivateKeyPath;

    @ConfigProperty(name = "attester.cosign.privateKeyPassword")
    String cosignPrivateKeyPassword;

    @ConfigProperty(name = "attester.cosign.publicKeyPath")
    Path cosignPublicKeyPath;

    @ConfigProperty(name = "attester.cosign.signingConfigPath")
    Optional<Path> cosignSigningConfigPath;

    @ConfigProperty(name = "attester.addBuildAttribute", defaultValue = "true")
    boolean addBuildAttribute;

    @ConfigProperty(name = "attester.kafkaListenerAttest", defaultValue = "false")
    boolean kafkaListenerAttest;

    // Used to push to build-attributes where to find the public key oci ref to verify the bundle
    @ConfigProperty(name = "attester.publicKey.ociImageRegistry", defaultValue = "")
    String publicKeyOciImageRegistry;

    @ConfigProperty(name = "attester.publicKey.ociSha256", defaultValue = "")
    String publicKeyOciSha256;

    public boolean isPublicKeyOciRefSpecified() {
        return publicKeyOciImageRegistry != null && !publicKeyOciImageRegistry.isBlank() && publicKeyOciSha256 != null
                && !publicKeyOciSha256.isBlank();
    }

    public String getPublicKeyOciRef() {
        return publicKeyOciImageRegistry + "@sha256:" + publicKeyOciSha256;
    }
}
