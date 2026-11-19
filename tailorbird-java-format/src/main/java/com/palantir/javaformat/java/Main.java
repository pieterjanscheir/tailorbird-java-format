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

package com.palantir.javaformat.java;

/**
 * Tailorbird: the upstream IntelliJ plugin starts the formatter by this class name (in its JDK bootstrap), so it
 * forwards to {@link io.github.pieterjanscheir.tailorbird.java.Main}. Remove it once Tailorbird's own plugins ship
 * (#33).
 */
public final class Main {
    private Main() {}

    public static void main(String[] args) {
        io.github.pieterjanscheir.tailorbird.java.Main.main(args);
    }
}
