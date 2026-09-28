package bd.ac.kuet.campuscycle.data;

import bd.ac.kuet.campuscycle.domain.event.CampusCycleEvent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thread-safe EventBus implementing the Observer pattern.
 * Allows decoupled pub-sub communication across UI views and background services.
 */
public class EventBus {

    private static final Logger LOGGER = Logger.getLogger(EventBus.class.getName());

    private static final EventBus INSTANCE = new EventBus();

    public static EventBus getInstance() {
        return INSTANCE;
    }

    private final Map<Class<? extends CampusCycleEvent>, List<Consumer<CampusCycleEvent>>> subscribers = new ConcurrentHashMap<>();

    private EventBus() {}

    /**
     * Subscribes a listener to a specific event type.
     */
    @SuppressWarnings("unchecked")
    public <T extends CampusCycleEvent> void subscribe(Class<T> eventType, Consumer<T> listener) {
        subscribers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                   .add((Consumer<CampusCycleEvent>) listener);
    }

    /**
     * Publishes an event to all subscribed listeners.
     *
     * <p>Dispatch contract: listeners run <b>synchronously on the publishing
     * thread</b> in subscription order. There is no thread hop. A listener
     * that touches the JavaFX scene graph must therefore either be published
     * from the FX Application Thread or wrap its UI work in
     * {@code Platform.runLater(...)} itself — otherwise it throws
     * {@code IllegalStateException: Not on FX thread}. A throwing listener
     * never affects other listeners or the publisher; the failure is logged.
     *
     * <p>Lifecycle: views must pair every {@link #subscribe} with an
     * {@link #unsubscribe} in {@code dispose()}, and sign-out must call
     * {@link #clearAll()} so stale views are never mutated (P-154).
     */
    public void publish(CampusCycleEvent event) {
        if (event == null) return;
        List<Consumer<CampusCycleEvent>> listeners = subscribers.get(event.getClass());
        if (listeners != null) {
            for (Consumer<CampusCycleEvent> listener : listeners) {
                try {
                    listener.accept(event);
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE,
                            "EventBus listener " + listener.getClass().getName()
                                    + " failed on event " + event.getClass().getSimpleName(), e);
                }
            }
        }
    }

    /**
     * Unsubscribes a previously registered listener.
     */
    @SuppressWarnings("unchecked")
    public <T extends CampusCycleEvent> void unsubscribe(Class<T> eventType, Consumer<T> listener) {
        List<Consumer<CampusCycleEvent>> listeners = subscribers.get(eventType);
        if (listeners != null) {
            listeners.remove((Consumer<CampusCycleEvent>) listener);
        }
    }

    /**
     * Clears all subscribers across all event channels.
     */
    public void clearAll() {
        subscribers.clear();
    }
}
