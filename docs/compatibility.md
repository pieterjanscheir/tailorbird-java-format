# Compatibility

Which Java, Maven and Gradle versions Tailorbird works with. "Tested" means a Tailorbird build was run against that
version; anything else is marked as such.

Last checked: 2026-11-05, on top of Tailorbird `ad789c1`.

## Java

| Component | Minimum Java | Why |
|---|---|---|
| `tailorbird-java-format`, `tailorbird-java-format-spi` (formatter jars) | 11 | Compiled to Java 11 bytecode |
| `tailorbird-maven-plugin` | 17 | Compiled to Java 17 bytecode, so Maven must run on Java 17+ |
| Building this repository | 21 | Gradle daemon runs on 21; the build provisions JDK 17, 21 and 23 itself (`gradle/jdks`) |

### Java version of the code being formatted

The formatter parses sources with the `javac` of the JVM it runs on. That JVM must therefore support the language
features in your code: formatting Java 21 syntax (record patterns, pattern matching in `switch`, ...) needs Java 21+.

| Formatter runs on | Java 17 syntax | Java 21 syntax |
|---|---|---|
| JDK 17 | Tested | Fails with parse errors (tested) |
| JDK 21 | Tested | Tested |
| JDK 26 | Tested | Tested |

The Maven plugin runs the formatter on the JVM running Maven by default. To format newer code while Maven runs on an older
JDK, point `javaExecutable` (`-Dtailorbird.java=...`) at a newer `java`.

## Maven plugin

Each cell ran `tailorbird:apply` followed by `tailorbird:check` on a sample project. A `tailorbird.yml` in the module
or in the root of a multi-module build was also tested, including building one module from its own directory
(Maven 3.9.16, JDK 26).

| Maven | JDK 11 | JDK 16 | JDK 17 | JDK 21 | JDK 26 |
|---|---|---|---|---|---|
| 3.6.3 | ❌ plugin needs Java 17 | ❌ plugin needs Java 17 | ✅ | ✅ | ✅ |
| 3.8.8 | ❌ plugin needs Java 17 | ❌ plugin needs Java 17 | ✅ | ✅ | ✅ |
| 3.9.16 | ❌ plugin needs Java 17 | ❌ plugin needs Java 17 | ✅ | ✅ | ✅ |
| 4.0.0-rc-7 | ❌ Maven 4 needs Java 17 | ❌ Maven 4 needs Java 17 | ✅ | ✅ | ✅ |

On Java 11 or 16 the build fails with `UnsupportedClassVersionError` (class file version 61.0). Maven versions older
than 3.6.3 were not tested.

## Gradle

Gradle projects use Tailorbird through the upstream `com.palantir.java-format` plugin (see the README).

| Use | Gradle version | Status |
|---|---|---|
| Building this repository | 9.3.1 (wrapper) | Tested |
| Consuming Tailorbird via the upstream plugin | 8.14.3, 9.3.1 | Tested by upstream (`gradle/gradle-test-versions.yml`), not verified with Tailorbird |

Do not enable the native formatter (`palantir.native.formatter`) in a consuming project: it downloads Palantir's
binary, which does not contain Tailorbird's rules.
