package com.enhancedfly;

import com.enhancedfly.database.*;
import com.enhancedfly.data.PlayerFlyData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.sql.*;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StorageTest {
    @TempDir Path directory;
    EnhancedFly plugin;
    StorageExecutor executor;
    SQLiteManager database;

    @BeforeEach void setup() {
        plugin = mock(EnhancedFly.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("database.sqlite.enabled", true);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        executor = new StorageExecutor(plugin.getLogger());
        when(plugin.getStorageExecutor()).thenReturn(executor);
        database = new SQLiteManager(plugin);
        database.connect();
    }
    @AfterEach void teardown() {
        executor.close();
        database.disconnect();
    }
    PlayerFlyData player() { return new PlayerFlyData(UUID.randomUUID(), "TestPlayer"); }

    @Test void snapshotsAreOrderedAndIndependentFromLaterMutations() throws Exception {
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        executor.run(() -> {
            started.countDown();
            try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
            catch (InterruptedException e) { throw new RuntimeException(e); }
        });
        assertTrue(started.await(5, TimeUnit.SECONDS));
        PlayerFlyData data = player();
        data.addTime(60);
        database.savePlayerData(data);
        data.consumeTime(10);
        CompletableFuture<Void> saved = database.savePlayerData(data);
        data.consumeTime(40);
        release.countDown();
        saved.join();
        assertEquals(50, database.loadPlayerData(data.getUuid(), "Renamed").join().getTempFlyTime());
    }
    @Test void failedStatisticsWriteRollsBackPlayerBalance() {
        PlayerFlyData data = player(); data.addTime(100);
        database.savePlayerData(data).join();
        executor.run(() -> {
            try (Statement statement = database.getConnection().createStatement()) {
                statement.execute("CREATE TRIGGER fail_stats BEFORE INSERT ON enhancedfly_statistics BEGIN SELECT RAISE(ABORT, 'test failure'); END");
            } catch (SQLException e) { throw new RuntimeException(e); }
        }).join();
        data.consumeTime(50);
        assertThrows(CompletionException.class, () -> database.savePlayerData(data).join());
        assertEquals(100L, executor.submit(() -> {
            try (Statement statement = database.getConnection().createStatement(); ResultSet rows = statement.executeQuery("SELECT temp_fly_time FROM enhancedfly_players")) {
                rows.next(); return rows.getLong(1);
            } catch (SQLException e) { throw new RuntimeException(e); }
        }).join());
        // The pending absolute snapshot remains recoverable instead of disappearing at shutdown.
        assertEquals(50, database.loadPlayerData(data.getUuid(), "TestPlayer").join().getTempFlyTime());
    }
    @Test void failedReadIsNotReportedAsNewPlayer() {
        executor.run(() -> {
            try (Statement statement = database.getConnection().createStatement()) {
                statement.execute("DROP TABLE enhancedfly_players");
            } catch (SQLException e) { throw new RuntimeException(e); }
        }).join();
        assertThrows(CompletionException.class, () -> database.loadPlayerData(UUID.randomUUID(), "Test").join());
    }
    @Test void newPlayerHasNoDatabaseRow() { assertNull(database.loadPlayerData(UUID.randomUUID(), "Test").join()); }
    @Test void statisticsAndAchievementsRoundTrip() {
        PlayerFlyData data = player();
        data.setHasPermanentFly(true); data.addTotalFlyTime(19); data.addDistance(28.5);
        data.incrementPurchases(); data.updateMaxAltitude(208); data.setFirstFlyDate(123); data.setLastFlyDate(456);
        database.savePlayerData(data).join();
        database.unlockAchievement(data.getUuid(), "first_flight").join();
        database.unlockAchievement(data.getUuid(), "first_flight").join();
        PlayerFlyData read = database.loadPlayerData(data.getUuid(), "TestPlayer").join();
        assertAll(() -> assertTrue(read.hasPermanentFly()), () -> assertEquals(19, read.getTotalFlyTime()),
            () -> assertEquals(28.5, read.getTotalDistance()), () -> assertEquals(1, read.getTotalPurchases()),
            () -> assertEquals(208, read.getMaxAltitude()), () -> assertEquals(123, read.getFirstFlyDate()),
            () -> assertEquals(456, read.getLastFlyDate()),
            () -> assertEquals(1, database.getPlayerAchievements(data.getUuid()).join().size()));
    }
    @Test void dynamicSqlRejectsUnknownColumns() {
        assertThrows(IllegalArgumentException.class, () -> database.updateStatistics(UUID.randomUUID(), "uuid); DROP TABLE enhancedfly_players;--", 1));
    }
    @Test void shutdownDrainsFinalSave() {
        PlayerFlyData data = player(); data.addTime(1234);
        database.savePlayerData(data);
        executor.close();
        try (Statement statement = database.getConnection().createStatement(); ResultSet rows = statement.executeQuery("SELECT temp_fly_time FROM enhancedfly_players")) {
            assertTrue(rows.next()); assertEquals(1234, rows.getLong(1));
        } catch (SQLException e) { fail(e); }
    }
    @Test void invalidTimeCannotRemoveOrOverflowBalance() {
        PlayerFlyData data = player(); data.addTime(Long.MAX_VALUE);
        assertThrows(IllegalArgumentException.class, () -> data.addTime(1));
        assertThrows(IllegalArgumentException.class, () -> data.addTime(-1));
        assertThrows(IllegalArgumentException.class, () -> data.addTime(0));
        assertThrows(IllegalArgumentException.class, () -> data.consumeTime(-1));
        assertEquals(Long.MAX_VALUE, data.getTempFlyTime());
    }
    @Test void atomicYamlReplacementAndMalformedInput() throws Exception {
        Path file = directory.resolve("test.yml");
        AtomicFiles.write(file.toFile(), "balance: 100\n");
        AtomicFiles.write(file.toFile(), "balance: 80\n");
        assertEquals(80, AtomicFiles.load(file.toFile()).getInt("balance"));
        java.nio.file.Files.writeString(file, "balance: [broken\n");
        assertThrows(IllegalStateException.class, () -> AtomicFiles.load(file.toFile()));
    }
}
