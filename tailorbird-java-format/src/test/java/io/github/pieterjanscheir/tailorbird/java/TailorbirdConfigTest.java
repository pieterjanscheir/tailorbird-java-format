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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.pieterjanscheir.tailorbird.java.TailorbirdConfig.TailorbirdConfigException;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TailorbirdConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void empty_file_gives_defaults() {
        assertThat(TailorbirdConfig.parse("", "test")).isEqualTo(TailorbirdRules.defaults());
        assertThat(TailorbirdConfig.parse("# only a comment\n", "test")).isEqualTo(TailorbirdRules.defaults());
    }

    @Test
    void reads_every_setting() {
        String yaml = """
            lineLength: 100
            indent: 2
            continuationIndent: 8
            spaceAfterControlKeyword: true
            arrayInitializerBraceSpaces: false
            closingParenOnOwnLine: false
            tryResourcesOnOwnLines: false
            bodyWithoutBracesOnOwnLine: false
            blankLines:
              keepTyped: true
              aroundClassBody: false
              betweenFields: true
              aroundFieldsWithJavadoc: true
              aroundInterfaceMethods: true
            imports:
              groups: [static, com.example, "*"]
              blankLineBetweenGroups: false
            javadoc:
              enabled: false
              removeBlankLinesInDescription: false
              blankLineAfterDescription: false
              blankLineAfterParams: false
              blankLineAfterReturn: false
              singleSpaceAfterTagName: false
            formatterTags:
              enabled: true
              off: "fmt:off"
              on: "fmt:on"
            """;

        TailorbirdRules rules = TailorbirdConfig.parse(yaml, "test");

        assertThat(rules)
                .isEqualTo(TailorbirdRules.builder()
                        .lineLength(100)
                        .indent(2)
                        .continuationIndent(8)
                        .spaceAfterControlKeyword(true)
                        .arrayInitializerBraceSpaces(false)
                        .closingParenOnOwnLine(false)
                        .tryResourcesOnOwnLines(false)
                        .bodyWithoutBracesOnOwnLine(false)
                        .keepTypedBlankLines(true)
                        .blankLinesAroundClassBody(false)
                        .blankLinesBetweenFields(true)
                        .blankLinesAroundFieldsWithJavadoc(true)
                        .blankLinesAroundInterfaceMethods(true)
                        .importGroups(List.of("static", "com.example", "*"))
                        .blankLineBetweenImportGroups(false)
                        .javadocLayout(false)
                        .javadocRemoveBlankLinesInDescription(false)
                        .javadocBlankLineAfterDescription(false)
                        .javadocBlankLineAfterParams(false)
                        .javadocBlankLineAfterReturn(false)
                        .javadocSingleSpaceAfterTagName(false)
                        .formatterTags(true)
                        .formatterOffTag("fmt:off")
                        .formatterOnTag("fmt:on")
                        .build());
        assertThat(TailorbirdConfig.settingNames()).hasSize(24);
    }

    @Test
    void preset_is_the_starting_point_for_the_other_settings() {
        // The request in palantir/palantir-java-format#951: Palantir's style with 120 characters and 2 spaces.
        TailorbirdRules rules = TailorbirdConfig.parse(
                "indent: 2\ncontinuationIndent: 4\npreset: palantir\n", "test"); // preset may come last

        assertThat(rules)
                .isEqualTo(TailorbirdRules.builder(TailorbirdRules.Preset.PALANTIR)
                        .indent(2)
                        .continuationIndent(4)
                        .build());
        assertThat(rules.lineLength()).isEqualTo(120);
        assertThat(rules.spaceAfterControlKeyword()).isTrue();
    }

    @Test
    void presets() {
        assertThat(TailorbirdConfig.parse("preset: tailorbird\n", "test")).isEqualTo(TailorbirdRules.defaults());
        TailorbirdRules google = TailorbirdConfig.parse("preset: google\n", "test");
        assertThat(google.lineLength()).isEqualTo(100);
        assertThat(google.indent()).isEqualTo(2);
        assertThat(google.continuationIndent()).isEqualTo(4);
        assertThatThrownBy(() -> TailorbirdConfig.parse("preset: intellij\n", "test"))
                .isInstanceOf(TailorbirdConfigException.class)
                .hasMessageContaining("preset must be one of tailorbird, palantir or google");
    }

    @Test
    void reads_a_file_given_by_path() throws IOException {
        Path file = tempDir.resolve("tailorbird.yml");
        Files.writeString(file, "preset: palantir\nindent: 2\n", StandardCharsets.UTF_8);

        assertThat(TailorbirdConfig.load(file).indent()).isEqualTo(2);
        assertThatThrownBy(() -> TailorbirdConfig.load(tempDir.resolve("missing.yml")))
                .isInstanceOf(TailorbirdConfigException.class)
                .hasMessageContaining("Could not read");
    }

    @Test
    void settings_that_are_left_out_keep_their_default() {
        TailorbirdRules rules = TailorbirdConfig.parse("indent: 2\nblankLines:\n  betweenFields: true\n", "test");

        assertThat(rules.indent()).isEqualTo(2);
        assertThat(rules.blankLinesBetweenFields()).isTrue();
        assertThat(rules.continuationIndent())
                .isEqualTo(TailorbirdRules.defaults().continuationIndent());
        assertThat(rules.importGroups()).isEqualTo(TailorbirdRules.defaults().importGroups());
    }

    @Test
    void unknown_setting_is_an_error_that_lists_the_known_ones() {
        assertThatThrownBy(() -> TailorbirdConfig.parse("blankLines:\n  betweenFeilds: true\n", "tailorbird.yml"))
                .isInstanceOf(TailorbirdConfigException.class)
                .hasMessageContaining("unknown setting 'blankLines.betweenFeilds'")
                .hasMessageContaining("blankLines.betweenFields");
    }

    @Test
    void wrong_types_are_errors() {
        assertThatThrownBy(() -> TailorbirdConfig.parse("indent: four\n", "test"))
                .isInstanceOf(TailorbirdConfigException.class)
                .hasMessageContaining("indent must be a whole number");
        assertThatThrownBy(() -> TailorbirdConfig.parse("spaceAfterControlKeyword: yes please\n", "test"))
                .isInstanceOf(TailorbirdConfigException.class)
                .hasMessageContaining("spaceAfterControlKeyword must be true or false");
        assertThatThrownBy(() -> TailorbirdConfig.parse("imports:\n  groups: java\n", "test"))
                .isInstanceOf(TailorbirdConfigException.class)
                .hasMessageContaining("imports.groups must be a list");
        assertThatThrownBy(() -> TailorbirdConfig.parse("blankLines: true\n", "test"))
                .isInstanceOf(TailorbirdConfigException.class)
                .hasMessageContaining("unknown setting 'blankLines'");
    }

    @Test
    void invalid_values_are_errors() {
        assertThatThrownBy(() -> TailorbirdConfig.parse("formatterTags:\n  off: \" \"\n", "test"))
                .isInstanceOf(TailorbirdConfigException.class)
                .hasMessageContaining("formatterTags.off must not be empty");
        assertThatThrownBy(() -> TailorbirdConfig.parse("formatterTags:\n  off: \"// foo\"\n", "test"))
                .isInstanceOf(TailorbirdConfigException.class)
                .hasMessageContaining("formatterTags.off must not contain spaces, / or *");
        assertThatThrownBy(() -> TailorbirdConfig.parse("formatterTags:\n  off: \"fmt\u00A0off\"\n", "test"))
                .isInstanceOf(TailorbirdConfigException.class)
                .hasMessageContaining("formatterTags.off must not contain spaces, / or *");
        assertThatThrownBy(() -> TailorbirdConfig.parse("formatterTags:\n  on: \"fmt on\"\n", "test"))
                .isInstanceOf(TailorbirdConfigException.class)
                .hasMessageContaining("formatterTags.on must not contain spaces, / or *");
        assertThatThrownBy(() -> TailorbirdConfig.parse("formatterTags:\n  on: 3\n", "test"))
                .isInstanceOf(TailorbirdConfigException.class)
                .hasMessageContaining("formatterTags.on must be text");
        assertThatThrownBy(() -> TailorbirdConfig.parse("lineLength: 10\n", "test"))
                .isInstanceOf(TailorbirdConfigException.class)
                .hasMessageContaining("lineLength must be at least 40");
        assertThatThrownBy(() -> TailorbirdConfig.parse("imports:\n  groups: [java, java]\n", "test"))
                .isInstanceOf(TailorbirdConfigException.class)
                .hasMessageContaining("lists a group twice");
        assertThatThrownBy(() -> TailorbirdConfig.parse("imports:\n  groups: []\n", "test"))
                .isInstanceOf(TailorbirdConfigException.class)
                .hasMessageContaining("at least one group");
    }

    @Test
    void a_setting_written_twice_is_an_error() {
        assertThatThrownBy(() ->
                        TailorbirdConfig.parse("indent: 2\nspaceAfterControlKeyword: true\nindent: 4\n", "my.yml"))
                .isInstanceOf(TailorbirdConfigException.class)
                .hasMessageContaining("my.yml is not valid YAML")
                .hasMessageContaining("indent");
        assertThatThrownBy(() ->
                        TailorbirdConfig.parse("javadoc:\n  enabled: true\njavadoc:\n  enabled: false\n", "my.yml"))
                .isInstanceOf(TailorbirdConfigException.class)
                .hasMessageContaining("javadoc");
    }

    @Test
    void import_groups_without_static_keep_a_static_and_a_normal_import_of_the_same_name() throws Exception {
        String source = """
            package example;

            import a.B.C;
            import static a.B.C;

            class Example {
                C field = C;
            }
            """;
        TailorbirdRules rules = TailorbirdConfig.parse("imports:\n  groups: [java, \"*\"]\n", "test");

        String imports = ImportOrderer.reorderImports(source, rules);

        assertThat(imports).contains("import static a.B.C;").contains("import a.B.C;");
    }

    @Test
    void invalid_yaml_is_an_error() {
        assertThatThrownBy(() -> TailorbirdConfig.parse("indent: [2\n", "my.yml"))
                .isInstanceOf(TailorbirdConfigException.class)
                .hasMessageContaining("my.yml is not valid YAML");
        assertThatThrownBy(() -> TailorbirdConfig.parse("just a value\n", "my.yml"))
                .isInstanceOf(TailorbirdConfigException.class)
                .hasMessageContaining("must contain settings");
    }

    @Test
    void loads_the_file_from_a_folder_on_the_classpath() throws IOException {
        Path folder = Files.createDirectory(tempDir.resolve("config"));
        Files.writeString(folder.resolve(TailorbirdConfig.FILE_NAME), "indent: 2\n", StandardCharsets.UTF_8);

        try (URLClassLoader classLoader = classLoader(folder)) {
            assertThat(TailorbirdConfig.load(classLoader).indent()).isEqualTo(2);
        }
    }

    @Test
    void loads_the_file_when_it_is_a_classpath_entry_itself() throws IOException {
        // palantirJavaFormat files('config/tailorbird.yml'): the file, not its folder, is on the classpath.
        Path file = Files.createDirectory(tempDir.resolve("config")).resolve(TailorbirdConfig.FILE_NAME);
        Files.writeString(file, "indent: 2\n", StandardCharsets.UTF_8);

        try (URLClassLoader classLoader = classLoader(file)) {
            assertThat(TailorbirdConfig.load(classLoader).indent()).isEqualTo(2);
        }
    }

    @Test
    void the_same_file_as_entry_and_in_a_folder_counts_once() throws IOException {
        Path folder = Files.createDirectory(tempDir.resolve("both"));
        Path file = folder.resolve(TailorbirdConfig.FILE_NAME);
        Files.writeString(file, "indent: 2\n", StandardCharsets.UTF_8);

        try (URLClassLoader classLoader = classLoader(folder, file)) {
            assertThat(TailorbirdConfig.load(classLoader).indent()).isEqualTo(2);
        }
    }

    @Test
    void no_file_on_the_classpath_gives_defaults() throws IOException {
        try (URLClassLoader classLoader = classLoader(Files.createDirectory(tempDir.resolve("empty")))) {
            assertThat(TailorbirdConfig.load(classLoader)).isEqualTo(TailorbirdRules.defaults());
        }
    }

    @Test
    void two_files_on_the_classpath_is_an_error() throws IOException {
        Path first = Files.createDirectory(tempDir.resolve("first"));
        Path second = Files.createDirectory(tempDir.resolve("second"));
        Files.writeString(first.resolve(TailorbirdConfig.FILE_NAME), "indent: 2\n", StandardCharsets.UTF_8);
        Files.writeString(second.resolve(TailorbirdConfig.FILE_NAME), "indent: 8\n", StandardCharsets.UTF_8);

        try (URLClassLoader classLoader = classLoader(first, second)) {
            assertThatThrownBy(() -> TailorbirdConfig.load(classLoader))
                    .isInstanceOf(TailorbirdConfigException.class)
                    .hasMessageContaining("more than once");
        }
    }

    @Test
    void formatter_service_uses_the_file_on_its_classpath() throws Exception {
        String source = "class A { void f(int x) { if (x > 0) { f(x); } } }\n";

        assertThat(new FormatterServiceImpl(TailorbirdConfig.parse("spaceAfterControlKeyword: true\n", "test"))
                        .formatSourceReflowStringsAndFixImports(source))
                .contains("if (x > 0)");
        assertThat(new FormatterServiceImpl(TailorbirdRules.defaults()).formatSourceReflowStringsAndFixImports(source))
                .contains("if(x > 0)");
    }

    /** A class loader that only sees {@code folders}, like the plugins' formatter class loader with a config folder. */
    private static URLClassLoader classLoader(Path... folders) throws IOException {
        URL[] urls = new URL[folders.length];
        for (int i = 0; i < folders.length; i++) {
            urls[i] = folders[i].toUri().toURL();
        }
        return new URLClassLoader(urls, null);
    }
}
