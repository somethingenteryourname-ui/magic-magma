package dev.shardwatch;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every literal message key the code asks for must exist in lang.yml. */
class LangKeysTest {

    private static final Pattern CALL = Pattern.compile("lang\\(\\)\\.(?:get|send|raw|rawList|getLines|sendActionBar)\\(");
    private static final Pattern BUTTON = Pattern.compile("icons\\(\\)\\.button\\(\"[a-z_]+\",\\s*([^,)]+)");
    private static final Pattern KEY = Pattern.compile("\"([a-z][a-z0-9_\\-]*(?:\\.[a-z0-9_\\-]+)+)\"");

    /** YAML 1.1 (Bukkit's loader) turns these keys into booleans, so they can never be looked up by name. */
    @Test
    void noBooleanLikeKeys() throws IOException {
        Pattern bad = Pattern.compile("^\\s*(on|off|yes|no|y|n|true|false):", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
        for (String f : List.of("src/main/resources/lang.yml", "src/main/resources/config.yml")) {
            Matcher m = bad.matcher(Files.readString(Path.of(f)));
            assertTrue(!m.find(), f + " has a boolean-like key: " + (m.hitEnd() ? "" : m.group()));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyLiteralKeyExists() throws IOException {
        Map<String, Object> lang;
        try (InputStream in = Files.newInputStream(Path.of("src/main/resources/lang.yml"))) {
            lang = new Yaml().load(in);
        }
        TreeSet<String> missing = new TreeSet<>();
        List<Path> sources = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(Path.of("src/main/java"))) {
            walk.filter(p -> p.toString().endsWith(".java")).forEach(sources::add);
        }
        for (Path file : sources) {
            String src = Files.readString(file);
            Matcher m = CALL.matcher(src);
            while (m.find()) {
                String call = src.substring(m.end(), Math.min(src.length(), src.indexOf(';', m.end()) + 1));
                Matcher k = KEY.matcher(call);
                while (k.find()) {
                    String key = k.group(1);
                    String before = call.substring(Math.max(0, k.start() - 16), k.start());
                    String after = call.substring(k.end()).stripLeading();
                    boolean notAMessage = key.startsWith("shardwatch.") || key.startsWith("minecraft.")
                            || before.matches("(?s).*get(String|Int|Boolean|Long|Double|StringList)\\($")
                            || after.startsWith("+");
                    if (!notAMessage && lookup(lang, key) == null) {
                        missing.add(file.getFileName() + ": " + key);
                    }
                }
            }
            Matcher b = BUTTON.matcher(src);
            while (b.find()) {
                Matcher k = Pattern.compile("\"([a-z][a-z0-9_\\-.]*)\"").matcher(b.group(1));
                while (k.find()) {
                    String key = "gui." + k.group(1);
                    if (lookup(lang, key + ".name") == null) {
                        missing.add(file.getFileName() + ": " + key + ".name");
                    }
                }
            }
        }
        assertTrue(missing.isEmpty(), "Missing lang keys:\n" + String.join("\n", missing));
    }

    @SuppressWarnings("unchecked")
    private static Object lookup(Map<String, Object> root, String path) {
        Object cur = root;
        for (String part : path.split("\\.")) {
            if (!(cur instanceof Map<?, ?> map)) {
                return null;
            }
            cur = ((Map<String, Object>) map).get(part);
        }
        return cur;
    }
}
