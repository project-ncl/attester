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
}
