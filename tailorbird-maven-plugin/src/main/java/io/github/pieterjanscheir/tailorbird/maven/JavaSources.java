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

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Selects the Java files a module's sources consist of. */
final class JavaSources {

    private JavaSources() {}

    /**
     * The module's main and test source roots, leaving out roots inside the build directory: those hold generated
     * sources.
     */
    static List<Path> defaultDirectories(List<String> compileRoots, List<String> testRoots, Path buildDirectory) {
        Path build = buildDirectory.toAbsolutePath().normalize();
        return Stream.concat(compileRoots.stream(), testRoots.stream())
                .map(root -> Paths.get(root).toAbsolutePath().normalize())
                .filter(root -> !root.startsWith(build))
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * The {@code .java} files in the given directories, without those matching one of the exclude globs. The globs are
     * matched against the path relative to {@code baseDir}.
     */
    static List<Path> find(Path baseDir, List<Path> directories, List<String> excludes) throws IOException {
        List<PathMatcher> excludeMatchers = excludes.stream()
                .map(pattern -> FileSystems.getDefault().getPathMatcher("glob:" + pattern))
                .collect(Collectors.toList());
        Set<Path> files = new LinkedHashSet<>();
        for (Path directory : directories) {
            if (!Files.isDirectory(directory)) {
                continue;
            }
            try (Stream<Path> stream = Files.walk(directory)) {
                stream.filter(path -> path.toString().endsWith(".java"))
                        .filter(Files::isRegularFile)
                        .map(path -> path.toAbsolutePath().normalize())
                        .filter(path -> excludeMatchers.stream()
                                .noneMatch(matcher -> matcher.matches(baseDir.relativize(path))))
                        .sorted()
                        .forEach(files::add);
            }
        }
        return new ArrayList<>(files);
    }

    /**
     * Whether the formatter can safely read and write sources in this encoding. It always uses UTF-8, so files in
     * another encoding would have their non-ASCII characters replaced when it rewrites them.
     */
    static boolean isUtf8(String encoding) {
        try {
            return Charset.forName(encoding).equals(StandardCharsets.UTF_8);
        } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
            return false;
        }
    }
}
