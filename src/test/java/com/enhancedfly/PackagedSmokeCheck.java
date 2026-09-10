package com.enhancedfly;

import java.io.File;
import java.io.DataInputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import javax.sql.DataSource;

/** Verify shaded services, driver classes and SQLite native resources without a Minecraft server. */
public final class PackagedSmokeCheck {
    public static void main(String[] args) throws Exception {
        File jarFile = new File(args[0]);
        verifyCompatibilityMetadata(jarFile);
        URL jar = jarFile.toURI().toURL();
        try (URLClassLoader loader = new URLClassLoader(new URL[]{jar}, ClassLoader.getPlatformClassLoader())) {
            Thread.currentThread().setContextClassLoader(loader);
            Driver mysql = (Driver) Class.forName("com.enhancedfly.libs.mysql.cj.jdbc.Driver", true, loader).getConstructor().newInstance();
            if (!mysql.acceptsURL("jdbc:mysql://localhost/test")) throw new AssertionError("MySQL driver missing");
            Class<?> messages = Class.forName("com.enhancedfly.libs.mysql.cj.Messages", true, loader);
            messages.getMethod("getString", String.class).invoke(null, "Connection.0");
            Class<?> type = Class.forName("com.enhancedfly.libs.hikari.HikariConfig", true, loader);
            Object config = type.getConstructor().newInstance();
            type.getMethod("setJdbcUrl", String.class).invoke(config, "jdbc:sqlite::memory:");
            type.getMethod("setDriverClassName", String.class).invoke(config, "org.sqlite.JDBC");
            type.getMethod("setMaximumPoolSize", int.class).invoke(config, 1);
            Object pool = Class.forName("com.enhancedfly.libs.hikari.HikariDataSource", true, loader).getConstructor(type).newInstance(config);
            try {
                try (Connection connection = ((DataSource) pool).getConnection(); Statement statement = connection.createStatement()) {
                    statement.execute("CREATE TABLE smoke (value INTEGER)");
                    statement.execute("INSERT INTO smoke VALUES (42)");
                    try (ResultSet result = statement.executeQuery("SELECT value FROM smoke")) {
                        if (!result.next() || result.getInt(1) != 42) throw new AssertionError("SQLite read/write failed");
                    }
                }
            } finally { ((AutoCloseable) pool).close(); }
            System.out.println("Packaged JDBC / HikariCP / native SQLite smoke check passed");
        }
    }

    private static void verifyCompatibilityMetadata(File file) throws Exception {
        try (JarFile jar = new JarFile(file)) {
            JarEntry descriptor = jar.getJarEntry("plugin.yml");
            if (descriptor == null) throw new AssertionError("plugin.yml missing");
            String pluginYml;
            try (InputStream input = jar.getInputStream(descriptor)) {
                pluginYml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (!pluginYml.contains("api-version: '1.16'")) {
                throw new AssertionError("Plugin API baseline is not 1.16");
            }

            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class") || targetsNewerMultiReleaseRuntime(entry.getName())) continue;
                try (DataInputStream input = new DataInputStream(jar.getInputStream(entry))) {
                    if (input.readInt() != 0xCAFEBABE) throw new AssertionError("Invalid class: " + entry.getName());
                    input.readUnsignedShort();
                    int major = input.readUnsignedShort();
                    if (major > 55) throw new AssertionError("Java 11 incompatible class: " + entry.getName() + " (" + major + ")");
                }
            }
        }
        System.out.println("Plugin API 1.16 / Java 11 compatibility metadata check passed");
    }

    private static boolean targetsNewerMultiReleaseRuntime(String name) {
        String prefix = "META-INF/versions/";
        if (!name.startsWith(prefix)) return false;
        int slash = name.indexOf('/', prefix.length());
        if (slash < 0) return false;
        try {
            return Integer.parseInt(name.substring(prefix.length(), slash)) > 11;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
}
