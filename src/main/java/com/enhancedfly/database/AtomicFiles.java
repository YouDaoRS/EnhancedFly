package com.enhancedfly.database;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.bukkit.configuration.file.YamlConfiguration;

public final class AtomicFiles {
    private AtomicFiles() { }

    public static YamlConfiguration load(File file) {
        YamlConfiguration result = new YamlConfiguration();
        if (!file.exists()) return result;
        try { result.load(file); }
        catch (Exception e) { throw new IllegalStateException("无法读取数据文件: " + file, e); }
        return result;
    }

    public static void write(File file, String content) {
        Path temporary = null;
        try {
            Path target = file.toPath().toAbsolutePath();
            Files.createDirectories(target.getParent());
            temporary = Files.createTempFile(target.getParent(), file.getName(), ".tmp");
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            try { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException e) { throw new UncheckedIOException(e); }
        finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
    }
}
