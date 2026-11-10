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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/** Formats the project's Java sources in place. */
@Mojo(name = "apply", defaultPhase = LifecyclePhase.PROCESS_SOURCES, threadSafe = true)
public final class ApplyMojo extends AbstractTailorbirdMojo {

    @Override
    protected void execute(List<Path> files) throws MojoExecutionException {
        // The formatter only rewrites files whose content changes, so modification times tell us what it touched.
        Map<Path, FileTime> modifiedTimes = new HashMap<>();
        for (Path file : files) {
            modifiedTimes.put(file, lastModified(file));
        }

        runFormatter(List.of("--replace"), files);

        long changed = 0;
        for (Path file : files) {
            if (!lastModified(file).equals(modifiedTimes.get(file))) {
                getLog().debug("Formatted " + file);
                changed++;
            }
        }
        getLog().info("Tailorbird formatted " + changed + " of " + files.size() + " Java files");
    }

    private static FileTime lastModified(Path file) throws MojoExecutionException {
        try {
            return Files.getLastModifiedTime(file);
        } catch (IOException e) {
            throw new MojoExecutionException("Could not read modification time of " + file, e);
        }
    }
}
