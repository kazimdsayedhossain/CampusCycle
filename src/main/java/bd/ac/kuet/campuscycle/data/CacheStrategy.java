package bd.ac.kuet.campuscycle.data;

import java.util.Optional;

/**
 * Strategy pattern interface for caching strategies.
 * Demonstrates advanced OOP with generic type parameters.
 *
 * @param <K> key type
 * @param <V> value type
 */
public interface CacheStrategy<K, V> {

    /**
     * Retrieves an entry from cache if present.
     */
    Optional<V> get(K key);

    /**
     * Stores a value in the cache.
     */
    void put(K key, V value);

    /**
     * Evicts a key from the cache.
     */
    void evict(K key);

    /**
     * Clears all cache entries.
     */
    void clear();

    /**
     * Returns current number of entries in the cache.
     */
    int size();
}
