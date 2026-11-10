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

package io.github.pieterjanscheir.bowerbird.api;

import com.google.common.collect.Range;
import com.palantir.javaformat.java.FormatterDiagnostic;
import com.palantir.javaformat.java.FormatterException;
import com.palantir.javaformat.java.JavaFormatterOptions;
import com.palantir.javaformat.java.JavaFormatterOptions.Style;
import com.palantir.javaformat.java.Replacement;
import io.github.pieterjanscheir.bowerbird.java.BowerbirdConfig;
import io.github.pieterjanscheir.bowerbird.java.BowerbirdRules;
import io.github.pieterjanscheir.bowerbird.java.Formatter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * The supported way to format Java code with Bowerbird from another tool, such as a build plugin, an IDE plugin or a
 * Spotless step (issue #6). Everything else in this library may change with any upstream merge; this class, and the
 * other public types in this package, won't change incompatibly within a major version.
 *
 * <pre>{@code
 * BowerbirdFormatter formatter = BowerbirdFormatter.fromConfig(Path.of("bowerbird.yml"));
 * String formatted = formatter.format(source);
 * }</pre>
 *
 * <p>A formatter can be shared between threads.
 *
 * <p><b>JDK 16 and later:</b> the formatter uses the JDK's own Java parser, which the JDK only opens up with the
 * {@link #requiredJvmArguments()}. A tool that formats in its own JVM, such as a Maven plugin, needs them on that
 * JVM's command line, or must run the formatter in a JVM it starts with them.
 */
public final class BowerbirdFormatter {

    private static final List<String> REQUIRED_JVM_ARGUMENTS = List.of(
            "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED");

    private final Formatter formatter;

    private BowerbirdFormatter(BowerbirdRules rules) {
        // Style.PALANTIR only sets what the rules don't cover.
        this.formatter = Formatter.createFormatter(
                JavaFormatterOptions.builder().style(Style.PALANTIR).build(), rules);
    }

    /** Formats with Bowerbird's defaults, as with an empty {@code bowerbird.yml}. */
    public static BowerbirdFormatter defaults() {
        return new BowerbirdFormatter(BowerbirdRules.defaults());
    }

    /**
     * Formats with the settings in {@code file}, a {@code bowerbird.yml}.
     *
     * @throws BowerbirdConfigException if the file can't be read or has an invalid setting
     */
    public static BowerbirdFormatter fromConfig(Path file) {
        Objects.requireNonNull(file, "file");
        return withConfig(() -> BowerbirdConfig.load(file));
    }

    /**
     * Formats with the settings in {@code yaml}, the contents of a {@code bowerbird.yml}. {@code source} names where
     * the text came from, for error messages; for example, its path.
     *
     * @throws BowerbirdConfigException if the text has an invalid setting
     */
    public static BowerbirdFormatter fromConfigText(String yaml, String source) {
        Objects.requireNonNull(yaml, "yaml");
        Objects.requireNonNull(source, "source");
        return withConfig(() -> BowerbirdConfig.parse(yaml, source));
    }

    /**
     * Formats with the {@code bowerbird.yml} at the root of {@code classLoader}'s classpath, or Bowerbird's defaults
     * without one. This is how the Gradle, Spotless and IntelliJ plugins find the settings.
     *
     * @throws BowerbirdConfigException if the file has an invalid setting, or there's more than one
     */
    public static BowerbirdFormatter fromClasspath(ClassLoader classLoader) {
        Objects.requireNonNull(classLoader, "classLoader");
        return withConfig(() -> BowerbirdConfig.load(classLoader));
    }

    private static BowerbirdFormatter withConfig(Supplier<BowerbirdRules> rules) {
        try {
            return new BowerbirdFormatter(rules.get());
        } catch (BowerbirdConfig.BowerbirdConfigException e) {
            throw new BowerbirdConfigException(e.getMessage(), e);
        }
    }

    /**
     * The JVM arguments the formatter needs on JDK 16 and later, as {@code --add-exports=...} options. A tool that
     * starts a JVM for the formatter passes these to it.
     */
    public static List<String> requiredJvmArguments() {
        return REQUIRED_JVM_ARGUMENTS;
    }

    /**
     * Formats a whole Java source file: orders imports and removes unused ones, lays out the code, and wraps long
     * string literals. This is what the command line does.
     *
     * @throws BowerbirdFormatException if the source can't be formatted, usually because it doesn't parse
     */
    public String format(String source) throws BowerbirdFormatException {
        Objects.requireNonNull(source, "source");
        try {
            return formatter.formatSourceAndFixImports(source);
        } catch (FormatterException e) {
            throw formatException(e);
        }
    }

    /**
     * Orders imports and removes unused ones, and changes nothing else.
     *
     * @throws BowerbirdFormatException if the source doesn't parse
     */
    public String fixImports(String source) throws BowerbirdFormatException {
        Objects.requireNonNull(source, "source");
        try {
            return formatter.fixImports(source);
        } catch (FormatterException e) {
            throw formatException(e);
        }
    }

    /**
     * The edits that format the lines touched by {@code ranges}, and nothing else, for an editor that formats a
     * selection. Imports and long strings are left alone. The edits are sorted and don't overlap, and apply to
     * {@code source} as it is; {@link TextEdit#apply} applies them. There are none when the lines are already
     * formatted.
     *
     * @throws BowerbirdFormatException if the source can't be formatted, usually because it doesn't parse
     */
    public List<TextEdit> formatRanges(String source, List<TextRange> ranges) throws BowerbirdFormatException {
        Objects.requireNonNull(source, "source");
        List<Range<Integer>> characterRanges = new ArrayList<>(ranges.size());
        for (TextRange range : ranges) {
            characterRanges.add(Range.closedOpen(range.start(), range.end()));
        }
        List<Replacement> replacements;
        try {
            replacements = formatter.getFormatReplacements(source, characterRanges);
        } catch (FormatterException e) {
            throw formatException(e);
        }
        List<TextEdit> edits = new ArrayList<>(replacements.size());
        for (Replacement replacement : replacements) {
            Range<Integer> range = replacement.getReplaceRange();
            if (source.substring(range.lowerEndpoint(), range.upperEndpoint())
                    .equals(replacement.getReplacementString())) {
                continue; // the formatter replaces whole ranges, including ones that are already formatted
            }
            edits.add(TextEdit.of(
                    TextRange.of(range.lowerEndpoint(), range.upperEndpoint()), replacement.getReplacementString()));
        }
        return List.copyOf(edits);
    }

    private static BowerbirdFormatException formatException(FormatterException e) {
        List<BowerbirdFormatException.Diagnostic> diagnostics = new ArrayList<>();
        for (FormatterDiagnostic diagnostic : e.diagnostics()) {
            diagnostics.add(new BowerbirdFormatException.Diagnostic(
                    diagnostic.line(), diagnostic.column() >= 0 ? diagnostic.column() + 1 : -1, diagnostic.message()));
        }
        if (diagnostics.isEmpty()) {
            diagnostics.add(new BowerbirdFormatException.Diagnostic(-1, -1, String.valueOf(e.getMessage())));
        }
        return new BowerbirdFormatException(diagnostics, e);
    }
}
