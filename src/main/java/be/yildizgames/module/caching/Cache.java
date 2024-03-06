package be.yildizgames.module.caching;

import java.util.Optional;
import java.util.function.Function;

/**
 * <pre>
 * Cache to retrieve data from a slow provider as fast as possible.
 * The cache is not responsible for the provider content, and can return empty values.
 * The types are for the ease of use of the cache, but in practice, the cache is able to hold any type.
 * So closing a cache, to force the persistence of its content and recreate it with the same name and different type should work.
 * Opening 2 instances of the same cache(same name) in parallel is not guarantee to work properly.
 * </pre>
 * @param <K> Type of the key used to retrieve the data.
 * @param <V> Type of the data associated to the key.
 * @author Gregory Van den Borre
 */
public interface Cache<K, V> extends AutoCloseable {

    /**
     * Put manually a file in the cache.
     * @param key Key that will be used to retrieve the element (no null).
     * @param value Value that will be returned on get (no null).
     * @throws NullPointerException if key or value is null.
     */
    void put(K key, V value);

    /**
     * Retrieve an element from the cache, this will always return a value,
     * as long as it can be retrieved at least from the addValue data provider.
     *
     * @param key Key that will be used to retrieve the element (no null).
     * @param addValue Data provider to feed the cache if the entry is not yet stored (no null).
     * @return The optional value associated to the provided key (no null).
     * @throws NullPointerException if key or addValue is null.
     */
    Optional<V> get(K key, Function<? super K, ? extends V> addValue);
}
