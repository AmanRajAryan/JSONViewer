package aman.jsonviewer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.View;

public class DampedScrollBar extends View {
    private Paint thumbPaint;
    private Paint trackPaint;
    private Rect thumbRect;
    private boolean isVertical;
    private float scrollRatio = 0.1f;
    private float scrollPosition = 0;
    private int contentSize = 1000;
    private float dampingFactor = 1.0f;

    private boolean isDragging = false;
    private boolean isDampingDisabled = false;
    private float dragStartPos;
    private float lastMovePos;
    private Handler handler;
    private Runnable longPressRunnable;
    private Vibrator vibrator;

    private static final long LONG_PRESS_TIMEOUT = 100;
    private static final float MOVEMENT_THRESHOLD = 10.0f;

    private OnScrollListener listener;

    interface OnScrollListener {
        void onScroll(float delta, boolean isDampingDisabled);
    }

    public DampedScrollBar(Context context, boolean isVertical) {
        super(context);
        this.isVertical = isVertical;
        init(context);
    }

    private void init(Context context) {
        thumbPaint = new Paint();
        thumbPaint.setAntiAlias(true);

        trackPaint = new Paint();
        trackPaint.setColor(0x33000000); // More translucent track
        trackPaint.setAntiAlias(true);

        thumbRect = new Rect();
        handler = new Handler();
        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    }

    public void setOnScrollListener(OnScrollListener listener) {
        this.listener = listener;
    }

    public void updateScrollBar(float ratio, int position, int totalSize, float damping) {
        if (isDragging && isDampingDisabled) {
            return;
        }

        this.scrollRatio = Math.max(0.05f, Math.min(1.0f, ratio));
        this.scrollPosition = position;
        this.contentSize = totalSize;
        this.dampingFactor = damping;
        calculateThumbRect();
        invalidate();
    }

    private void calculateThumbRect() {
        int padding = 8; // Padding from edges
        
        if (isVertical) {
            int availableHeight = getHeight() - (padding * 2);
            int thumbHeight = Math.max(60, (int) (availableHeight * scrollRatio));
            
            // Ensure thumb height doesn't exceed available height
            if (thumbHeight > availableHeight) {
                thumbHeight = availableHeight;
            }
            
            int trackHeight = availableHeight - thumbHeight;
            int maxScroll = contentSize - (int) (contentSize * scrollRatio);
            int thumbTop = maxScroll > 0 ? (int) ((scrollPosition / (float) maxScroll) * trackHeight) : 0;
            
            // Clamp thumb position to stay within bounds
            thumbTop = Math.max(0, Math.min(thumbTop, trackHeight));
            
            thumbRect.set(padding, padding + thumbTop, getWidth() - padding, padding + thumbTop + thumbHeight);
        } else {
            int availableWidth = getWidth() - (padding * 2);
            int thumbWidth = Math.max(60, (int) (availableWidth * scrollRatio));
            
            // Ensure thumb width doesn't exceed available width
            if (thumbWidth > availableWidth) {
                thumbWidth = availableWidth;
            }
            
            int trackWidth = availableWidth - thumbWidth;
            int maxScroll = contentSize - (int) (contentSize * scrollRatio);
            int thumbLeft = maxScroll > 0 ? (int) ((scrollPosition / (float) maxScroll) * trackWidth) : 0;
            
            // Clamp thumb position to stay within bounds
            thumbLeft = Math.max(0, Math.min(thumbLeft, trackWidth));
            
            thumbRect.set(padding + thumbLeft, padding, padding + thumbLeft + thumbWidth, getHeight() - padding);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int padding = 8;
        
        // Draw track with padding
        canvas.drawRoundRect(padding, padding, getWidth() - padding, getHeight() - padding, 10, 10, trackPaint);

        if (isDampingDisabled) {
            thumbPaint.setColor(0xB34CAF50); // More translucent green (was 0xCC)
        } else {
            thumbPaint.setColor(0xCC00BCD4); // More translucent cyan (was 0xFF)
        }
        canvas.drawRoundRect(thumbRect.left, thumbRect.top, thumbRect.right, thumbRect.bottom, 10, 10, thumbPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float pos = isVertical ? event.getY() : event.getX();
        float touchX = event.getX();
        float touchY = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // Check if touch is within the visible thumb bounds (respecting padding)
                if (thumbRect.contains((int) touchX, (int) touchY)) {
                    isDragging = true;
                    dragStartPos = pos;
                    lastMovePos = pos;

                    longPressRunnable = new Runnable() {
                        @Override
                        public void run() {
                            if (isDragging && !isDampingDisabled) {
                                float distance = Math.abs(lastMovePos - dragStartPos);
                                if (distance < MOVEMENT_THRESHOLD) {
                                    isDampingDisabled = true;
                                    if (vibrator != null && vibrator.hasVibrator()) {
                                        try {
                                            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK));
                                        } catch (Exception e) {
                                            // Fallback for older devices
                                            vibrator.vibrate(50);
                                        }
                                    }
                                    invalidate();
                                }
                            }
                        }
                    };
                    handler.postDelayed(longPressRunnable, LONG_PRESS_TIMEOUT);
                    getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                }
                return false; // Don't consume touch if not on thumb
                

            case MotionEvent.ACTION_MOVE:
                if (isDragging) {
                    int padding = 8;
                    
                    if (isDampingDisabled) {
                        int availableSize = (isVertical ? getHeight() : getWidth()) - (padding * 2);
                        int thumbSize = isVertical ? thumbRect.height() : thumbRect.width();
                        int trackSize = availableSize - thumbSize;
                        float thumbPos = pos - (isVertical ? thumbRect.height() : thumbRect.width()) / 2.0f - padding;
                        float normalizedPos = Math.max(0, Math.min(1, thumbPos / trackSize));

                        int newThumbPos = (int) (normalizedPos * trackSize);
                        if (isVertical) {
                            thumbRect.set(padding, padding + newThumbPos, getWidth() - padding, padding + newThumbPos + thumbSize);
                        } else {
                            thumbRect.set(padding + newThumbPos, padding, padding + newThumbPos + thumbSize, getHeight() - padding);
                        }
                        invalidate();

                        if (listener != null) {
                            listener.onScroll(normalizedPos, true);
                        }
                    } else {
                        float delta = pos - lastMovePos;

                        float totalMovement = Math.abs(pos - dragStartPos);
                        if (totalMovement > MOVEMENT_THRESHOLD && !isDampingDisabled) {
                            if (longPressRunnable != null) {
                                handler.removeCallbacks(longPressRunnable);
                                longPressRunnable = null;
                            }
                        }

                        if (listener != null && Math.abs(delta) > 0.1f) {
                            listener.onScroll(delta, false);
                        }
                    }

                    lastMovePos = pos;
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isDragging) {
                    isDragging = false;
                    isDampingDisabled = false;
                    if (longPressRunnable != null) {
                        handler.removeCallbacks(longPressRunnable);
                        longPressRunnable = null;
                    }
                    getParent().requestDisallowInterceptTouchEvent(false);
                    invalidate();
                    return true;
                }
                break;
        }

        return isDragging;
    }
}