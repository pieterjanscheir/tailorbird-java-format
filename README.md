# Tailorbird

[![CI](https://github.com/pieterjanscheir/tailorbird-java-format/actions/workflows/ci.yml/badge.svg)](https://github.com/pieterjanscheir/tailorbird-java-format/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/pieterjanscheir/tailorbird-java-format)](https://github.com/pieterjanscheir/tailorbird-java-format/releases)
[![Docs](https://img.shields.io/badge/docs-GitHub%20Pages-blue)](https://pieterjanscheir.github.io/tailorbird-java-format/)

_A Java formatter that stitches your code together the way a tailorbird stitches its nest: carefully, and always the same way._

Tailorbird is a modified version of [palantir-java-format](https://github.com/palantir/palantir-java-format)
(itself based on [google-java-format](https://github.com/google/google-java-format)). Unlike most formatters it
isn't one fixed style: every rule is a setting in a `tailorbird.yml`, so a team can choose its own layout. Without
one you get Tailorbird's default style, whose output matches IntelliJ IDEA's formatter for the target code style.
It is available under the same [Apache 2.0 License](./LICENSE). See [NOTICE](./NOTICE) for attribution.

**Documentation: <https://pieterjanscheir.github.io/tailorbird-java-format/>** ([中文](https://pieterjanscheir.github.io/tailorbird-java-format/zh/)).
It covers setup, every setting, the default style with before/after examples, and the contributor workflow.
The pages are built from [docs/index.html](./docs/index.html) and [docs/zh/index.html](./docs/zh/index.html).
They share their styles and scripts (search, table of contents, code highlighting) in [docs/assets](./docs/assets/); there is no build step.
The upstream documentation is kept in [docs/upstream-README.md](./docs/upstream-README.md).

## Configuring the rules

Create a `tailorbird.yml` and add the file to the `palantirJavaFormat` dependencies
(see [Installing](#installing)). List only the settings you want to change:

```yaml
# config/tailorbird.yml
preset: palantir          # start from upstream palantir-java-format (or: tailorbird, the default; google)
indent: 2
continuationIndent: 4
```

That example is Palantir's style with 2-space indentation, which upstream [can't do](https://github.com/palantir/palantir-java-format/issues/951).
`preset: palantir` is tested against every upstream test case. Gradle, Spotless and IntelliJ all read the same file,
and the command line takes `--config tailorbird.yml`. An unknown setting or a wrong type stops formatting with a
message that names it. See [all settings](https://pieterjanscheir.github.io/tailorbird-java-format/#settings).

## The default style

Without a `tailorbird.yml`, Tailorbird changes these things compared to upstream; each is a setting:

- No space between a control keyword and `(`: `if(`, `for(`, `while(`, `switch(`, `try(`, `catch(`, `synchronized(`.
- Import groups: static, `java`, `javax`, `org`, `com`, everything else, with a blank line between groups.
- A continuation indent of 4 spaces instead of 8, and `)` on its own line when arguments wrap one per line.
- Array initializers with spaces inside the braces: `{ 1, 2, 3 }`.
- Blank lines in code removed; fixed blank-line rules for class bodies, fields and methods.
- JavaDoc tag blocks separated by blank lines, and JavaDoc never re-wrapped.

See [Default style](https://pieterjanscheir.github.io/tailorbird-java-format/#rules) for examples.

## Installing

| Artifact | Coordinates |
|---|---|
| Formatter | `io.github.pieterjanscheir.tailorbird:tailorbird-java-format` |
| SPI | `io.github.pieterjanscheir.tailorbird:tailorbird-java-format-spi` |
| JDK bootstrap | `io.github.pieterjanscheir.tailorbird:tailorbird-java-format-jdk-bootstrap` |

Releases are published to GitHub Packages, which needs a personal access token with the `read:packages`
scope, even for public packages. The jars are also attached to each [GitHub Release](https://github.com/pieterjanscheir/tailorbird-java-format/releases).

Apply the upstream Gradle plugin and point it at Tailorbird instead of the default formatter:

```groovy
plugins {
    id 'com.palantir.java-format' version '<upstream version Tailorbird is based on>'
}

repositories {
    mavenCentral()
    maven {
        url = 'https://maven.pkg.github.com/pieterjanscheir/tailorbird-java-format'
        credentials {
            username = providers.gradleProperty('gpr.user').orElse(providers.environmentVariable('GITHUB_ACTOR')).get()
            password = providers.gradleProperty('gpr.key').orElse(providers.environmentVariable('GITHUB_TOKEN')).get()
        }
    }
}

dependencies {
    palantirJavaFormat 'io.github.pieterjanscheir.tailorbird:tailorbird-java-format:<tailorbird version>'
    palantirJavaFormat files('config/tailorbird.yml') // optional: your settings
}
```

Do not enable the native formatter (`palantir.native.formatter`): it downloads Palantir's precompiled
binary, which does not contain Tailorbird's rules and can't read `tailorbird.yml`. For IntelliJ, install the upstream
[palantir-java-format plugin](https://plugins.jetbrains.com/plugin/13180-palantir-java-format); it picks up the
formatter version the Gradle plugin resolves. See [IntelliJ](https://pieterjanscheir.github.io/tailorbird-java-format/#intellij).

Tailorbird's Java packages are `io.github.pieterjanscheir.tailorbird`, except the SPI (`com.palantir.javaformat.java.FormatterService`
and its types) and a forwarding `com.palantir.javaformat.java.Main`. The upstream Gradle and IntelliJ plugins load
Tailorbird through those names.

## Building

Requires JDK 21.

```bash
./gradlew :tailorbird-java-format:test
./gradlew :tailorbird-java-format-spi:publishToMavenLocal :tailorbird-java-format-jdk-bootstrap:publishToMavenLocal :tailorbird-java-format:publishToMavenLocal
```

CI ([ci.yml](./.github/workflows/ci.yml)) runs `./gradlew build` on every pull request and push to `main`.
It fails when the build changes tracked files.

## Adding a formatting rule

1. Add a `YourRule.input` and `YourRule.output` pair to
   `tailorbird-java-format/src/test/resources/io/github/pieterjanscheir/tailorbird/java/tailorbird-testdata/`.
   `TailorbirdIntegrationTest` picks them up.
2. Add the setting to `TailorbirdRules` (with its default, and its value in the `palantir` preset) and to the
   table in `TailorbirdConfig`.
3. Change the formatter, usually in `JavaInputAstVisitor.java`, behind `rule(TailorbirdRules::yourSetting)`, so
   upstream styles and their tests are unaffected. `TailorbirdPalantirPresetTest` checks that `preset: palantir`
   still matches upstream.
4. To test a non-default value, add a `YourRule.yml` next to the input and output files.
5. Run the tests. `./gradlew test -Drecreate=true` regenerates expected outputs, so review that diff carefully.
6. Check the output against IntelliJ's formatter with the target code style: IntelliJ should leave it unchanged.

Keep one rule per commit, so upstream merge conflicts are easy to trace. When a rule changes, update
[docs/index.html](./docs/index.html) and [docs/zh/index.html](./docs/zh/index.html) too.

## Staying up to date with upstream

Every Monday, [upstream-sync.yml](./.github/workflows/upstream-sync.yml) merges the latest palantir-java-format
release into a branch and opens a pull request, or opens an issue when the merge conflicts. Run it from the
Actions tab to merge a specific upstream tag or branch.

Don't use a plain `git merge`. Tailorbird renamed upstream's packages and modules, so every renamed line would
conflict with upstream edits next to it. [upstream-merge.sh](./.github/scripts/upstream-merge.sh) first gives
upstream's code Tailorbird's names with [tailorbird-rename.sh](./.github/scripts/tailorbird-rename.sh), then merges,
so only real conflicts are left. The upstream commit is still the merge's second parent. To merge by hand:

```bash
git fetch upstream --tags
.github/scripts/upstream-merge.sh upstream/develop
./gradlew format # renamed lines can need re-wrapping; commit what it changes
```

### Resolving upstream conflicts

- `README.md` conflicts on every upstream change to it: keep this version and apply their changes to
  `docs/upstream-README.md` if you care about them.
- In the formatter, keep Tailorbird's `rule(...)` checks and apply upstream's change around them. Then run
  `./gradlew :tailorbird-java-format:test`: `TailorbirdPalantirPresetTest` fails when upstream's formatting changed
  and `preset: palantir` no longer matches it.
- When the conflicts are resolved, run `git add -A && git commit --no-edit`, then `./gradlew format`, and commit
  what it changes.
- The SPI (`com.palantir.javaformat.java.FormatterService` and its types) and the forwarding
  `com.palantir.javaformat.java.Main` keep upstream's package. Everything else upstream adds is moved to
  `io.github.pieterjanscheir.tailorbird` and the `tailorbird-java-format*` modules. The sync fails if a file is left
  under the old names.

## Releasing

Push a version tag. [release.yml](./.github/workflows/release.yml) runs the tests, publishes the three
artifacts to GitHub Packages and creates a GitHub Release with the jars and generated release notes.

```bash
git tag 1.0.0
git push origin 1.0.0
```
