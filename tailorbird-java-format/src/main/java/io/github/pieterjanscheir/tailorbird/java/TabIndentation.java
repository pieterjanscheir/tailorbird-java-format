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

import com.google.common.collect.Range;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Tailorbird: converts leading indentation between spaces and tabs ({@code indentWithTabs}, issue #20). The formatter
 * itself only works with spaces: its input is converted to spaces first, and its output to tabs last, so every layout
 * decision is the same as with spaces, a tab counting as {@code indent} columns.
 *
 * <p>Output uses "smart tabs": one tab per {@code indent} columns, and the remainder (for example the space before the
 * {@code *} of a JavaDoc line) as spaces. Lines that start inside a text block are never changed, because their
 * whitespace is part of the string.
 */
final class TabIndentation {

    private TabIndentation() {}

    /** Replaces the leading tabs of each line with spaces, a tab reaching the next multiple of {@code width}. */
    static String toSpaces(String text, int width) {
        return convert(text, width, false);
    }

    /** Replaces each {@code width} leading spaces of a line with a tab, keeping the remainder as spaces. */
    static String toTabs(String text, int width) {
        return convert(text, width, true);
    }

    /**
     * Maps {@code range} in {@code from} to the same characters in {@code to}, where {@code to} is {@code from} with
     * only the leading whitespace of its lines changed. An end point inside the leading whitespace moves to its end.
     */
    static Range<Integer> mapRange(String from, String to, Range<Integer> range) {
        List<Integer> fromStarts = lineStarts(from);
        List<Integer> toStarts = lineStarts(to);
        return Range.closedOpen(
                mapOffset(from, to, fromStarts, toStarts, range.lowerEndpoint()),
                mapOffset(from, to, fromStarts, toStarts, range.upperEndpoint()));
    }

    private static int mapOffset(String from, String to, List<Integer> fromStarts, List<Integer> toStarts, int offset) {
        if (offset >= from.length()) {
            return to.length() + (offset - from.length());
        }
        int line = lineOf(fromStarts, offset);
        int fromStart = fromStarts.get(line);
        int toStart = toStarts.get(line);
        int fromLead = leadingWhitespace(from, fromStart);
        int toLead = leadingWhitespace(to, toStart);
        int column = offset - fromStart;
        return column <= fromLead ? toStart + (column == 0 ? 0 : toLead) : toStart + toLead + (column - fromLead);
    }

    private static int lineOf(List<Integer> starts, int offset) {
        int low = 0;
        int high = starts.size() - 1;
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (starts.get(mid) <= offset) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return low;
    }

    private static int leadingWhitespace(String text, int start) {
        int i = start;
        while (i < text.length() && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) {
            i++;
        }
        return i - start;
    }

    private static String convert(String text, int width, boolean tabs) {
        List<Integer> starts = lineStarts(text);
        BitSet inTextBlock = linesInTextBlocks(text, starts);
        StringBuilder out = new StringBuilder(text.length());
        for (int line = 0; line < starts.size(); line++) {
            int start = starts.get(line);
            int end = line + 1 < starts.size() ? starts.get(line + 1) : text.length();
            int lead = leadingWhitespace(text, start);
            boolean blank = start + lead == end || isLineBreak(text.charAt(start + lead));
            if (inTextBlock.get(line) || lead == 0 || blank) {
                out.append(text, start, end);
                continue;
            }
            int columns = 0;
            for (int i = start; i < start + lead; i++) {
                columns = text.charAt(i) == '\t' ? (columns / width + 1) * width : columns + 1;
            }
            if (tabs) {
                out.append("\t".repeat(columns / width)).append(" ".repeat(columns % width));
            } else {
                out.append(" ".repeat(columns));
            }
            out.append(text, start + lead, end);
        }
        return out.toString();
    }

    private static boolean isLineBreak(char c) {
        return c == '\n' || c == '\r';
    }

    /** The offset where each line starts; {@code \n}, {@code \r\n} and {@code \r} all end a line. */
    private static List<Integer> lineStarts(String text) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                i++;
            }
            if ((c == '\r' || c == '\n') && i + 1 < text.length()) {
                starts.add(i + 1);
            }
        }
        return starts;
    }

    private enum State {
        CODE,
        STRING,
        CHAR,
        TEXT_BLOCK,
        LINE_COMMENT,
        BLOCK_COMMENT
    }

    /** The lines that start inside a text block, found with a small lexer for strings, characters and comments. */
    private static BitSet linesInTextBlocks(String text, List<Integer> starts) {
        BitSet result = new BitSet();
        State state = State.CODE;
        int line = 0;
        for (int i = 0; i < text.length(); i++) {
            // <= because an escape can skip over a line break (a text block's \<line break>)
            while (line + 1 < starts.size() && starts.get(line + 1) <= i) {
                line++;
                if (state == State.TEXT_BLOCK) {
                    result.set(line);
                }
            }
            char c = text.charAt(i);
            switch (state) {
                case CODE:
                    if (text.startsWith("\"\"\"", i)) {
                        state = State.TEXT_BLOCK;
                        i += 2;
                    } else if (c == '"') {
                        state = State.STRING;
                    } else if (c == '\'') {
                        state = State.CHAR;
                    } else if (text.startsWith("//", i)) {
                        state = State.LINE_COMMENT;
                        i++;
                    } else if (text.startsWith("/*", i)) {
                        state = State.BLOCK_COMMENT;
                        i++;
                    }
                    break;
                case STRING:
                case CHAR:
                    char quote = state == State.STRING ? '"' : '\'';
                    if (c == '\\') {
                        i++;
                    } else if (c == quote || isLineBreak(c)) {
                        state = State.CODE;
                    }
                    break;
                case TEXT_BLOCK:
                    if (c == '\\') {
                        i++;
                    } else if (text.startsWith("\"\"\"", i)) {
                        state = State.CODE;
                        i += 2;
                    }
                    break;
                case LINE_COMMENT:
                    if (isLineBreak(c)) {
                        state = State.CODE;
                    }
                    break;
                case BLOCK_COMMENT:
                    if (text.startsWith("*/", i)) {
                        state = State.CODE;
                        i++;
                    }
                    break;
            }
        }
        return result;
    }
}
