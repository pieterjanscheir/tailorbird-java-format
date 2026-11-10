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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Runs the formatter's command line entry point in a forked JVM.
 *
 * <p>The formatter uses javac internals, which need {@code --add-exports} flags on Java 16+. Forking means Maven users
 * don't have to put those flags in {@code .mvn/jvm.config}. All arguments go through a launcher argument file, so a
 * large number of source files never hits the OS command line length limit.
 */
final class FormatterProcess {

    private static final String FORMATTER_MAIN_CLASS = "io.github.pieterjanscheir.tailorbird.java.Main";

    private static final List<String> JVM_ARGS = List.of(
            "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED");

    private final Path javaExecutable;
    private final List<Path> classpath;
    private final Path workDirectory;

    FormatterProcess(Path javaExecutable, List<Path> classpath, Path workDirectory) {
        this.javaExecutable = javaExecutable;
        this.classpath = classpath;
        this.workDirectory = workDirectory;
    }

    Result run(List<String> formatterArgs, List<Path> files) throws IOException, InterruptedException {
        Files.createDirectories(workDirectory);
        Path argFile = workDirectory.resolve("formatter.args");
        Path stdout = workDirectory.resolve("formatter.out");
        Path stderr = workDirectory.resolve("formatter.err");
        Files.write(argFile, argFileLines(formatterArgs, files), UTF_8);

        Process process = new ProcessBuilder(javaExecutable.toString(), "@" + argFile)
                .redirectOutput(stdout.toFile())
                .redirectError(stderr.toFile())
                .start();
        int exitCode = process.waitFor();
        return new Result(exitCode, Files.readAllLines(stdout, UTF_8), Files.readString(stderr, UTF_8));
    }

    List<String> argFileLines(List<String> formatterArgs, List<Path> files) {
        List<String> lines = new ArrayList<>(JVM_ARGS);
        lines.add("-cp");
        lines.add(quote(classpath.stream()
                .map(path -> path.toAbsolutePath().toString())
                .collect(Collectors.joining(File.pathSeparator))));
        lines.add(FORMATTER_MAIN_CLASS);
        for (String arg : formatterArgs) {
            lines.add(quote(arg));
        }
        for (Path file : files) {
            lines.add(quote(file.toAbsolutePath().toString()));
        }
        return lines;
    }

    /** Quotes a value for a java launcher argument file, where backslash is the escape character. */
    static String quote(String value) {
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    static final class Result {
        private final int exitCode;
        private final List<String> stdout;
        private final String stderr;

        Result(int exitCode, List<String> stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }

        int exitCode() {
            return exitCode;
        }

        List<String> stdout() {
            return stdout;
        }

        String stderr() {
            return stderr;
        }
    }
}
