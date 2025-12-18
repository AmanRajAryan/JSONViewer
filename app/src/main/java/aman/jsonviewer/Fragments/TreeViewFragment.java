package aman.jsonviewer;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TreeViewFragment extends Fragment implements ViewerActivity.SearchableFragment {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TreeAdapter adapter;
    private List<TreeNode> nodes = new ArrayList<>();
    private String jsonData;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static TreeViewFragment newInstance() {
        return new TreeViewFragment();
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_tree_view, container, false);

        if (getActivity() instanceof ViewerActivity) {
            jsonData = ((ViewerActivity) getActivity()).getJsonData();
        }

        recyclerView = view.findViewById(R.id.recyclerView);
        progressBar = view.findViewById(R.id.progressBar);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        if (jsonData != null) {
            parseJsonAsync();
        }

        return view;
    }

    private void parseJsonAsync() {
        progressBar.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);

        executor.execute(
                () -> {
                    List<TreeNode> tempNodes = new ArrayList<>();
                    try {
                        String trimmed = jsonData.trim();
                        boolean[] rootLines = new boolean[0];

                        if (trimmed.startsWith("{")) {
                            JSONObject json = new JSONObject(jsonData);
                            parseObject(json, "", 0, tempNodes, rootLines);
                        } else if (trimmed.startsWith("[")) {
                            JSONArray json = new JSONArray(jsonData);
                            parseArray(json, "", 0, tempNodes, rootLines);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    mainHandler.post(
                            () -> {
                                nodes.clear();
                                nodes.addAll(tempNodes);
                                adapter = new TreeAdapter(nodes);
                                recyclerView.setAdapter(adapter);
                                progressBar.setVisibility(View.GONE);
                                recyclerView.setVisibility(View.VISIBLE);

                                // Apply search if needed
                                if (getActivity() instanceof ViewerActivity) {
                                    String query =
                                            ((ViewerActivity) getActivity())
                                                    .getCurrentSearchQuery();
                                    if (query != null && !query.isEmpty()) {
                                        adapter.search(query);
                                    }
                                }
                            });
                });
    }

    private void parseObject(
            JSONObject obj, String key, int level, List<TreeNode> list, boolean[] parentLines) {
        TreeNode node = new TreeNode(key, "Object", level, NodeType.OBJECT);
        node.childCount = obj.length();
        node.verticalLines = parentLines;
        list.add(node);

        List<String> keysList = new ArrayList<>();
        Iterator<String> it = obj.keys();
        while (it.hasNext()) keysList.add(it.next());

        for (int i = 0; i < keysList.size(); i++) {
            String k = keysList.get(i);
            boolean isLast = (i == keysList.size() - 1);
            boolean[] nextLines = new boolean[level + 1];
            System.arraycopy(parentLines, 0, nextLines, 0, parentLines.length);
            nextLines[level] = !isLast;

            try {
                Object value = obj.get(k);
                if (value instanceof JSONObject) {
                    parseObject((JSONObject) value, k, level + 1, node.children, nextLines);
                } else if (value instanceof JSONArray) {
                    parseArray((JSONArray) value, k, level + 1, node.children, nextLines);
                } else {
                    TreeNode child =
                            new TreeNode(k, String.valueOf(value), level + 1, getNodeType(value));
                    child.verticalLines = nextLines;
                    node.children.add(child);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void parseArray(
            JSONArray arr, String key, int level, List<TreeNode> list, boolean[] parentLines) {
        TreeNode node = new TreeNode(key, "Array", level, NodeType.ARRAY);
        node.childCount = arr.length();
        node.verticalLines = parentLines;
        list.add(node);

        for (int i = 0; i < arr.length(); i++) {
            boolean isLast = (i == arr.length() - 1);
            boolean[] nextLines = new boolean[level + 1];
            System.arraycopy(parentLines, 0, nextLines, 0, parentLines.length);
            nextLines[level] = !isLast;

            try {
                Object value = arr.get(i);
                String indexKey = "[" + i + "]";
                if (value instanceof JSONObject) {
                    parseObject((JSONObject) value, indexKey, level + 1, node.children, nextLines);
                } else if (value instanceof JSONArray) {
                    parseArray((JSONArray) value, indexKey, level + 1, node.children, nextLines);
                } else {
                    TreeNode child =
                            new TreeNode(
                                    indexKey, String.valueOf(value), level + 1, getNodeType(value));
                    child.verticalLines = nextLines;
                    node.children.add(child);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private NodeType getNodeType(Object value) {
        if (value instanceof String) return NodeType.STRING;
        if (value instanceof Number) return NodeType.NUMBER;
        if (value instanceof Boolean) return NodeType.BOOLEAN;
        if (value == JSONObject.NULL) return NodeType.NULL;
        return NodeType.STRING;
    }

    @Override
    public void onSearch(String query) {
        if (adapter != null) adapter.search(query);
    }

    public TreeAdapter getAdapter() {
        return adapter;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    enum NodeType {
        OBJECT,
        ARRAY,
        STRING,
        NUMBER,
        BOOLEAN,
        NULL
    }

    static class TreeNode {
        String key;
        String value;
        int level;
        NodeType type;
        boolean expanded = false;
        List<TreeNode> children = new ArrayList<>();
        int childCount = 0;
        boolean[] verticalLines;
        boolean matchesSearch = false;
        boolean hasMatchingDescendant = false;

        TreeNode(String key, String value, int level, NodeType type) {
            this.key = key;
            this.value = value;
            this.level = level;
            this.type = type;
            this.verticalLines = new boolean[level];
        }
    }

    class TreeAdapter extends RecyclerView.Adapter<TreeAdapter.ViewHolder> {
        private List<TreeNode> displayNodes;
        private List<TreeNode> allNodes;
        private static final int INDENT_WIDTH_DP = 20;
        private int indentPx;
        private float density;
        private String currentSearchQuery = "";
        private List<Integer> searchMatches = new ArrayList<>();
        private int currentMatchIndex = -1;

        TreeAdapter(List<TreeNode> nodes) {
            this.allNodes = new ArrayList<>(nodes);
            this.displayNodes = new ArrayList<>();
            this.density = getResources().getDisplayMetrics().density;
            this.indentPx = (int) (INDENT_WIDTH_DP * density);
            rebuildDisplayList();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view =
                    LayoutInflater.from(parent.getContext())
                            .inflate(R.layout.item_tree_node, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            TreeNode node = displayNodes.get(position);

            ViewGroup.LayoutParams params = holder.indentationView.getLayoutParams();
            params.width = node.level * indentPx;
            holder.indentationView.setLayoutParams(params);

            if (node.level > 0) {
                boolean isLastChild = isLastChildOfParent(position, node.level);
                holder.indentationView.setBackground(
                        new IndentationDrawable(
                                node.level, indentPx, isLastChild, density, node.verticalLines));
            } else {
                holder.indentationView.setBackground(null);
            }

            if (node.type == NodeType.OBJECT || node.type == NodeType.ARRAY) {
                if (node.expanded && !node.children.isEmpty()) {
                    holder.iconWrapper.setBackground(new ParentLineDrawable(density));
                } else {
                    holder.iconWrapper.setBackground(null);
                }
                holder.icon.setImageResource(
                        node.expanded ? R.drawable.ic_tree_minus : R.drawable.ic_tree_plus);
                holder.icon.setColorFilter(null);
            } else {
                holder.iconWrapper.setBackground(new LeafLineDrawable(density));
                holder.icon.setImageResource(R.drawable.ic_tree_arrow);
                holder.icon.setColorFilter(null);
            }

            SpannableString spanned;
            if (!node.key.isEmpty()) {
                String text = node.key + ": " + node.value;
                spanned = new SpannableString(text);
                spanned.setSpan(
                        new ForegroundColorSpan(0xFF00BCD4),
                        0,
                        node.key.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                int valueColor = getColorForType(node.type);
                spanned.setSpan(
                        new ForegroundColorSpan(valueColor),
                        node.key.length() + 2,
                        text.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (!currentSearchQuery.isEmpty() && node.matchesSearch) {
                    highlightSearchInText(spanned, text, currentSearchQuery);
                }
            } else {
                spanned = new SpannableString(node.value);
                int valueColor = getColorForType(node.type);
                spanned.setSpan(
                        new ForegroundColorSpan(valueColor),
                        0,
                        node.value.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (!currentSearchQuery.isEmpty() && node.matchesSearch) {
                    highlightSearchInText(spanned, node.value, currentSearchQuery);
                }
            }
            holder.text.setText(spanned);

            holder.itemLayout.setOnClickListener(
                    v -> {
                        if (node.type == NodeType.OBJECT || node.type == NodeType.ARRAY) {
                            int clickedPosition = holder.getAdapterPosition();
                            if (clickedPosition != RecyclerView.NO_POSITION) {
                                toggleNodeAtPosition(clickedPosition);
                            }
                        }
                    });
        }

        private void highlightSearchInText(SpannableString spanned, String text, String query) {
            String lowerText = text.toLowerCase();
            String lowerQuery = query.toLowerCase();
            int index = 0;
            while ((index = lowerText.indexOf(lowerQuery, index)) != -1) {
                spanned.setSpan(
                        new BackgroundColorSpan(0xFFFFD54F),
                        index,
                        index + query.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                index += query.length();
            }
        }

        private void toggleNodeAtPosition(int position) {
            TreeNode node = displayNodes.get(position);
            node.expanded = !node.expanded;

            if (node.expanded) {
                // Expanding is already fast because addAll handles shifting efficiently
                List<TreeNode> childrenToAdd = new ArrayList<>();
                addVisibleNodes(node.children, childrenToAdd);
                displayNodes.addAll(position + 1, childrenToAdd);
                notifyItemChanged(position);
                notifyItemRangeInserted(position + 1, childrenToAdd.size());
            } else {
                // Collapsing
                int removeCount = countDescendants(position);

                if (removeCount > 0) {
                    // OPTIMIZATION: remove the whole range at once
                    // This prevents repeated array shifting which causes the freeze
                    displayNodes.subList(position + 1, position + 1 + removeCount).clear();

                    notifyItemChanged(position);
                    notifyItemRangeRemoved(position + 1, removeCount);
                }
            }

            if (!currentSearchQuery.isEmpty()) updateSearchMatches();
        }

        private int countDescendants(int parentPosition) {
            TreeNode parent = displayNodes.get(parentPosition);
            int count = 0;
            int checkLevel = parent.level;
            for (int i = parentPosition + 1; i < displayNodes.size(); i++) {
                TreeNode node = displayNodes.get(i);
                if (node.level <= checkLevel) break;
                count++;
            }
            return count;
        }

        private void addVisibleNodes(List<TreeNode> nodes, List<TreeNode> targetList) {
            for (TreeNode node : nodes) {
                targetList.add(node);
                if (node.expanded && !node.children.isEmpty())
                    addVisibleNodes(node.children, targetList);
            }
        }

        private void rebuildDisplayList() {
            displayNodes.clear();
            addVisibleNodes(allNodes, displayNodes);
        }

        private boolean isLastChildOfParent(int position, int currentLevel) {
            for (int i = position + 1; i < displayNodes.size(); i++) {
                TreeNode nextNode = displayNodes.get(i);
                if (nextNode.level == currentLevel) return false;
                if (nextNode.level < currentLevel) return true;
            }
            return true;
        }

        private int getColorForType(NodeType type) {
            switch (type) {
                case STRING:
                    return 0xFF4CAF50;
                case NUMBER:
                    return 0xFFFF9800;
                case BOOLEAN:
                    return 0xFF9C27B0;
                case NULL:
                    return 0xFF757575;
                case OBJECT:
                    return 0xFF2196F3;
                case ARRAY:
                    return 0xFFE91E63;
                default:
                    return 0xFFFFFFFF;
            }
        }

        @Override
        public int getItemCount() {
            return displayNodes.size();
        }

        void search(String query) {
            currentSearchQuery = query;
            if (query.isEmpty()) {
                clearSearchState(allNodes);
                rebuildDisplayList();
                searchMatches.clear();
                currentMatchIndex = -1;
                notifyDataSetChanged();
            } else {
                clearSearchState(allNodes);
                markSearchMatches(allNodes, query.toLowerCase());
                expandNodesWithMatches(allNodes);
                rebuildDisplayList();
                updateSearchMatches();
                if (!searchMatches.isEmpty()) {
                    currentMatchIndex = 0;
                    scrollToCurrentMatch();
                }
                notifyDataSetChanged();
            }
        }

        private void clearSearchState(List<TreeNode> nodes) {
            for (TreeNode node : nodes) {
                node.matchesSearch = false;
                node.hasMatchingDescendant = false;
                if (!node.children.isEmpty()) clearSearchState(node.children);
            }
        }

        private boolean markSearchMatches(List<TreeNode> nodes, String query) {
            boolean hasMatch = false;
            for (TreeNode node : nodes) {
                boolean nodeMatches =
                        node.key.toLowerCase().contains(query)
                                || node.value.toLowerCase().contains(query);
                boolean childrenMatch = false;
                if (!node.children.isEmpty())
                    childrenMatch = markSearchMatches(node.children, query);
                node.matchesSearch = nodeMatches;
                node.hasMatchingDescendant = childrenMatch;
                if (nodeMatches || childrenMatch) hasMatch = true;
            }
            return hasMatch;
        }

        private void expandNodesWithMatches(List<TreeNode> nodes) {
            for (TreeNode node : nodes) {
                if (node.hasMatchingDescendant) {
                    node.expanded = true;
                    expandNodesWithMatches(node.children);
                }
            }
        }

        private void updateSearchMatches() {
            searchMatches.clear();
            for (int i = 0; i < displayNodes.size(); i++) {
                if (displayNodes.get(i).matchesSearch) searchMatches.add(i);
            }
            if (currentMatchIndex >= searchMatches.size())
                currentMatchIndex = searchMatches.isEmpty() ? -1 : 0;
        }

        private void scrollToCurrentMatch() {
            if (currentMatchIndex >= 0 && currentMatchIndex < searchMatches.size()) {
                int position = searchMatches.get(currentMatchIndex);
                RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
                if (layoutManager instanceof LinearLayoutManager) {
                    ((LinearLayoutManager) layoutManager).scrollToPositionWithOffset(position, 100);
                }
            }
        }

        public void nextMatch() {
            if (searchMatches.isEmpty()) return;
            currentMatchIndex = (currentMatchIndex + 1) % searchMatches.size();
            scrollToCurrentMatch();
        }

        public void previousMatch() {
            if (searchMatches.isEmpty()) return;
            currentMatchIndex--;
            if (currentMatchIndex < 0) currentMatchIndex = searchMatches.size() - 1;
            scrollToCurrentMatch();
        }

        public int getCurrentMatchIndex() {
            return currentMatchIndex;
        }

        public int getTotalMatches() {
            return searchMatches.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            LinearLayout itemLayout;
            View indentationView;
            FrameLayout iconWrapper;
            ImageView icon;
            TextView text;

            ViewHolder(View view) {
                super(view);
                itemLayout = view.findViewById(R.id.itemLayout);
                indentationView = view.findViewById(R.id.indentationView);
                iconWrapper = view.findViewById(R.id.iconWrapper);
                icon = view.findViewById(R.id.icon);
                text = view.findViewById(R.id.text);
            }
        }
    }

    private static class ParentLineDrawable extends Drawable {
        private final Paint paint;
        private final float density;

        ParentLineDrawable(float density) {
            this.density = density;
            this.paint = new Paint();
            this.paint.setColor(0xFF444444);
            this.paint.setStyle(Paint.Style.STROKE);
            this.paint.setStrokeWidth(3f);
            this.paint.setAntiAlias(true);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            float width = getBounds().width();
            float height = getBounds().height();
            float centerX = width / 2f;
            float startY = 13f * density;
            canvas.drawLine(centerX, startY, centerX, height, paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    private static class LeafLineDrawable extends Drawable {
        private final Paint paint;
        private final float density;

        LeafLineDrawable(float density) {
            this.density = density;
            this.paint = new Paint();
            this.paint.setColor(0xFF444444);
            this.paint.setStyle(Paint.Style.STROKE);
            this.paint.setStrokeWidth(3f);
            this.paint.setAntiAlias(true);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            float width = getBounds().width();
            float centerX = width / 2f;
            float centerY = 13f * density;
            canvas.drawLine(0, centerY, centerX, centerY, paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    private static class IndentationDrawable extends Drawable {
        private final int levels;
        private final int stepPx;
        private final boolean isLastChild;
        private final float density;
        private final boolean[] verticalLines;
        private final Paint paint;
        private final Path path = new Path();

        public IndentationDrawable(
                int levels,
                int stepPx,
                boolean isLastChild,
                float density,
                boolean[] verticalLines) {
            this.levels = levels;
            this.stepPx = stepPx;
            this.isLastChild = isLastChild;
            this.density = density;
            this.verticalLines = verticalLines;
            this.paint = new Paint();
            this.paint.setColor(0xFF444444);
            this.paint.setStyle(Paint.Style.STROKE);
            this.paint.setStrokeWidth(3f);
            this.paint.setAntiAlias(true);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            int height = getBounds().height();
            for (int i = 0; i < levels - 1; i++) {
                if (i < verticalLines.length && verticalLines[i]) {
                    float x = (i * stepPx) + (stepPx / 2f);
                    canvas.drawLine(x, 0, x, height, paint);
                }
            }
            if (levels > 0) {
                int i = levels - 1;
                float x = (i * stepPx) + (stepPx / 2f);
                float centerY = 13f * density;
                float curveRadius = 10f * density;

                path.reset();
                path.moveTo(x, 0);

                if (isLastChild) {
                    path.lineTo(x, centerY - curveRadius);
                    path.quadTo(x, centerY, x + curveRadius, centerY);
                    path.lineTo(getBounds().width(), centerY);
                } else {
                    canvas.drawLine(x, 0, x, height, paint);
                    path.moveTo(x, centerY - curveRadius);
                    path.quadTo(x, centerY, x + curveRadius, centerY);
                    path.lineTo(getBounds().width(), centerY);
                }
                canvas.drawPath(path, paint);
            }
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
