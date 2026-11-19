/*
 * Copyright 2026 Pieter-Jan Scheir
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.github.pieterjanscheir.tailorbird.java;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;

/**
 * Tailorbird: reads {@link TailorbirdRules} from a {@code tailorbird.yml} file. Every setting is optional; a missing
 * setting keeps Tailorbird's default. Unknown settings and wrong types are errors, so typos don't go unnoticed.
 *
 * <pre>{@code
 * preset: tailorbird        # or palantir, google: the starting point for every other setting
 * lineLength: 120
 * indent: 4
 * continuationIndent: 4
 * spaceAfterControlKeyword: false
 * arrayInitializerBraceSpaces: true
 * closingParenOnOwnLine: true
 * tryResourcesOnOwnLines: true
 * bodyWithoutBracesOnOwnLine: true
 * blankLines:
 *   keepTyped: false
 *   aroundClassBody: true
 *   betweenFields: false
 *   aroundFieldsWithJavadoc: false
 *   aroundInterfaceMethods: false
 * imports:
 *   groups: [static, java, javax, org, com, "*"]
 *   blankLineBetweenGroups: true
 * javadoc:
 *   enabled: true
 *   removeBlankLinesInDescription: true
 *   blankLineAfterDescription: true
 *   blankLineAfterParams: true
 *   blankLineAfterReturn: true
 *   singleSpaceAfterTagName: true
 *   wrap: false
 * }</pre>
 *
 * <p>The formatter finds the file on its own classpath, so a project adds the folder that contains it to the
 * {@code palantirJavaFormat} dependencies. That classpath is the only thing the upstream Gradle, Spotless and IntelliJ
 * plugins share with the formatter.
 */
public final class TailorbirdConfig {

    /** Name of the settings file, looked up at the root of the formatter's classpath. */
    public static final String FILE_NAME = "tailorbird.yml";

    /**
     * Created on first use, so formatting without a settings file never loads the YAML parser (which keeps the
     * native-image formatter free of it).
     */
    private static final class Yaml {
        // A setting written twice is an error, like an unknown one, instead of the last one silently winning.
        static final ObjectMapper MAPPER =
                new ObjectMapper(new YAMLFactory()).enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
    }

    private static final ImmutableMap<String, BiConsumer<TailorbirdRules.Builder, Value>> SETTINGS =
            ImmutableMap.<String, BiConsumer<TailorbirdRules.Builder, Value>>builder()
                    .put("lineLength", (b, v) -> b.lineLength(v.integer()))
                    .put("indent", (b, v) -> b.indent(v.integer()))
                    .put("continuationIndent", (b, v) -> b.continuationIndent(v.integer()))
                    .put("spaceAfterControlKeyword", (b, v) -> b.spaceAfterControlKeyword(v.bool()))
                    .put("arrayInitializerBraceSpaces", (b, v) -> b.arrayInitializerBraceSpaces(v.bool()))
                    .put("closingParenOnOwnLine", (b, v) -> b.closingParenOnOwnLine(v.bool()))
                    .put("tryResourcesOnOwnLines", (b, v) -> b.tryResourcesOnOwnLines(v.bool()))
                    .put("bodyWithoutBracesOnOwnLine", (b, v) -> b.bodyWithoutBracesOnOwnLine(v.bool()))
                    .put("blankLines.keepTyped", (b, v) -> b.keepTypedBlankLines(v.bool()))
                    .put("blankLines.aroundClassBody", (b, v) -> b.blankLinesAroundClassBody(v.bool()))
                    .put("blankLines.betweenFields", (b, v) -> b.blankLinesBetweenFields(v.bool()))
                    .put("blankLines.aroundFieldsWithJavadoc", (b, v) -> b.blankLinesAroundFieldsWithJavadoc(v.bool()))
                    .put("blankLines.aroundInterfaceMethods", (b, v) -> b.blankLinesAroundInterfaceMethods(v.bool()))
                    .put("imports.groups", (b, v) -> b.importGroups(v.strings()))
                    .put("imports.blankLineBetweenGroups", (b, v) -> b.blankLineBetweenImportGroups(v.bool()))
                    .put("javadoc.enabled", (b, v) -> b.javadocLayout(v.bool()))
                    .put(
                            "javadoc.removeBlankLinesInDescription",
                            (b, v) -> b.javadocRemoveBlankLinesInDescription(v.bool()))
                    .put("javadoc.blankLineAfterDescription", (b, v) -> b.javadocBlankLineAfterDescription(v.bool()))
                    .put("javadoc.blankLineAfterParams", (b, v) -> b.javadocBlankLineAfterParams(v.bool()))
                    .put("javadoc.blankLineAfterReturn", (b, v) -> b.javadocBlankLineAfterReturn(v.bool()))
                    .put("javadoc.singleSpaceAfterTagName", (b, v) -> b.javadocSingleSpaceAfterTagName(v.bool()))
                    .put("javadoc.wrap", (b, v) -> b.javadocWrap(v.bool()))
                    .put("formatterTags.enabled", (b, v) -> b.formatterTags(v.bool()))
                    .put("formatterTags.off", (b, v) -> b.formatterOffTag(v.string()))
                    .put("formatterTags.on", (b, v) -> b.formatterOnTag(v.string()))
                    .buildOrThrow();

