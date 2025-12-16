package aman.jsonviewer;

import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import java.util.ArrayList;
import java.util.List;

/**
 * MEMORY-OPTIMIZED HIGHLIGHTER
 * Key optimizations:
 * 1. Reuse ForegroundColorSpan objects (pool pattern)
 * 2. Skip highlighting for lines longer than threshold
 * 3. Only highlight visible syntax elements
 * 4. Use simple char-by-char parsing instead of regex
 */
public class JSONSyntaxHighlighter {
    
    // Span object pools to avoid creating thousands of span objects
    private static final SpanPool COLOR_CYAN = new SpanPool(0xFF00BCD4);    // Keys
    private static final SpanPool COLOR_GREEN = new SpanPool(0xFF4CAF50);   // Strings
    private static final SpanPool COLOR_ORANGE = new SpanPool(0xFFFF9800);  // Numbers
    private static final SpanPool COLOR_PURPLE = new SpanPool(0xFF9C27B0);  // Keywords
    private static final SpanPool COLOR_GRAY = new SpanPool(0xFF757575);    // Null
    
    // CRITICAL: Don't highlight very long lines - they cause OOM
    private static final int MAX_LINE_LENGTH_FULL = 500;
    private static final int MAX_LINE_LENGTH_KEYS = 2000;
    
    /**
     * Object pool for ForegroundColorSpan to reduce allocations
     */
    private static class SpanPool {
        private final int color;
        private final List<ForegroundColorSpan> pool = new ArrayList<>(20);
        private int nextIndex = 0;
        
        SpanPool(int color) {
            this.color = color;
            // Pre-create some spans
            for (int i = 0; i < 20; i++) {
                pool.add(new ForegroundColorSpan(color));
            }
        }
        
        ForegroundColorSpan get() {
            if (nextIndex < pool.size()) {
                return pool.get(nextIndex++);
            }
            // Create new if needed
            ForegroundColorSpan span = new ForegroundColorSpan(color);
            pool.add(span);
            nextIndex++;
            return span;
        }
        
        void reset() {
            nextIndex = 0;
        }
    }
    
    public static SpannableStringBuilder highlight(String text) {
        // CRITICAL: For very long lines, skip highlighting entirely
        if (text.length() > MAX_LINE_LENGTH_FULL) {
            return new SpannableStringBuilder(text);
        }
        
        SpannableStringBuilder builder = new SpannableStringBuilder(text);
        
        // Reset span pools
        COLOR_CYAN.reset();
        COLOR_GREEN.reset();
        COLOR_ORANGE.reset();
        COLOR_PURPLE.reset();
        COLOR_GRAY.reset();
        
        try {
            highlightFast(builder, text);
        } catch (Exception e) {
            // If highlighting fails, return plain text
            return new SpannableStringBuilder(text);
        }
        
        return builder;
    }
    
    /**
     * Fast char-by-char parser - avoids regex overhead
     */
    private static void highlightFast(SpannableStringBuilder builder, String text) {
        int len = text.length();
        int i = 0;
        
        while (i < len) {
            char c = text.charAt(i);
            
            // String values
            if (c == '"') {
                int end = findStringEnd(text, i + 1);
                if (end > i) {
                    // Check if this is a key (followed by colon)
                    boolean isKey = false;
                    int j = end + 1;
                    while (j < len && Character.isWhitespace(text.charAt(j))) j++;
                    if (j < len && text.charAt(j) == ':') {
                        isKey = true;
                    }
                    
                    if (isKey) {
                        builder.setSpan(
                            COLOR_CYAN.get(),
                            i, end + 1,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        );
                    } else {
                        builder.setSpan(
                            COLOR_GREEN.get(),
                            i, end + 1,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        );
                    }
                    i = end + 1;
                    continue;
                }
            }
            
            // Numbers
            if (c == '-' || (c >= '0' && c <= '9')) {
                int end = findNumberEnd(text, i);
                if (end > i) {
                    builder.setSpan(
                        COLOR_ORANGE.get(),
                        i, end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                    i = end;
                    continue;
                }
            }
            
            // Keywords: true, false, null
            if (c == 't' && i + 4 <= len && text.substring(i, i + 4).equals("true")) {
                builder.setSpan(
                    COLOR_PURPLE.get(),
                    i, i + 4,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                );
                i += 4;
                continue;
            }
            
            if (c == 'f' && i + 5 <= len && text.substring(i, i + 5).equals("false")) {
                builder.setSpan(
                    COLOR_PURPLE.get(),
                    i, i + 5,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                );
                i += 5;
                continue;
            }
            
            if (c == 'n' && i + 4 <= len && text.substring(i, i + 4).equals("null")) {
                builder.setSpan(
                    COLOR_GRAY.get(),
                    i, i + 4,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                );
                i += 4;
                continue;
            }
            
            i++;
        }
    }
    
    private static int findStringEnd(String text, int start) {
        int len = text.length();
        for (int i = start; i < len; i++) {
            char c = text.charAt(i);
            if (c == '"') {
                // Check if escaped
                int backslashes = 0;
                int j = i - 1;
                while (j >= start && text.charAt(j) == '\\') {
                    backslashes++;
                    j--;
                }
                if (backslashes % 2 == 0) {
                    return i;
                }
            }
        }
        return -1;
    }
    
    private static int findNumberEnd(String text, int start) {
        int len = text.length();
        int i = start;
        
        // Optional minus
        if (i < len && text.charAt(i) == '-') i++;
        
        // Digits
        boolean hasDigits = false;
        while (i < len && text.charAt(i) >= '0' && text.charAt(i) <= '9') {
            hasDigits = true;
            i++;
        }
        
        if (!hasDigits) return start;
        
        // Optional decimal
        if (i < len && text.charAt(i) == '.') {
            i++;
            while (i < len && text.charAt(i) >= '0' && text.charAt(i) <= '9') {
                i++;
            }
        }
        
        // Optional exponent
        if (i < len && (text.charAt(i) == 'e' || text.charAt(i) == 'E')) {
            i++;
            if (i < len && (text.charAt(i) == '+' || text.charAt(i) == '-')) {
                i++;
            }
            while (i < len && text.charAt(i) >= '0' && text.charAt(i) <= '9') {
                i++;
            }
        }
        
        return i;
    }
    
    /**
     * Lightweight highlighting for very long lines - only keys
     */
    public static SpannableStringBuilder highlightKeysOnly(String text) {
        if (text.length() > MAX_LINE_LENGTH_KEYS) {
            return new SpannableStringBuilder(text);
        }
        
        SpannableStringBuilder builder = new SpannableStringBuilder(text);
        COLOR_CYAN.reset();
        
        try {
            int len = text.length();
            int i = 0;
            
            while (i < len) {
                if (text.charAt(i) == '"') {
                    int end = findStringEnd(text, i + 1);
                    if (end > i) {
                        // Check for colon after string
                        int j = end + 1;
                        while (j < len && Character.isWhitespace(text.charAt(j))) j++;
                        if (j < len && text.charAt(j) == ':') {
                            builder.setSpan(
                                COLOR_CYAN.get(),
                                i, end + 1,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            );
                        }
                        i = end + 1;
                        continue;
                    }
                }
                i++;
            }
        } catch (Exception e) {
            // Return plain text on error
        }
        
        return builder;
    }
}