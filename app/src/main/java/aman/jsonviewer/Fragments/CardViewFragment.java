package aman.jsonviewer;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CardViewFragment extends Fragment implements ViewerActivity.SearchableFragment {
    
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private CardAdapter adapter;
    private List<CardItem> items = new ArrayList<>();
    private String jsonData;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    public static CardViewFragment newInstance() {
        return new CardViewFragment();
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_card_view, container, false);
        
        if (getActivity() instanceof ViewerActivity) {
            jsonData = ((ViewerActivity) getActivity()).getJsonData();
        }
        
        recyclerView = view.findViewById(R.id.recyclerView);
        progressBar = view.findViewById(R.id.progressBar);
        
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new CardAdapter(items);
        recyclerView.setAdapter(adapter);
        
        if (jsonData != null) {
            parseJsonAsync();
        }
        
        return view;
    }
    
    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof ViewerActivity) {
            String currentQuery = ((ViewerActivity) getActivity()).getCurrentSearchQuery();
            if (currentQuery != null && !currentQuery.isEmpty() && adapter != null) {
                recyclerView.postDelayed(() -> {
                    if (adapter != null && !items.isEmpty()) {
                        adapter.filter(currentQuery);
                    }
                }, 100);
            }
        }
    }
    
    private void parseJsonAsync() {
        progressBar.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        
        executor.execute(() -> {
            List<CardItem> tempItems = new ArrayList<>();
            try {
                String trimmed = jsonData.trim();
                if (trimmed.startsWith("{")) {
                    JSONObject json = new JSONObject(jsonData);
                    parseObject(json, "", tempItems, 0);
                } else if (trimmed.startsWith("[")) {
                    JSONArray json = new JSONArray(jsonData);
                    parseArray(json, "", tempItems, 0);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            
            mainHandler.post(() -> {
                items.clear();
                items.addAll(tempItems);
                adapter.updateAllItems(tempItems);
                adapter.notifyDataSetChanged();
                progressBar.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
                
                if (getActivity() instanceof ViewerActivity) {
                    String currentQuery = ((ViewerActivity) getActivity()).getCurrentSearchQuery();
                    if (currentQuery != null && !currentQuery.isEmpty()) {
                        adapter.filter(currentQuery);
                    }
                }
            });
        });
    }
    
    private void parseObject(JSONObject obj, String prefix, List<CardItem> list, int depth) {
        if (depth > 10) return;
        
        Iterator<String> keys = obj.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
            try {
                Object value = obj.get(key);
                if (value instanceof JSONObject) {
                    parseObject((JSONObject) value, fullKey, list, depth + 1);
                } else if (value instanceof JSONArray) {
                    parseArray((JSONArray) value, fullKey, list, depth + 1);
                } else {
                    list.add(new CardItem(fullKey, String.valueOf(value), 
                        getValueType(value)));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    private void parseArray(JSONArray arr, String prefix, List<CardItem> list, int depth) {
        if (depth > 10) return;
        
        int maxItems = Math.min(arr.length(), 100);
        for (int i = 0; i < maxItems; i++) {
            String fullKey = prefix + "[" + i + "]";
            try {
                Object value = arr.get(i);
                if (value instanceof JSONObject) {
                    parseObject((JSONObject) value, fullKey, list, depth + 1);
                } else if (value instanceof JSONArray) {
                    parseArray((JSONArray) value, fullKey, list, depth + 1);
                } else {
                    list.add(new CardItem(fullKey, String.valueOf(value), 
                        getValueType(value)));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        if (arr.length() > maxItems) {
            list.add(new CardItem(prefix + "[...]", 
                "... " + (arr.length() - maxItems) + " more items", "Info"));
        }
    }
    
    private String getValueType(Object value) {
        if (value instanceof String) return "String";
        if (value instanceof Number) return "Number";
        if (value instanceof Boolean) return "Boolean";
        if (value == JSONObject.NULL) return "Null";
        return "Unknown";
    }
    
    @Override
    public void onSearch(String query) {
        if (adapter != null) {
            adapter.filter(query);
        }
    }
    
    public CardAdapter getAdapter() {
        return adapter;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
    
    static class CardItem {
        String key;
        String value;
        String type;
        boolean matchesSearch = false; 
        
        CardItem(String key, String value, String type) {
            this.key = key;
            this.value = value;
            this.type = type;
        }
    }
    
    class CardAdapter extends RecyclerView.Adapter<CardAdapter.ViewHolder> {
        private List<CardItem> displayItems;
        private List<CardItem> allItems;
        
        private String currentSearchQuery = "";
        private final List<Integer> searchMatches = new ArrayList<>();
        private int currentMatchIndex = -1;
        
        CardAdapter(List<CardItem> items) {
            this.allItems = items;
            this.displayItems = items;
        }
        
        void updateAllItems(List<CardItem> newItems) {
            this.allItems = newItems;
            if (currentSearchQuery.isEmpty()) {
                this.displayItems = newItems;
            }
        }
        
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_card, parent, false);
            return new ViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            CardItem item = displayItems.get(position);
            
            holder.keyText.setText(item.key);
            holder.valueText.setText(item.value);
            holder.typeText.setText(item.type);
            
            int color = getTypeColor(item.type);
            holder.typeText.setTextColor(color);
            
            if (!searchMatches.isEmpty() && currentMatchIndex >= 0 && 
                currentMatchIndex < searchMatches.size() &&
                searchMatches.get(currentMatchIndex) == position) {
                holder.card.setStrokeColor(0xFFFFEB3B);
                holder.card.setStrokeWidth(6);
            } else if (item.matchesSearch) {
                holder.card.setStrokeColor(0xFFFFD54F);
                holder.card.setStrokeWidth(4);
            } else {
                holder.card.setStrokeColor(0x00000000);
                holder.card.setStrokeWidth(0);
            }
            
            holder.card.setOnLongClickListener(v -> {
                ClipboardManager clipboard = (ClipboardManager) 
                    getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText(item.key, item.value);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(getContext(), "Copied: " + item.key, 
                    Toast.LENGTH_SHORT).show();
                return true;
            });
        }
        
        private int getTypeColor(String type) {
            switch (type) {
                case "String": return 0xFF4CAF50;
                case "Number": return 0xFFFF9800;
                case "Boolean": return 0xFF9C27B0;
                case "Null": return 0xFF757575;
                case "Info": return 0xFF2196F3;
                default: return 0xFFFFFFFF;
            }
        }
        
        @Override
        public int getItemCount() {
            return displayItems.size();
        }
        
        void filter(String query) {
            currentSearchQuery = query;
            
            if (query.isEmpty()) {
                for (CardItem item : allItems) {
                    item.matchesSearch = false;
                }
                displayItems = allItems;
                searchMatches.clear();
                currentMatchIndex = -1;
            } else {
                List<CardItem> filtered = new ArrayList<>();
                String lowerQuery = query.toLowerCase();
                for (CardItem item : allItems) {
                    boolean matches = item.key.toLowerCase().contains(lowerQuery) ||
                        item.value.toLowerCase().contains(lowerQuery);
                    item.matchesSearch = matches;
                    if (matches) {
                        filtered.add(item);
                    }
                }
                displayItems = filtered;
                
                updateSearchMatches();
                if (!searchMatches.isEmpty()) {
                    currentMatchIndex = 0;
                    scrollToCurrentMatch();
                }
            }
            notifyDataSetChanged();
        }
        
        private void updateSearchMatches() {
            searchMatches.clear();
            for (int i = 0; i < displayItems.size(); i++) {
                if (displayItems.get(i).matchesSearch) {
                    searchMatches.add(i);
                }
            }
        }
        
        private void scrollToCurrentMatch() {
            if (currentMatchIndex >= 0 && currentMatchIndex < searchMatches.size()) {
                int position = searchMatches.get(currentMatchIndex);
                RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
                if (layoutManager instanceof LinearLayoutManager) {
                    ((LinearLayoutManager) layoutManager).scrollToPositionWithOffset(position, 100);
                }
                notifyDataSetChanged();
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
            if (currentMatchIndex < 0) {
                currentMatchIndex = searchMatches.size() - 1;
            }
            scrollToCurrentMatch();
        }
        
        public int getCurrentMatchIndex() {
            return currentMatchIndex;
        }
        
        public int getTotalMatches() {
            return searchMatches.size();
        }
        
        class ViewHolder extends RecyclerView.ViewHolder {
            MaterialCardView card;
            TextView keyText;
            TextView valueText;
            TextView typeText;
            
            ViewHolder(View view) {
                super(view);
                card = (MaterialCardView) view;
                keyText = view.findViewById(R.id.keyText);
                valueText = view.findViewById(R.id.valueText);
                typeText = view.findViewById(R.id.typeText);
            }
        }
    }
}
