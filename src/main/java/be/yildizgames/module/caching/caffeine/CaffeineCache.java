package be.yildizgames.module.caching.caffeine;

import com.github.benmanes.caffeine.cache.Caffeine;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StreamCorruptedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Optional;
import java.util.function.Function;
import be.yildizgames.module.caching.Cache;

/**
 * <pre>
 * Caffeine implementation for the cache system (more info at <a href="https://github.com/ben-manes/caffeine">Caffeine</a>).
 * Support file persistence through binary serialization of the cache Map.
 * Caching non-serializable data will result in non-persistent cache, so only keeping data in memory for the duration of
 * the application session.
 * Mutable: yes.
 * Thread safe: yes.
 * Accept null: no.
 * Returns null: no.
 * Side effects: file read and write.
 * Throws exceptions: yes.
 * </pre>
 * @param <K> Type of the key used to retrieve the data.
 * @param <V> Type of the data associated to the key.
 * @author Gregory Van den Borre
 */
public class CaffeineCache<K, V> implements Cache<K, V> {

    private static final String NAME_PLACEHOLDER = "{NAME}";

    /**
     * Name of the file to use when persisting the data on disk.
     */
    public static final String DATA = "cache-" + NAME_PLACEHOLDER + ".data";

    /**
     * Name of the file to use when persisting the metadata on disk.
     */
    public static final String METADATA = "cache-" + NAME_PLACEHOLDER + ".meta";

    /**
     * Caffeine cache.
     */
    private final com.github.benmanes.caffeine.cache.Cache<K, V> cache;

    /**
     * Name of the cache, expected to be unique to avoid to override existing persisted data.
     */
    private final String name;

    /**
     * Create a new cache instance.
     * If a cache file matching the name exists it will try to be loaded, else a new empty cache will be created.
     * On close, the cache data and metadata will be stored on disk.
     * @param name Cache name expected to be unique, non-unique name may result in an overwrite of disk file data,
     *             resulting on a performance penalty or worse, wrong data. (no null, not empty)
     * @param maxItem The maximum number of items to be accepted by the cache. (> 0)
     * @param duration Maximum time before the data from the cache are refreshed after writing.(no null, > 0)
     * @throws IllegalArgumentException for any parameter not following the restrictions.
     */
     public CaffeineCache(String name, long maxItem, Duration duration) {
        super();
        if(name == null) {
            throw new IllegalArgumentException("Name cannot be null.");
        }
        if(name.isEmpty()) {
            throw new IllegalArgumentException("Name cannot be empty.");
        }
        if(maxItem <= 0) {
            throw new IllegalArgumentException("Max item must be bigger than 0 (value " + maxItem + ").");
        }
        if(duration == null) {
            throw new IllegalArgumentException("Duration cannot be null.");
        }
        if(duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("Duration must be bigger than 0 (value " + duration.getSeconds() + " sec).");
        }
        this.name = name;
        this.cache = readMetadataFromFile().orElseGet(() -> Caffeine.newBuilder()
                .expireAfterWrite(duration)
                .maximumSize(maxItem)
                .build());
        readDataFromFile().ifPresent(this.cache::putAll);
    }

    @Override
    public final void put(K key, V o) {
        this.cache.put(key, o);
    }

    @Override
    public final Optional<V> get(K key, Function<? super K, ? extends V> addValue) {
        return Optional.ofNullable(this.cache.get(key, addValue));
    }

    @Override
    public final void close() {
        writeDataToFile();
        writeCacheMetadataToFile();
    }

    private void writeCacheMetadataToFile() {
        try (var stream = new FileOutputStream(METADATA.replace(NAME_PLACEHOLDER, this.name)); var objects = new ObjectOutputStream(stream)) {
            objects.writeObject(this.cache);
        } catch (Exception e) {
            System.getLogger(CaffeineCache.class.getName()).log(System.Logger.Level.ERROR, "Cannot write cache metadata", e);
        }
    }

    private void writeDataToFile() {
        try (var stream = new FileOutputStream(DATA.replace(NAME_PLACEHOLDER, this.name)); var objects = new ObjectOutputStream(stream)) {
            var content = new HashMap<>(this.cache.asMap());
            objects.writeObject(content);
        } catch (Exception e) {
            System.getLogger(CaffeineCache.class.getName()).log(System.Logger.Level.ERROR, "Cannot write cache data", e);
        }
    }

    @SuppressWarnings({"unchecked"})
    private Optional<HashMap<K, V>> readDataFromFile() {
        if(Files.notExists(Path.of(DATA.replace(NAME_PLACEHOLDER, this.name)))) {
            return Optional.empty();
        }
        try (var stream = new FileInputStream(DATA.replace(NAME_PLACEHOLDER, this.name)); var objects = new ObjectInputStream(stream)) {
            return Optional.of((HashMap<K, V>) objects.readObject());
        } catch (StreamCorruptedException e) {
            System.getLogger(CaffeineCache.class.getName()).log(System.Logger.Level.ERROR, "Corrupted cache file", e);
        } catch (IOException | ClassNotFoundException e) {
            System.getLogger(CaffeineCache.class.getName()).log(System.Logger.Level.ERROR, "Unreadable cache file", e);
        }
        return Optional.empty();
    }

    @SuppressWarnings({"unchecked"})
    private Optional<com.github.benmanes.caffeine.cache.Cache<K, V>> readMetadataFromFile() {
        if(Files.notExists(Path.of(METADATA.replace(NAME_PLACEHOLDER, this.name)))) {
            return Optional.empty();
        }
        try (var stream = new FileInputStream(METADATA.replace(NAME_PLACEHOLDER, this.name)); var objects = new ObjectInputStream(stream)) {
            return Optional.of((com.github.benmanes.caffeine.cache.Cache<K, V>) objects.readObject());
        } catch (StreamCorruptedException e) {
            System.getLogger(CaffeineCache.class.getName()).log(System.Logger.Level.ERROR, "Corrupted metadata file", e);
        } catch (IOException | ClassNotFoundException e) {
            System.getLogger(CaffeineCache.class.getName()).log(System.Logger.Level.ERROR, "Unreadable metadata file", e);
        }
        return Optional.empty();
    }
}
