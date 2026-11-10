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

/**
 * A half-open range of characters in a source file, from {@link #start()} (inclusive) to {@link #end()} (exclusive),
 * as offsets into the {@code String}.
 */
public final class TextRange {
    private final int start;
    private final int end;

    private TextRange(int start, int end) {
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("invalid range: [" + start + ", " + end + ")");
        }
        this.start = start;
        this.end = end;
    }

    /** The characters from {@code start} (inclusive) to {@code end} (exclusive). */
    public static TextRange of(int start, int end) {
        return new TextRange(start, end);
    }

    /** The offset of the first character in the range. */
    public int start() {
        return start;
    }

    /** The offset just after the last character in the range; equal to {@link #start()} for an empty range. */
    public int end() {
        return end;
    }

    /** Ranges are equal when they have the same start and end. */
    @Override
    public boolean equals(Object other) {
        return other instanceof TextRange && ((TextRange) other).start == start && ((TextRange) other).end == end;
    }

    /** A hash code consistent with {@link #equals}. */
    @Override
    public int hashCode() {
        return 31 * start + end;
    }

    /** The range as {@code [start, end)}. */
    @Override
    public String toString() {
        return "[" + start + ", " + end + ")";
    }
}
