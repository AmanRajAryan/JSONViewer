package aman.jsonviewer;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;

import com.google.android.material.tabs.TabLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ViewerActivity extends AppCompatActivity {

    private JsonLoader jsonLoader;
    private FragmentController fragmentController;
    private SearchNavigator searchNavigator;
    private String jsonData;

    // Async handling components
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ProgressBar loadingSpinner;
    private View fragmentContainerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_viewer);

        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("JSON Viewer");
        }

        // Initialize UI elements for loading state
        loadingSpinner = findViewById(R.id.loadingSpinner);
        fragmentContainerView = findViewById(R.id.fragmentContainer);

        jsonLoader = new JsonLoader(this);
        fragmentController =
                new FragmentController(
                        getSupportFragmentManager(),
                        R.id.fragmentContainer,
                        findViewById(R.id.tabLayout));
        searchNavigator = new SearchNavigator(this, fragmentController);

        // Start asynchronous loading
        loadDataAsync(savedInstanceState);
    }

    private void loadDataAsync(Bundle savedInstanceState) {
        // 1. Show Spinner, Hide Fragment Container immediately
        if (loadingSpinner != null) loadingSpinner.setVisibility(View.VISIBLE);
        if (fragmentContainerView != null) fragmentContainerView.setVisibility(View.GONE);

        executor.execute(
                () -> {
                    // 2. Background Thread: Load Data
                    String data = jsonLoader.loadJson(getIntent());

                    // 3. Background Thread: Validate Data (Heavy Parsing)
                    Exception validationError = null;
                    if (data != null) {
                        String action = getIntent().getAction();
                        if (Intent.ACTION_VIEW.equals(action)
                                || Intent.ACTION_SEND.equals(action)) {
                            validationError = checkJsonValidity(data);
                        }
                    }

                    // Capture results for the main thread
                    String finalData = data;
                    Exception finalError = validationError;

                    // 4. Main Thread: Update UI
                    mainHandler.post(
                            () -> {
                                // Hide spinner
                                if (loadingSpinner != null) loadingSpinner.setVisibility(View.GONE);
                                if (fragmentContainerView != null)
                                    fragmentContainerView.setVisibility(View.VISIBLE);

                                jsonData = finalData;

                                if (jsonData == null) {
                                    Toast.makeText(
                                                    ViewerActivity.this,
                                                    "No JSON data found",
                                                    Toast.LENGTH_SHORT)
                                            .show();
                                    return;
                                }

                                // If background validation found an error, show dialog now
                                if (finalError != null) {
                                    String safeError =
                                            getFastTruncatedText(finalError.getMessage(), 250);
                                    String safePreview = getFastTruncatedText(jsonData, 2000);
                                    showErrorDialog(safeError, safePreview);
                                }

                                // Proceed with normal setup
                                fragmentController.restoreState(savedInstanceState);
                                searchNavigator.restoreState(savedInstanceState);

                                fragmentController.setOnFragmentChangedListener(
                                        () -> {
                                            searchNavigator.updateVisibility();
                                            setupFragmentCallbacks();
                                        });

                                // Handle default tab selection
                                if (savedInstanceState == null) {
                                    int defaultTab = getIntent().getIntExtra("default_tab", 0);
                                    selectTab(defaultTab);
                                }

                                setupFragmentCallbacks();
                            });
                });
    }

    /**
     * Checks if JSON is valid without interacting with UI. Returns the Exception if invalid, or
     * null if valid.
     */
    private Exception checkJsonValidity(String jsonText) {
        if (jsonText == null || jsonText.isEmpty()) return null;

        try {
            String trimmed = jsonText.trim();
            if (trimmed.startsWith("{")) {
                new JSONObject(trimmed);
            } else if (trimmed.startsWith("[")) {
                new JSONArray(trimmed);
            } else {
                throw new Exception("Invalid start character");
            }
            return null; // Valid
        } catch (Exception e) {
            return e; // Invalid
        }
    }

    private void showErrorDialog(String errorMessage, String truncatedText) {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setPadding(50, 30, 50, 30);

        TextView textView = new TextView(this);
        SpannableStringBuilder builder = new SpannableStringBuilder();

        String errorLabel = "Error:\n";
        int start = builder.length();
        builder.append(errorLabel);
        builder.setSpan(
                new ForegroundColorSpan(Color.parseColor("#FF5252")),
                start,
                builder.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.setSpan(
                new StyleSpan(Typeface.BOLD),
                start,
                builder.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        builder.append(errorMessage).append("\n\n");

        String previewLabel = "Content Preview:\n";
        start = builder.length();
        builder.append(previewLabel);
        builder.setSpan(
                new ForegroundColorSpan(Color.parseColor("#00BCD4")),
                start,
                builder.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.setSpan(
                new StyleSpan(Typeface.BOLD),
                start,
                builder.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        builder.append(truncatedText);

        textView.setText(builder);
        textView.setTextSize(14f);
        textView.setTextColor(0xFFEEEEEE);
        textView.setTypeface(Typeface.MONOSPACE);

        scrollView.addView(textView);

        new AlertDialog.Builder(this)
                .setTitle("Invalid JSON")
                .setView(scrollView)
                .setPositiveButton("Open Raw", (dialog, which) -> selectTab(4))
                // UPDATED: finish() closes the activity instead of just dismissing the dialog
                .setNegativeButton("Close", (dialog, which) -> finish())
                .setCancelable(false) // Optional: Prevents clicking outside to dismiss
                .show();
    }

    private String getFastTruncatedText(String text, int maxChars) {
        if (text == null || text.isEmpty()) return "Unknown Error";
        if (text.length() > maxChars) {
            return text.substring(0, maxChars) + "\n... (Truncated)";
        }
        return text;
    }

    private void selectTab(int tabIndex) {
        TabLayout tabLayout = findViewById(R.id.tabLayout);
        if (tabLayout != null && tabIndex >= 0 && tabIndex < tabLayout.getTabCount()) {
            TabLayout.Tab tab = tabLayout.getTabAt(tabIndex);
            if (tab != null) tab.select();
        }
    }

    private void setupFragmentCallbacks() {
        Fragment current = fragmentController.getCurrentFragment();
        if (current instanceof PrettyViewFragment) {
            ((PrettyViewFragment) current)
                    .setCounterUpdateCallback(() -> searchNavigator.updateCounter());
        }

        String query = searchNavigator.getCurrentQuery();
        if (current instanceof SearchableFragment && !query.isEmpty()) {
            findViewById(R.id.fragmentContainer)
                    .post(() -> ((SearchableFragment) current).onSearch(query));
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.viewer_menu, menu);
        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();

        searchNavigator.attachToSearchView(searchView);

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
            executor.shutdownNow(); // Ensure background threads are stopped
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
            ClipboardManager clipboard =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
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

    public String getJsonData() {
        return jsonData;
    }

    public String getCurrentSearchQuery() {
        return searchNavigator.getCurrentQuery();
    }

    public CachedData getCachedData(String key) {
        return fragmentController.getCachedData(key);
    }

    public void setCachedData(
            String key, String formattedJson, android.text.SpannableStringBuilder highlightedText) {
        fragmentController.setCachedData(key, new CachedData(formattedJson, highlightedText));
    }

    public interface SearchableFragment {
        void onSearch(String query);
    }
}
