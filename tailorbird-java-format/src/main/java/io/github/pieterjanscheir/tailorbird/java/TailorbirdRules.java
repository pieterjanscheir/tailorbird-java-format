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

import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import java.util.List;
import java.util.Objects;

/**
 * Tailorbird: the formatting rules a project can configure in {@code tailorbird.yml}. Every rule is independent, and
 * {@link #defaults()} is Tailorbird's own style. See {@link TailorbirdConfig} for the file format.
 *
 * <p>The rules are separate from {@link JavaFormatterOptions} on purpose: that class lives in the SPI jar, which the
 * upstream Gradle and IntelliJ plugins load themselves, so Tailorbird can't add anything to it.
 */
@Immutable
public final class TailorbirdRules {

    /** Import group that holds every static import. */
    public static final String STATIC_IMPORTS = "static";

    /** Import group that holds every import no other group matches. */
    public static final String OTHER_IMPORTS = "*";

    private static final TailorbirdRules DEFAULTS = builder().build();

    private final int lineLength;
    private final int indent;
    private final int continuationIndent;
    private final boolean spaceAfterControlKeyword;
    private final boolean arrayInitializerBraceSpaces;
    private final boolean closingParenOnOwnLine;
    private final boolean tryResourcesOnOwnLines;
    private final boolean bodyWithoutBracesOnOwnLine;
    private final boolean keepTypedBlankLines;
    private final boolean blankLinesAroundClassBody;
    private final boolean blankLinesBetweenFields;
    private final boolean blankLinesAroundFieldsWithJavadoc;
    private final boolean blankLinesAroundInterfaceMethods;
    private final ImmutableList<String> importGroups;
    private final boolean blankLineBetweenImportGroups;
    private final boolean javadocLayout;
    private final boolean javadocRemoveBlankLinesInDescription;
    private final boolean javadocBlankLineAfterDescription;
    private final boolean javadocBlankLineAfterParams;
    private final boolean javadocBlankLineAfterReturn;
    private final boolean javadocSingleSpaceAfterTagName;
    private final boolean javadocWrap;
    private final boolean formatterTags;
    private final String formatterOffTag;
    private final String formatterOnTag;

    private TailorbirdRules(Builder b) {
        this.lineLength = b.lineLength;
        this.indent = b.indent;
        this.continuationIndent = b.continuationIndent;
        this.spaceAfterControlKeyword = b.spaceAfterControlKeyword;
        this.arrayInitializerBraceSpaces = b.arrayInitializerBraceSpaces;
        this.closingParenOnOwnLine = b.closingParenOnOwnLine;
        this.tryResourcesOnOwnLines = b.tryResourcesOnOwnLines;
        this.bodyWithoutBracesOnOwnLine = b.bodyWithoutBracesOnOwnLine;
        this.keepTypedBlankLines = b.keepTypedBlankLines;
        this.blankLinesAroundClassBody = b.blankLinesAroundClassBody;
        this.blankLinesBetweenFields = b.blankLinesBetweenFields;
        this.blankLinesAroundFieldsWithJavadoc = b.blankLinesAroundFieldsWithJavadoc;
        this.blankLinesAroundInterfaceMethods = b.blankLinesAroundInterfaceMethods;
        this.importGroups = b.importGroups;
        this.blankLineBetweenImportGroups = b.blankLineBetweenImportGroups;
        this.javadocLayout = b.javadocLayout;
        this.javadocRemoveBlankLinesInDescription = b.javadocRemoveBlankLinesInDescription;
        this.javadocBlankLineAfterDescription = b.javadocBlankLineAfterDescription;
        this.javadocBlankLineAfterParams = b.javadocBlankLineAfterParams;
        this.javadocBlankLineAfterReturn = b.javadocBlankLineAfterReturn;
        this.javadocSingleSpaceAfterTagName = b.javadocSingleSpaceAfterTagName;
        this.javadocWrap = b.javadocWrap;
        this.formatterTags = b.formatterTags;
        this.formatterOffTag = b.formatterOffTag;
        this.formatterOnTag = b.formatterOnTag;
    }

