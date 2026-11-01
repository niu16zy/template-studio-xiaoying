package com.xiaoying.studio.task;

import java.io.IOException;
import java.nio.file.*;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class TaskResultStorage {
    private final Path root;
    public TaskResultStorage(@Value("${studio.storage-root:/app/data}") String configured) throws IOException {
        Path directory = Path.of(configured).toAbsolutePath().normalize();
        Files.createDirectories(directory);
        root = directory.toRealPath();
        Files.createDirectories(root.resolve("work"));
        Files.createDirectories(root.resolve("results"));
    }

    public void generate(String taskId, String sourceName) throws IOException {
        if (sourceName == null || !sourceName.matches("[a-z0-9-]+\\.png")) throw new IOException("Invalid result source");
        var source = new ClassPathResource("demo/" + sourceName);
        Path path = temporary(taskId);
        verifyParent(path);
        try (var input = source.getInputStream()) { Files.copy(input, path); }
    }

    public String finalizeResult(String taskId) throws IOException {
        Path temporary = temporary(taskId);
        verifyExisting(temporary);
        try (var input = Files.newInputStream(temporary)) {
            byte[] signature = input.readNBytes(8);
            if (!java.util.Arrays.equals(signature, new byte[]{(byte)137,80,78,71,13,10,26,10})) {
                throw new IOException("Result is not PNG");
            }
        }
        var decoded = ImageIO.read(temporary.toFile());
        if (decoded == null) throw new IOException("Result cannot be decoded");
        decoded.flush();
        String relative = "results/" + checkedId(taskId) + ".png";
        Path target = root.resolve(relative);
        verifyParent(target);
        try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException exception) { Files.move(temporary, target); }
        return relative;
    }

    public Path readable(String relative) throws IOException {
        if (relative == null) throw new IOException("No result path");
        Path path = root.resolve(relative).normalize();
        verifyExisting(path);
        return path;
    }
    public void cleanTemporary(String taskId) throws IOException {
        Path path = temporary(taskId);
        verifyParent(path);
        Files.deleteIfExists(path);
    }
    private Path temporary(String id) { return root.resolve("work/" + checkedId(id) + ".png"); }
    private static String checkedId(String id) {
        String parsed = java.util.UUID.fromString(id).toString();
        if (!parsed.equals(id)) throw new IllegalArgumentException("Invalid task ID");
        return parsed;
    }
    private void verifyParent(Path path) throws IOException {
        if (!path.normalize().startsWith(root) || !path.getParent().toRealPath().startsWith(root)) throw new IOException("Invalid storage path");
    }
    private void verifyExisting(Path path) throws IOException {
        verifyParent(path);
        if (!Files.isRegularFile(path) || !Files.isReadable(path) || !path.toRealPath().startsWith(root)) throw new IOException("Missing result file");
    }
}
