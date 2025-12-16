package aman.jsonviewer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Builds a tree structure from JSON data
 */
public class JSONTreeBuilder {
    private List<CardNode> nodes;
    private List<Connection> connections;
    private Map<CardNode, List<CardNode>> childrenMap;

    // Safety limit for text preview size to prevent OOM on massive arrays
    private static final int PREVIEW_TEXT_LIMIT = 5000;

    public JSONTreeBuilder(List<CardNode> nodes, 
                           List<Connection> connections,
                           Map<CardNode, List<CardNode>> childrenMap) {
        this.nodes = nodes;
        this.connections = connections;
        this.childrenMap = childrenMap;
    }

    public CardNode buildFromJSON(JSONObject json) throws JSONException {
        CardNode rootNode = new CardNode("Root", "Object", json.toString(), "Object", 0, 0, 0);
        nodes.add(rootNode);
        childrenMap.put(rootNode, new ArrayList<>());
        
        buildObjectNode(json, rootNode);
        return rootNode;
    }

    public CardNode buildFromJSONArray(JSONArray jsonArray) throws JSONException {
        CardNode rootNode = new CardNode("Root", "Array[" + jsonArray.length() + "]", jsonArray.toString(), "Array", 0, 0, 0);
        nodes.add(rootNode);
        childrenMap.put(rootNode, new ArrayList<>());
        
        buildArrayNode(jsonArray, rootNode);
        return rootNode;
    }

    private void buildObjectNode(JSONObject json, CardNode parent) throws JSONException {
        Iterator<String> keys = json.keys();
        List<CardNode> children = new ArrayList<>();

        while (keys.hasNext()) {
            String key = keys.next();
            Object value = json.get(key);

            String type = getValueType(value);
            String displayValue = formatValue(value); 
            String fullValue = getRawValue(value);    

            CardNode childNode = new CardNode(key, displayValue, fullValue, type, 0, 0, parent.level + 1);
            nodes.add(childNode);
            children.add(childNode);
            childrenMap.put(childNode, new ArrayList<>());

            Connection connection = new Connection(parent, childNode);
            connections.add(connection);

            if (value instanceof JSONObject) {
                buildObjectNode((JSONObject) value, childNode);
            } else if (value instanceof JSONArray) {
                buildArrayNode((JSONArray) value, childNode);
            }
        }

        childrenMap.put(parent, children);
    }

    private void buildArrayNode(JSONArray jsonArray, CardNode parent) throws JSONException {
        // Limit visual nodes to 8 to keep the chart performant
        int maxItems = Math.min(jsonArray.length(), 8);
        List<CardNode> children = new ArrayList<>();

        for (int i = 0; i < maxItems; i++) {
            Object value = jsonArray.get(i);

            String type = getValueType(value);
            String displayValue = formatValue(value);
            String fullValue = getRawValue(value);
            String key = "[" + i + "]";

            CardNode childNode = new CardNode(key, displayValue, fullValue, type, 0, 0, parent.level + 1);
            nodes.add(childNode);
            children.add(childNode);
            childrenMap.put(childNode, new ArrayList<>());

            Connection connection = new Connection(parent, childNode);
            connections.add(connection);

            if (value instanceof JSONObject) {
                buildObjectNode((JSONObject) value, childNode);
            } else if (value instanceof JSONArray) {
                buildArrayNode((JSONArray) value, childNode);
            }
        }

        // Handle the "Truncated" items
        if (jsonArray.length() > maxItems) {
            int remainingCount = jsonArray.length() - maxItems;
            
            // 1. Build the detailed explanation string for the BottomSheet
            StringBuilder hiddenContent = new StringBuilder();
            hiddenContent.append("⚠️ There were too many items to display graphically.\n");
            hiddenContent.append("Showing indices " + maxItems + " to " + (jsonArray.length() - 1) + ":\n\n");
            
            hiddenContent.append("[\n");
            
            // Loop through hidden items to build the preview
            for (int i = maxItems; i < jsonArray.length(); i++) {
                // Safety Check: Stop if text gets too huge
                if (hiddenContent.length() > PREVIEW_TEXT_LIMIT) {
                    hiddenContent.append("\n    ... (and " + (jsonArray.length() - i) + " more items)");
                    break;
                }
                
                try {
                    Object val = jsonArray.get(i);
                    hiddenContent.append("    "); // Indent
                    hiddenContent.append(String.valueOf(val));
                    
                    if (i < jsonArray.length() - 1) {
                        hiddenContent.append(",\n");
                    }
                } catch (Exception e) {
                    hiddenContent.append("    <error reading item>");
                }
            }
            hiddenContent.append("\n]");

            // Correctly formatted constructor call
            CardNode moreNode = new CardNode(
                "...",
                "+" + remainingCount + " more",
                hiddenContent.toString(), // Pass the detailed preview here
                "Info",
                0,
                0,
                parent.level + 1
            );
            
            nodes.add(moreNode);
            children.add(moreNode);
            childrenMap.put(moreNode, new ArrayList<>());

            Connection connection = new Connection(parent, moreNode);
            connections.add(connection);
        }

        childrenMap.put(parent, children);
    }

    private String getValueType(Object value) {
        if (value instanceof JSONObject) return "Object";
        if (value instanceof JSONArray) return "Array";
        if (value instanceof String) return "String";
        if (value instanceof Number) return "Number";
        if (value instanceof Boolean) return "Boolean";
        if (value == JSONObject.NULL) return "Null";
        return "Unknown";
    }

    private String getRawValue(Object value) {
        if (value == JSONObject.NULL) return "null";
        return String.valueOf(value);
    }

    private String formatValue(Object value) {
        if (value instanceof JSONObject) {
            JSONObject obj = (JSONObject) value;
            return "{ " + obj.length() + " }";
        } else if (value instanceof JSONArray) {
            JSONArray arr = (JSONArray) value;
            return "[ " + arr.length() + " ]";
        } else if (value == JSONObject.NULL) {
            return "null";
        } else {
            String str = value.toString();
            if (str.length() > 25) {
                return str.substring(0, 22) + "...";
            }
            return str;
        }
    }
}
