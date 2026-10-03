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

/** plugin.yml must declare every command the plugin registers and every permission the code checks. */
class PluginYmlTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> pluginYml() throws IOException {
        try (InputStream in = Files.newInputStream(Path.of("src/main/resources/plugin.yml"))) {
            return new Yaml().load(in);
        }
    }

    private static String sources() throws IOException {
        StringBuilder sb = new StringBuilder();
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(Path.of("src/main/java"))) {
            walk.filter(p -> p.toString().endsWith(".java")).forEach(files::add);
        }
        for (Path f : files) {
            sb.append(Files.readString(f)).append('\n');
        }
        return sb.toString();
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyRegisteredCommandIsDeclared() throws IOException {
        Map<String, Object> commands = (Map<String, Object>) pluginYml().get("commands");
        String main = Files.readString(Path.of("src/main/java/dev/shardwatch/Shardwatch.java"));
        TreeSet<String> missing = new TreeSet<>();
        Matcher m = Pattern.compile("command\\(\"([a-z]+)\"").matcher(main);
        while (m.find()) {
            if (!commands.containsKey(m.group(1))) {
                missing.add(m.group(1));
            }
        }
        Matcher arr = Pattern.compile("\\{\"chip\", [^}]+}").matcher(main);
        if (arr.find()) {
            for (String c : arr.group().replaceAll("[{}\" ]", "").split(",")) {
                if (!commands.containsKey(c)) {
                    missing.add(c);
                }
            }
        }
        assertTrue(missing.isEmpty(), "Commands registered but not in plugin.yml: " + missing);
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyCheckedPermissionIsDeclared() throws IOException {
        Map<String, Object> perms = (Map<String, Object>) pluginYml().get("permissions");
        TreeSet<String> missing = new TreeSet<>();
        Matcher m = Pattern.compile("\"(shardwatch\\.[a-z_.*]+)\"(?!\\s*\\+)").matcher(sources());
        while (m.find()) {
            String perm = m.group(1);
            if (perm.endsWith(".") || perm.endsWith(".db") || perm.startsWith("shardwatch.facet.") && !perm.startsWith("shardwatch.facet.manage")) {
                continue; // dynamic prefixes and per-Facet nodes (shardwatch.facet.<id>)
            }
            if (!perms.containsKey(perm)) {
                missing.add(perm);
            }
        }
        // Dynamic families built in code: verdict types and tools.
        for (String v : List.of("chip", "hush", "eject", "encase", "petrify")) {
            if (!perms.containsKey("shardwatch.verdict." + v)) {
                missing.add("shardwatch.verdict." + v);
            }
        }
        for (String t : List.of("echo_lens", "timeglass", "verdict_gavel", "petrify_prism", "veil_lantern",
                "flare_compass", "glint_monocle")) {
            if (!perms.containsKey("shardwatch.tool." + t)) {
                missing.add("shardwatch.tool." + t);
            }
        }
        assertTrue(missing.isEmpty(), "Permissions used in code but not declared in plugin.yml: " + missing);
    }

    @Test
    @SuppressWarnings("unchecked")
    void rankPermissionsExist() throws IOException {
        Map<String, Object> perms = (Map<String, Object>) pluginYml().get("permissions");
        Map<String, Object> config;
        try (InputStream in = Files.newInputStream(Path.of("src/main/resources/config.yml"))) {
            config = new Yaml().load(in);
        }
        Map<String, Object> ranks = (Map<String, Object>) ((Map<String, Object>) config.get("facets")).get("ranks");
        TreeSet<String> missing = new TreeSet<>();
        for (Map.Entry<String, Object> rank : ranks.entrySet()) {
            for (String perm : (List<String>) ((Map<String, Object>) rank.getValue()).get("permissions")) {
                String p = perm.startsWith("-") ? perm.substring(1) : perm;
                if (!perms.containsKey(p)) {
                    missing.add(rank.getKey() + ": " + p);
                }
            }
        }
        assertTrue(missing.isEmpty(), "Facet ranks grant undeclared permissions: " + missing);
    }
}
