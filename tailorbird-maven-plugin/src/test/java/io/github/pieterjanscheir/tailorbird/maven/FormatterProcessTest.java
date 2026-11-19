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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FormatterProcessTest {

    @Test
    void quotes_backslashes_and_quotes_for_argument_files() {
        assertThat(FormatterProcess.quote("C:\\My Project\\A.java")).isEqualTo("\"C:\\\\My Project\\\\A.java\"");
        assertThat(FormatterProcess.quote("a\"b")).isEqualTo("\"a\\\"b\"");
    }

    @Test
    void writes_argument_files_in_the_given_encoding(@TempDir Path tempDir) throws Exception {
        Path argFile = tempDir.resolve("formatter.args");

        FormatterProcess.writeArgFile(argFile, List.of("C:\\Users\\Zo\u00eb"), StandardCharsets.ISO_8859_1);

        byte[] bytes = Files.readAllBytes(argFile);
        // One byte for the \u00eb, as windows-1252 and ISO-8859-1 encode it, not the two bytes of UTF-8.
        assertThat(bytes).contains((byte) 0xEB);
        assertThat(new String(bytes, StandardCharsets.ISO_8859_1)).startsWith("C:\\Users\\Zo\u00eb");
    }

    @Test
    void refuses_arguments_the_launcher_could_not_read(@TempDir Path tempDir) {
        Path argFile = tempDir.resolve("formatter.args");

        assertThatThrownBy(() -> FormatterProcess.writeArgFile(
                        argFile, List.of("/home/zo\u00eb/A.java"), StandardCharsets.US_ASCII))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("/home/zo\u00eb/A.java")
                .hasMessageContaining("US-ASCII");
        assertThat(argFile).doesNotExist();
    }

    @Test
    void uses_the_native_encoding_for_argument_files() {
        String nativeEncoding = System.getProperty("native.encoding", System.getProperty("sun.jnu.encoding"));
        assertThat(FormatterProcess.argFileCharset()).isEqualTo(Charset.forName(nativeEncoding));
    }

    @Test
    void formats_files_with_non_ascii_characters_in_their_path(@TempDir Path tempDir) throws Exception {
        Assumptions.assumeTrue(
                FormatterProcess.argFileCharset().newEncoder().canEncode("zo\u00eb"),
                "the native encoding can't represent the path");
        Path source = tempDir.resolve("zo\u00eb").resolve("Unformatted.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "class   Unformatted {  void f( ) { } }\n");

        FormatterProcess process = new FormatterProcess(
                Paths.get(System.getProperty("java.home"), "bin", "java"), testClasspath(), tempDir);

        FormatterProcess.Result replace = process.run(List.of("--palantir", "--replace"), List.of(source));
        assertThat(replace.exitCode()).as(replace.stderr()).isZero();
        assertThat(Files.readString(source)).isEqualTo("class Unformatted {\n\n    void f() {}\n\n}\n");
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
