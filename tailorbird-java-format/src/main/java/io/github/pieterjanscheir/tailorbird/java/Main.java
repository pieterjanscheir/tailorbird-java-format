/*
 * Copyright 2015 Google Inc.
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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.io.ByteStreams;
import com.palantir.javaformat.java.FormatterDiagnostic;
import com.palantir.javaformat.java.FormatterException;
import com.palantir.javaformat.java.JavaFormatterOptions;
import com.palantir.javaformat.java.JavaFormatterOptions.Style;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.annotation.Nullable;

/** The main class for the Java formatter CLI. */
public final class Main {
    private static final int MAX_THREADS = 20;
    private static final String STDIN_FILENAME = "<stdin>";

    static String versionString() {
        return "palantir-java-format: Version " + Main.class.getPackage().getImplementationVersion();
    }

    private final PrintWriter outWriter;
    private final PrintWriter errWriter;
    private final InputStream inStream;

    /** Tailorbird: the rules for this run, set by {@link #format}; {@code null} for the Google and AOSP styles. */
    @Nullable
    private TailorbirdRules rules;

    public Main(PrintWriter outWriter, PrintWriter errWriter, InputStream inStream) {
        this.outWriter = outWriter;
        this.errWriter = errWriter;
        this.inStream = inStream;
    }

    /**
     * The main method for the formatter, with some number of file names to format. We process them in parallel, but we
     * must be careful; if multiple file names refer to the same file (which is hard to determine), we must serialize
     * their updates.
     *
     * @param args the command-line arguments
     */
    public static void main(String[] args) {
        int result;
        PrintWriter out = new PrintWriter(new OutputStreamWriter(System.out, UTF_8));
        PrintWriter err = new PrintWriter(new OutputStreamWriter(System.err, UTF_8));
        try {
            Main formatter = new Main(out, err, System.in);
            result = formatter.format(args);
        } catch (UsageException e) {
            err.print(e.getMessage());
            result = 0;
        } catch (TailorbirdConfig.TailorbirdConfigException e) {
            // Tailorbird: an invalid tailorbird.yml is the user's to fix, so print the message, not a stack trace.
            err.println("error: " + e.getMessage());
            result = 2;
        } finally {
            err.flush();
            out.flush();
        }
        System.exit(result);
    }

    /**
     * The main entry point for the formatter, with some number of file names to format. We process them in parallel,
     * but we must be careful; if multiple file names refer to the same file (which is hard to determine), we must
     * serialize their update.
     *
     * @param args the command-line arguments
     */
    public int format(String... args) throws UsageException {
        CommandLineOptions parameters = processArgs(args);
        if (parameters.version()) {
            errWriter.println(versionString());
            return 0;
        }
        if (parameters.help()) {
            throw new UsageException();
        }

        // TODO(someone): update this to always use Style.PALANTIR
        JavaFormatterOptions options = JavaFormatterOptions.builder()
                .style(
                        parameters.aosp()
                                ? Style.AOSP
                                : parameters.palantirStyle()
                                                || parameters.tailorbirdConfig().isPresent()
                                        ? Style.PALANTIR
                                        : Style.GOOGLE)
                .build();
        // Tailorbird: --palantir formats with Tailorbird's rules, from a tailorbird.yml on the classpath if any.
        // The upstream IntelliJ plugin and the native-image formatter run this class with --palantir, and their
        // arguments can't be changed from here.
        if (parameters.tailorbirdConfig().isPresent()) {
            this.rules = TailorbirdConfig.load(
                    Paths.get(parameters.tailorbirdConfig().get()));
        } else {
            this.rules = parameters.palantirStyle() ? TailorbirdConfig.load(Main.class.getClassLoader()) : null;
        }

        if (parameters.stdin()) {
            return formatStdin(parameters, options);
        }
        List<String> files;
        try {
            files = expandFolders(parameters.files(), errWriter);
        } catch (IOException | UncheckedIOException e) {
            errWriter.println("error: could not list the files in a folder: " + e.getMessage());
            return 2;
        }
        if (parameters.isSelection() && files.size() != 1) {
            throw new UsageException("partial formatting is only support for a single file");
        }
        if (files.isEmpty()) {
            return 0;
        }
        return formatFiles(parameters, options, files);
    }

    /**
     * Tailorbird: the file arguments, with each folder replaced by the {@code .java} files under it, in sorted order.
     * Hidden folders and folders named {@code build} or {@code target} are skipped, since they hold generated code.
     * A folder without Java files gets a warning on {@code err}. Without this, a folder was skipped as a non-Java file
     * and the run still passed.
     */
    static List<String> expandFolders(List<String> arguments, PrintWriter err) throws IOException {
        List<String> files = new ArrayList<>();
        for (String argument : arguments) {
            Path path = Paths.get(argument);
            if (!Files.isDirectory(path)) {
                files.add(argument);
                continue;
            }
            // Walk from "<folder>/." so that a symbolic link to a folder, given directly, is walked too. Links inside
            // the folder aren't followed, so a link loop can't hang the run.
            Path start = path.resolve(".");
            List<Path> found = new ArrayList<>();
            Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    return !dir.equals(start)
                                    && isSkippedFolder(dir.getFileName().toString())
                            ? FileVisitResult.SKIP_SUBTREE
                            : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile() && file.getFileName().toString().endsWith(".java")) {
                        found.add(path.resolve(start.relativize(file)));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            if (found.isEmpty()) {
                err.println("warning: no Java files found in " + argument);
            }
            Collections.sort(found);
            for (Path file : found) {
                files.add(file.toString());
            }
        }
        return files;
    }

