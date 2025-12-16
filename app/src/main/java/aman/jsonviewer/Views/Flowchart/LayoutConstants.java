package aman.jsonviewer;

public class LayoutConstants {
    // Card dimensions
    public static final float CARD_WIDTH = 340;
    public static final float CARD_HEIGHT = 90;
    public static final float CARD_CORNER_RADIUS = 12;
    
    // Spacing
    public static final float HORIZONTAL_SPACING = 50;
    
    // REMOVED 'final' keyword so this can be changed by the slider
    public static float VERTICAL_SPACING = 500; 
    
    public static final float TREE_TOP_MARGIN = 300;
    
    // Zoom limits
    public static final float MIN_SCALE = 0.05f;
    public static final float MAX_SCALE = 5.0f;
    
    // UI elements
    public static final float COLLAPSE_BADGE_SIZE = 70;
    public static final float RESET_BUTTON_SIZE = 112; 
    public static final float RESET_BUTTON_MARGIN = 24;
    public static final int CLICK_THRESHOLD = 10; 
}
