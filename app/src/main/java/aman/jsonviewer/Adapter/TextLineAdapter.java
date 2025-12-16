package aman.jsonviewer;

import android.content.Context;
import android.graphics.Paint;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.util.Log;
import android.util.LruCache;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextLineAdapter extends RecyclerView.Adapter<TextLineAdapter.LineViewHolder> {
    private String[] lines = new String[0];
    private int maxLineWidth = 0;
    private Context context;
    private boolean enableWrapping = false;

    private String searchQuery = null;

    private LruCache<Integer, CharSequence> spanCache;
    
    private static final int TINY_CACHE = 20;
    private static final int SMALL_CACHE = 30;
    private static final int MEDIUM_CACHE = 50;

    public TextLineAdapter() {
        spanCache = new LruCache<>(TINY_CACHE);
    }

    public void setLines(String[] lines) {
        this.lines = lines;
        
        int cacheSize;
        if (lines.length < 1000) {
            cacheSize = TINY_CACHE;
        } else if (lines.length < 5000) {
            cacheSize = SMALL_CACHE;
        } else {
            cacheSize = MEDIUM_CACHE;
        }
        
        spanCache = new LruCache<Integer, CharSequence>(cacheSize) {
            @Override
            protected void entryRemoved(boolean evicted, Integer key, 
                                       CharSequence oldValue, CharSequence newValue) {
                if (oldValue instanceof SpannableStringBuilder) {
                    ((SpannableStringBuilder) oldValue).clear();
                }
            }
        };
        
        maxLineWidth = 0;
        notifyDataSetChanged();
        
        Log.d("TextLineAdapter", "Lines: " + lines.length + 
              ", Cache size: " + cacheSize);
    }

    public void setSearchQuery(String query) {
        this.searchQuery = query;
        this.spanCache.evictAll();
        notifyDataSetChanged();
    }

    public int findFirstMatchLine() {
        if (searchQuery == null || searchQuery.isEmpty()) return -1;
        String lowerQuery = searchQuery.toLowerCase();
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].toLowerCase().contains(lowerQuery)) {
                return i;
            }
        }
        return -1;
    }

    public int getMaxLineWidth() {
        return maxLineWidth;
    }

    public void setEnableWrapping(boolean enable) {
        this.enableWrapping = enable;
        notifyDataSetChanged();
    }

    public void calculateMaxWidth(Context ctx) {
        this.context = ctx;
        if (lines.length == 0) return;

        float textSizePx = android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_SP,
                12,
                ctx.getResources().getDisplayMetrics());

        Paint paint = new Paint();
        paint.setTextSize(textSizePx);
        paint.setTypeface(android.graphics.Typeface.MONOSPACE);

        String longestLine = "";
        int maxLength = 0;
        
        for (String line : lines) {
            if (line.length() > maxLength) {
                maxLength = line.length();
                longestLine = line;
            }
        }

        float width = paint.measureText(longestLine);
        maxLineWidth = (int) Math.ceil(width) + 50;
    }

    @NonNull
    @Override
    public LineViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (context == null) context = parent.getContext();

        // Custom TextView that handles touch better
        SelectableTextView textView = new SelectableTextView(parent.getContext());
        textView.setTextSize(12);
        textView.setTypeface(android.graphics.Typeface.MONOSPACE);
        textView.setIncludeFontPadding(false);

        if (enableWrapping) {
            textView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            textView.setSingleLine(false);
            textView.setMaxLines(Integer.MAX_VALUE);
        } else {
            int width = maxLineWidth > 0 ? maxLineWidth : ViewGroup.LayoutParams.WRAP_CONTENT;
            textView.setLayoutParams(new ViewGroup.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT));
            textView.setSingleLine(true);
            textView.setMaxLines(1);
            textView.setHorizontallyScrolling(true);
            textView.setEllipsize(null);
        }

        textView.setTextColor(0xFFFFFFFF);
        textView.setTextIsSelectable(true);

        return new LineViewHolder(textView);
    }

    @Override
    public void onBindViewHolder(@NonNull LineViewHolder holder, int position) {
        String line = lines[position];

        CharSequence cached = spanCache.get(position);
        if (cached != null) {
            holder.textView.setText(cached);
            return;
        }

        SpannableStringBuilder processed;
        if (line.length() < 1000) {
            processed = JSONSyntaxHighlighter.highlight(line);
        } else {
            processed = JSONSyntaxHighlighter.highlightKeysOnly(line);
        }

        if (searchQuery != null && !searchQuery.isEmpty()) {
            try {
                Pattern p = Pattern.compile(Pattern.quote(searchQuery), Pattern.CASE_INSENSITIVE);
                Matcher m = p.matcher(line);
                while (m.find()) {
                    processed.setSpan(
                            new BackgroundColorSpan(0xFF554400),
                            m.start(),
                            m.end(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                }
            } catch (Exception e) {
                // Ignore
            }
        }

        spanCache.put(position, processed);
        holder.textView.setText(processed);
    }

    @Override
    public int getItemCount() {
        return lines.length;
    }
    
    @Override
    public void onViewRecycled(@NonNull LineViewHolder holder) {
        super.onViewRecycled(holder);
        holder.textView.setText(null);
    }
    
    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        spanCache.evictAll();
    }

    public void trimCache() {
        if (spanCache != null) {
            spanCache.trimToSize(5);
        }
    }
    
    public void clearCache() {
        if (spanCache != null) {
            spanCache.evictAll();
        }
    }

    /**
     * Custom TextView that better handles selection vs scrolling
     */
    private static class SelectableTextView extends TextView {
        private boolean isSelecting = false;
        private float startX, startY;
        private static final int MOVEMENT_THRESHOLD = 20;
        
        public SelectableTextView(Context context) {
            super(context);
        }
        
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startX = event.getX();
                    startY = event.getY();
                    isSelecting = false;
                    // Request that parent doesn't intercept touch events
                    getParent().requestDisallowInterceptTouchEvent(true);
                    break;
                    
                case MotionEvent.ACTION_MOVE:
                    // If user moved significantly, it's scrolling not selection
                    float dx = Math.abs(event.getX() - startX);
                    float dy = Math.abs(event.getY() - startY);
                    if (dx > MOVEMENT_THRESHOLD || dy > MOVEMENT_THRESHOLD) {
                        // User is scrolling, allow parent to handle it
                        isSelecting = false;
                        getParent().requestDisallowInterceptTouchEvent(false);
                        return false; // Let parent handle scrolling
                    } else {
                        // Small movement, might be selection
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    break;
                    
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    // Reset parent interception
                    getParent().requestDisallowInterceptTouchEvent(false);
                    break;
            }
            
            return super.onTouchEvent(event);
        }
        
        @Override
        public boolean performLongClick() {
            isSelecting = true;
            // Ensure parent doesn't interfere with selection
            getParent().requestDisallowInterceptTouchEvent(true);
            return super.performLongClick();
        }
    }

    static class LineViewHolder extends RecyclerView.ViewHolder {
        TextView textView;

        LineViewHolder(TextView textView) {
            super(textView);
            this.textView = textView;
        }
    }
}