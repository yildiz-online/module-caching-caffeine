package be.yildizgames.module.caching.caffeine;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static be.yildizgames.module.caching.caffeine.CaffeineCache.DATA;
import static be.yildizgames.module.caching.caffeine.CaffeineCache.METADATA;

public class CaffeineCacheTest {

    @BeforeEach
    void clean() throws IOException {
        Files.deleteIfExists(Path.of(DATA.replace("{NAME}", "test")));
        Files.deleteIfExists(Path.of(METADATA.replace("{NAME}", "test")));
    }

    @Nested
    class Constructor {

        @Test
        void nullName() {
            Assertions.assertThrows(IllegalArgumentException.class, () -> new CaffeineCache<Integer, String>(null, 5, Duration.of(2, ChronoUnit.DAYS)));
        }

        @Test
        void emptyName() {
            Assertions.assertThrows(IllegalArgumentException.class, () -> new CaffeineCache<Integer, String>("", 5, Duration.of(2, ChronoUnit.DAYS)));
        }

        @Test
        void maxItem0() {
            Assertions.assertThrows(IllegalArgumentException.class, () -> new CaffeineCache<Integer, String>("test", 0, Duration.of(2, ChronoUnit.DAYS)));
        }

        @Test
        void maxItemNegative() {
            Assertions.assertThrows(IllegalArgumentException.class, () -> new CaffeineCache<Integer, String>("test", -1, Duration.of(2, ChronoUnit.DAYS)));
        }

        @Test
        void nullDuration() {
            Assertions.assertThrows(IllegalArgumentException.class, () -> new CaffeineCache<Integer, String>("test", 5, null));
        }

        @Test
        void zeroDuration() {
            Assertions.assertThrows(IllegalArgumentException.class, () -> new CaffeineCache<Integer, String>("test", 5, Duration.of(0, ChronoUnit.DAYS)));
        }

        @Test
        void noCache() {
            try(var cache = basicCache()) {
                var provider = new DataProvider();
                Assertions.assertEquals(0, provider.used);
                var result = cache.get(1, provider::getData);
                Assertions.assertEquals("test 1", result.get());
                Assertions.assertEquals(1, provider.used);
            }
        }

        @Test
        void tryNotSerializableType() {
            try(var cache = new CaffeineCache<Integer, NotSerializableType>("test", 1000, Duration.of(2, ChronoUnit.DAYS))) {
                var provider = new NotSerializableTypeDataProvider();
                Assertions.assertEquals(0, provider.used);
                cache.get(1, provider::getData);
                Assertions.assertEquals(1, provider.used);
                cache.get(1, provider::getData);
                Assertions.assertEquals(1, provider.used);
            }
            try(var cache = new CaffeineCache<Integer, NotSerializableType>("test", 1000, Duration.of(2, ChronoUnit.DAYS))) {
                var provider = new NotSerializableTypeDataProvider();
                Assertions.assertEquals(0, provider.used);
                cache.get(1, provider::getData);
                Assertions.assertEquals(1, provider.used);
            }
        }

        @Test
        void expiredCache() throws InterruptedException {
            try(var cache = new CaffeineCache<Integer, String>("test", 1000, Duration.of(1, ChronoUnit.SECONDS));) {
                var provider = new DataProvider();
                Assertions.assertEquals(0, provider.used);
                var result = cache.get(1, provider::getData);
                Assertions.assertEquals("test 1", result.get());
                Assertions.assertEquals(1, provider.used);
                cache.get(1, provider::getData);
                Assertions.assertEquals(1, provider.used);
                Thread.sleep(1000);
                cache.get(1, provider::getData);
                Assertions.assertEquals(2, provider.used);
            }
        }



