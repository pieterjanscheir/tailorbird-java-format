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

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

abstract class AbstractTailorbirdMojo extends AbstractMojo {

    @SuppressWarnings("NullAway.Init")
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @SuppressWarnings("NullAway.Init")
    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    @SuppressWarnings("NullAway.Init")
    @Parameter(defaultValue = "${plugin}", readonly = true, required = true)
    private PluginDescriptor plugin;

    /** Skips formatting entirely. */
    @Parameter(property = "tailorbird.skip", defaultValue = "false")
    private boolean skip;

    /**
     * The {@code tailorbird.yml} with the formatting rules. Defaults to the first {@code tailorbird.yml} found in the
     * module's directory, the directories of its parent modules, and the root directory of the build. Without one,
     * Tailorbird's default style is used.
     */
    @Nullable
    @Parameter(property = "tailorbird.config")
    private File configFile;

    /**
     * Directories to format. Defaults to the project's main and test source roots, leaving out roots inside the build
     * directory (generated sources).
     */
    @Parameter
    private List<File> sourceDirectories = new ArrayList<>();

    /** Glob patterns, relative to the project base directory, of files to leave alone. Example: {@code **}{@code /gen/**}. */
    @Parameter
    private List<String> excludes = new ArrayList<>();

    /** The encoding of the sources. The formatter reads and writes UTF-8 only. */
    @Nullable
    @Parameter(defaultValue = "${project.build.sourceEncoding}", readonly = true)
    private String sourceEncoding;

    /** The java executable for the forked formatter JVM. Defaults to the JVM that runs Maven. */
    @SuppressWarnings("NullAway.Init")
    @Parameter(property = "tailorbird.java", defaultValue = "${java.home}/bin/java")
    private File javaExecutable;

    @Override
    public final void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Skipping Tailorbird");
            return;
        }
        requireUtf8Sources();
        List<Path> files = javaFiles();
        if (files.isEmpty()) {
            getLog().debug("No Java files to format");
            return;
        }
        execute(files);
    }

    protected abstract void execute(List<Path> files) throws MojoExecutionException, MojoFailureException;

    protected final FormatterProcess.Result runFormatter(List<String> args, List<Path> files)
            throws MojoExecutionException {
        FormatterProcess process = new FormatterProcess(
                javaExecutable.toPath(),
                formatterClasspath(),
                Paths.get(project.getBuild().getDirectory(), "tailorbird"));
        List<String> allArgs = new ArrayList<>(configArgs());
        allArgs.addAll(args);
        FormatterProcess.Result result;
        try {
            result = process.run(allArgs, files);
        } catch (IOException e) {
            throw new MojoExecutionException("Could not run the Tailorbird formatter", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MojoExecutionException("Interrupted while running the Tailorbird formatter", e);
        }
        if (result.exitCode() != 0) {
            throw new MojoExecutionException("Tailorbird failed with exit code " + result.exitCode() + ":\n"
                    + result.stderr().strip());
        }
        if (!result.stderr().isBlank()) {
            getLog().warn(result.stderr().strip());
        }
        return result;
    }

    private List<String> configArgs() throws MojoExecutionException {
        Optional<Path> config = configFile();
        if (config.isEmpty()) {
            // --palantir formats with Tailorbird's default style.
            return List.of("--palantir");
        }
        getLog().debug("Using " + config.get());
        return List.of("--config", config.get().toString());
    }

    private Optional<Path> configFile() throws MojoExecutionException {
        if (configFile != null) {
            if (!configFile.isFile()) {
                throw new MojoExecutionException("Tailorbird config file " + configFile + " does not exist");
            }
            return Optional.of(configFile.toPath().toAbsolutePath());
        }
        return ConfigFiles.find(configSearchDirectories());
    }

    /**
     * The module's directory, then those of its parent modules, then the root of the build. Walking up the parents
     * finds the root's {@code tailorbird.yml} also when a single module is built from its own directory, where Maven
     * takes that directory as the root unless the build has a {@code .mvn} directory.
     */
    private List<Path> configSearchDirectories() {
        List<Path> directories = new ArrayList<>();
        for (MavenProject current = project; current != null; current = current.getParent()) {
            // A parent resolved from a repository instead of from disk has no directory.
            File basedir = current.getBasedir();
            if (basedir != null) {
                directories.add(basedir.toPath());
            }
        }
        File rootDirectory = session.getRequest().getMultiModuleProjectDirectory();
        if (rootDirectory != null) {
            directories.add(rootDirectory.toPath());
        }
        return directories;
    }

    private void requireUtf8Sources() throws MojoExecutionException {
        // Without project.build.sourceEncoding, Maven's compiler uses the platform encoding.
        String encoding = sourceEncoding != null
                ? sourceEncoding
                : Charset.defaultCharset().name();
        if (!JavaSources.isUtf8(encoding)) {
            throw new MojoExecutionException(
                    "Tailorbird only formats UTF-8 sources, but this module's source encoding is "
                            + encoding
                            + ". Formatting would replace non-ASCII characters. Convert the sources to UTF-8 and set"
                            + " project.build.sourceEncoding to UTF-8, or skip Tailorbird for this module.");
        }
    }

    private List<Path> formatterClasspath() {
        return plugin.getArtifacts().stream()
                .map(Artifact::getFile)
                .map(File::toPath)
                .collect(Collectors.toList());
    }

    private List<Path> javaFiles() throws MojoExecutionException {
        List<Path> directories = sourceDirectories.isEmpty()
                ? JavaSources.defaultDirectories(
                        project.getCompileSourceRoots(),
                        project.getTestCompileSourceRoots(),
                        Paths.get(project.getBuild().getDirectory()))
                : sourceDirectories.stream()
                        .map(file -> file.toPath().toAbsolutePath().normalize())
                        .collect(Collectors.toList());
        try {
            return JavaSources.find(project.getBasedir().toPath(), directories, excludes);
        } catch (IOException e) {
            throw new MojoExecutionException("Could not list the Java files to format", e);
        }
    }
}
