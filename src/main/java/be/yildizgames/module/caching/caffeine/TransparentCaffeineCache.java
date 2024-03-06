package be.yildizgames.module.caching.caffeine;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;
import be.yildizgames.module.caching.Cache;


public class TransparentCaffeineCache<K, V> implements Cache<K,V> {

    public static void main(String[] args) {

    }

    private final CaffeineCache<K, V> cache;

    public TransparentCaffeineCache(String name, long maxItem, Duration duration) {
        CaffeineCache<K, V> tmp;
        try {
            tmp = new CaffeineCache<>(name, maxItem, duration);
        } catch (Exception e) {
            tmp = null;
        }
        this.cache = tmp;
    }

    @Override
    public void put(K key, V value) {
        try {
            Optional.ofNullable(this.cache).ifPresent(c -> c.put(key, value));
        } catch (Exception e) {
            System.getLogger(CaffeineCache.class.getName()).log(System.Logger.Level.ERROR, "Error putting value", e);
        }
    }

    @Override
    public Optional<V> get(K key, Function<? super K, ? extends V> addValue) {
        try {
            return Optional.ofNullable(this.cache).flatMap(c -> c.get(key, addValue));
        } catch (Exception e) {
            System.getLogger(CaffeineCache.class.getName()).log(System.Logger.Level.ERROR, "Error retrieving value", e);
            return Optional.empty();
        }
    }

    @Override
    public void close() {
        try {
            Optional.ofNullable(this.cache).ifPresent(CaffeineCache::close);
        } catch (Exception e) {
            System.getLogger(CaffeineCache.class.getName()).log(System.Logger.Level.ERROR, "Error closing cache", e);
        }
    }

}
