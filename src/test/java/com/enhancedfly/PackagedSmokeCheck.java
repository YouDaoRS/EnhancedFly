package com.enhancedfly;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.*;
import javax.sql.DataSource;

/** Verify shaded services, driver classes and SQLite native resources without a Minecraft server. */
public final class PackagedSmokeCheck {
    public static void main(String[] args) throws Exception {
        URL jar = new File(args[0]).toURI().toURL();
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
}
