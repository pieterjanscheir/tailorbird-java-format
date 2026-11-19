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

class ConfigFilesTest {

    @Test
    void prefers_the_modules_own_config(@TempDir Path root) throws Exception {
        Path module = Files.createDirectories(root.resolve("module"));
        Files.writeString(root.resolve("tailorbird.yml"), "indent: 3\n");
        Files.writeString(module.resolve("tailorbird.yml"), "indent: 2\n");

        assertThat(ConfigFiles.find(List.of(module, root))).contains(module.resolve("tailorbird.yml"));
    }

    @Test
    void finds_a_parents_config_when_the_module_has_none(@TempDir Path root) throws Exception {
        Path parent = Files.createDirectories(root.resolve("services"));
        Path module = Files.createDirectories(parent.resolve("orders"));
        Files.writeString(root.resolve("tailorbird.yml"), "indent: 3\n");

        assertThat(ConfigFiles.find(List.of(module, parent, root))).contains(root.resolve("tailorbird.yml"));
    }

    @Test
    void finds_nothing_without_a_config(@TempDir Path root) throws Exception {
        Path module = Files.createDirectories(root.resolve("module"));

        assertThat(ConfigFiles.find(List.of(module, root))).isEmpty();
    }

    @Test
    void ignores_a_directory_named_like_the_config(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("tailorbird.yml"));

        assertThat(ConfigFiles.find(List.of(root))).isEmpty();
    }
}
