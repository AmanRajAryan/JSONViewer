package aman.jsonviewer;

import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handles touch gestures including pan, zoom, and tap
 */
public class TouchGestureHandler {
    
    public interface TouchCallback {
        void onOffsetChanged(float offsetX, float offsetY);
        void onScaleChanged(float scale, float offsetX, float offsetY);
        void onFling(float velocityX, float velocityY);
        void onCardClick(CardNode node);
        void onCollapseToggle(CardNode node);
        void onResetClick();
        void onToggleExpandCollapseClick();
        void stopFling();
        void invalidate();
    }

    private TouchCallback callback;
    private List<CardNode> nodes;
    private Map<CardNode, List<CardNode>> childrenMap;
    private Set<CardNode> collapsedNodes;

    private float offsetX = 0;
    private float offsetY = 0;
    private float scale = 1.0f;
    private float lastTouchX = 0;
    private float lastTouchY = 0;
    private float touchDownX = 0;
    private float touchDownY = 0;
    private boolean isDragging = false;
    private boolean isScaling = false;

    private ScaleGestureDetector scaleDetector;
    private android.view.VelocityTracker velocityTracker;

    public TouchGestureHandler(TouchCallback callback,
                               List<CardNode> nodes,
                               Map<CardNode, List<CardNode>> childrenMap,
                               Set<CardNode> collapsedNodes,
                               ScaleGestureDetector scaleDetector) {
        this.callback = callback;
        this.nodes = nodes;
        this.childrenMap = childrenMap;
        this.collapsedNodes = collapsedNodes;
        this.scaleDetector = scaleDetector;
    }

    public void setOffset(float x, float y) {
        this.offsetX = x;
        this.offsetY = y;
    }

    public void setScale(float scale) {
        this.scale = scale;
    }

    public float getOffsetX() {
        return offsetX;
    }

    public float getOffsetY() {
        return offsetY;
    }

    public float getScale() {
        return scale;
    }

    public boolean onTouchEvent(MotionEvent event, int viewWidth, int viewHeight) {
        scaleDetector.onTouchEvent(event);

        // Initialize velocity tracker
        if (velocityTracker == null) {
            velocityTracker = android.view.VelocityTracker.obtain();
        }
        velocityTracker.addMovement(event);

        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                // Stop any ongoing fling IMMEDIATELY
                callback.stopFling();
                
                touchDownX = event.getX();
                touchDownY = event.getY();
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                isDragging = false;
                isScaling = false;

                // Check if reset button clicked
                if (isResetButtonClicked(event.getX(), event.getY(), viewWidth)) {
                    callback.onResetClick();
                    return true;
                }
                
                // Check if expand/collapse toggle button clicked
                if (isToggleButtonClicked(event.getX(), event.getY(), viewWidth)) {
                    callback.onToggleExpandCollapseClick();
                    return true;
                }
                
                return true;

            case MotionEvent.ACTION_POINTER_DOWN:
                isScaling = true;
                isDragging = false;
                return true;

            case MotionEvent.ACTION_MOVE:
                if (isScaling || scaleDetector.isInProgress()) {
                    return true;
                }

                if (event.getPointerCount() == 1) {
                    float dx = event.getX() - lastTouchX;
                    float dy = event.getY() - lastTouchY;

                    if (Math.abs(dx) > LayoutConstants.CLICK_THRESHOLD || 
                        Math.abs(dy) > LayoutConstants.CLICK_THRESHOLD) {
                        isDragging = true;
                    }

                    if (isDragging) {
                        offsetX += dx;
                        offsetY += dy;
                        callback.onOffsetChanged(offsetX, offsetY);

                        lastTouchX = event.getX();
                        lastTouchY = event.getY();

                        callback.invalidate();
                    }
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (isDragging) {
                    // Calculate velocity and start fling
                    velocityTracker.computeCurrentVelocity(1000);
                    float velocityX = velocityTracker.getXVelocity();
                    float velocityY = velocityTracker.getYVelocity();

                    if (Math.abs(velocityX) > 100 || Math.abs(velocityY) > 100) {
                        callback.onFling(velocityX, velocityY);
                        callback.invalidate();
                    }
                } else if (!isScaling) {
                    float totalDx = Math.abs(event.getX() - touchDownX);
                    float totalDy = Math.abs(event.getY() - touchDownY);

                    if (totalDx < LayoutConstants.CLICK_THRESHOLD && 
                        totalDy < LayoutConstants.CLICK_THRESHOLD) {
                        handleClick(event.getX(), event.getY());
                    }
                }

                isDragging = false;
                isScaling = false;

                if (velocityTracker != null) {
                    velocityTracker.recycle();
                    velocityTracker = null;
                }
                return true;

            case MotionEvent.ACTION_POINTER_UP:
                if (event.getPointerCount() == 2) {
                    isScaling = false;
                    isDragging = false;

                    // Update last touch to remaining pointer
                    int remainingIndex = event.getActionIndex() == 0 ? 1 : 0;
                    lastTouchX = event.getX(remainingIndex);
                    lastTouchY = event.getY(remainingIndex);
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                isDragging = false;
                isScaling = false;
                if (velocityTracker != null) {
                    velocityTracker.recycle();
                    velocityTracker = null;
                }
                return true;
        }

        return false;
    }

    private void handleClick(float screenX, float screenY) {
        float touchX = (screenX - offsetX) / scale;
        float touchY = (screenY - offsetY) / scale;

        CardNode clickedNode = getCardAtPosition(touchX, touchY);
        if (clickedNode != null) {
            // Check if clicked on collapse badge
            List<CardNode> children = childrenMap.get(clickedNode);
            if (children != null && !children.isEmpty()) {
                float badgeX = clickedNode.x + LayoutConstants.CARD_WIDTH - 
                              LayoutConstants.COLLAPSE_BADGE_SIZE - 8;
                float badgeY = clickedNode.y + 8;

                if (touchX >= badgeX && 
                    touchX <= badgeX + LayoutConstants.COLLAPSE_BADGE_SIZE &&
                    touchY >= badgeY && 
                    touchY <= badgeY + LayoutConstants.COLLAPSE_BADGE_SIZE) {
                    callback.onCollapseToggle(clickedNode);
                    return;
                }
            }

            // Regular card click
            callback.onCardClick(clickedNode);
        }
    }

    private boolean isResetButtonClicked(float x, float y, int viewWidth) {
        float buttonX = viewWidth - LayoutConstants.RESET_BUTTON_SIZE - 
                       LayoutConstants.RESET_BUTTON_MARGIN;
        float buttonY = LayoutConstants.RESET_BUTTON_MARGIN;
        float centerX = buttonX + LayoutConstants.RESET_BUTTON_SIZE / 2f;
        float centerY = buttonY + LayoutConstants.RESET_BUTTON_SIZE / 2f;

        float dx = x - centerX;
        float dy = y - centerY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);

        return distance <= LayoutConstants.RESET_BUTTON_SIZE / 2f;
    }

