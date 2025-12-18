package aman.jsonviewer;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;

public class ViewerActivity extends AppCompatActivity {

    private JsonLoader jsonLoader;
    private FragmentController fragmentController;
    private SearchNavigator searchNavigator;
    private String jsonData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_viewer);
        
        // 1. UI Setup
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("JSON Viewer");

        // 2. Initialize Helpers
        jsonLoader = new JsonLoader(this);
        fragmentController = new FragmentController(
            getSupportFragmentManager(), 
            R.id.fragmentContainer, 
            findViewById(R.id.tabLayout)
        );
        searchNavigator = new SearchNavigator(this, fragmentController);

        // 3. Load Data
        jsonData = jsonLoader.loadJson(getIntent());
        
        if (jsonData == null) {
            Toast.makeText(this, "No JSON data found", Toast.LENGTH_SHORT).show();
        }

        // 4. Restore State & Setup Listeners
        fragmentController.restoreState(savedInstanceState);
        searchNavigator.restoreState(savedInstanceState);
        
        // Sync search visibility when tabs change
        fragmentController.setOnFragmentChangedListener(() -> {
            searchNavigator.updateVisibility();
            setupFragmentCallbacks();
        });
        
        setupFragmentCallbacks();
        logMemoryUsage();
    }
    
    // Wire up callbacks for vttyView or other dynamic fragments
    private void setupFragmentCallbacks() {
        Fragment current = fragmentController.getCurrentFragment();
        if (current instanceof PrettyViewFragment) {
            ((PrettyViewFragment) current).setCounterUpdateCallback(
                () -> searchNavigator.updateCounter()
            );
        }
        
        // If there is an active search, re-apply it to the new fragment
        String query = searchNavigator.getCurrentQuery();
        if (current instanceof SearchableFragment && !query.isEmpty()) {
            // Post to ensure view is created
            findViewById(R.id.fragmentContainer).post(() -> 
                ((SearchableFragment) current).onSearch(query)
            );
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.viewer_menu, menu);
        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        
        searchNavigator.attachToSearchView(searchView);
        
        // Expand if there was a previous state
        if (!searchNavigator.getCurrentQuery().isEmpty()) {
            searchItem.expandActionView();
        }
        
        return true;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        fragmentController.saveState(outState);
        searchNavigator.saveState(outState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) {
            fragmentController.cleanup();
            jsonLoader.clear();
            System.gc();
        }
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            fragmentController.trimMemory();
            System.gc();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.action_copy) {
            copyToClipboard();
            return true;
        } else if (id == R.id.action_share) {
            shareJson();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void copyToClipboard() {
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("JSON", jsonData);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
        } catch (Exception err) {
            Toast.makeText(this, err.toString(), Toast.LENGTH_SHORT).show();
        }
    }

    private void shareJson() {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, jsonData);
        startActivity(Intent.createChooser(shareIntent, "Share JSON"));
    }
    
    // --- Getters for Fragments to access if needed ---

    public String getJsonData() {
        return jsonData;
    }
    
    public String getCurrentSearchQuery() {
        return searchNavigator.getCurrentQuery();
    }
    
    public CachedData getCachedData(String key) {
        return fragmentController.getCachedData(key);
    }
    
    public void setCachedData(String key, String formattedJson, android.text.SpannableStringBuilder highlightedText) {
        fragmentController.setCachedData(key, new CachedData(formattedJson, highlightedText));
    }

    public interface SearchableFragment {
        void onSearch(String query);
    }
    
    private void logMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / (1024 * 1024);
        long totalMemory = runtime.totalMemory() / (1024 * 1024);
        Log.d("MEMORY_CHECK", "Max: " + maxMemory + "MB, Current: " + totalMemory + "MB");
    }
}