        @Test
        void failedWriteDataCache() throws IOException {
            try(var in = new RandomAccessFile(DATA.replace("{NAME}", "test"), "rw"); var ignored = in.getChannel().lock()) {
                try (var cache = basicCache()) {
                    var provider = new DataProvider();
                    Assertions.assertEquals(0, provider.used);
                    var result = cache.get(1, provider::getData);
                    Assertions.assertEquals("test 1", result.get());
                    Assertions.assertEquals(1, provider.used);
                }
            }
        }

        @Test
        void failedWriteMetadataCache() throws IOException {
            try(var in = new RandomAccessFile(METADATA.replace("{NAME}", "test"), "rw"); var ignored = in.getChannel().lock()) {
                try (var cache = basicCache()) {
                    var provider = new DataProvider();
                    Assertions.assertEquals(0, provider.used);
                    var result = cache.get(1, provider::getData);
                    Assertions.assertEquals("test 1", result.get());
                    Assertions.assertEquals(1, provider.used);
                }
            }
        }

        @Test
        void withCache() {
            try(var cache = basicCache()) {
                var provider = new DataProvider();
                Assertions.assertEquals(0, provider.used);
                var result = cache.get(1, provider::getData);
                Assertions.assertEquals("test 1", result.get());
                Assertions.assertEquals(1, provider.used);
            }
            try(var cache = basicCache()) {
                var provider = new DataProvider();
                Assertions.assertEquals(0, provider.used);
                var result = cache.get(1, provider::getData);
                Assertions.assertEquals("test 1", result.get());
                Assertions.assertEquals(0, provider.used);
            }
            //replace existing file after previous close.
            try(var cache = basicCache()) {
                var provider = new DataProvider();
                Assertions.assertEquals(0, provider.used);
                var result = cache.get(1, provider::getData);
                Assertions.assertEquals("test 1", result.get());
                Assertions.assertEquals(0, provider.used);
            }
        }

        @Test
        void withWrongKeyTypeCache() {
            var date = new Date();
            try(var cache = basicCache()) {
                var provider = new DataProvider();
                Assertions.assertEquals(0, provider.used);
                var result = cache.get(1, provider::getData);
                Assertions.assertEquals("test 1", result.get());
                Assertions.assertEquals(1, provider.used);
            }
            try(var cache = new CaffeineCache<Date, String>("test", 1000, Duration.of(2, ChronoUnit.DAYS))) {
                var provider = new WrongKeyTypeDataProvider();
                Assertions.assertEquals(0, provider.used);
                cache.get(date, provider::getData);
                Assertions.assertEquals(1, provider.used);
            }
            try(var cache = basicCache()) {
                var provider = new DataProvider();
                Assertions.assertEquals(0, provider.used);
                var result = cache.get(1, provider::getData);
                Assertions.assertEquals("test 1", result.get());
                //persisted cache is used
                Assertions.assertEquals(0, provider.used);
            }
            try(var cache = new CaffeineCache<Date, String>("test", 1000, Duration.of(2, ChronoUnit.DAYS))) {
                var provider = new WrongKeyTypeDataProvider();
                Assertions.assertEquals(0, provider.used);
                cache.get(date, provider::getData);
                //persisted cache is used
                Assertions.assertEquals(0, provider.used);
            }
        }

        @Test
        void withWrongDataTypeCache() {
            var key = System.currentTimeMillis();
            try(var cache = basicCache()) {
                var provider = new DataProvider();
                Assertions.assertEquals(0, provider.used);
                var result = cache.get(1, provider::getData);
                Assertions.assertEquals("test 1", result.get());
                Assertions.assertEquals(1, provider.used);
            }
            try(var cache = new CaffeineCache<Long, Date>("test", 1000, Duration.of(2, ChronoUnit.DAYS))) {
                var provider = new WrongDataTypeDataProvider();
                Assertions.assertEquals(0, provider.used);
                cache.get(key, provider::getData);
                Assertions.assertEquals(1, provider.used);
            }
            try(var cache = basicCache()) {
                var provider = new DataProvider();
                Assertions.assertEquals(0, provider.used);
                var result = cache.get(1, provider::getData);
                Assertions.assertEquals("test 1", result.get());
                //persisted cache is used
                Assertions.assertEquals(0, provider.used);
            }
            try(var cache = new CaffeineCache<Long, Date>("test", 1000, Duration.of(2, ChronoUnit.DAYS))) {
                var provider = new WrongDataTypeDataProvider();
                Assertions.assertEquals(0, provider.used);
                cache.get(key, provider::getData);
                //persisted cache is used
                Assertions.assertEquals(0, provider.used);
            }
        }

