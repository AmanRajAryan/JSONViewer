package aman.jsonviewer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Calculates a compact tree layout by merging subtree contours 
 * rather than using simple bounding boxes.
 */
public class TreeLayoutCalculator {
    private final Map<CardNode, List<CardNode>> childrenMap;
    private final Set<CardNode> collapsedNodes;

    public TreeLayoutCalculator(Map<CardNode, List<CardNode>> childrenMap, 
                                Set<CardNode> collapsedNodes) {
        this.childrenMap = childrenMap;
        this.collapsedNodes = collapsedNodes;
    }

    /**
     * Entry point for layout calculation.
     */
    public void calculateTreeLayout(CardNode root, float startX, float startY) {
        calculateNodeContour(root);
        // The recursive method calculates relative positions. 
        // We need to apply the final absolute coordinates.
        applyAbsoluteCoordinates(root, startX, startY);
    }

    /**
     * Recursive function that calculates relative X positions and returns the 
     * contour (shape) of the subtree.
     */
    private NodeContour calculateNodeContour(CardNode node) {
        NodeContour currentContour = new NodeContour();
        
        // Base dimensions of this single node (half-width left, half-width right)
        float halfWidth = LayoutConstants.CARD_WIDTH / 2f;
        currentContour.addLevel(0, -halfWidth, halfWidth);

        List<CardNode> children = childrenMap.get(node);
        if (children == null || children.isEmpty() || collapsedNodes.contains(node)) {
            return currentContour;
        }

        // --- MERGE CHILDREN CONTOURS ---
        
        // We accumulate the contours of all children into one "super contour"
        NodeContour childrenBlockContour = null;
        
        // This tracks the right-most edge of the group so we can center the parent later
        float minChildX = 0;
        float maxChildX = 0;

        for (CardNode child : children) {
            NodeContour childContour = calculateNodeContour(child);
            
            if (childrenBlockContour == null) {
                // First child acts as the anchor at 0
                childrenBlockContour = childContour;
                child.x = 0; 
                minChildX = 0;
                maxChildX = 0;
            } else {
                // Calculate minimum shift needed to avoid overlap with the ACCUMULATED block
                float distance = childrenBlockContour.computeMinDistance(childContour);
                
                // Position is simply the necessary distance + spacing
                // ERROR FIX: Removed "currentChildXOffset +"
                child.x = distance + LayoutConstants.HORIZONTAL_SPACING;
                
                // Merge this child's contour into the block
                childrenBlockContour.merge(childContour, child.x);
                
                maxChildX = child.x;
            }
        }

        // --- CENTER PARENT OVER CHILDREN ---
        
        // Center point is the average of the first child (0) and the last child
        float childrenCenter = (minChildX + maxChildX) / 2f;
        
        // We want the parent (at x=0 local) to be over 'childrenCenter'.
        // So we shift all children left so their center aligns with parent's 0.
        float shiftChildrenLeft = -childrenCenter;

        for (CardNode child : children) {
            child.x += shiftChildrenLeft;
        }
        
        // Finally, create the merged result: Parent (level 0) + Children Block (level 1+)
        // We shift the children block vertically by 1 unit (logic handled in addLevel or merge)
        currentContour.appendChildrenBelow(childrenBlockContour, shiftChildrenLeft);

        return currentContour;
    }

    /**
     * Converts the calculated relative X coordinates to absolute screen coordinates.
     */
    private void applyAbsoluteCoordinates(CardNode node, float absoluteX, float absoluteY) {
        node.x = absoluteX;
        node.y = absoluteY;

        List<CardNode> children = childrenMap.get(node);
        if (children != null && !collapsedNodes.contains(node)) {
            for (CardNode child : children) {
                // child.x currently holds the offset relative to the parent
                applyAbsoluteCoordinates(child, absoluteX + child.x, 
                                         absoluteY + LayoutConstants.VERTICAL_SPACING + LayoutConstants.CARD_HEIGHT);
            }
        }
    }

    /**
     * Helper class to define the shape of a subtree.
     * Maps Relative Depth -> [LeftBound, RightBound]
     */
    private static class NodeContour {
        // Map<RelativeDepth, float[]{minX, maxX}>
        private final Map<Integer, float[]> bounds = new HashMap<>();

        void addLevel(int depth, float min, float max) {
            if (!bounds.containsKey(depth)) {
                bounds.put(depth, new float[]{min, max});
            } else {
                float[] val = bounds.get(depth);
                val[0] = Math.min(val[0], min);
                val[1] = Math.max(val[1], max);
            }
        }

        /**
         * Checks collisions between this contour (accumulated left) and a new neighbor (right).
         * Returns the absolute X position required for the neighbor's origin (0) 
         * such that it touches the rightmost edge of this contour.
         */
        float computeMinDistance(NodeContour other) {
            float maxRequiredShift = 0;
            
            // Check every depth level that exists in both contours
            for (Map.Entry<Integer, float[]> entry : bounds.entrySet()) {
                int depth = entry.getKey();
                if (other.bounds.containsKey(depth)) {
                    float[] myBounds = entry.getValue();      // [left, right] of Accumulated Block
                    float[] otherBounds = other.bounds.get(depth); // [left, right] of New Child (centered at 0)
                    
                    // We need (other.left + shift) > my.right
                    // shift > my.right - other.left
                    
                    float neededShift = myBounds[1] - otherBounds[0];
                    if (neededShift > maxRequiredShift) {
                        maxRequiredShift = neededShift;
                    }
                }
            }
            return maxRequiredShift;
        }

        /**
         * Merges another contour into this one, shifting the other by xOffset.
         */
        void merge(NodeContour other, float xOffset) {
            for (Map.Entry<Integer, float[]> entry : other.bounds.entrySet()) {
                int depth = entry.getKey();
                float[] val = entry.getValue();
                addLevel(depth, val[0] + xOffset, val[1] + xOffset);
            }
        }

        /**
         * Adds a children-block contour below the current (root) level.
         */
        void appendChildrenBelow(NodeContour childrenContour, float xOffset) {
            for (Map.Entry<Integer, float[]> entry : childrenContour.bounds.entrySet()) {
                int depth = entry.getKey();
                float[] val = entry.getValue();
                // Depth + 1 because these are children
                addLevel(depth + 1, val[0] + xOffset, val[1] + xOffset);
            }
        }
    }
}
