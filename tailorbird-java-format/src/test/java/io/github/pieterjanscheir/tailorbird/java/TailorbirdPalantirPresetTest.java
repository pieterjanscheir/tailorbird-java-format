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

package io.github.pieterjanscheir.tailorbird.java;

import static io.github.pieterjanscheir.tailorbird.java.FileBasedTests.assumeJavaVersionForTest;
import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.javaformat.java.FormatterException;
import com.palantir.javaformat.java.JavaFormatterOptions;
import com.palantir.javaformat.java.JavaFormatterOptions.Style;
import io.github.pieterjanscheir.tailorbird.jupiter.ParameterizedClass;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * Tailorbird: {@code preset: palantir} is exactly upstream palantir-java-format. Runs every upstream test case in
 * {@code testdata} (the ones {@link FormatterIntegrationTest} runs with the {@code PALANTIR} style) through the
 * Tailorbird rules of the preset, and expects upstream's output.
 */
@ExtendWith(ParameterizedClass.class)
@Execution(ExecutionMode.CONCURRENT)
public class TailorbirdPalantirPresetTest {

    private static final FileBasedTests tests = new FileBasedTests(FormatterIntegrationTest.class, "testdata");

    private static final TailorbirdRules PALANTIR =
            TailorbirdRules.builder(TailorbirdRules.Preset.PALANTIR).build();

    @ParameterizedClass.Parameters(name = "{0}")
    public static List<Object[]> data() throws IOException {
        return tests.paramsAsNameInputOutput();
    }

    private final String name;
    private final String input;
    private final String expected;

    public TailorbirdPalantirPresetTest(String name, String input, String expected) {
        this.name = name;
        this.input = input;
        this.expected = expected;
    }

    @TestTemplate
    public void formats_like_upstream_palantir() throws FormatterException {
        assumeJavaVersionForTest(name);
        Assumptions.assumeFalse(FileBasedTests.isRecreate(), "upstream's outputs are not recreated from here");
        Formatter formatter = new Formatter(
                JavaFormatterOptions.builder().style(Style.PALANTIR).build(), false, PALANTIR);

        String output = formatter.formatSource(input);
        output = StringWrapper.wrap(PALANTIR.lineLength(), output, formatter);

        assertThat(output)
                .describedAs("preset: palantir differs from upstream for " + name)
                .isEqualTo(expected);
    }
}
