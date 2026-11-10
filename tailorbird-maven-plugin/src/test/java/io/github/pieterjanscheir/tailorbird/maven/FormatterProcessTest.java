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

package io.github.pieterjanscheir.tailorbird.maven;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FormatterProcessTest {

    @Test
    void quotes_backslashes_and_quotes_for_argument_files() {
        assertThat(FormatterProcess.quote("C:\\My Project\\A.java")).isEqualTo("\"C:\\\\My Project\\\\A.java\"");
        assertThat(FormatterProcess.quote("a\"b")).isEqualTo("\"a\\\"b\"");
    }

    @Test
    void formats_files_with_spaces_in_their_path(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("dir with spaces").resolve("Unformatted.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "class   Unformatted {  void f( ) { } }\n");

        FormatterProcess process = new FormatterProcess(
                Paths.get(System.getProperty("java.home"), "bin", "java"), testClasspath(), tempDir);

        FormatterProcess.Result dryRun = process.run(List.of("--palantir", "--dry-run"), List.of(source));
        assertThat(dryRun.exitCode()).isZero();
        assertThat(dryRun.stdout()).containsExactly(source.toAbsolutePath().toString());

        FormatterProcess.Result replace = process.run(List.of("--palantir", "--replace"), List.of(source));
        assertThat(replace.exitCode()).as(replace.stderr()).isZero();
        assertThat(Files.readString(source)).isEqualTo("class Unformatted {\n\n    void f() {}\n\n}\n");
    }

    @Test
    void formats_with_the_rules_from_a_config_file(@TempDir Path tempDir) throws Exception {
        Path config = tempDir.resolve("tailorbird.yml");
        Files.writeString(config, "indent: 2\n");
        Path source = tempDir.resolve("Unformatted.java");
        Files.writeString(source, "class   Unformatted {  void f( ) { } }\n");

        FormatterProcess process = new FormatterProcess(
                Paths.get(System.getProperty("java.home"), "bin", "java"), testClasspath(), tempDir);

        FormatterProcess.Result replace =
                process.run(List.of("--config", config.toString(), "--replace"), List.of(source));
        assertThat(replace.exitCode()).as(replace.stderr()).isZero();
        assertThat(Files.readString(source)).isEqualTo("class Unformatted {\n\n  void f() {}\n\n}\n");
    }

    @Test
    void reports_an_invalid_config_file(@TempDir Path tempDir) throws Exception {
        Path config = tempDir.resolve("tailorbird.yml");
        Files.writeString(config, "noSuchSetting: true\n");
        Path source = tempDir.resolve("A.java");
        Files.writeString(source, "class A {}\n");

        FormatterProcess process = new FormatterProcess(
                Paths.get(System.getProperty("java.home"), "bin", "java"), testClasspath(), tempDir);

        FormatterProcess.Result result =
                process.run(List.of("--config", config.toString(), "--dry-run"), List.of(source));
        assertThat(result.exitCode()).isNotZero();
        assertThat(result.stderr()).contains("noSuchSetting");
    }

    private static List<Path> testClasspath() {
        return Arrays.stream(System.getProperty("java.class.path").split(java.io.File.pathSeparator))
                .map(Paths::get)
                .collect(Collectors.toList());
    }
}
