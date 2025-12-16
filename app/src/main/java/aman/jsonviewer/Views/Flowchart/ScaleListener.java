package aman.jsonviewer;

import android.view.ScaleGestureDetector;

/**
 * Handles pinch-to-zoom gestures
 */
public class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
    
    public interface ScaleCallback {
        void onScaleUpdate(float newScale, float focusX, float focusY);
        void invalidate();
    }

    private ScaleCallback callback;
    private TouchGestureHandler gestureHandler;

    public ScaleListener(ScaleCallback callback, TouchGestureHandler gestureHandler) {
        this.callback = callback;
        this.gestureHandler = gestureHandler;
    }

    public void setGestureHandler(TouchGestureHandler gestureHandler) {
        this.gestureHandler = gestureHandler;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        return true;
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        float scaleFactor = detector.getScaleFactor();
        float currentScale = gestureHandler.getScale();
        float newScale = currentScale * scaleFactor;

        newScale = Math.max(LayoutConstants.MIN_SCALE, 
                           Math.min(newScale, LayoutConstants.MAX_SCALE));

        float focusX = detector.getFocusX();
        float focusY = detector.getFocusY();

        gestureHandler.updateScale(newScale, focusX, focusY);
        callback.onScaleUpdate(newScale, focusX, focusY);
        callback.invalidate();

        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {
        // Keep the state, don't reset here
    }
}