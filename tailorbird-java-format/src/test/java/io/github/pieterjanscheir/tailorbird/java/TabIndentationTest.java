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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Range;
import com.palantir.javaformat.java.JavaFormatterOptions;
import java.util.List;
import org.junit.jupiter.api.Test;

class TabIndentationTest {

    @Test
    void smart_tabs_keep_the_remainder_as_spaces() {
        assertThat(TabIndentation.toTabs("a\n    b\n     * c\n          d\n", 4))
                .isEqualTo("a\n\tb\n\t * c\n\t\t  d\n");
    }

    @Test
    void tabs_reach_the_next_tab_stop() {
        assertThat(TabIndentation.toSpaces("\tb\n  \tc\n\t  d\n", 4)).isEqualTo("    b\n    c\n      d\n");
    }

    @Test
    void round_trip() {
        String spaces = "class A {\n    int a;\n\n        int b;\n     * c\n}\n";
        assertThat(TabIndentation.toSpaces(TabIndentation.toTabs(spaces, 4), 4)).isEqualTo(spaces);
    }

    @Test
    void text_blocks_are_left_alone() {
        String text =
                "class A {\n    String s = \"\"\"\n        one\n          two \\\n        three\n        \"\"\";\n"
                        + "    int x;\n}\n";
        assertThat(TabIndentation.toTabs(text, 4))
                .isEqualTo("class A {\n"
                        + "\tString s = \"\"\"\n"
                        + "        one\n"
                        + "          two \\\n"
                        + "        three\n"
                        + "        \"\"\";\n"
                        + "\tint x;\n"
                        + "}\n");
    }

    @Test
    void quotes_in_comments_and_strings_do_not_start_a_text_block() {
        String text =
                "class A {\n    // \"\"\" not a text block\n    String s = \"\\\"\"; /* \"\"\" */\n    char c = '\"';\n"
                        + "    int x;\n}\n";
        assertThat(TabIndentation.toTabs(text, 4))
                .isEqualTo("class A {\n"
                        + "\t// \"\"\" not a text block\n"
                        + "\tString s = \"\\\"\"; /* \"\"\" */\n"
                        + "\tchar c = '\"';\n"
                        + "\tint x;\n"
                        + "}\n");
    }

    @Test
    void windows_line_endings_and_blank_lines() {
        assertThat(TabIndentation.toTabs("a\r\n    b\r\n    \r\n\r\n        c", 2))
                .isEqualTo("a\r\n\t\tb\r\n    \r\n\r\n\t\t\t\tc");
    }

    @Test
    void ranges_follow_the_characters() {
        String from = "a\n\tbc\n\td\n";
        String to = TabIndentation.toSpaces(from, 4);
        // "bc" starts at 3 in from and at 6 in to; "d" at 7 and 13
        assertThat(TabIndentation.mapRange(from, to, Range.closedOpen(3, 5))).isEqualTo(Range.closedOpen(6, 8));
        assertThat(TabIndentation.mapRange(from, to, Range.closedOpen(2, 8))).isEqualTo(Range.closedOpen(2, 14));
        assertThat(TabIndentation.mapRange(from, to, Range.closedOpen(0, from.length())))
                .isEqualTo(Range.closedOpen(0, to.length()));
    }

    @Test
    void formatter_indents_with_tabs_and_reads_tabs() throws Exception {
        Formatter formatter = Formatter.createFormatter(
                JavaFormatterOptions.builder().build(),
                TailorbirdRules.builder().indentWithTabs(true).build());
        String input = "class A {\n\tvoid f() {\n\t\tint x = 1;\n\t}\n}\n";
        String output = formatter.formatSourceAndFixImports(input);
        assertThat(output).isEqualTo("class A {\n\n\tvoid f() {\n\t\tint x = 1;\n\t}\n\n}\n");
        assertThat(formatter.formatSourceAndFixImports(output)).isEqualTo(output);
        assertThat(formatter.getFormatReplacements(output, ImmutableList.of(Range.closedOpen(0, output.length()))))
                .isEmpty();
    }

    @Test
    void command_line_formats_with_tabs() throws Exception {
        TailorbirdRules rules = TailorbirdRules.builder().indentWithTabs(true).build();
        String input = "class A {\n\tvoid f() {\n\t\tint x=1;\n    int y=2;\n\t}\n}\n";
        String expected = "class A {\n\n\tvoid f() {\n\t\tint x = 1;\n\t\tint y = 2;\n\t}\n\n}\n";
        assertThat(callable(List.of("-"), input, rules).call()).isEqualTo(expected);

        // Only line 4 is formatted, but the rest of the file is indented with tabs too.
        assertThat(callable(List.of("--lines=4", "-"), input, rules).call())
                .isEqualTo("class A {\n\tvoid f() {\n\t\tint x=1;\n\t\tint y = 2;\n\t}\n}\n");

        assertThat(callable(List.of("--output-replacements", "-"), expected, rules)
                        .call())
                .isEqualTo("[]");
        assertThat(callable(List.of("--output-replacements", "-"), input, rules).call())
                .contains("\"replacementString\":\"class A {\\n\\n\\tvoid f()");
    }

    private static FormatFileCallable callable(List<String> args, String input, TailorbirdRules rules) {
        return new FormatFileCallable(
                CommandLineOptionsParser.parse(args),
                input,
                JavaFormatterOptions.builder().build(),
                rules);
    }
}
