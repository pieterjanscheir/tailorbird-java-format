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

package io.github.pieterjanscheir.tailorbird.gradle;

import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.junit.DisabledConfigurationCache;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import java.io.File;
import org.junit.jupiter.api.Test;

/**
 * Tailorbird: a project configures the rules with a {@code tailorbird.yml} that it adds to the {@code palantirJavaFormat}
 * dependencies, the way the documentation describes.
 */
@GradlePluginTests
@DisabledConfigurationCache
class TailorbirdConfigSpotlessTest {

    /** ./gradlew writeImplClasspath generates this file. */
    private static final String CLASSPATH_FILE = new File("build/impl.classpath").getAbsolutePath();

    private static final String UNFORMATTED = """
        package test;
        public class Test { void test(int x) {if (x > 0) { test(x); }} }
        """;

    @Test
    void spotless_formats_with_the_rules_in_tailorbird_yml(GradleInvoker gradle, RootProject project) {
        setUpProject(gradle, project);
        project.file("config/tailorbird.yml").overwrite("""
            indent: 2
            spaceAfterControlKeyword: true
            blankLines:
              aroundClassBody: false
            """);
        project.file("src/main/java/Main.java").overwrite(UNFORMATTED);

        gradle.withArgs("spotlessApply").buildsSuccessfully();

        project.file("src/main/java/Main.java").assertThat().hasContent("""
            package test;

            public class Test {
              void test(int x) {
                if (x > 0) {
                  test(x);
                }
              }
            }
            """);
    }

    @Test
    void editing_tailorbird_yml_changes_the_next_format(GradleInvoker gradle, RootProject project) {
        setUpProject(gradle, project);
        project.file("config/tailorbird.yml").overwrite("spaceAfterControlKeyword: true\n");
        project.file("src/main/java/Main.java").overwrite(UNFORMATTED);
        gradle.withArgs("spotlessApply").buildsSuccessfully();
        project.file("src/main/java/Main.java").assertThat().content().contains("if (x > 0)");

        project.file("config/tailorbird.yml").overwrite("spaceAfterControlKeyword: false\n");
        gradle.withArgs("spotlessApply").buildsSuccessfully();

        project.file("src/main/java/Main.java").assertThat().content().contains("if(x > 0)");
    }

    @Test
    void an_invalid_tailorbird_yml_fails_with_its_error(GradleInvoker gradle, RootProject project) {
        setUpProject(gradle, project);
        project.file("config/tailorbird.yml").overwrite("indnet: 2\n");
        project.file("src/main/java/Main.java").overwrite(UNFORMATTED);

        gradle.withArgs("spotlessApply")
                .buildsWithFailure()
                .assertThat()
                .output()
                .contains("unknown setting 'indnet'");
    }

    private static void setUpProject(GradleInvoker gradle, RootProject project) {
        project.settingsGradle().plugins().add("com.palantir.jdks.settings");
        project.buildGradle()
                .plugins()
                .add("java")
                .add("com.palantir.java-format")
                .add("com.palantir.baseline-java-versions")
                .add("com.palantir.jdks")
                .add("com.palantir.jdks.latest");
        project.buildGradle().append("""
            javaVersions {
                libraryTarget = 21
            }

            jdks {
                daemonTarget = 21
            }
            """);
        project.gradlePropertiesFile()
                .appendProperty(
                        "org.gradle.jvmargs",
                        "--add-exports jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED "
                                + "--add-exports jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED "
                                + "--add-exports jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED "
                                + "--add-exports jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED "
                                + "--add-exports jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED")
                .appendProperty("palantir.jdk.setup.enabled", "true");

        gradle.withArgs("wrapper").buildsSuccessfully();

        project.buildGradle().plugins().add("com.diffplug.spotless");
        project.buildGradle().append("""
            dependencies {
                palantirJavaFormat files(file("%s").text.split(':'))
                palantirJavaFormat files('config/tailorbird.yml')
            }
            """, CLASSPATH_FILE);
    }
}
