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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BowerbirdFormatterTest {

    private static final String UNFORMATTED = "import java.util.List;\nimport java.util.Map;\n"
            + "class A {\n  Map<String, String> m;\n  void f() { int x=1; }\n}\n";

    @TempDir
    Path folder;

    @Test
    void defaults_format_the_whole_file() throws Exception {
        assertThat(BowerbirdFormatter.defaults().format(UNFORMATTED))
                .isEqualTo("import java.util.Map;\n\n"
                        + "class A {\n\n"
                        + "    Map<String, String> m;\n\n"
                        + "    void f() {\n"
                        + "        int x = 1;\n"
                        + "    }\n\n"
                        + "}\n");
    }

    @Test
    void config_text_is_used() throws Exception {
        BowerbirdFormatter formatter = BowerbirdFormatter.fromConfigText("preset: google\n", "inline");
        assertThat(formatter.format("class A {\n    int x;\n}\n")).isEqualTo("class A {\n  int x;\n}\n");
    }

    @Test
    void config_file_is_used() throws Exception {
        Path config = Files.writeString(folder.resolve("bowerbird.yml"), "preset: google\n");
        assertThat(BowerbirdFormatter.fromConfig(config).format("class A {\n    int x;\n}\n"))
                .isEqualTo("class A {\n  int x;\n}\n");
    }

    @Test
    void classpath_config_is_used() throws Exception {
        Files.writeString(folder.resolve("bowerbird.yml"), "preset: google\n");
        try (URLClassLoader loader =
                new URLClassLoader(new URL[] {folder.toUri().toURL()}, null)) {
            assertThat(BowerbirdFormatter.fromClasspath(loader).format("class A {\n    int x;\n}\n"))
                    .isEqualTo("class A {\n  int x;\n}\n");
        }
    }

    @Test
    void invalid_setting_names_the_file_and_the_setting() throws Exception {
        Path config = Files.writeString(folder.resolve("bowerbird.yml"), "lineLength: wide\n");
        assertThatThrownBy(() -> BowerbirdFormatter.fromConfig(config))
                .isInstanceOf(BowerbirdConfigException.class)
                .hasMessageContaining(config.toString())
                .hasMessageContaining("lineLength");
    }

    @Test
    void missing_config_file() {
        Path config = folder.resolve("missing.yml");
        assertThatThrownBy(() -> BowerbirdFormatter.fromConfig(config))
                .isInstanceOf(BowerbirdConfigException.class)
                .hasMessageContaining(config.toString());
    }

    @Test
    void source_that_does_not_parse() {
        assertThatThrownBy(() -> BowerbirdFormatter.defaults().format("class A {\n  int x\n}\n"))
                .isInstanceOfSatisfying(BowerbirdFormatException.class, e -> {
                    assertThat(e.diagnostics()).isNotEmpty();
                    BowerbirdFormatException.Diagnostic diagnostic =
                            e.diagnostics().get(0);
                    assertThat(diagnostic.line()).isEqualTo(2);
                    assertThat(diagnostic.column()).isGreaterThan(0);
                    assertThat(diagnostic.toString()).startsWith("2:" + diagnostic.column() + ": error: ");
                    assertThat(e.getMessage()).isEqualTo(diagnostic.toString());
                });
    }

    @Test
    void ranges_format_only_the_lines_they_touch() throws Exception {
        String source = "class A {\n  int a=1;\n  int b=2;\n}\n";
        int line3 = source.indexOf("  int b");
        List<TextEdit> edits =
                BowerbirdFormatter.defaults().formatRanges(source, List.of(TextRange.of(line3, line3 + 3)));
        assertThat(edits).isNotEmpty();
        assertThat(TextEdit.apply(source, edits)).isEqualTo("class A {\n  int a=1;\n    int b = 2;\n\n}\n");
    }

    @Test
    void no_edits_for_formatted_code() throws Exception {
        String source = BowerbirdFormatter.defaults().format(UNFORMATTED);
        assertThat(BowerbirdFormatter.defaults().formatRanges(source, List.of(TextRange.of(0, source.length()))))
                .isEmpty();
    }

    @Test
    void fix_imports_changes_nothing_else() throws Exception {
        assertThat(BowerbirdFormatter.defaults().fixImports(UNFORMATTED))
                .isEqualTo("import java.util.Map;\n\n"
                        + "class A {\n  Map<String, String> m;\n  void f() { int x=1; }\n}\n");
    }

    @Test
    void required_jvm_arguments_open_the_compiler_packages() {
        assertThat(BowerbirdFormatter.requiredJvmArguments())
                .hasSize(6)
                .allMatch(argument -> argument.startsWith("--add-exports=jdk.compiler/com.sun.tools.javac."));
    }

    @Test
    void edits_must_be_sorted_and_not_overlap() {
        List<TextEdit> overlapping =
                List.of(TextEdit.of(TextRange.of(0, 3), "x"), TextEdit.of(TextRange.of(2, 4), "y"));
        assertThatThrownBy(() -> TextEdit.apply("abcdef", overlapping)).isInstanceOf(IllegalArgumentException.class);
        assertThat(TextEdit.apply(
                        "abcdef", List.of(TextEdit.of(TextRange.of(1, 3), "XY"), TextEdit.of(TextRange.of(5, 6), ""))))
                .isEqualTo("aXYde");
        assertThatThrownBy(() -> TextRange.of(3, 2)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void config_file_in_utf8_is_read() throws Exception {
        Path config =
                Files.write(folder.resolve("bowerbird.yml"), "# é\npreset: google\n".getBytes(StandardCharsets.UTF_8));
        assertThat(BowerbirdFormatter.fromConfig(config).format("class A {\n    int x;\n}\n"))
                .isEqualTo("class A {\n  int x;\n}\n");
    }
}
