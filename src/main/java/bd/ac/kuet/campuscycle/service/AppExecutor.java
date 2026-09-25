package bd.ac.kuet.campuscycle.service;

import javafx.application.Platform;

import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Concurrency coordinator managing thread pools for background operations.
 * Prevents blocking the JavaFX Application Thread.
 */
public class AppExecutor {

    private static final int IO_THREADS = 4;
    private static final ExecutorService IO_POOL = Executors.newFixedThreadPool(IO_THREADS, new ThreadFactory() {
        private int count = 1;
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "CampusCycle-Worker-" + count++);
            t.setDaemon(true);
            return t;
        }
    });

    private static final ScheduledExecutorService SCHEDULED_POOL = Executors.newScheduledThreadPool(2, new ThreadFactory() {
        private int count = 1;
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "CampusCycle-Scheduler-" + count++);
            t.setDaemon(true);
            return t;
        }
    });

    public static ExecutorService io() {
        return IO_POOL;
    }

    public static void execute(Runnable runnable) {
        IO_POOL.execute(runnable);
    }

    /**
     * Executes an asynchronous task in the worker thread pool.
     */
    public static CompletableFuture<Void> runAsync(Runnable runnable) {
        return CompletableFuture.runAsync(runnable, IO_POOL);
    }

    /**
     * Executes an asynchronous task with a return value in the worker thread pool.
     */
    public static <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, IO_POOL);
    }

    /**
     * Executes a background task and delivers the result on the JavaFX UI thread.
     */
    public static <T> void asyncThenFx(Supplier<T> backgroundTask, Consumer<T> uiConsumer, Consumer<Throwable> errorHandler) {
        supplyAsync(backgroundTask).whenComplete((result, throwable) -> {
            Platform.runLater(() -> {
                if (throwable != null) {
                    if (errorHandler != null) errorHandler.accept(throwable);
                    else throwable.printStackTrace();
                } else {
                    if (uiConsumer != null) uiConsumer.accept(result);
                }
            });
        });
    }

    /**
     * Schedules a recurring periodic task on a daemon thread.
     */
    public static ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        return SCHEDULED_POOL.scheduleAtFixedRate(command, initialDelay, period, unit);
    }

    /**
     * Shuts down all thread pools gracefully.
     */
    public static void shutdown() {
        IO_POOL.shutdown();
        SCHEDULED_POOL.shutdown();
    }
}