    private TailorbirdConfig() {}

    /**
     * Reads {@value #FILE_NAME} from the root of {@code classLoader}'s classpath, or returns
     * {@link TailorbirdRules#defaults()} when there is none.
     *
     * @throws TailorbirdConfigException when the file can't be read, is invalid, or is on the classpath twice
     */
    public static TailorbirdRules load(ClassLoader classLoader) {
        // Keyed by location, so the same file found as a resource and as an entry counts once.
        Map<String, URL> files = new LinkedHashMap<>();
        try {
            for (URL url : Collections.list(classLoader.getResources(FILE_NAME))) {
                files.putIfAbsent(location(url), url);
            }
        } catch (IOException e) {
            throw new TailorbirdConfigException("Could not look up " + FILE_NAME + " on the formatter classpath", e);
        }
        classpathEntries(classLoader).forEach(files::putIfAbsent);
        if (files.isEmpty()) {
            return TailorbirdRules.defaults();
        }
        if (files.size() > 1) {
            throw new TailorbirdConfigException("Found " + FILE_NAME
                    + " more than once on the formatter classpath, keep only one: " + files.keySet());
        }
        URL file = files.values().iterator().next();
        try (InputStream in = file.openStream()) {
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), file.toString());
        } catch (IOException e) {
            throw new TailorbirdConfigException("Could not read " + file, e);
        }
    }

    /**
     * Classpath entries that are a {@value #FILE_NAME} file themselves, as in
     * {@code palantirJavaFormat files('tailorbird.yml')}. A class loader only reads folders and jars, so these are found
     * in the entries: the {@link URLClassLoader} URLs (the Gradle plugin and Spotless) and {@code java.class.path}
     * (the separate JVM the IntelliJ plugin starts).
     */
    private static Map<String, URL> classpathEntries(ClassLoader classLoader) {
        Map<String, URL> result = new LinkedHashMap<>();
        for (ClassLoader loader = classLoader; loader != null; loader = loader.getParent()) {
            if (loader instanceof URLClassLoader) {
                for (URL url : ((URLClassLoader) loader).getURLs()) {
                    if ("file".equals(url.getProtocol())) {
                        try {
                            addIfSettingsFile(result, Paths.get(url.toURI()));
                        } catch (URISyntaxException | IllegalArgumentException e) {
                            // not a local file path; a class loader can't use it either
                        }
                    }
                }
            }
        }
        String classPath = System.getProperty("java.class.path", "");
        for (String entry : classPath.split(File.pathSeparator, -1)) {
            if (!entry.isEmpty()) {
                addIfSettingsFile(result, Paths.get(entry));
            }
        }
        return result;
    }

    /** A local file's normalized path, or the URL itself for a file inside a jar. */
    private static String location(URL url) {
        if ("file".equals(url.getProtocol())) {
            try {
                return Paths.get(url.toURI()).toAbsolutePath().normalize().toString();
            } catch (URISyntaxException | IllegalArgumentException e) {
                // fall through
            }
        }
        return url.toExternalForm();
    }

    private static void addIfSettingsFile(Map<String, URL> result, Path path) {
        Path name = path.getFileName();
        if (name != null && name.toString().equals(FILE_NAME) && Files.isRegularFile(path)) {
            Path normalized = path.toAbsolutePath().normalize();
            try {
                result.putIfAbsent(normalized.toString(), normalized.toUri().toURL());
            } catch (MalformedURLException e) {
                throw new TailorbirdConfigException("Could not read " + path, e);
            }
        }
    }

    /** Reads the settings file at {@code file}, for the command line's {@code --config}. */
    public static TailorbirdRules load(Path file) {
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new TailorbirdConfigException("Could not read " + file, e);
        }
        return parse(text, file.toString());
    }

    /** Parses the YAML {@code text} of a settings file; {@code source} names the file in error messages. */
    public static TailorbirdRules parse(String text, String source) {
        JsonNode root;
        try {
            root = Yaml.MAPPER.readTree(text);
        } catch (JacksonException e) {
            throw new TailorbirdConfigException(source + " is not valid YAML: " + e.getOriginalMessage(), e);
        }
        if (root == null || root.isMissingNode() || root.isNull()) {
            return TailorbirdRules.defaults(); // empty file
        }
        if (!root.isObject()) {
            throw new TailorbirdConfigException(source + " must contain settings (key: value), not a single value");
        }
        // The preset is the starting point, so it applies first, wherever it is in the file.
        TailorbirdRules.Builder builder = TailorbirdRules.builder(preset(root.get(PRESET), source));
        apply(builder, root, "", source);
        return builder.build();
    }

    /** The names of all settings, with sections joined by dots, for example {@code blankLines.keepTyped}. */
    public static List<String> settingNames() {
        return SETTINGS.keySet().asList();
    }

    /** The setting that picks the starting point for all other settings. */
    private static final String PRESET = "preset";

    private static TailorbirdRules.Preset preset(@Nullable JsonNode node, String source) {
        if (node == null) {
            return TailorbirdRules.Preset.TAILORBIRD;
        }
        for (TailorbirdRules.Preset preset : TailorbirdRules.Preset.values()) {
            if (node.isTextual() && node.textValue().equals(preset.name().toLowerCase(Locale.ROOT))) {
                return preset;
            }
        }
        throw new TailorbirdConfigException(
                source + ": preset must be one of tailorbird, palantir or google, got " + node);
    }

    private static void apply(TailorbirdRules.Builder builder, JsonNode node, String prefix, String source) {
        for (Map.Entry<String, JsonNode> field : node.properties()) {
            String name = prefix + field.getKey();
            JsonNode value = field.getValue();
            if (name.equals(PRESET)) {
                continue; // already applied
            }
            BiConsumer<TailorbirdRules.Builder, Value> setting = SETTINGS.get(name);
            if (setting != null) {
                try {
                    setting.accept(builder, new Value(name, value));
                } catch (IllegalArgumentException e) {
                    throw new TailorbirdConfigException(source + ": " + e.getMessage(), e);
                }
            } else if (value.isObject() && isSection(name)) {
                apply(builder, value, name + ".", source);
            } else {
                throw new TailorbirdConfigException(source + ": unknown setting '" + name + "'. Known settings: "
                        + PRESET + ", " + String.join(", ", SETTINGS.keySet()));
            }
        }
    }

    private static boolean isSection(String name) {
        String prefix = name + ".";
        return SETTINGS.keySet().stream().anyMatch(key -> key.startsWith(prefix));
    }

    /** A setting's YAML value, converted with a clear error when it has the wrong type. */
    private static final class Value {
        private final String name;
        private final JsonNode node;

        Value(String name, JsonNode node) {
            this.name = name;
            this.node = node;
        }

        int integer() {
            if (!node.isIntegralNumber() || !node.canConvertToInt()) {
                throw new IllegalArgumentException(name + " must be a whole number, got " + node);
            }
            return node.intValue();
        }

        String string() {
            if (!node.isTextual()) {
                throw new IllegalArgumentException(name + " must be text, got " + node);
            }
            return node.textValue();
        }

        boolean bool() {
            if (!node.isBoolean()) {
                throw new IllegalArgumentException(name + " must be true or false, got " + node);
            }
            return node.booleanValue();
        }

        List<String> strings() {
            if (!node.isArray()) {
                throw new IllegalArgumentException(name + " must be a list, for example [static, java, \"*\"]");
            }
            List<String> result = new ArrayList<>();
            for (JsonNode element : node) {
                if (!element.isTextual()) {
                    throw new IllegalArgumentException(name + " must only contain text, got " + element);
                }
                result.add(element.textValue());
            }
            return result;
        }
    }

    /** Thrown when {@value #FILE_NAME} can't be read or contains an invalid setting. */
    public static final class TailorbirdConfigException extends RuntimeException {
        TailorbirdConfigException(String message) {
            super(message);
        }

        TailorbirdConfigException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