    private static boolean isSkippedFolder(String name) {
        return name.startsWith(".") || name.equals("build") || name.equals("target");
    }

    @SuppressWarnings("for-rollout:RedundantControlFlow")
    private int formatFiles(CommandLineOptions parameters, JavaFormatterOptions options, List<String> files) {
        int numThreads = Math.min(MAX_THREADS, files.size());
        ExecutorService executorService = Executors.newFixedThreadPool(numThreads);

        Map<Path, String> inputs = new LinkedHashMap<>();
        Map<Path, Future<String>> results = new LinkedHashMap<>();
        boolean allOk = true;

        for (String fileName : files) {
            if (!fileName.endsWith(".java")) {
                errWriter.println("Skipping non-Java file: " + fileName);
                continue;
            }
            Path path = Paths.get(fileName);
            String input;
            try {
                input = new String(Files.readAllBytes(path), UTF_8);
                inputs.put(path, input);
                results.put(path, executorService.submit(new FormatFileCallable(parameters, input, options, rules)));
            } catch (IOException e) {
                errWriter.println(fileName + ": could not read file: " + e.getMessage());
                allOk = false;
            }
        }

        for (Map.Entry<Path, Future<String>> result : results.entrySet()) {
            Path path = result.getKey();
            String formatted;
            try {
                formatted = result.getValue().get();
            } catch (InterruptedException e) {
                errWriter.println(e.getMessage());
                allOk = false;
                continue;
            } catch (ExecutionException e) {
                if (e.getCause() instanceof FormatterException) {
                    for (FormatterDiagnostic diagnostic : ((FormatterException) e.getCause()).diagnostics()) {
                        errWriter.println(path + ":" + diagnostic.toString());
                    }
                } else {
                    // Tailorbird: print the cause's type and message, not its stack trace.
                    errWriter.println(path + ": error: "
                            + Optional.ofNullable(e.getCause())
                                    .map(Throwable::toString)
                                    .orElse("null"));
                }
                allOk = false;
                continue;
            }
            boolean changed = !formatted.equals(inputs.get(path));
            if (changed && parameters.setExitIfChanged()) {
                allOk = false;
            }
            if (parameters.inPlace()) {
                if (!changed) {
                    continue; // preserve original file
                }
                try {
                    Files.write(path, formatted.getBytes(UTF_8));
                } catch (IOException e) {
                    errWriter.println(path + ": could not write file: " + e.getMessage());
                    allOk = false;
                    continue;
                }
            } else if (parameters.dryRun()) {
                if (changed) {
                    outWriter.println(path);
                }
            } else {
                outWriter.write(formatted);
            }
        }
        return allOk ? 0 : 1;
    }

    private int formatStdin(CommandLineOptions parameters, JavaFormatterOptions options) {
        String input;
        try {
            input = new String(ByteStreams.toByteArray(inStream), UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String stdinFilename = parameters.assumeFilename().orElse(STDIN_FILENAME);
        boolean ok = true;
        try {
            String output = new FormatFileCallable(parameters, input, options, rules).call();
            boolean changed = !input.equals(output);
            if (changed && parameters.setExitIfChanged()) {
                ok = false;
            }
            if (parameters.dryRun()) {
                if (changed) {
                    outWriter.println(stdinFilename);
                }
            } else {
                outWriter.write(output);
            }
        } catch (FormatterException e) {
            for (FormatterDiagnostic diagnostic : e.diagnostics()) {
                errWriter.println(stdinFilename + ":" + diagnostic.toString());
            }
            ok = false;
            // TODO(cpovirk): Catch other types of exception (as we do in the formatFiles case).
        }
        return ok ? 0 : 1;
    }

    /** Parses and validates command-line flags. */
    public static CommandLineOptions processArgs(String... args) throws UsageException {
        CommandLineOptions parameters;
        try {
            parameters = CommandLineOptionsParser.parse(Arrays.asList(args));
        } catch (IllegalArgumentException e) {
            throw new UsageException(Objects.requireNonNullElse(e.getMessage(), "null"));
        } catch (Throwable t) {
            // Tailorbird: the message reaches the user through UsageException, without a stack trace.
            throw new UsageException(Objects.requireNonNullElse(t.getMessage(), "null"));
        }
        int filesToFormat = parameters.files().size();
        if (parameters.stdin()) {
            filesToFormat++;
        }

        if (parameters.inPlace() && parameters.files().isEmpty()) {
            throw new UsageException("in-place formatting was requested but no files were provided");
        }
        if (parameters.isSelection() && filesToFormat != 1) {
            throw new UsageException("partial formatting is only support for a single file");
        }
        if (parameters.offsets().size() != parameters.lengths().size()) {
            throw new UsageException("-offsets and -lengths flags must be provided in matching pairs");
        }
        if (filesToFormat <= 0 && !parameters.version() && !parameters.help()) {
            throw new UsageException("no files were provided");
        }
        if (parameters.stdin() && !parameters.files().isEmpty()) {
            throw new UsageException("cannot format from standard input and files simultaneously");
        }
        if (parameters.assumeFilename().isPresent() && !parameters.stdin()) {
            throw new UsageException("--assume-filename is only supported when formatting standard input");
        }
        if (parameters.dryRun() && parameters.inPlace()) {
            throw new UsageException("cannot use --dry-run and --in-place at the same time");
        }
        return parameters;
    }
}
