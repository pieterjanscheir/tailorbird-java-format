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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaSourcesTest {

    @Test
    void default_directories_leave_out_generated_sources(@TempDir Path module) {
        Path main = module.resolve("src/main/java");
        Path test = module.resolve("src/test/java");
        Path generated = module.resolve("target/generated-sources/annotations");

        List<Path> directories = JavaSources.defaultDirectories(
                List.of(main.toString(), generated.toString()), List.of(test.toString()), module.resolve("target"));

        assertThat(directories).containsExactly(main, test);
    }

    @Test
    void default_directories_are_listed_once(@TempDir Path module) {
        Path main = module.resolve("src/main/java");

        List<Path> directories = JavaSources.defaultDirectories(
                List.of(main.toString()), List.of(main.toString()), module.resolve("target"));

        assertThat(directories).containsExactly(main);
    }

    @Test
    void finds_java_files_and_skips_other_files_and_missing_directories(@TempDir Path module) throws Exception {
        Path main = module.resolve("src/main/java");
        Path a = write(main.resolve("demo/A.java"));
        Path b = write(main.resolve("demo/nested/B.java"));
        write(main.resolve("demo/notes.txt"));

        List<Path> files = JavaSources.find(module, List.of(main, module.resolve("src/test/java")), List.of());

        assertThat(files).containsExactly(a, b);
    }

    @Test
    void leaves_out_files_matching_an_exclude(@TempDir Path module) throws Exception {
        Path main = module.resolve("src/main/java");
        Path kept = write(main.resolve("demo/A.java"));
        write(main.resolve("demo/generated/Gen.java"));
        write(main.resolve("demo/Legacy.java"));

        List<Path> files =
                JavaSources.find(module, List.of(main), List.of("**/generated/**", "src/main/java/demo/Legacy.java"));

        assertThat(files).containsExactly(kept);
    }

    @Test
    void lists_a_file_once_when_directories_overlap(@TempDir Path module) throws Exception {
        Path main = module.resolve("src/main/java");
        Path a = write(main.resolve("demo/A.java"));

        List<Path> files = JavaSources.find(module, List.of(main, main.resolve("demo")), List.of());

        assertThat(files).containsExactly(a);
    }

    @Test
    void accepts_only_utf8_encodings() {
        assertThat(JavaSources.isUtf8("UTF-8")).isTrue();
        assertThat(JavaSources.isUtf8("utf8")).isTrue();
        assertThat(JavaSources.isUtf8("ISO-8859-1")).isFalse();
        assertThat(JavaSources.isUtf8("windows-1252")).isFalse();
        assertThat(JavaSources.isUtf8("no-such-encoding")).isFalse();
        assertThat(JavaSources.isUtf8("not a valid name!")).isFalse();
    }

    private static Path write(Path file) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, "class X {}\n");
        return file;
    }
}
