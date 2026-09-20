package org.example.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class AcademicTextTokenizer {

    private AcademicTextTokenizer() {
    }

    static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String normalized = text.toLowerCase(Locale.ROOT);
        List<String> tokens = new ArrayList<>();
        StringBuilder latinToken = new StringBuilder();
        StringBuilder cjkRun = new StringBuilder();

        for (int index = 0; index < normalized.length(); ) {
            int codePoint = normalized.codePointAt(index);
            index += Character.charCount(codePoint);
            if (Character.isLetterOrDigit(codePoint) && !isCjk(codePoint)) {
                flush(cjkRun, tokens, true);
                latinToken.appendCodePoint(codePoint);
            } else if (isCjk(codePoint)) {
                flush(latinToken, tokens, false);
                cjkRun.appendCodePoint(codePoint);
            } else {
                flush(latinToken, tokens, false);
                flush(cjkRun, tokens, true);
            }
        }
        flush(latinToken, tokens, false);
        flush(cjkRun, tokens, true);
        return tokens;
    }

    static Set<String> uniqueTokens(String text) {
        return new LinkedHashSet<>(tokenize(text));
    }

    private static void flush(StringBuilder value, List<String> target, boolean cjk) {
        if (value.isEmpty()) {
            return;
        }
        String token = value.toString();
        value.setLength(0);
        if (!cjk) {
            if (token.length() > 1) {
                target.add(token);
            }
            return;
        }

        int[] chars = token.codePoints().toArray();
        for (int i = 0; i < chars.length; i++) {
            target.add(new String(chars, i, 1));
            if (i + 1 < chars.length) {
                target.add(new String(chars, i, 2));
            }
        }
    }

    private static boolean isCjk(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }
}
