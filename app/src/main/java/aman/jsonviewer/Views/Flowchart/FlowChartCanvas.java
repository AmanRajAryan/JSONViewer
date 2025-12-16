package aman.jsonviewer;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FlowChartCanvas extends View
        implements TouchGestureHandler.TouchCallback, ScaleListener.ScaleCallback {

    private List<CardNode> nodes = new ArrayList<>();
    private List<Connection> connections = new ArrayList<>();
    private Map<CardNode, List<CardNode>> childrenMap = new HashMap<>();
    private Set<CardNode> collapsedNodes = new HashSet<>();
    private Set<CardNode> highlightedNodes = new HashSet<>();

    private FlowChartRenderer renderer;
    private TreeLayoutCalculator layoutCalculator;
    private TouchGestureHandler gestureHandler;
    private ScaleListener scaleListener;

    private float offsetX = 0;
    private float offsetY = 0;
    private float scale = 1.0f;
    private boolean allNodesExpanded = false;

    private ScaleGestureDetector scaleDetector;
    private android.widget.OverScroller scroller;

    private OnCardClickListener clickListener;

    public interface OnCardClickListener {
        void onCardClick(String key, String value, String type);
    }

    public void setOnCardClickListener(OnCardClickListener listener) {
        this.clickListener = listener;
    }

    public FlowChartCanvas(Context context) {
        super(context);
        init(context);
    }

    private void init(Context context) {
        setBackgroundColor(0xFF121212);
        renderer = new FlowChartRenderer(context, childrenMap, collapsedNodes);
        layoutCalculator = new TreeLayoutCalculator(childrenMap, collapsedNodes);
        scaleListener = new ScaleListener(this, null);
        scaleDetector = new ScaleGestureDetector(context, scaleListener);
        gestureHandler =
                new TouchGestureHandler(this, nodes, childrenMap, collapsedNodes, scaleDetector);
        scaleListener.setGestureHandler(gestureHandler);
        scroller = new android.widget.OverScroller(context);
    }

    public void performSearch(String query) {
        highlightedNodes.clear();
        if (query == null || query.trim().isEmpty()) {
            renderer.setHighlightedNodes(highlightedNodes);
            invalidate();
            return;
        }

        String lowerQuery = query.toLowerCase();
        CardNode firstMatch = null;

        for (CardNode node : nodes) {
            boolean match =
                    node.key.toLowerCase().contains(lowerQuery)
                            || (node.fullValue != null
                                    && node.fullValue.toLowerCase().contains(lowerQuery));
            if (match && node.isVisible) {
                highlightedNodes.add(node);
                if (firstMatch == null) {
                    firstMatch = node;
                }
            }
        }

        renderer.setHighlightedNodes(highlightedNodes);
        if (firstMatch != null) {
            focusOnNode(firstMatch);
        }
        invalidate();
    }

    private void focusOnNode(CardNode node) {
        float screenCenterX = getWidth() / 2f;
        float screenCenterY = getHeight() / 2f;
        float nodeCenterX = node.getCenterX(LayoutConstants.CARD_WIDTH);
        float nodeCenterY = node.y + LayoutConstants.CARD_HEIGHT / 2f;
        float targetOffsetX = screenCenterX - (nodeCenterX * scale);
        float targetOffsetY = screenCenterY - (nodeCenterY * scale);

        scroller.startScroll(
                (int) offsetX,
                (int) offsetY,
                (int) (targetOffsetX - offsetX),
                (int) (targetOffsetY - offsetY),
                1000);
        invalidate();
    }

    public void buildFromJSON(JSONObject json) {
        nodes.clear();
        connections.clear();
        childrenMap.clear();
        collapsedNodes.clear();
        JSONTreeBuilder builder = new JSONTreeBuilder(nodes, connections, childrenMap);
        try {
            CardNode rootNode = builder.buildFromJSON(json);
            initTreeState(rootNode);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void buildFromJSONArray(JSONArray jsonArray) {
        nodes.clear();
        connections.clear();
        childrenMap.clear();
        collapsedNodes.clear();
        JSONTreeBuilder builder = new JSONTreeBuilder(nodes, connections, childrenMap);
        try {
            CardNode rootNode = builder.buildFromJSONArray(jsonArray);
            initTreeState(rootNode);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void initTreeState(CardNode rootNode) {
        for (CardNode node : nodes) {
            if (node != rootNode) {
                List<CardNode> children = childrenMap.get(node);
                if (children != null && !children.isEmpty()) {
                    collapsedNodes.add(node);
                }
            }
        }
        allNodesExpanded = false;
        updateNodeVisibility();
        layoutCalculator.calculateTreeLayout(rootNode, 0, 0);
        centerView();
    }

    public void updateVerticalSpacing(float newSpacing) {
        LayoutConstants.VERTICAL_SPACING = newSpacing;
        if (!nodes.isEmpty()) {
            CardNode root = nodes.get(0);
            layoutCalculator.calculateTreeLayout(root, root.x, root.y);
            invalidate();
        }
    }

    private void updateNodeVisibility() {
        for (CardNode node : nodes) node.isVisible = false;
        if (!nodes.isEmpty()) markChildrenVisible(nodes.get(0));
    }

    private void markChildrenVisible(CardNode node) {
        node.isVisible = true;
        if (collapsedNodes.contains(node)) return;
        List<CardNode> children = childrenMap.get(node);
        if (children != null) {
            for (CardNode child : children) markChildrenVisible(child);
        }
    }

    // FlowChartCanvas.java

    private void centerView() {
        post(
                () -> {
                    if (!nodes.isEmpty()) {
                        CardNode rootNode = nodes.get(0);

                        // 1. Horizontal Centering Fix (using focusOnNode logic for X)
                        float screenCenterX = getWidth() / 2f;
                        // Calculate root node's center X (unscaled coordinates)
                        float nodeCenterX = rootNode.getCenterX(LayoutConstants.CARD_WIDTH);

                        // Calculate the target offsetX to move the node's center X to the screen's
                        // center X
                        offsetX = screenCenterX - (nodeCenterX * scale);

                        // 2. Vertical Positioning (REVERTED to original logic)
                        offsetY = LayoutConstants.TREE_TOP_MARGIN;
                    } else {
                        // Fallback if there are no nodes
                        offsetX = getWidth() / 2f;
                        offsetY = LayoutConstants.TREE_TOP_MARGIN;
                    }

                    gestureHandler.setOffset(offsetX, offsetY);
                    invalidate();
                });
    }

    @Override
    public void computeScroll() {
        if (scroller != null && scroller.computeScrollOffset()) {
            offsetX = scroller.getCurrX();
            offsetY = scroller.getCurrY();
            gestureHandler.setOffset(offsetX, offsetY);
            invalidate();
        }
    }

    private void stopScroller() {
        if (scroller != null && !scroller.isFinished()) scroller.abortAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        canvas.translate(offsetX, offsetY);
        canvas.scale(scale, scale);

        for (Connection connection : connections) {
            if (connection.from.isVisible && connection.to.isVisible) {
                renderer.drawConnection(canvas, connection);
            }
        }
        for (CardNode node : nodes) {
            if (node.isVisible) {
                renderer.drawCard(canvas, node);
            }
        }
        canvas.restore();

        renderer.drawResetButton(canvas, getWidth());

        // Logic fixed:
        // If allNodesExpanded is TRUE (Tree Open) -> Show Minus (Collapse)
        // If allNodesExpanded is FALSE (Tree Closed) -> Show Plus (Expand)
        if (allNodesExpanded) {
            renderer.drawCollapseAllButton(canvas, getWidth());
        } else {
            renderer.drawExpandAllButton(canvas, getWidth());
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return gestureHandler.onTouchEvent(event, getWidth(), getHeight());
    }

    @Override
    public void onOffsetChanged(float newOffsetX, float newOffsetY) {
        this.offsetX = newOffsetX;
        this.offsetY = newOffsetY;
    }

    @Override
    public void onScaleChanged(float newScale, float newOffsetX, float newOffsetY) {
        this.scale = newScale;
        this.offsetX = newOffsetX;
        this.offsetY = newOffsetY;
    }

    @Override
    public void onFling(float velocityX, float velocityY) {
        scroller.fling(
                (int) offsetX,
                (int) offsetY,
                (int) velocityX,
                (int) velocityY,
                Integer.MIN_VALUE,
                Integer.MAX_VALUE,
                Integer.MIN_VALUE,
                Integer.MAX_VALUE);
    }

    @Override
    public void onCardClick(CardNode node) {
        if (clickListener != null) {
            clickListener.onCardClick(node.key, node.fullValue, node.type);
        }
    }

    @Override
    public void onCollapseToggle(CardNode node) {
        if (collapsedNodes.contains(node)) collapsedNodes.remove(node);
        else collapsedNodes.add(node);
        updateNodeVisibility();
        CardNode root = nodes.get(0);
        layoutCalculator.calculateTreeLayout(root, root.x, root.y);
        invalidate();
    }

    // FlowChartCanvas.java

@Override
public void onResetClick() {
    // 1. Reset Camera
    scale = 1.0f;
    gestureHandler.setScale(scale);
    
    if (!nodes.isEmpty()) {
        CardNode rootNode = nodes.get(0);
        
        // Horizontal Centering Fix (using focusOnNode logic for X)
        float screenCenterX = getWidth() / 2f;
        float nodeCenterX = rootNode.getCenterX(LayoutConstants.CARD_WIDTH);
        
        // Calculate the target offsetX
        offsetX = screenCenterX - (nodeCenterX * scale);
        
        // Vertical Positioning (REVERTED to original logic)
        offsetY = LayoutConstants.TREE_TOP_MARGIN;
        
        gestureHandler.setOffset(offsetX, offsetY);
        
        // 2. Reset Tree (Collapse All except Root)
        collapsedNodes.clear();
        for (CardNode node : nodes) {
            if (node != rootNode) {
                List<CardNode> children = childrenMap.get(node);
                if (children != null && !children.isEmpty()) {
                    collapsedNodes.add(node);
                }
            }
        }
        allNodesExpanded = false;
        
        // 3. Update Layout
        updateNodeVisibility();
        layoutCalculator.calculateTreeLayout(rootNode, rootNode.x, rootNode.y);
    } else {
        // Fallback for empty state
        offsetX = getWidth() / 2f;
        offsetY = LayoutConstants.TREE_TOP_MARGIN;
        gestureHandler.setOffset(offsetX, offsetY);
    }
    
    invalidate();
    }

    @Override
    public void onToggleExpandCollapseClick() {
        if (nodes.isEmpty()) return;
        CardNode rootNode = nodes.get(0);

        if (allNodesExpanded) {
            // Logic: COLLAPSE ALL
            collapsedNodes.clear();
            for (CardNode node : nodes) {
                if (node != rootNode) {
                    List<CardNode> children = childrenMap.get(node);
                    if (children != null && !children.isEmpty()) collapsedNodes.add(node);
                }
            }
            allNodesExpanded = false;
        } else {
            // Logic: EXPAND ALL
            collapsedNodes.clear();
            allNodesExpanded = true;
        }
        updateNodeVisibility();
        layoutCalculator.calculateTreeLayout(rootNode, rootNode.x, rootNode.y);
        invalidate();
    }

    @Override
    public void stopFling() {
        stopScroller();
    }

    @Override
    public void onScaleUpdate(float newScale, float focusX, float focusY) {}
}
