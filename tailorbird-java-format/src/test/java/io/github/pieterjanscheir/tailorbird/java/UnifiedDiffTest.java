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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** The expected outputs are what {@code diff -u} prints for the same files. */
class UnifiedDiffTest {

    @Test
    void equal_texts_have_no_diff() {
        assertThat(UnifiedDiff.diff("A.java", "a\nb\n", "a\nb\n")).isEmpty();
    }

    @Test
    void changes_far_apart_get_their_own_hunks() {
        String before = "a\nb\nc\nd\ne\nf\ng\nh\ni\nj\nk\nl\nm\n";
        String after = "a\nB\nc\nd\ne\nf\ng\nh\ni\nj\nk\nl\nM\nn\n";
        assertThat(UnifiedDiff.diff("A.java", before, after))
                .isEqualTo("--- A.java\n"
                        + "+++ A.java\n"
                        + "@@ -1,5 +1,5 @@\n"
                        + " a\n"
                        + "-b\n"
                        + "+B\n"
                        + " c\n"
                        + " d\n"
                        + " e\n"
                        + "@@ -10,4 +10,5 @@\n"
                        + " j\n"
                        + " k\n"
                        + " l\n"
                        + "-m\n"
                        + "+M\n"
                        + "+n\n");
    }

    @Test
    void changes_close_together_share_a_hunk() {
        String before = "a\nb\nc\nd\ne\nf\ng\nh\ni\n";
        String after = "a\nB\nc\nd\ne\nf\ng\nH\ni\n";
        assertThat(UnifiedDiff.diff("A.java", before, after))
                .isEqualTo("--- A.java\n"
                        + "+++ A.java\n"
                        + "@@ -1,9 +1,9 @@\n"
                        + " a\n"
                        + "-b\n"
                        + "+B\n"
                        + " c\n"
                        + " d\n"
                        + " e\n"
                        + " f\n"
                        + " g\n"
                        + "-h\n"
                        + "+H\n"
                        + " i\n");
    }

    @Test
    void missing_newline_at_end_of_file() {
        assertThat(UnifiedDiff.diff("A.java", "x\ny", "x\ny\n"))
                .isEqualTo("--- A.java\n"
                        + "+++ A.java\n"
                        + "@@ -1,2 +1,2 @@\n"
                        + " x\n"
                        + "-y\n"
                        + "\\ No newline at end of file\n"
                        + "+y\n");
    }

    @Test
    void empty_files() {
        assertThat(UnifiedDiff.diff("A.java", "", "x\ny\n"))
                .isEqualTo("--- A.java\n+++ A.java\n@@ -0,0 +1,2 @@\n+x\n+y\n");
        assertThat(UnifiedDiff.diff("A.java", "x\ny\n", ""))
                .isEqualTo("--- A.java\n+++ A.java\n@@ -1,2 +0,0 @@\n-x\n-y\n");
    }

    @Test
    void line_endings_are_part_of_the_line() {
        assertThat(UnifiedDiff.diff("A.java", "x\r\ny\n", "x\ny\n"))
                .isEqualTo("--- A.java\n+++ A.java\n@@ -1,2 +1,2 @@\n-x\r\n+x\n y\n");
    }

    @Test
    void applying_the_diff_gives_the_new_text() {
        Random random = new Random(52);
        for (int run = 0; run < 2000; run++) {
            String before = randomText(random);
            String after = random.nextInt(4) == 0 ? randomText(random) : mutate(before, random);
            String diff = UnifiedDiff.diff("A.java", before, after);
            assertThat(apply(before, diff)).as("diff:\n%s", diff).isEqualTo(after);
        }
    }

    private static String randomText(Random random) {
        StringBuilder text = new StringBuilder();
        int lines = random.nextInt(25);
        for (int i = 0; i < lines; i++) {
            text.append((char) ('a' + random.nextInt(4))).append('\n');
        }
        return text.toString();
    }

    private static String mutate(String text, Random random) {
        List<String> lines = new ArrayList<>(List.of(text.split("(?<=\n)")));
        lines.removeIf(String::isEmpty);
        int changes = random.nextInt(4);
        for (int i = 0; i < changes; i++) {
            int at = lines.isEmpty() ? 0 : random.nextInt(lines.size() + 1);
            switch (random.nextInt(3)) {
                case 0:
                    lines.add(at, "z\n");
                    break;
                case 1:
                    if (at < lines.size()) {
                        lines.remove(at);
                    }
                    break;
                default:
                    if (at < lines.size()) {
                        lines.set(at, "y\n");
                    }
            }
        }
        return String.join("", lines);
    }

    private static final Pattern HUNK = Pattern.compile("@@ -(\\d+)(?:,(\\d+))? \\+\\d+(?:,\\d+)? @@");

    /** Applies a diff of texts that all end with a newline, checking the context and removed lines. */
    private static String apply(String before, String diff) {
        if (diff.isEmpty()) {
            return before;
        }
        List<String> source = new ArrayList<>(List.of(before.split("(?<=\n)")));
        source.removeIf(String::isEmpty);
        List<String> result = new ArrayList<>();
        String[] lines = diff.split("(?<=\n)");
        int next = 0; // the next source line to copy
        for (int i = 2; i < lines.length; i++) {
            Matcher matcher = HUNK.matcher(lines[i]);
            assertThat(matcher.lookingAt()).as(lines[i]).isTrue();
            int start = Integer.parseInt(matcher.group(1));
            int count = matcher.group(2) == null ? 1 : Integer.parseInt(matcher.group(2));
            int from = count == 0 ? start : start - 1;
            while (next < from) {
                result.add(source.get(next++));
            }
            while (i + 1 < lines.length && !lines[i + 1].startsWith("@@")) {
                String line = lines[++i];
                char kind = line.charAt(0);
                String text = line.substring(1);
                if (kind == '+') {
                    result.add(text);
                } else {
                    assertThat(source.get(next++)).isEqualTo(text);
                    if (kind == ' ') {
                        result.add(text);
                    }
                }
            }
        }
        while (next < source.size()) {
            result.add(source.get(next++));
        }
        return String.join("", result);
    }
}
