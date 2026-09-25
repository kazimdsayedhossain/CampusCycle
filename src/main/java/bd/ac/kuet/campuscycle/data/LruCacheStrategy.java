package bd.ac.kuet.campuscycle.data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Concrete LRU (Least Recently Used) cache strategy.
 * Implements CacheStrategy using an access-ordered LinkedHashMap.
 */
public class LruCacheStrategy<K, V> implements CacheStrategy<K, V> {

    private final int capacity;
    private final Map<K, V> map;

    public LruCacheStrategy(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        this.capacity = capacity;
        this.map = new LinkedHashMap<>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > LruCacheStrategy.this.capacity;
            }
        };
    }

    @Override
    public synchronized Optional<V> get(K key) {
        return Optional.ofNullable(map.get(key));
    }

    @Override
    public synchronized void put(K key, V value) {
        if (key != null && value != null) {
            map.put(key, value);
        }
    }

    @Override
    public synchronized void evict(K key) {
        if (key != null) {
            map.remove(key);
        }
    }

    @Override
    public synchronized void clear() {
        map.clear();
    }

    @Override
    public synchronized int size() {
        return map.size();
    }
}
