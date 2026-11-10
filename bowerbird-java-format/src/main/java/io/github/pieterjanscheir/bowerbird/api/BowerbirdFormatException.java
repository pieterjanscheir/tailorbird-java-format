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

package io.github.pieterjanscheir.bowerbird.api;

import java.util.List;
import java.util.stream.Collectors;

/** Thrown when a source file can't be formatted, usually because it doesn't parse. */
public final class BowerbirdFormatException extends Exception {
    private final List<Diagnostic> diagnostics;

    BowerbirdFormatException(List<Diagnostic> diagnostics, Throwable cause) {
        super(diagnostics.stream().map(Diagnostic::toString).collect(Collectors.joining("\n")), cause);
        this.diagnostics = List.copyOf(diagnostics);
    }

    /** What went wrong, and where; never empty. */
    public List<Diagnostic> diagnostics() {
        return diagnostics;
    }

    /** One problem in the source file. */
    public static final class Diagnostic {
        private final int line;
        private final int column;
        private final String message;

        Diagnostic(int line, int column, String message) {
            this.line = line;
            this.column = column;
            this.message = message;
        }

        /** The 1-based line, or -1 when the problem has no position. */
        public int line() {
            return line;
        }

        /** The 1-based column, or -1 when the problem has no position. */
        public int column() {
            return column;
        }

        /** What went wrong. */
        public String message() {
            return message;
        }

        /** {@code line:column: error: message}, as the command line prints it. */
        @Override
        public String toString() {
            String position = (line >= 0 ? line + ":" : "") + (column >= 0 ? column + ":" : "");
            return (position.isEmpty() ? "" : position + " ") + "error: " + message;
        }
    }
}
