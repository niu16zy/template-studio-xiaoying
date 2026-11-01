package com.xiaoying.studio.asset;

import java.io.IOException;
import java.nio.file.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LocalAssetStorage {
    private final Path root;
    public LocalAssetStorage(@Value("${studio.storage-root:/app/data}") String configuredRoot) throws IOException {
        Path configured = Path.of(configuredRoot).toAbsolutePath().normalize();
        Files.createDirectories(configured);
        this.root = configured.toRealPath();
        Files.createDirectories(root.resolve("uploads"));
    }

    public String write(String id, ImageValidator.CheckedImage image) throws IOException {
        String relative = "uploads/" + id + "." + image.extension();
        Path destination = root.resolve(relative);
        if (!destination.getParent().toRealPath().startsWith(root)) throw new IOException("Storage directory escapes root");
        Path temporary = Files.createTempFile(destination.getParent(), "upload-", ".tmp");
        try {
            Files.write(temporary, image.bytes());
            try { Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException exception) { Files.move(temporary, destination); }
            return relative;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public Path readable(String relative) throws IOException {
        Path path = root.resolve(relative).normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)
                || !path.toRealPath().startsWith(root) || !Files.isReadable(path)) {
            throw new IOException("Asset is missing or outside storage root");
        }
        return path;
    }

    public void delete(String relative) throws IOException {
        Path path = root.resolve(relative).normalize();
        if (!path.startsWith(root) || !path.getParent().toRealPath().startsWith(root)) throw new IOException("Invalid asset path");
        Files.deleteIfExists(path);
    }
}
