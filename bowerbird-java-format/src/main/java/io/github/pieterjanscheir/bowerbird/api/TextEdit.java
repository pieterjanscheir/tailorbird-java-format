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

import java.util.List;
import java.util.Objects;

/** A change to a source file: replace the characters in {@link #range()} with {@link #replacement()}. */
public final class TextEdit {
    private final TextRange range;
    private final String replacement;

    private TextEdit(TextRange range, String replacement) {
        this.range = Objects.requireNonNull(range, "range");
        this.replacement = Objects.requireNonNull(replacement, "replacement");
    }

    /** An edit that replaces the characters in {@code range} with {@code replacement}. */
    public static TextEdit of(TextRange range, String replacement) {
        return new TextEdit(range, replacement);
    }

    /** The characters to replace, in the source before any edit is applied. */
    public TextRange range() {
        return range;
    }

    /** The text that replaces the characters in {@link #range()}. */
    public String replacement() {
        return replacement;
    }

    /**
     * Applies {@code edits} to {@code source}. The edits must be sorted and not overlap, as
     * {@link BowerbirdFormatter#formatRanges} returns them.
     */
    public static String apply(String source, List<TextEdit> edits) {
        StringBuilder result = new StringBuilder(source.length());
        int copied = 0;
        for (TextEdit edit : edits) {
            if (edit.range.start() < copied || edit.range.end() > source.length()) {
                throw new IllegalArgumentException("edits must be sorted, not overlap, and fit the source: " + edit);
            }
            result.append(source, copied, edit.range.start()).append(edit.replacement);
            copied = edit.range.end();
        }
        return result.append(source, copied, source.length()).toString();
    }

    /** Edits are equal when they have the same range and replacement. */
    @Override
    public boolean equals(Object other) {
        return other instanceof TextEdit
                && ((TextEdit) other).range.equals(range)
                && ((TextEdit) other).replacement.equals(replacement);
    }

    /** A hash code consistent with {@link #equals}. */
    @Override
    public int hashCode() {
        return 31 * range.hashCode() + replacement.hashCode();
    }

    /** The edit as {@code [start, end) -> "replacement"}, for debugging. */
    @Override
    public String toString() {
        return range + " -> \"" + replacement + "\"";
    }
}
