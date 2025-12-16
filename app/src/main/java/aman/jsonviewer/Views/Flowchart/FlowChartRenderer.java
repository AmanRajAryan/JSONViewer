package aman.jsonviewer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import androidx.core.content.ContextCompat;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FlowChartRenderer {
    private Paint cardPaint;
    private Paint cardStrokePaint;
    private Paint textPaint;
    private Paint linePaint;
    private Paint arrowPaint;
    private Paint collapseBadgePaint;
    private Paint resetButtonPaint;
    private Paint resetButtonStrokePaint;
    private Paint iconPaint; 
    private Paint highlightPaint;

    private Drawable resetIconDrawable;

    private final Path connectionPath = new Path();
    private final Path arrowPath = new Path();
    private final RectF reusableRect = new RectF();
    private final Paint reusableBorderPaint = new Paint();

    private Map<CardNode, List<CardNode>> childrenMap;
    private Set<CardNode> collapsedNodes;
    private Set<CardNode> highlightedNodes = new HashSet<>();

    public FlowChartRenderer(Context context, 
                             Map<CardNode, List<CardNode>> childrenMap, 
                             Set<CardNode> collapsedNodes) {
        this.childrenMap = childrenMap;
        this.collapsedNodes = collapsedNodes;
        resetIconDrawable = ContextCompat.getDrawable(context, R.drawable.ic_reset);
        initPaints();
    }
    
    public void setHighlightedNodes(Set<CardNode> nodes) {
        this.highlightedNodes = nodes;
    }

    private void initPaints() {
        cardPaint = new Paint();
        cardPaint.setAntiAlias(true);
        cardPaint.setColor(0xFF1E1E1E);
        cardPaint.setStyle(Paint.Style.FILL);

        cardStrokePaint = new Paint();
        cardStrokePaint.setAntiAlias(true);
        cardStrokePaint.setColor(0xFF00BCD4);
        cardStrokePaint.setStyle(Paint.Style.STROKE);
        cardStrokePaint.setStrokeWidth(3);
        
        reusableBorderPaint.set(cardStrokePaint);

        textPaint = new Paint();
        textPaint.setAntiAlias(true);
        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextSize(32);
        textPaint.setTypeface(android.graphics.Typeface.MONOSPACE);

        linePaint = new Paint();
        linePaint.setAntiAlias(true);
        linePaint.setColor(0xFF00BCD4);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(3);

        arrowPaint = new Paint();
        arrowPaint.setAntiAlias(true);
        arrowPaint.setColor(0xFF00BCD4);
        arrowPaint.setStyle(Paint.Style.FILL);

        collapseBadgePaint = new Paint();
        collapseBadgePaint.setAntiAlias(true);
        collapseBadgePaint.setColor(0xFF00BCD4);
        collapseBadgePaint.setStyle(Paint.Style.FILL);

        resetButtonPaint = new Paint();
        resetButtonPaint.setAntiAlias(true);
        resetButtonPaint.setColor(0xFF1E1E1E);
        resetButtonPaint.setStyle(Paint.Style.FILL);

        resetButtonStrokePaint = new Paint();
        resetButtonStrokePaint.setAntiAlias(true);
        resetButtonStrokePaint.setColor(0xFF00BCD4);
        resetButtonStrokePaint.setStyle(Paint.Style.STROKE);
        resetButtonStrokePaint.setStrokeWidth(3);

        iconPaint = new Paint();
        iconPaint.setAntiAlias(true);
        iconPaint.setColor(0xFF00BCD4);
        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeWidth(4);
        iconPaint.setStrokeCap(Paint.Cap.ROUND);
        
        highlightPaint = new Paint();
        highlightPaint.setAntiAlias(true);
        highlightPaint.setColor(0xFFFFD700); 
        highlightPaint.setStyle(Paint.Style.STROKE);
        highlightPaint.setStrokeWidth(8);
    }

    public void drawConnection(Canvas canvas, Connection connection) {
        float x1 = connection.from.getCenterX(LayoutConstants.CARD_WIDTH);
        float y1 = connection.from.getBottomY(LayoutConstants.CARD_HEIGHT);
        float x2 = connection.to.getCenterX(LayoutConstants.CARD_WIDTH);
        float y2 = connection.to.y;

        connectionPath.reset();
        connectionPath.moveTo(x1, y1);

        float midY = (y1 + y2) / 2f;
        connectionPath.cubicTo(x1, midY, x2, midY, x2, y2);

        canvas.drawPath(connectionPath, linePaint);
        drawArrow(canvas, x2, y2);
    }

    private void drawArrow(Canvas canvas, float x, float y) {
        arrowPath.reset();
        arrowPath.moveTo(x, y);
        arrowPath.lineTo(x - 10, y - 15);
        arrowPath.lineTo(x + 10, y - 15);
        arrowPath.close();
        canvas.drawPath(arrowPath, arrowPaint);
    }

    public void drawCard(Canvas canvas, CardNode node) {
        reusableRect.set(
            node.x, 
            node.y, 
            node.x + LayoutConstants.CARD_WIDTH, 
            node.y + LayoutConstants.CARD_HEIGHT
        );

        canvas.drawRoundRect(reusableRect, LayoutConstants.CARD_CORNER_RADIUS, 
                            LayoutConstants.CARD_CORNER_RADIUS, cardPaint);

        if (highlightedNodes.contains(node)) {
            canvas.drawRoundRect(reusableRect, LayoutConstants.CARD_CORNER_RADIUS, 
                            LayoutConstants.CARD_CORNER_RADIUS, highlightPaint);
        } else {
            reusableBorderPaint.setColor(getTypeColor(node.type));
            canvas.drawRoundRect(reusableRect, LayoutConstants.CARD_CORNER_RADIUS, 
                            LayoutConstants.CARD_CORNER_RADIUS, reusableBorderPaint);
        }

        textPaint.setTextSize(34);
        textPaint.setFakeBoldText(true);
        textPaint.setColor(0xFFFFFFFF);
        
        String displayKey = node.key;
        float padding = 40f; 
        float maxWidth = LayoutConstants.CARD_WIDTH - padding;
        
        if (textPaint.measureText(displayKey) > maxWidth) {
            String ellipsis = "...";
            float ellipsisWidth = textPaint.measureText(ellipsis);
            float availableWidth = maxWidth - ellipsisWidth;
            int count = textPaint.breakText(node.key, true, availableWidth, null);
            if (count > 0 && count < node.key.length()) {
                displayKey = node.key.substring(0, count) + ellipsis;
            }
        }
        
        float keyWidth = textPaint.measureText(displayKey);
        canvas.drawText(displayKey, node.x + (LayoutConstants.CARD_WIDTH - keyWidth) / 2f, 
                       node.y + 40, textPaint);

        textPaint.setTextSize(26);
        textPaint.setFakeBoldText(false);
        textPaint.setColor(0xFF888888);
        float typeWidth = textPaint.measureText(node.type);
        canvas.drawText(node.type, node.x + (LayoutConstants.CARD_WIDTH - typeWidth) / 2f, 
                       node.y + 68, textPaint);

        List<CardNode> children = childrenMap.get(node);
        if (children != null && !children.isEmpty()) {
            drawCollapseBadge(canvas, node);
        }
    }

    private void drawCollapseBadge(Canvas canvas, CardNode node) {
        float badgeX = node.x + LayoutConstants.CARD_WIDTH - LayoutConstants.COLLAPSE_BADGE_SIZE - 8;
        float badgeY = node.y + 8;

        canvas.drawCircle(
            badgeX + LayoutConstants.COLLAPSE_BADGE_SIZE / 2f,
            badgeY + LayoutConstants.COLLAPSE_BADGE_SIZE / 2f,
            LayoutConstants.COLLAPSE_BADGE_SIZE / 2f,
            collapseBadgePaint
        );

        textPaint.setTextSize(24);
        textPaint.setFakeBoldText(true);
        textPaint.setColor(0xFF000000);
        String symbol = collapsedNodes.contains(node) ? "+" : "−";
        float symbolWidth = textPaint.measureText(symbol);
        canvas.drawText(
            symbol,
            badgeX + (LayoutConstants.COLLAPSE_BADGE_SIZE - symbolWidth) / 2f,
            badgeY + LayoutConstants.COLLAPSE_BADGE_SIZE / 2f + 8,
            textPaint
        );
    }

    public void drawResetButton(Canvas canvas, int viewWidth) {
        float buttonX = viewWidth - LayoutConstants.RESET_BUTTON_SIZE - LayoutConstants.RESET_BUTTON_MARGIN;
        float buttonY = LayoutConstants.RESET_BUTTON_MARGIN;
        float centerX = buttonX + LayoutConstants.RESET_BUTTON_SIZE / 2f;
        float centerY = buttonY + LayoutConstants.RESET_BUTTON_SIZE / 2f;

        canvas.drawCircle(centerX, centerY, LayoutConstants.RESET_BUTTON_SIZE / 2f, resetButtonPaint);
        canvas.drawCircle(centerX, centerY, LayoutConstants.RESET_BUTTON_SIZE / 2f, resetButtonStrokePaint);

        if (resetIconDrawable != null) {
            int iconSize = (int) (LayoutConstants.RESET_BUTTON_SIZE * 0.6f);
            int halfSize = iconSize / 2;
            resetIconDrawable.setBounds(
                (int) centerX - halfSize, (int) centerY - halfSize,
                (int) centerX + halfSize, (int) centerY + halfSize
            );
            resetIconDrawable.draw(canvas);
        }
    }

    // RENAMED & FIXED: Now draws a PLUS (+) icon
    public void drawExpandAllButton(Canvas canvas, int viewWidth) {
        float buttonX = viewWidth - LayoutConstants.RESET_BUTTON_SIZE - LayoutConstants.RESET_BUTTON_MARGIN;
        float buttonY = LayoutConstants.RESET_BUTTON_MARGIN + LayoutConstants.RESET_BUTTON_SIZE + 16;
        drawButtonBase(canvas, buttonX, buttonY);
        float centerX = buttonX + LayoutConstants.RESET_BUTTON_SIZE / 2f;
        float centerY = buttonY + LayoutConstants.RESET_BUTTON_SIZE / 2f;
        
        iconPaint.setStrokeWidth(6);
        // Horizontal Line
        canvas.drawLine(centerX - 20, centerY, centerX + 20, centerY, iconPaint);
        // Vertical Line (Makes it a Plus)
        canvas.drawLine(centerX, centerY - 20, centerX, centerY + 20, iconPaint);
        iconPaint.setStrokeWidth(4);
    }

    // RENAMED & FIXED: Now draws a MINUS (-) icon
    public void drawCollapseAllButton(Canvas canvas, int viewWidth) {
        float buttonX = viewWidth - LayoutConstants.RESET_BUTTON_SIZE - LayoutConstants.RESET_BUTTON_MARGIN;
        float buttonY = LayoutConstants.RESET_BUTTON_MARGIN + LayoutConstants.RESET_BUTTON_SIZE + 16;
        drawButtonBase(canvas, buttonX, buttonY);
        float centerX = buttonX + LayoutConstants.RESET_BUTTON_SIZE / 2f;
        float centerY = buttonY + LayoutConstants.RESET_BUTTON_SIZE / 2f;
        
        iconPaint.setStrokeWidth(6);
        // Horizontal Line Only (Makes it a Minus)
        canvas.drawLine(centerX - 20, centerY, centerX + 20, centerY, iconPaint);
        iconPaint.setStrokeWidth(4); 
    }

    private void drawButtonBase(Canvas canvas, float x, float y) {
        canvas.drawCircle(
            x + LayoutConstants.RESET_BUTTON_SIZE / 2f,
            y + LayoutConstants.RESET_BUTTON_SIZE / 2f,
            LayoutConstants.RESET_BUTTON_SIZE / 2f,
            resetButtonPaint
        );
        canvas.drawCircle(
            x + LayoutConstants.RESET_BUTTON_SIZE / 2f,
            y + LayoutConstants.RESET_BUTTON_SIZE / 2f,
            LayoutConstants.RESET_BUTTON_SIZE / 2f,
            resetButtonStrokePaint
        );
    }

    private int getTypeColor(String type) {
        switch (type) {
            case "Object": return 0xFF00BCD4;
            case "Array": return 0xFF9C27B0;
            case "String": return 0xFF4CAF50;
            case "Number": return 0xFFFF9800;
            case "Boolean": return 0xFFF44336;
            case "Null": return 0xFF757575;
            case "Info": return 0xFF2196F3;
            default: return 0xFF00BCD4;
        }
    }
}