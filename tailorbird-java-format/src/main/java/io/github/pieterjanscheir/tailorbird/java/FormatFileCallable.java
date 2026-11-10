/*
 * Copyright 2015 Google Inc.
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;
import com.palantir.javaformat.java.FormatterException;
import com.palantir.javaformat.java.JavaFormatterOptions;
import com.palantir.javaformat.java.Replacement;
import io.github.pieterjanscheir.tailorbird.Utils;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import javax.annotation.Nullable;

/** Encapsulates information about a file to be formatted, including which parts of the file to format. */
class FormatFileCallable implements Callable<String> {
    private static final ObjectMapper MAPPER =
            JsonMapper.builder().addModule(new GuavaModule()).build();

    private final String input;
    private final CommandLineOptions parameters;
    private final JavaFormatterOptions options;

    /** Tailorbird: the rules to apply, or {@code null} for the upstream style in {@link #options}. */
    @Nullable
    private final TailorbirdRules rules;

    public FormatFileCallable(CommandLineOptions parameters, String input, JavaFormatterOptions options) {
        this(parameters, input, options, null);
    }

    public FormatFileCallable(
            CommandLineOptions parameters,
            String input,
            JavaFormatterOptions options,
            @Nullable TailorbirdRules rules) {
        this.input = input;
        this.parameters = parameters;
        this.options = options;
        this.rules = rules;
    }

    private int maxLineLength() {
        return rules != null ? rules.lineLength() : options.maxLineLength();
    }

    @Override
    public String call() throws FormatterException {
        if (parameters.fixImportsOnly()) {
            return fixImports(input);
        }

        Formatter formatter =
                rules != null ? Formatter.createFormatter(options, rules) : Formatter.createFormatter(options);
        if (parameters.outputReplacements()) {
            RangeSet<Integer> characterRanges = characterRanges(input);
            Set<Range<Integer>> rangesToChange = characterRanges.asRanges();
            List<Replacement> replacements = formatter.getFormatReplacements(input, rangesToChange);
            if (parameters.reflowLongStrings()) {
                String formattedText = Utils.applyReplacements(input, replacements);
                // avoid trying to wrap the input unless the lines needing wrapping are part of the "characterRanges"
                if (!StringWrapper.linesNeedWrapping(maxLineLength(), formattedText, characterRanges)) {
                    return writeFormatReplacements(replacements);
                }
                String wrappedFormattedText = StringWrapper.wrap(maxLineLength(), formattedText, formatter);
                // if no wrapping change happened, return the initial replacements
                if (wrappedFormattedText.equals(formattedText)) {
                    return writeFormatReplacements(replacements);
                }
                return writeFormatReplacements(List.of(Replacement.create(0, input.length(), wrappedFormattedText)));
            } else {
                return writeFormatReplacements(replacements);
            }
        }
        return formatFile(formatter);
    }

    String writeFormatReplacements(List<Replacement> replacements) throws FormatterException {
        try {
            return MAPPER.writeValueAsString(replacements);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Error serializing replacement output", e);
        }
    }

    private String formatFile(Formatter formatter) throws FormatterException {
        String formatted = formatter.formatSource(input, characterRanges(input).asRanges());
        formatted = fixImports(formatted);
        if (parameters.reflowLongStrings()) {
            formatted = StringWrapper.wrap(maxLineLength(), formatted, formatter);
        }
        return formatted;
    }

    private String fixImports(String input) throws FormatterException {
        // Tailorbird: imports inside a @formatter:off region are left as written (issue #22).
        if (FormatterTags.coversImports(input, rules)) {
            return input;
        }
        if (parameters.removeUnusedImports()) {
            input = RemoveUnusedImports.removeUnusedImports(input);
        }
        if (parameters.sortImports()) {
            input = rules != null
                    ? ImportOrderer.reorderImports(input, rules)
                    : ImportOrderer.reorderImports(input, options.style());
        }
        return input;
    }

    private RangeSet<Integer> characterRanges(String input) {
        final RangeSet<Integer> characterRanges = TreeRangeSet.create();

        if (parameters.characterRanges().isEmpty()
                && parameters.lines().isEmpty()
                && parameters.offsets().isEmpty()) {
            characterRanges.add(Range.closedOpen(0, input.length()));
            return characterRanges;
        }

        if (!parameters.characterRanges().isEmpty()) {
            characterRanges.addAll(parameters.characterRanges());
        }

        if (!parameters.lines().isEmpty()) {
            characterRanges.addAll(Utils.lineRangesToCharRanges(input, parameters.lines()));
        }

        for (int i = 0; i < parameters.offsets().size(); i++) {
            Integer length = parameters.lengths().get(i);
            if (length == 0) {
                // 0 stands for "format the line under the cursor"
                length = 1;
            }
            characterRanges.add(Range.closedOpen(
                    parameters.offsets().get(i), parameters.offsets().get(i) + length));
        }

        return characterRanges;
    }
}
