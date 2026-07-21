package org.jboss.pnc.attester.utils.hash;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class DigestGeneratorTest {

    @Inject
    DigestGenerator digestGenerator;

    @TempDir
    Path tempDir;

    @Test
    void generateDigests_producesCorrectHashFiles() throws Exception {
        Path input = tempDir.resolve("artifact.jar");
        byte[] content = "hello world".getBytes();
        Files.write(input, content);

        List<Path> result = digestGenerator.generateDigests(input);

        assertEquals(3, result.size());

        String md5 = Files.readString(result.get(0));
        String sha1 = Files.readString(result.get(1));
        String sha256 = Files.readString(result.get(2));

        HexFormat hex = HexFormat.of();
        assertEquals(hex.formatHex(MessageDigest.getInstance("MD5").digest(content)), md5);
        assertEquals(hex.formatHex(MessageDigest.getInstance("SHA-1").digest(content)), sha1);
        assertEquals(hex.formatHex(MessageDigest.getInstance("SHA-256").digest(content)), sha256);
    }

    @Test
    void generateDigests_createsFilesWithCorrectNames() throws Exception {
        Path input = tempDir.resolve("bundle.sigstore");
        Files.write(input, new byte[] { 1, 2, 3 });

        List<Path> result = digestGenerator.generateDigests(input);

        assertEquals(tempDir.resolve("bundle.sigstore.md5"), result.get(0));
        assertEquals(tempDir.resolve("bundle.sigstore.sha1"), result.get(1));
        assertEquals(tempDir.resolve("bundle.sigstore.sha256"), result.get(2));

        for (Path p : result) {
            assertTrue(Files.exists(p));
        }
    }

    @Test
    void generateDigests_handlesEmptyFile() throws Exception {
        Path input = tempDir.resolve("empty.txt");
        Files.write(input, new byte[0]);

        List<Path> result = digestGenerator.generateDigests(input);

        HexFormat hex = HexFormat.of();
        assertEquals(
                hex.formatHex(MessageDigest.getInstance("MD5").digest(new byte[0])),
                Files.readString(result.get(0)));
        assertEquals(
                hex.formatHex(MessageDigest.getInstance("SHA-1").digest(new byte[0])),
                Files.readString(result.get(1)));
        assertEquals(
                hex.formatHex(MessageDigest.getInstance("SHA-256").digest(new byte[0])),
                Files.readString(result.get(2)));
    }

    @Test
    void generateDigests_handlesLargeFile() throws Exception {
        Path input = tempDir.resolve("large.bin");
        byte[] content = new byte[32768];
        for (int i = 0; i < content.length; i++) {
            content[i] = (byte) (i % 251);
        }
        Files.write(input, content);

        List<Path> result = digestGenerator.generateDigests(input);

        HexFormat hex = HexFormat.of();
        assertEquals(
                hex.formatHex(MessageDigest.getInstance("SHA-256").digest(content)),
                Files.readString(result.get(2)));
    }

    @Test
    void generateDigests_throwsOnNonExistentFile() {
        Path missing = tempDir.resolve("does-not-exist.jar");

        assertThrows(IllegalArgumentException.class, () -> digestGenerator.generateDigests(missing));
    }

    @Test
    void generateDigests_throwsOnDirectory() {
        assertThrows(IllegalArgumentException.class, () -> digestGenerator.generateDigests(tempDir));
    }
}
