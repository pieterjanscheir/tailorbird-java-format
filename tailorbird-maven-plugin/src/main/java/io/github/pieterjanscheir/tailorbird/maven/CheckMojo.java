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

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/** Fails the build when any of the project's Java sources is not formatted. */
@Mojo(name = "check", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public final class CheckMojo extends AbstractTailorbirdMojo {

    @Override
    protected void execute(List<Path> files) throws MojoExecutionException, MojoFailureException {
        List<String> unformatted = runFormatter(List.of("--dry-run"), files).stdout().stream()
                .filter(line -> !line.isBlank())
                .collect(Collectors.toList());
        if (!unformatted.isEmpty()) {
            throw new MojoFailureException(unformatted.size() + " of " + files.size()
                    + " Java files are not formatted. Run 'mvn tailorbird:apply' to fix them:\n  "
                    + String.join("\n  ", unformatted));
        }
        getLog().info("Tailorbird checked " + files.size() + " Java files, all formatted");
    }
}
