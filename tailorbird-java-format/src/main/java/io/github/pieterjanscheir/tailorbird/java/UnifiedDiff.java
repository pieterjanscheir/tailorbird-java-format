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

import io.github.pieterjanscheir.tailorbird.Newlines;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Tailorbird: a unified diff between a file and its formatted version, for {@code --diff} (issue #50). The output is
 * what {@code diff -u} prints, with three lines of context, so {@code patch -p0} and {@code git apply -p0} can apply
 * it.
 *
 * <p>Lines are compared with their line endings, so a change from {@code \r\n} to {@code \n} shows up as a change. A
 * last line without a line ending is followed by {@code \ No newline at end of file}.
 */
final class UnifiedDiff {

    private static final int CONTEXT = 3;

    private UnifiedDiff() {}

    /** The diff from {@code before} to {@code after}, both named {@code path}; empty when they're equal. */
    static String diff(String path, String before, String after) {
        if (before.equals(after)) {
            return "";
        }
        List<String> a = lines(before);
        List<String> b = lines(after);
        List<Edit> edits = edits(a, b);
        StringBuilder out = new StringBuilder();
        out.append("--- ").append(path).append('\n');
        out.append("+++ ").append(path).append('\n');
        int i = 0;
        while (i < edits.size()) {
            if (edits.get(i).kind == ' ') {
                i++;
                continue;
            }
            // A hunk: from CONTEXT lines before this change up to CONTEXT lines after the last change that is at
            // most 2 * CONTEXT unchanged lines away from the next one.
            int start = Math.max(0, i - CONTEXT);
            int end = i;
            int unchanged = 0;
            for (int j = i; j < edits.size() && unchanged <= 2 * CONTEXT; j++) {
                if (edits.get(j).kind == ' ') {
                    unchanged++;
                } else {
                    unchanged = 0;
                    end = j;
                }
            }
            end = Math.min(edits.size() - 1, end + CONTEXT);
            appendHunk(out, a, b, edits.subList(start, end + 1));
            i = end + 1;
        }
        return out.toString();
    }

    private static void appendHunk(StringBuilder out, List<String> a, List<String> b, List<Edit> hunk) {
        int aStart = -1;
        int bStart = -1;
        int aCount = 0;
        int bCount = 0;
        for (Edit edit : hunk) {
            if (edit.kind != '+') {
                if (aStart < 0) {
                    aStart = edit.aIndex;
                }
                aCount++;
            }
            if (edit.kind != '-') {
                if (bStart < 0) {
                    bStart = edit.bIndex;
                }
                bCount++;
            }
        }
        // An empty range is written as the line before it, which is 0 at the start of the file.
        Edit first = hunk.get(0);
        out.append("@@ -")
                .append(range(aCount == 0 ? first.aIndex : aStart + 1, aCount))
                .append(" +")
                .append(range(bCount == 0 ? first.bIndex : bStart + 1, bCount))
                .append(" @@\n");
        for (Edit edit : hunk) {
            String line = edit.kind == '+' ? b.get(edit.bIndex) : a.get(edit.aIndex);
            out.append(edit.kind).append(line);
            if (!line.endsWith("\n") && !line.endsWith("\r")) {
                out.append("\n\\ No newline at end of file\n");
            }
        }
    }

    private static String range(int start, int count) {
        return count == 1 ? Integer.toString(start) : start + "," + count;
    }

    /** The lines of {@code text}, each with its line ending. */
    private static List<String> lines(String text) {
        List<String> lines = new ArrayList<>();
        Iterator<String> it = Newlines.lineIterator(text);
        while (it.hasNext()) {
            String line = it.next();
            if (!line.isEmpty()) {
                lines.add(line);
            }
        }
        return lines;
    }

    /**
     * One line of the edit script: {@code ' '} for a line in both, {@code '-'} for a line only in {@code a}, {@code '+'}
     * for a line only in {@code b}. {@code aIndex} and {@code bIndex} are where it is, or would be, in each.
     */
    private static final class Edit {
        final char kind;
        final int aIndex;
        final int bIndex;

        Edit(char kind, int aIndex, int bIndex) {
            this.kind = kind;
            this.aIndex = aIndex;
            this.bIndex = bIndex;
        }
    }

    /** The shortest edit script from {@code a} to {@code b}, with Myers' O(ND) algorithm. */
    private static List<Edit> edits(List<String> a, List<String> b) {
        int n = a.size();
        int m = b.size();
        int max = n + m;
        int offset = max + 1;
        int[] v = new int[2 * max + 3];
        List<int[]> trace = new ArrayList<>();
        int found = -1;
        for (int d = 0; d <= max && found < 0; d++) {
            trace.add(v.clone());
            for (int k = -d; k <= d; k += 2) {
                int x = k == -d || (k != d && v[offset + k - 1] < v[offset + k + 1])
                        ? v[offset + k + 1]
                        : v[offset + k - 1] + 1;
                int y = x - k;
                while (x < n && y < m && a.get(x).equals(b.get(y))) {
                    x++;
                    y++;
                }
                v[offset + k] = x;
                if (x >= n && y >= m) {
                    found = d;
                    break;
                }
            }
        }
        // Walk back from (n, m) through the saved rows to recover the script.
        List<Edit> reversed = new ArrayList<>();
        int x = n;
        int y = m;
        for (int d = found; d > 0; d--) {
            int[] row = trace.get(d);
            int k = x - y;
            int prevK = k == -d || (k != d && row[offset + k - 1] < row[offset + k + 1]) ? k + 1 : k - 1;
            int prevX = row[offset + prevK];
            int prevY = prevX - prevK;
            while (x > prevX && y > prevY) {
                x--;
                y--;
                reversed.add(new Edit(' ', x, y));
            }
            if (prevK == k + 1) {
                y--;
                reversed.add(new Edit('+', x, y));
            } else {
                x--;
                reversed.add(new Edit('-', x, y));
            }
        }
        while (x > 0 && y > 0) {
            x--;
            y--;
            reversed.add(new Edit(' ', x, y));
        }
        List<Edit> edits = new ArrayList<>(reversed.size());
        for (int i = reversed.size() - 1; i >= 0; i--) {
            edits.add(reversed.get(i));
        }
        return edits;
    }
}
