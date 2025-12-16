package aman.jsonviewer;

import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JSONSyntaxHighlighter {
    
    // PRE-COMPILED PATTERNS for performance
    private static final Pattern KEY_PATTERN = Pattern.compile("\"[^\"]+\"\\s*:");
    private static final Pattern STRING_VALUE_PATTERN = Pattern.compile("\"[^\"]+\"");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\b-?\\d+\\.?\\d*([eE][+-]?\\d+)?\\b");
    private static final Pattern BRACE_PATTERN = Pattern.compile("[\\{\\}\\[\\]]");
    private static final Pattern PUNCTUATION_PATTERN = Pattern.compile("[,:]");
    
    private static final String[] JSON_KEYWORDS = {"true", "false", "null"};
    
    public static SpannableStringBuilder highlight(String text) {
        SpannableStringBuilder builder = new SpannableStringBuilder(text);
        
        // Safety limit for extremely long single lines
        if (text.length() > 2000) {
            return builder;
        }
        
        try {
            // Highlight Keys (Cyan)
            highlightPattern(builder, KEY_PATTERN, 0xFF00BCD4);
            
            // Highlight String Values (Green)
            highlightJsonStrings(builder, text);
            
            // Highlight Numbers (Orange)
            highlightPattern(builder, NUMBER_PATTERN, 0xFFFF9800);
            
            // Highlight Keywords (Purple)
            for (String keyword : JSON_KEYWORDS) {
                highlightJsonKeyword(builder, text, keyword, 0xFF9C27B0);
            }
            
            // Highlight Braces/Brackets (Light Gray)
            highlightPattern(builder, BRACE_PATTERN, 0xFFEEEEEE);
            
            // Highlight Punctuation (Gray)
            highlightPattern(builder, PUNCTUATION_PATTERN, 0xFFBDBDBD);
            
        } catch (Exception e) {
            // Ignore errors to prevent crash
        }
        
        return builder;
    }
    
    /**
     * Lightweight highlighting for long lines - only highlights keys
     */
    public static SpannableStringBuilder highlightKeysOnly(String text) {
        SpannableStringBuilder builder = new SpannableStringBuilder(text);
        try {
            highlightPattern(builder, KEY_PATTERN, 0xFF00BCD4);
        } catch (Exception e) {
            // Ignore errors
        }
        return builder;
    }
    
    private static void highlightJsonStrings(SpannableStringBuilder builder, String text) {
        try {
            Matcher matcher = STRING_VALUE_PATTERN.matcher(text);
            
            while (matcher.find()) {
                int start = matcher.start();
                int end = matcher.end();
                
                // Simple check to see if this string is a key (followed by colon)
                boolean isKey = false;
                for (int i = end; i < text.length(); i++) {
                    char c = text.charAt(i);
                    if (c == ':') {
                        isKey = true;
                        break;
                    } else if (!Character.isWhitespace(c)) {
                        break;
                    }
                }
                
                // Only highlight if it's a value
                if (!isKey) {
                    builder.setSpan(
                            new ForegroundColorSpan(0xFF4CAF50),
                            start,
                            end,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        } catch (Exception e) {
            // Ignore
        }
    }
    
    private static void highlightJsonKeyword(SpannableStringBuilder builder, String text, String keyword, int color) {
        int index = 0;
        while ((index = text.indexOf(keyword, index)) != -1) {
            boolean isWord = (index == 0 || !Character.isLetterOrDigit(text.charAt(index - 1)))
                    && (index + keyword.length() >= text.length()
                    || !Character.isLetterOrDigit(text.charAt(index + keyword.length())));
            
            if (isWord) {
                builder.setSpan(
                        new ForegroundColorSpan(color),
                        index,
                        index + keyword.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            index += keyword.length();
        }
    }
    
    private static void highlightPattern(SpannableStringBuilder builder, Pattern pattern, int color) {
        try {
            Matcher m = pattern.matcher(builder);
            while (m.find()) {
                builder.setSpan(
                        new ForegroundColorSpan(color),
                        m.start(),
                        m.end(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        } catch (Exception e) {
            // Ignore
        }
    }
}
