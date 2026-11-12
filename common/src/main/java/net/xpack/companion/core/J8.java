package net.xpack.companion.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Équivalents des API de Java 9 à 17 utilisées par le cœur, pour qu'il tourne en Java 8 (Minecraft
 * 1.12.2 et plus ancien). Même comportement : collections non modifiables, texte en UTF-8.
 */
public final class J8 {
    private J8() {}

    @SafeVarargs
    public static <T> List<T> listOf(T... items) {
        return Collections.unmodifiableList(Arrays.asList(items.clone()));
    }

    @SafeVarargs
    public static <T> Set<T> setOf(T... items) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(items)));
    }

    public static <K, V> Map<K, V> mapOf() {
        return Collections.emptyMap();
    }

    public static <T> List<T> copyOf(Collection<? extends T> items) {
        return Collections.unmodifiableList(new ArrayList<>(items));
    }

    public static <T> Set<T> copyOfSet(Collection<? extends T> items) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(items));
    }

    public static <K, V> Map<K, V> copyOf(Map<? extends K, ? extends V> map) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }

    public static <K, V> Map.Entry<K, V> entry(K key, V value) {
        return new AbstractMap.SimpleImmutableEntry<>(key, value);
    }

    public static <T> Optional<T> or(Optional<T> first, Supplier<Optional<T>> other) {
        return first.isPresent() ? first : other.get();
    }

    public static boolean isBlank(String s) {
        return s.trim().isEmpty();
    }

    public static List<String> lines(String s) {
        return Arrays.asList(s.split("\r\n|\r|\n"));
    }

    public static String readString(Path file, Charset cs) throws IOException {
        return new String(Files.readAllBytes(file), cs);
    }

    public static Path writeString(Path file, CharSequence text, Charset cs) throws IOException {
        return Files.write(file, text.toString().getBytes(cs));
    }

    public static String hex(byte[] bytes) {
        return hex(bytes, 0, bytes.length);
    }

    public static String hex(byte[] bytes, int from, int to) {
        StringBuilder sb = new StringBuilder((to - from) * 2);
        for (int i = from; i < to; i++) {
            sb.append(Character.forDigit((bytes[i] >> 4) & 0xF, 16)).append(Character.forDigit(bytes[i] & 0xF, 16));
        }
        return sb.toString();
    }

    // ---- Gson : Minecraft 1.12.2 embarque Gson 2.2.4, sans JsonParser.parseString / parseReader ni JsonObject.deepCopy publique.

    @SuppressWarnings("deprecation")
    public static JsonElement parseJson(String json) {
        return new JsonParser().parse(json);
    }

    @SuppressWarnings("deprecation")
    public static JsonElement parseJson(Reader reader) {
        return new JsonParser().parse(reader);
    }

    public static JsonObject copy(JsonObject o) {
        return parseJson(o.toString()).getAsJsonObject();
    }
}
