package bd.ac.kuet.campuscycle.service;

import javafx.application.Platform;
import javafx.scene.control.Alert;

import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Concurrency coordinator managing thread pools for background operations.
 * Prevents blocking the JavaFX Application Thread.
 *
 * <p>Pool sizing (deliberate, no resize needed): {@code IO_POOL} is a fixed
 * pool of <b>8 daemon threads</b> shared by blocking JDBC calls, HTTP calls
 * (up to ~8s timeouts) and short IP-geolocation lookups (~1.5s). Eight
 * workers keep one slow query from starving the rest while bounding total
 * Postgres connections well under the pool limit. {@code SCHEDULED_POOL}
 * has <b>2 daemon threads</b> for lightweight periodic ticks (clocks,
 * polling) only — never blocking I/O. Daemon threads never prevent JVM
 * exit; {@link #shutdown()} additionally interrupts and awaits in-flight
 * work so DB transactions are not silently abandoned (P-153).
 */
public class AppExecutor {

    private static final Logger LOGGER = Logger.getLogger(AppExecutor.class.getName());

    private static final int IO_THREADS = 8;
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
    public static <T> void asyncThenFx(Supplier<T> backgroundTask, Consumer<T> uiConsumer) {
        asyncThenFx(backgroundTask, uiConsumer, null);
    }

    /**
     * Executes a background task and delivers the result on the JavaFX UI thread with error handling.
     */
    public static <T> void asyncThenFx(Supplier<T> backgroundTask, Consumer<T> uiConsumer, Consumer<Throwable> errorHandler) {
        supplyAsync(backgroundTask).whenComplete((result, throwable) -> {
            try {
                Platform.runLater(() -> {
                    if (throwable != null) {
                        if (errorHandler != null) {
                            try {
                                errorHandler.accept(throwable);
                            } catch (Exception e) {
                                LOGGER.log(Level.SEVERE, "AppExecutor error handler failed", e);
                            }
                        } else {
                            defaultErrorHandler(throwable);
                        }
                    } else {
                        if (uiConsumer != null) uiConsumer.accept(result);
                    }
                });
            } catch (IllegalStateException e) {
                // Toolkit not initialised or already shut down: log, never throw.
                LOGGER.log(Level.SEVERE, "AppExecutor could not deliver result: JavaFX toolkit unavailable", throwable != null ? throwable : e);
            }
        });
    }

    /**
     * Default failure path when a caller supplies no error handler: logs the
     * failure and shows an error Alert. Already on the FX thread when called;
     * guarded so a torn-down toolkit can never throw out of here (P-153).
     */
    private static void defaultErrorHandler(Throwable throwable) {
        LOGGER.log(Level.SEVERE, "Background task failed with no error handler", throwable);
        try {
            String detail = throwable != null && throwable.getMessage() != null
                    ? throwable.getMessage() : String.valueOf(throwable);
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    "Something went wrong:\n" + detail);
            alert.setHeaderText("Operation failed");
            alert.showAndWait();
        } catch (IllegalStateException | UnsupportedOperationException e) {
            LOGGER.log(Level.WARNING, "Could not show error alert: JavaFX toolkit unavailable", e);
        }
    }

    /**
     * Schedules a recurring periodic task on a daemon thread.
     */
    public static ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        return SCHEDULED_POOL.scheduleAtFixedRate(command, initialDelay, period, unit);
    }

    /**
     * Shuts down all thread pools gracefully: interrupts in-flight work and
     * awaits termination up to 2s per pool so DB transactions are given a
     * chance to finish instead of being abandoned mid-flight (P-153).
     */
    public static void shutdown() {
        IO_POOL.shutdownNow();
        SCHEDULED_POOL.shutdownNow();
        try {
            if (!IO_POOL.awaitTermination(2, TimeUnit.SECONDS)) {
                LOGGER.warning("AppExecutor IO pool did not terminate within 2s");
            }
            if (!SCHEDULED_POOL.awaitTermination(2, TimeUnit.SECONDS)) {
                LOGGER.warning("AppExecutor scheduler pool did not terminate within 2s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.WARNING, "AppExecutor shutdown interrupted", e);
        }
    }
}