        @Test
        void corruptedDataCache() throws IOException {
            var file = Files.createFile(Path.of(DATA.replace("{NAME}", "test")));
            String str = "qwerty";
            var writer = new BufferedWriter(new FileWriter(file.getFileName().toString()));
            writer.write(str);
            writer.close();
            try(var cache = basicCache()) {
                var provider = new DataProvider();
                Assertions.assertEquals(0, provider.used);
                var result = cache.get(1, provider::getData);
                Assertions.assertEquals("test 1", result.get());
                Assertions.assertEquals(1, provider.used);
            }
        }

        @Test
        void corruptedMetadataCache() throws IOException {
            var file = Files.createFile(Path.of(METADATA.replace("{NAME}", "test")));
            String str = "qwerty";
            var writer = new BufferedWriter(new FileWriter(file.getFileName().toString()));
            writer.write(str);
            writer.close();
            try(var cache = basicCache()) {
                var provider = new DataProvider();
                Assertions.assertEquals(0, provider.used);
                var result = cache.get(1, provider::getData);
                Assertions.assertEquals("test 1", result.get());
                Assertions.assertEquals(1, provider.used);
            }
        }
    }

    @Nested
    class Get {

        @Test
        void nullKey() {
            try(var cache = basicCache()) {
                var provider = new DataProvider();
                Assertions.assertThrows(NullPointerException.class, () -> cache.get(null, provider::getData));
            }
        }

        @Test
        void nullAddElement() {
            try(var cache = basicCache()) {
                Assertions.assertThrows(NullPointerException.class, () -> cache.get(1, null));
            }
        }
    }

    @Nested
    class Put {

        @Test
        void addElement() {
            try(var cache = basicCache()) {
                var provider = new DataProvider();
                Assertions.assertEquals(0, provider.used);
                cache.put(2, "test 2");
                var result = cache.get(2, provider::getData);
                Assertions.assertEquals("test 2", result.get());
                Assertions.assertEquals(0, provider.used);
            }
        }

        @Test
        void nullKey() {
            try(var cache = basicCache()) {
                Assertions.assertThrows(NullPointerException.class, () -> cache.put(null, "test 2"));
            }
        }

        @Test
        void nullElement() {
            try(var cache = basicCache()) {
                Assertions.assertThrows(NullPointerException.class, () -> cache.put(2, null));
            }
        }
    }

    private static CaffeineCache<Integer, String> basicCache() {
        return new CaffeineCache<>("test", 1000, Duration.of(2, ChronoUnit.DAYS));
    }

    public static class DataProvider {

        private int used = 0;

        public String getData(int key) {
            this.used++;
            return "test " + key;
        }

    }

    public static class WrongKeyTypeDataProvider {

        private int used = 0;

        public String getData(Date date) {
            this.used++;
            return date.toString();
        }
    }

    public static class WrongDataTypeDataProvider {

        private int used = 0;

        public Date getData(long key) {
            this.used++;
            return new Date(key);
        }
    }

    public static class NotSerializableTypeDataProvider {

        private int used = 0;

        public NotSerializableType getData(int key) {
            this.used++;
            return new NotSerializableType("tt", 2);
        }
    }

    public static class NotSerializableType {

        private final String data1;

        private final int data2;


        public NotSerializableType(String data1, int data2) {
            this.data1 = data1;
            this.data2 = data2;
        }
    }

}
