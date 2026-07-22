package org.jboss.pnc.attester.utils.hash;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DigestGenerator {

    private static final List<String> ALGORITHMS = List.of("MD5", "SHA-1", "SHA-256");

    public List<Path> generateDigests(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Not a regular file: " + file);
        }

        String fileName = file.getFileName().toString();
        Path parent = file.toAbsolutePath().getParent();

        byte[][] digests = computeAll(file);

        Path md5File = parent.resolve(fileName + ".md5");
        Path sha1File = parent.resolve(fileName + ".sha1");
        Path sha256File = parent.resolve(fileName + ".sha256");

        HexFormat hex = HexFormat.of();
        Files.writeString(md5File, hex.formatHex(digests[0]));
        Files.writeString(sha1File, hex.formatHex(digests[1]));
        Files.writeString(sha256File, hex.formatHex(digests[2]));

        return List.of(md5File, sha1File, sha256File);
    }

    private byte[][] computeAll(Path file) throws IOException {
        MessageDigest[] mds = ALGORITHMS.stream().map(DigestGenerator::newDigest).toArray(MessageDigest[]::new);

        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[8192];
            int read;
            while ((read = in.read(buf)) != -1) {
                for (MessageDigest md : mds) {
                    md.update(buf, 0, read);
                }
            }
        }

        byte[][] results = new byte[mds.length][];
        for (int i = 0; i < mds.length; i++) {
            results[i] = mds[i].digest();
        }
        return results;
    }

    private static MessageDigest newDigest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