    /** Tailorbird's own style: what you get without a {@code tailorbird.yml}. */
    public static TailorbirdRules defaults() {
        return DEFAULTS;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** A builder that starts from {@code preset} instead of {@link #defaults()}. */
    public static Builder builder(Preset preset) {
        Builder builder = new Builder();
        switch (preset) {
            case TAILORBIRD:
                return builder;
            case PALANTIR:
                return palantir(builder);
            case GOOGLE:
                return palantir(builder).lineLength(100).indent(2).continuationIndent(4);
        }
        throw new IllegalArgumentException("Unknown preset: " + preset);
    }

    /** The upstream palantir-java-format style: every rule set to what upstream does. */
    private static Builder palantir(Builder builder) {
        return builder.lineLength(120)
                .indent(4)
                .continuationIndent(8)
                .spaceAfterControlKeyword(true)
                .arrayInitializerBraceSpaces(false)
                .closingParenOnOwnLine(false)
                .tryResourcesOnOwnLines(false)
                .bodyWithoutBracesOnOwnLine(false)
                .keepTypedBlankLines(true)
                .blankLinesAroundClassBody(false)
                .blankLinesBetweenFields(false)
                .blankLinesAroundFieldsWithJavadoc(true)
                .blankLinesAroundInterfaceMethods(true)
                .importGroups(ImmutableList.of(STATIC_IMPORTS, OTHER_IMPORTS))
                .blankLineBetweenImportGroups(true)
                .javadocLayout(false);
    }

    /** A starting point for the rules, picked with {@code preset:} in {@code tailorbird.yml}. */
    public enum Preset {
        /** Tailorbird's own style, the default. */
        TAILORBIRD,
        /** Upstream palantir-java-format: 120 characters, 4-space indent, 8-space continuation indent. */
        PALANTIR,
        /** Upstream's Google style: like {@code palantir}, with 100 characters and 2-space indent. */
        GOOGLE
    }

    /** Maximum line length. */
    public int lineLength() {
        return lineLength;
    }

    /** Spaces per block indent. */
    public int indent() {
        return indent;
    }

    /** Spaces a wrapped line is indented by, relative to the line it continues. */
    public int continuationIndent() {
        return continuationIndent;
    }

    /** {@code if (x)} when true, {@code if(x)} when false. Also for, while, switch, try, catch and synchronized. */
    public boolean spaceAfterControlKeyword() {
        return spaceAfterControlKeyword;
    }

    /** {@code { 1, 2 }} when true, {@code {1, 2}} when false. */
    public boolean arrayInitializerBraceSpaces() {
        return arrayInitializerBraceSpaces;
    }

    /** {@code )} on its own line when parameters or arguments wrap one per line. */
    public boolean closingParenOnOwnLine() {
        return closingParenOnOwnLine;
    }

    /** A try with several resources puts each on its own line, between {@code (} and {@code )} on their own lines. */
    public boolean tryResourcesOnOwnLines() {
        return tryResourcesOnOwnLines;
    }

    /** The body of an if, for or while without braces always goes on its own line. */
    public boolean bodyWithoutBracesOnOwnLine() {
        return bodyWithoutBracesOnOwnLine;
    }

    /** Keep blank lines typed in code and between members; when false they are removed. */
    public boolean keepTypedBlankLines() {
        return keepTypedBlankLines;
    }

    /** One blank line after a class header and before the class's closing brace. */
    public boolean blankLinesAroundClassBody() {
        return blankLinesAroundClassBody;
    }

    /** A blank line around each field, like around methods. When false, fields are listed without blank lines. */
    public boolean blankLinesBetweenFields() {
        return blankLinesBetweenFields;
    }

    /** A blank line around each field that has JavaDoc, even when {@link #blankLinesBetweenFields()} is off. */
    public boolean blankLinesAroundFieldsWithJavadoc() {
        return blankLinesAroundFieldsWithJavadoc;
    }

    /** A blank line around each method of an interface or annotation type. */
    public boolean blankLinesAroundInterfaceMethods() {
        return blankLinesAroundInterfaceMethods;
    }

    /**
     * Import groups, in order. Each entry is a package prefix ({@code java}, {@code com.example}),
     * {@value #STATIC_IMPORTS} for static imports, or {@value #OTHER_IMPORTS} for everything else. An import goes in
     * the group with the longest matching prefix.
     */
    public ImmutableList<String> importGroups() {
        return importGroups;
    }

    /** A blank line between import groups. */
    public boolean blankLineBetweenImportGroups() {
        return blankLineBetweenImportGroups;
    }

    /**
     * Leave the code between a comment containing {@link #formatterOffTag()} and one containing
     * {@link #formatterOnTag()} as written (IntelliJ's formatter markers). Off by default.
     */
    public boolean formatterTags() {
        return formatterTags;
    }

    /** The text that marks the start of a region the formatter leaves alone, {@code @formatter:off} by default. */
    public String formatterOffTag() {
        return formatterOffTag;
    }

    /** The text that marks the end of such a region, {@code @formatter:on} by default. */
    public String formatterOnTag() {
        return formatterOnTag;
    }

    /** Apply the {@code javadoc} layout rules below; when false, their blank lines and spacing are left as written. */
    public boolean javadocLayout() {
        return javadocLayout;
    }

    public boolean javadocRemoveBlankLinesInDescription() {
        return javadocRemoveBlankLinesInDescription;
    }

    public boolean javadocBlankLineAfterDescription() {
        return javadocBlankLineAfterDescription;
    }

    public boolean javadocBlankLineAfterParams() {
        return javadocBlankLineAfterParams;
    }

    public boolean javadocBlankLineAfterReturn() {
        return javadocBlankLineAfterReturn;
    }

    public boolean javadocSingleSpaceAfterTagName() {
        return javadocSingleSpaceAfterTagName;
    }

    /**
     * Re-wrap JavaDoc text at {@link #lineLength()} with upstream's JavaDoc formatter, before the layout rules above.
     * {@code <pre>} blocks are never wrapped. Off by default, also when {@link #javadocLayout()} is off.
     */
    public boolean javadocWrap() {
        return javadocWrap;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TailorbirdRules)) {
            return false;
        }
        TailorbirdRules o = (TailorbirdRules) other;
        return lineLength == o.lineLength
                && indent == o.indent
                && continuationIndent == o.continuationIndent
                && spaceAfterControlKeyword == o.spaceAfterControlKeyword
                && arrayInitializerBraceSpaces == o.arrayInitializerBraceSpaces
                && closingParenOnOwnLine == o.closingParenOnOwnLine
                && tryResourcesOnOwnLines == o.tryResourcesOnOwnLines
                && bodyWithoutBracesOnOwnLine == o.bodyWithoutBracesOnOwnLine
                && keepTypedBlankLines == o.keepTypedBlankLines
                && blankLinesAroundClassBody == o.blankLinesAroundClassBody
                && blankLinesBetweenFields == o.blankLinesBetweenFields
                && blankLinesAroundFieldsWithJavadoc == o.blankLinesAroundFieldsWithJavadoc
                && blankLinesAroundInterfaceMethods == o.blankLinesAroundInterfaceMethods
                && importGroups.equals(o.importGroups)
                && blankLineBetweenImportGroups == o.blankLineBetweenImportGroups
                && javadocLayout == o.javadocLayout
                && javadocRemoveBlankLinesInDescription == o.javadocRemoveBlankLinesInDescription
                && javadocBlankLineAfterDescription == o.javadocBlankLineAfterDescription
                && javadocBlankLineAfterParams == o.javadocBlankLineAfterParams
                && javadocBlankLineAfterReturn == o.javadocBlankLineAfterReturn
                && javadocSingleSpaceAfterTagName == o.javadocSingleSpaceAfterTagName
                && javadocWrap == o.javadocWrap
                && formatterTags == o.formatterTags
                && formatterOffTag.equals(o.formatterOffTag)
                && formatterOnTag.equals(o.formatterOnTag);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                lineLength,
                indent,
                continuationIndent,
                spaceAfterControlKeyword,
                arrayInitializerBraceSpaces,
                closingParenOnOwnLine,
                tryResourcesOnOwnLines,
                bodyWithoutBracesOnOwnLine,
                keepTypedBlankLines,
                blankLinesAroundClassBody,
                blankLinesBetweenFields,
                blankLinesAroundFieldsWithJavadoc,
                blankLinesAroundInterfaceMethods,
                importGroups,
                blankLineBetweenImportGroups,
                javadocLayout,
                javadocRemoveBlankLinesInDescription,
                javadocBlankLineAfterDescription,
                javadocBlankLineAfterParams,
                javadocBlankLineAfterReturn,
                javadocSingleSpaceAfterTagName,
                javadocWrap,
                formatterTags,
                formatterOffTag,
                formatterOnTag);
    }

    @Override
    public String toString() {
        return "TailorbirdRules{lineLength=" + lineLength + ", indent=" + indent + ", continuationIndent="
                + continuationIndent + ", importGroups=" + importGroups + ", ...}";
    }

    /** Builder for {@link TailorbirdRules}; starts from {@link #defaults()}. */
    public static final class Builder {
        private int lineLength = 120;
        private int indent = 4;
        private int continuationIndent = 4;
        private boolean spaceAfterControlKeyword = false;
        private boolean arrayInitializerBraceSpaces = true;
        private boolean closingParenOnOwnLine = true;
        private boolean tryResourcesOnOwnLines = true;
        private boolean bodyWithoutBracesOnOwnLine = true;
        private boolean keepTypedBlankLines = false;
        private boolean blankLinesAroundClassBody = true;
        private boolean blankLinesBetweenFields = false;
        private boolean blankLinesAroundFieldsWithJavadoc = false;
        private boolean blankLinesAroundInterfaceMethods = false;
        private ImmutableList<String> importGroups =
                ImmutableList.of(STATIC_IMPORTS, "java", "javax", "org", "com", OTHER_IMPORTS);
        private boolean blankLineBetweenImportGroups = true;
        private boolean javadocLayout = true;
        private boolean javadocRemoveBlankLinesInDescription = true;
        private boolean javadocBlankLineAfterDescription = true;
        private boolean javadocBlankLineAfterParams = true;
        private boolean javadocBlankLineAfterReturn = true;
        private boolean javadocSingleSpaceAfterTagName = true;
        private boolean javadocWrap = false;
        private boolean formatterTags = false;
        private String formatterOffTag = "@formatter:off";
        private String formatterOnTag = "@formatter:on";

        private Builder() {}

        public Builder formatterTags(boolean value) {
            this.formatterTags = value;
            return this;
        }

        public Builder formatterOffTag(String value) {
            this.formatterOffTag = requireTag("formatterTags.off", value);
            return this;
        }

        public Builder formatterOnTag(String value) {
            this.formatterOnTag = requireTag("formatterTags.on", value);
            return this;
        }

        private static String requireTag(String name, String value) {
            String tag = value.trim();
            if (tag.isEmpty()) {
                throw new IllegalArgumentException(name + " must not be empty");
            }
            // Formatting adds a space after `//` and reflows JavaDoc at whitespace, so a tag with comment syntax or
            // whitespace could appear or break apart only after formatting.
            // Guava's whitespace, the definition the comment wrapping uses, which includes a non-breaking space.
            if (CharMatcher.whitespace().or(CharMatcher.anyOf("/*")).matchesAnyOf(tag)) {
                throw new IllegalArgumentException(
                        name + " must not contain spaces, / or *, like @formatter:off; got \"" + tag + "\"");
            }
            return tag;
        }

        public Builder lineLength(int value) {
            this.lineLength = requireAtLeast("lineLength", value, 40);
            return this;
        }

        public Builder indent(int value) {
            this.indent = requireAtLeast("indent", value, 1);
            return this;
        }

        public Builder continuationIndent(int value) {
            this.continuationIndent = requireAtLeast("continuationIndent", value, 1);
            return this;
        }

        public Builder spaceAfterControlKeyword(boolean value) {
            this.spaceAfterControlKeyword = value;
            return this;
        }

        public Builder arrayInitializerBraceSpaces(boolean value) {
            this.arrayInitializerBraceSpaces = value;
            return this;
        }

        public Builder closingParenOnOwnLine(boolean value) {
            this.closingParenOnOwnLine = value;
            return this;
        }

        public Builder tryResourcesOnOwnLines(boolean value) {
            this.tryResourcesOnOwnLines = value;
            return this;
        }

        public Builder bodyWithoutBracesOnOwnLine(boolean value) {
            this.bodyWithoutBracesOnOwnLine = value;
            return this;
        }

        public Builder keepTypedBlankLines(boolean value) {
            this.keepTypedBlankLines = value;
            return this;
        }

        public Builder blankLinesAroundClassBody(boolean value) {
            this.blankLinesAroundClassBody = value;
            return this;
        }

        public Builder blankLinesBetweenFields(boolean value) {
            this.blankLinesBetweenFields = value;
            return this;
        }

        public Builder blankLinesAroundFieldsWithJavadoc(boolean value) {
            this.blankLinesAroundFieldsWithJavadoc = value;
            return this;
        }

        public Builder blankLinesAroundInterfaceMethods(boolean value) {
            this.blankLinesAroundInterfaceMethods = value;
            return this;
        }

        public Builder importGroups(List<String> value) {
            if (value.isEmpty()) {
                throw new IllegalArgumentException("imports.groups needs at least one group");
            }
            for (String group : value) {
                if (group == null || group.isBlank() || !group.equals(group.strip())) {
                    throw new IllegalArgumentException("imports.groups has an empty or padded entry: '" + group + "'");
                }
            }
            if (value.stream().distinct().count() != value.size()) {
                throw new IllegalArgumentException("imports.groups lists a group twice: " + value);
            }
            this.importGroups = ImmutableList.copyOf(value);
            return this;
        }

        public Builder blankLineBetweenImportGroups(boolean value) {
            this.blankLineBetweenImportGroups = value;
            return this;
        }

        public Builder javadocLayout(boolean value) {
            this.javadocLayout = value;
            return this;
        }

        public Builder javadocRemoveBlankLinesInDescription(boolean value) {
            this.javadocRemoveBlankLinesInDescription = value;
            return this;
        }

        public Builder javadocBlankLineAfterDescription(boolean value) {
            this.javadocBlankLineAfterDescription = value;
            return this;
        }

        public Builder javadocBlankLineAfterParams(boolean value) {
            this.javadocBlankLineAfterParams = value;
            return this;
        }

        public Builder javadocBlankLineAfterReturn(boolean value) {
            this.javadocBlankLineAfterReturn = value;
            return this;
        }

        public Builder javadocSingleSpaceAfterTagName(boolean value) {
            this.javadocSingleSpaceAfterTagName = value;
            return this;
        }

        public Builder javadocWrap(boolean value) {
            this.javadocWrap = value;
            return this;
        }

        public TailorbirdRules build() {
            return new TailorbirdRules(this);
        }

        private static int requireAtLeast(String name, int value, int minimum) {
            if (value < minimum) {
                throw new IllegalArgumentException(name + " must be at least " + minimum + ", got " + value);
            }
            return value;
        }
    }
}
