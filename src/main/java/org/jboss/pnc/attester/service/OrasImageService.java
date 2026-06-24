package org.jboss.pnc.attester.service;

import io.quarkiverse.oras.runtime.OrasRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import land.oras.ContainerRef;
import land.oras.LocalPath;
import land.oras.Manifest;
import land.oras.Registry;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@Slf4j
@ApplicationScoped
public class OrasImageService {

    // quarkus.oras.registries.quay.enabled=true
    // quarkus.oras.registries.quay.host=docker.io
    // quarkus.oras.registries."names".username
    // quarkus.oras.registries."names".password
    // quarkus.oras.registries."names".token
    @OrasRegistry("quay")
    Registry registry;

    /**
     * Creates and pushes a container image with multiple files using default artifact type.
     *
     * @param imageRef Full image reference (e.g., "localhost:5000/myimage:v1.0.0")
     * @param files List of file paths to include in the image
     * @return The pushed manifest
     * @throws IOException if the push operation fails
     */
    public Manifest createAndPushImage(String imageRef, List<Path> files) throws IOException {
        log.info("Creating and pushing image to {}", imageRef);

        ContainerRef ref = ContainerRef.parse(imageRef);

        LocalPath[] localPaths = files.stream()
                .map(LocalPath::of)
                .toArray(LocalPath[]::new);

        Manifest manifest = registry.pushArtifact(ref, localPaths);

        log.info("Successfully pushed image. Digest: {}", manifest.getDigest());

        return manifest;
    }

    /**
     * Creates and pushes a single-file container image.
     *
     * @param imageRef Full image reference (e.g., "localhost:5000/myimage:v1.0.0")
     * @param file The file to include in the image
     * @return The pushed manifest
     * @throws IOException if the push operation fails
     */
    public Manifest createAndPushImage(String imageRef, Path file) throws IOException {
        log.info("Creating and pushing single file to {}", imageRef);

        ContainerRef ref = ContainerRef.parse(imageRef);
        LocalPath localPath = LocalPath.of(file);

        Manifest manifest = registry.pushArtifact(ref, localPath);

        log.info("Successfully pushed image. Digest: {}", manifest.getDigest());

        return manifest;
    }
}
