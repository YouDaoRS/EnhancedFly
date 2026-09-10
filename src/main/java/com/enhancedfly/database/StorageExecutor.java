package com.enhancedfly.database;

import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** One ordered queue for database and file IO; never accesses Bukkit player state. */
public final class StorageExecutor {
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "EnhancedFly-storage");
        thread.setDaemon(true);
        return thread;
    });
    private final Logger logger;

    public StorageExecutor(Logger logger) { this.logger = logger; }

    public <T> CompletableFuture<T> submit(Supplier<T> operation) {
        CompletableFuture<T> future = CompletableFuture.supplyAsync(operation, executor);
        future.whenComplete((result, failure) -> {
            if (failure != null) logger.log(Level.SEVERE, "存储操作失败，数据尚未确认保存", failure);
        });
        return future;
    }

    public CompletableFuture<Void> run(Runnable operation) {
        return submit(() -> { operation.run(); return null; });
    }

    public void close() {
        executor.shutdown();
        boolean interrupted = false;
        // Do not close JDBC connections while a queued final save is still using them.
        while (!executor.isTerminated()) {
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    logger.warning("正在等待存储队列完成，请勿强制结束服务器进程");
                }
            } catch (InterruptedException e) { interrupted = true; }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }
}