    private boolean isToggleButtonClicked(float x, float y, int viewWidth) {
        float buttonX = viewWidth - LayoutConstants.RESET_BUTTON_SIZE - 
                       LayoutConstants.RESET_BUTTON_MARGIN;
        float buttonY = LayoutConstants.RESET_BUTTON_MARGIN + 
                       LayoutConstants.RESET_BUTTON_SIZE + 16;
        float centerX = buttonX + LayoutConstants.RESET_BUTTON_SIZE / 2f;
        float centerY = buttonY + LayoutConstants.RESET_BUTTON_SIZE / 2f;

        float dx = x - centerX;
        float dy = y - centerY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);

        return distance <= LayoutConstants.RESET_BUTTON_SIZE / 2f;
    }

    private CardNode getCardAtPosition(float x, float y) {
        for (int i = nodes.size() - 1; i >= 0; i--) {
            CardNode node = nodes.get(i);
            if (shouldShowNode(node) && 
                node.contains(x, y, LayoutConstants.CARD_WIDTH, LayoutConstants.CARD_HEIGHT)) {
                return node;
            }
        }
        return null;
    }

    private boolean shouldShowNode(CardNode node) {
        // Check if any ancestor is collapsed
        for (CardNode n : nodes) {
            if (collapsedNodes.contains(n)) {
                List<CardNode> children = childrenMap.get(n);
                if (children != null && isDescendantOf(node, n)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isDescendantOf(CardNode node, CardNode ancestor) {
        List<CardNode> children = childrenMap.get(ancestor);
        if (children == null) return false;

        for (CardNode child : children) {
            if (child == node) return true;
            if (isDescendantOf(node, child)) return true;
        }
        return false;
    }

    public void updateScale(float newScale, float focusX, float focusY) {
        float worldX = (focusX - offsetX) / scale;
        float worldY = (focusY - offsetY) / scale;

        offsetX = focusX - worldX * newScale;
        offsetY = focusY - worldY * newScale;
        scale = newScale;

        callback.onScaleChanged(scale, offsetX, offsetY);
    }

    public void stopFling() {
        // Called when scroller should abort
    }
}