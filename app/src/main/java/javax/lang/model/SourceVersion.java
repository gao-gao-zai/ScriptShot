package javax.lang.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Minimal subset of javax.lang.model.SourceVersion used by Rhino when running on Android.
 */
public enum SourceVersion {
    RELEASE_0,
    RELEASE_1,
    RELEASE_2,
    RELEASE_3,
    RELEASE_4,
    RELEASE_5,
    RELEASE_6,
    RELEASE_7,
    RELEASE_8,
    RELEASE_9,
    RELEASE_10,
    RELEASE_11,
    RELEASE_12,
    RELEASE_13,
    RELEASE_14,
    RELEASE_15,
    RELEASE_16,
    RELEASE_17;

    private static final Set<String> KEYWORDS;

    static {
        KEYWORDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "abstract", "assert", "boolean", "break", "byte",
            "case", "catch", "char", "class", "const",
            "continue", "default", "do", "double", "else",
            "enum", "extends", "final", "finally", "float",
            "for", "goto", "if", "implements", "import",
            "instanceof", "int", "interface", "long", "native",
            "new", "package", "private", "protected", "public",
            "return", "short", "static", "strictfp", "super",
            "switch", "synchronized", "this", "throw", "throws",
            "transient", "try", "void", "volatile", "while",
            "true", "false", "null"
        )));
    }

    public static SourceVersion latest() {
        return RELEASE_17;
    }

    public static SourceVersion latestSupported() {
        return latest();
    }

    public static boolean isIdentifier(CharSequence name) {
        if (name == null || name.length() == 0) {
            return false;
        }
        if (isKeyword(name)) {
            return false;
        }
        char first = name.charAt(0);
        if (!Character.isJavaIdentifierStart(first)) {
            return false;
        }
        for (int i = 1; i < name.length(); i++) {
            if (!Character.isJavaIdentifierPart(name.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static boolean isKeyword(CharSequence name) {
        return name != null && KEYWORDS.contains(name.toString());
    }
}
