#!/usr/bin/env perl
# Rewrites one file from upstream's names to Tailorbird's. Called by tailorbird-rename.sh, which passes:
#   SPI_TYPES  the SPI type names, "FormatterService|FormatterException|..."
#   MODULES    the module name suffixes, "|-spi|-jdk-bootstrap|..." (the empty one is tailorbird-java-format)
use strict;
use warnings;
no warnings 'uninitialized';

local $/;
my $src = <>;
my $original = $src;
my $spi = qr/(?:$ENV{SPI_TYPES})\b/;
my $module = qr/(?:$ENV{MODULES})/;

# Links to the upstream repository keep upstream's paths: set them aside while rewriting.
my @urls;
$src =~ s{https://github\.com/palantir/[^\s)"'<>\]]*}{push @urls, $&; "\x00URL$#urls\x00"}ge;

# Packages, in Java (dots) and in paths (slashes), except SPI types and Maven coordinates.
$src =~ s{com([./])palantir\1javaformat(?!\1java\1$spi)(?!:)}{io$1github$1pieterjanscheir$1tailorbird}g;

# Gradle project paths, like project(':palantir-java-format-spi') and ./gradlew :palantir-java-format:test.
$src =~ s{(?<=['\s]):(gradle-)?palantir-java-format($module)(?=[':])}{:$1tailorbird-java-format$2}g;

# Module folders in paths, like palantir-java-format-native/build.gradle or ../palantir-java-format/src.
$src =~ s{(?<![\w.-])(gradle-)?palantir-java-format($module)/}{$1tailorbird-java-format$2/}g;

$src =~ s{\x00URL(\d+)\x00}{$urls[$1]}g;

# Classes in io.github.pieterjanscheir.tailorbird.java used the SPI types from the same package before the move.
if ($src =~ /^package io\.github\.pieterjanscheir\.tailorbird\.java;/m) {
    (my $code = $src) =~ s{"""(?s:.*?)"""|"(?:\\.|[^"\\\n])*"|'(?:\\.|[^'\\\n])*'|/\*(?s:.*?)\*/|//[^\n]*}{}g;
    $code =~ s/^\s*(?:package|import)\b[^\n]*//mg;
    my @add;
    for my $type (split /\|/, $ENV{SPI_TYPES}) {
        next unless $code =~ /\b$type\b/;
        next if $src =~ /^import\s+com\.palantir\.javaformat\.java\.$type;/m;
        push @add, "import com.palantir.javaformat.java.$type;";
    }
    $src = sort_imports($src, @add) if @add;
}

# Renamed imports sort differently (io.github... comes after com.*), so re-sort what the rename touched.
$src = sort_imports($src) if $ENV{FILE} =~ /\.java$/ && $src ne $original;

print $src;

# Adds imports and sorts the import section the way the formatter does: static imports, a blank line, the rest,
# each sorted by name.
sub sort_imports {
    my ($src, @add) = @_;
    my @lines = split /\n/, $src, -1;
    my @import_lines = grep { $lines[$_] =~ /^import\s/ } 0 .. $#lines;
    if (!@import_lines) {
        return $src unless @add;
        $src =~ s/^(package [^;]+;\n)/$1\n@{[join "\n", @add]}\n/m;
        return $src;
    }
    my ($first, $last) = ($import_lines[0], $import_lines[-1]);
    my @imports = (grep({ /^import\s/ } @lines[$first .. $last]), @add);
    my %seen;
    @imports = grep { !$seen{$_}++ } @imports;
    my $name = sub { (my $n = shift) =~ s/^import\s+(?:static\s+)?|;\s*$//g; $n };
    my @static = sort { $name->($a) cmp $name->($b) } grep { /^import static / } @imports;
    my @normal = sort { $name->($a) cmp $name->($b) } grep { !/^import static / } @imports;
    my @section = (@static, (@static && @normal ? ('') : ()), @normal);
    splice @lines, $first, $last - $first + 1, @section;
    return join "\n", @lines;
}
