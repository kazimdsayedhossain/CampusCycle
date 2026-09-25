package bd.ac.kuet.campuscycle.data;

import bd.ac.kuet.campuscycle.domain.event.CampusCycleEvent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Thread-safe EventBus implementing the Observer pattern.
 * Allows decoupled pub-sub communication across UI views and background services.
 */
public class EventBus {

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
     */
    public void publish(CampusCycleEvent event) {
        if (event == null) return;
        List<Consumer<CampusCycleEvent>> listeners = subscribers.get(event.getClass());
        if (listeners != null) {
            for (Consumer<CampusCycleEvent> listener : listeners) {
                try {
                    listener.accept(event);
                } catch (Exception e) {
                    System.err.println("Error in event listener: " + e.getMessage());
                }
            }
        }
    }
}
