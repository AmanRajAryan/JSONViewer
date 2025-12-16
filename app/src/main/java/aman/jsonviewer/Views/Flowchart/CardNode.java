package aman.jsonviewer;

/**
 * Represents a single node (card) in the flowchart tree.
 * Contains the key-value data and positioning information.
 */
public class CardNode {
    public String key;
    public String value;      // Display value (Short/Truncated)
    public String fullValue;  // NEW: Full raw value (For BottomSheet)
    public String type;
    public float x;
    public float y;
    public int level;
    
    public boolean isVisible = true;

    // Updated constructor to accept fullValue
    public CardNode(String key, String value, String fullValue, String type, float x, float y, int level) {
        this.key = key;
        this.value = value;
        this.fullValue = fullValue; // Store the full data
        this.type = type;
        this.x = x;
        this.y = y;
        this.level = level;
        this.isVisible = true; 
    }

    public float getCenterX(float cardWidth) {
        return x + cardWidth / 2f;
    }

    public float getBottomY(float cardHeight) {
        return y + cardHeight;
    }

    public boolean contains(float pointX, float pointY, float cardWidth, float cardHeight) {
        return pointX >= x && pointX <= x + cardWidth && 
               pointY >= y && pointY <= y + cardHeight;
    }
}
