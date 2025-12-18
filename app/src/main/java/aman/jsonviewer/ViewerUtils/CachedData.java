package aman.jsonviewer;

import android.text.SpannableStringBuilder;

public class CachedData {
    public String formattedJson;
    public SpannableStringBuilder highlightedText;

    public CachedData(String formattedJson, SpannableStringBuilder highlightedText) {
        this.formattedJson = formattedJson;
        this.highlightedText = highlightedText;
    }
}
