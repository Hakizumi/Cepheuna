package org.sempiria.cepheuna.service;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.sempiria.cepheuna.config.StreamingTokenizerProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Tokenize streaming LLM output into sentence-like chunks for incremental TTS.
 *
 * <p>This revision emits the first sentence more aggressively and supports CJK
 * text better, so Chinese replies do not wait for whitespace that never comes.
 *
 * @since 2.0.0
 * @version 1.2.0
 * @author Sempiria
 */
@Service
public final class StreamingTokenizerService {
    private final ReentrantLock lock = new ReentrantLock();
    private final boolean[] sepAscii = new boolean[128];
    private final char[] seps;
    private final @NonNull StringBuilder buf;
    private final int softLength;
    private final int aggressiveSoftLength;
    private final int hardLength;
    private final int minEmitChars;
    private final int aggressiveMinEmitChars;

    public StreamingTokenizerService(@NonNull StreamingTokenizerProperties properties) {
        String cut = properties.getCut();
        if (cut == null || cut.isBlank()) {
            cut = ",.;?!~，。；？！\n";
        }

        char[] cutChars = cut.toCharArray();
        this.softLength = properties.getSoftLength() > 0 ? properties.getSoftLength() : 48;
        this.aggressiveSoftLength = properties.getAggressiveSoftLength() > 0 ? properties.getAggressiveSoftLength() : 24;
        this.hardLength = Math.max(this.softLength + 16, properties.getHardLength());
        this.minEmitChars = Math.max(1, properties.getMinEmitChars());
        this.aggressiveMinEmitChars = Math.max(1, properties.getAggressiveMinEmitChars());

        this.seps = cutChars.clone();
        for (char c : cutChars) {
            if (c < 128) {
                sepAscii[c] = true;
            }
        }
        this.buf = new StringBuilder(Math.min(this.softLength, 64));
    }

    public void feed(CharSequence chunk, @NonNull Consumer<String> onToken) {
        feed(chunk, false, onToken);
    }

    public void feed(@Nullable CharSequence chunk, boolean aggressive, @NonNull Consumer<String> onToken) {
        if (chunk == null || chunk.isEmpty()) {
            return;
        }

        lock.lock();
        try {
            final int n = chunk.length();
            for (int i = 0; i < n; i++) {
                char ch = chunk.charAt(i);

                if (buf.length() == 0 && Character.isWhitespace(ch)) {
                    continue;
                }

                if (isSep(ch)) {
                    if (buf.length() > 0) {
                        buf.append(ch);
                        int min = aggressive ? aggressiveMinEmitChars : minEmitChars;
                        if (buf.length() >= min) {
                            emitAndReset(onToken);
                        }
                    }
                    continue;
                }

                buf.append(ch);

                int soft = aggressive ? aggressiveSoftLength : softLength;
                int min = aggressive ? aggressiveMinEmitChars : minEmitChars;
                if (buf.length() >= soft && isNaturalPauseChar(ch)) {
                    if (buf.length() >= min) {
                        emitAndReset(onToken);
                        continue;
                    }
                }

                if (buf.length() >= hardLength) {
                    emitAndReset(onToken);
                }
            }
        }
        finally {
            lock.unlock();
        }
    }

    public void flush(@NonNull Consumer<String> onToken) {
        lock.lock();
        try {
            if (buf.length() > 0) {
                emitAndReset(onToken);
            }
        }
        finally {
            lock.unlock();
        }
    }

    public @NonNull List<String> flush() {
        List<String> out = new ArrayList<>();
        flush(out::add);
        return out;
    }

    public void reset() {
        lock.lock();
        try {
            buf.setLength(0);
        }
        finally {
            lock.unlock();
        }
    }

    private void emitAndReset(@NonNull Consumer<String> onToken) {
        onToken.accept(buf.toString());
        buf.setLength(0);
    }

    private boolean isSep(char ch) {
        if (ch < 128) {
            return sepAscii[ch];
        }
        for (char s : seps) {
            if (s == ch) {
                return true;
            }
        }
        return false;
    }

    private boolean isNaturalPauseChar(char ch) {
        return Character.isWhitespace(ch) || isCjk(ch);
    }

    private boolean isCjk(char ch) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(ch);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS
                || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || block == Character.UnicodeBlock.HIRAGANA
                || block == Character.UnicodeBlock.KATAKANA
                || block == Character.UnicodeBlock.HANGUL_SYLLABLES;
    }
}
