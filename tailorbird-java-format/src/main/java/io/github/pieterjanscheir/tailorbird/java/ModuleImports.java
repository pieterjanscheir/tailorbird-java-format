/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.pieterjanscheir.tailorbird.java;

import com.sun.source.tree.ImportTree;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import javax.annotation.Nullable;

/**
 * Tailorbird: whether an import is a module import ({@code import module java.base;}), final in JDK 25 (issue #57).
 * {@code ImportTree.isModule()} only exists from JDK 25, and the formatter is compiled against an older JDK, so it is
 * called by reflection. On older JDKs, which can't parse module imports, every import is an ordinary one.
 */
final class ModuleImports {
    @Nullable
    private static final Method IS_MODULE = findIsModule();

    private ModuleImports() {}

    static boolean isModule(ImportTree tree) {
        if (IS_MODULE == null) {
            return false;
        }
        try {
            return (Boolean) IS_MODULE.invoke(tree);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Could not call ImportTree.isModule()", e);
        }
    }

    @Nullable
    private static Method findIsModule() {
        try {
            return ImportTree.class.getMethod("isModule");
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
