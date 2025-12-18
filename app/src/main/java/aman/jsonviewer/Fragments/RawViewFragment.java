package aman.jsonviewer;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RawViewFragment extends Fragment implements ViewerActivity.SearchableFragment {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private static final int CHUNK_SIZE = 3000;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static RawViewFragment newInstance() {
        return new RawViewFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_raw_view, container, false);

        recyclerView = view.findViewById(R.id.recyclerView);
        progressBar = view.findViewById(R.id.progressBar);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemViewCacheSize(10);

        String jsonData = null;
        if (getActivity() instanceof ViewerActivity) {
            jsonData = ((ViewerActivity) getActivity()).getJsonData();
        }

        if (jsonData != null) {
            loadDataAsync(jsonData);
        }

        return view;
    }

    private void loadDataAsync(String jsonData) {
        progressBar.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);

        executor.execute(() -> {
            List<String> chunks = splitString(jsonData, CHUNK_SIZE);

            mainHandler.post(() -> {
                if (recyclerView != null) {
                    recyclerView.setAdapter(new TextChunkAdapter(chunks));
                    progressBar.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    private List<String> splitString(String text, int interval) {
        List<String> chunks = new ArrayList<>();
        int length = text.length();
        for (int i = 0; i < length; i += interval) {
            chunks.add(text.substring(i, Math.min(length, i + interval)));
        }
        return chunks;
    }

    @Override
    public void onSearch(String query) {
        // Search not implemented for raw view
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cleanup();
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
        cleanup();
    }
    
    private void cleanup() {
        if (recyclerView != null) {
            recyclerView.setAdapter(null);
            recyclerView = null;
        }
    }

    private static class TextChunkAdapter extends RecyclerView.Adapter<TextChunkAdapter.ChunkViewHolder> {
        private final List<String> chunks;

        TextChunkAdapter(List<String> chunks) {
            this.chunks = chunks;
        }

        @NonNull
        @Override
        public ChunkViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView textView = new TextView(parent.getContext());
            textView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            
            textView.setTextColor(0xFFFFFFFF);
            textView.setTextSize(12);
            textView.setTypeface(android.graphics.Typeface.MONOSPACE);
            textView.setPadding(32, 0, 32, 0); 
            textView.setTextIsSelectable(true);
            
            return new ChunkViewHolder(textView);
        }

        @Override
        public void onBindViewHolder(@NonNull ChunkViewHolder holder, int position) {
            holder.text.setText(chunks.get(position));
        }

        @Override
        public int getItemCount() {
            return chunks.size();
        }
        
        @Override
        public void onViewRecycled(@NonNull ChunkViewHolder holder) {
            super.onViewRecycled(holder);
            holder.text.setText(null);
        }

        static class ChunkViewHolder extends RecyclerView.ViewHolder {
            TextView text;
            ChunkViewHolder(TextView itemView) {
                super(itemView);
                this.text = itemView;
            }
        }
    }
}
