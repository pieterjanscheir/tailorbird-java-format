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

import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;
import com.palantir.javaformat.java.FormatterException;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Options;
import io.github.pieterjanscheir.tailorbird.Input;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.annotation.Nullable;

/**
 * Tailorbird: finds the regions the formatter leaves as written, between a comment containing
 * {@link TailorbirdRules#formatterOffTag()} and one containing {@link TailorbirdRules#formatterOnTag()} (issue #22). A
 * region runs from the start of the off comment to the end of the on comment; an off comment without an on comment
 * runs to the end of the file. An off tag inside a region is ignored, as in IntelliJ. Only comments count, so the tag
 * inside a string literal does nothing.
 */
final class FormatterTags {
    private FormatterTags() {}

    /** The regions in {@code input}; empty when {@code rules} is null or doesn't enable formatter tags. */
    static RangeSet<Integer> offRegions(JavaInput input, @Nullable TailorbirdRules rules) {
        RangeSet<Integer> regions = TreeRangeSet.create();
        if (rules == null || !rules.formatterTags()) {
            return regions;
        }
        // A comment can be reachable from two tokens, so collect them by position first.
        Map<Integer, Input.Tok> comments = new TreeMap<>();
        for (Input.Token token : input.getTokens()) {
            for (Input.Tok tok : token.getToksBefore()) {
                if (tok.isComment()) {
                    comments.put(tok.getPosition(), tok);
                }
            }
            for (Input.Tok tok : token.getToksAfter()) {
                if (tok.isComment()) {
                    comments.put(tok.getPosition(), tok);
                }
            }
        }
        Integer start = null;
        for (Input.Tok comment : comments.values()) {
            // Read the markers in order, so a comment can close the region it opens.
            String text = comment.getOriginalText();
            int from = 0;
            while (true) {
                String marker = start == null ? rules.formatterOffTag() : rules.formatterOnTag();
                int at = text.indexOf(marker, from);
                if (at < 0) {
                    break;
                }
                if (start == null) {
                    start = comment.getPosition();
                } else {
                    regions.add(Range.closedOpen(start, comment.getPosition() + comment.length()));
                    start = null;
                }
                from = at + marker.length();
            }
        }
        if (start != null) {
            regions.add(Range.closedOpen(start, input.getText().length()));
        }
        return regions;
    }

    /** The regions in {@code text}, or none when it can't be read as Java (formatting reports that error itself). */
    static RangeSet<Integer> offRegions(String text, @Nullable TailorbirdRules rules) {
        if (rules == null || !rules.formatterTags()) {
            return TreeRangeSet.create();
        }
        try {
            return offRegions(new JavaInput(text), rules);
        } catch (FormatterException e) {
            return TreeRangeSet.create();
        }
    }

    /**
     * {@code output} (the formatted {@code input}) with each region replaced by the region's original text from
     * {@code input}, or {@code null} when {@code output} doesn't have the same number of regions.
     */
    @Nullable
    static String restore(String input, RangeSet<Integer> inputRegions, String output, TailorbirdRules rules) {
        List<Range<Integer>> before = new ArrayList<>(inputRegions.asRanges());
        List<Range<Integer>> after = new ArrayList<>(offRegions(output, rules).asRanges());
        if (before.size() != after.size()) {
            return null;
        }
        // Each region starts with its off comment, which formatting may only re-indent or wrap after the first line,
        // so a different first line means the regions don't correspond.
        for (int i = 0; i < before.size(); i++) {
            if (!firstLine(input, before.get(i)).equals(firstLine(output, after.get(i)))) {
                return null;
            }
        }
        StringBuilder result = new StringBuilder();
        int last = 0;
        for (int i = 0; i < before.size(); i++) {
            result.append(output, last, after.get(i).lowerEndpoint());
            result.append(input, before.get(i).lowerEndpoint(), before.get(i).upperEndpoint());
            last = after.get(i).upperEndpoint();
        }
        return result.append(output.substring(last)).toString();
    }

    /**
     * Whether {@code a} and {@code b} parse to the same program, comments and layout aside. The last check before a
     * file with regions is changed, like the one {@link StringWrapper} makes after reflowing strings.
     */
    static boolean sameSyntax(String a, String b) {
        try {
            return parse(a).toString().equals(parse(b).toString());
        } catch (FormatterException e) {
            return false;
        }
    }

    private static JCTree.JCCompilationUnit parse(String source) throws FormatterException {
        Context context = new Context();
        Options.instance(context).put("allowStringFolding", "true");
        return Formatter.parseJcCompilationUnit(context, source);
    }

    private static String firstLine(String text, Range<Integer> region) {
        String regionText = text.substring(region.lowerEndpoint(), region.upperEndpoint());
        int end = regionText.indexOf('\n');
        return (end < 0 ? regionText : regionText.substring(0, end)).trim();
    }

    /**
     * Whether replacing {@code range} would change text inside one of {@code regions}. An empty range is an insertion,
     * which only changes a region when it falls strictly inside it.
     */
    static boolean touches(RangeSet<Integer> regions, Range<Integer> range) {
        if (!range.isEmpty()) {
            return !regions.subRangeSet(range).isEmpty();
        }
        int position = range.lowerEndpoint();
        Range<Integer> region = regions.rangeContaining(position);
        return region != null && position > region.lowerEndpoint();
    }

    /** Whether an {@code import} declaration starts inside one of the regions, so imports must be left alone. */
    static boolean coversImports(String text, @Nullable TailorbirdRules rules) {
        if (rules == null || !rules.formatterTags()) {
            return false;
        }
        try {
            JavaInput input = new JavaInput(text);
            RangeSet<Integer> regions = offRegions(input, rules);
            if (regions.isEmpty()) {
                return false;
            }
            for (Input.Token token : input.getTokens()) {
                if (token.getTok().getText().equals("import")
                        && regions.contains(token.getTok().getPosition())) {
                    return true;
                }
            }
            return false;
        } catch (FormatterException e) {
            return false;
        }
    }
}
