package aman.jsonviewer;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import com.google.android.material.tabs.TabLayout;
import java.util.HashMap;
import java.util.Map;

public class ViewerActivity extends AppCompatActivity {

    private void logMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / (1024 * 1024);
        long totalMemory = runtime.totalMemory() / (1024 * 1024);
        long freeMemory = runtime.freeMemory() / (1024 * 1024);
        long usedMemory = totalMemory - freeMemory;

        Log.d("MEMORY_CHECK", "=================================");
        Log.d("MEMORY_CHECK", "Max Memory Limit:  " + maxMemory + " MB");
        Log.d("MEMORY_CHECK", "Current Heap Size: " + totalMemory + " MB");
        Log.d("MEMORY_CHECK", "Actually Used:     " + usedMemory + " MB");
        Log.d("MEMORY_CHECK", "=================================");
    }

    private FrameLayout fragmentContainer;
    private TabLayout tabLayout;
    private String jsonData;
    private String currentSearchQuery = "";

    private Fragment currentFragment;
    private Fragment[] fragments = new Fragment[5];

    private static final String[] TAGS = {
        "TAG_TREE", "TAG_RAW", "TAG_CARD", "TAG_PRETTY", "TAG_FLOW"
    };

    private Map<String, CachedData> fragmentCache = new HashMap<>();

    private TextView searchPreviousBtn;
    private TextView searchNextBtn;
    private TextView searchCounterText;
    private LinearLayout searchNavContainer;

    public static class CachedData {
        public String formattedJson;
        public SpannableStringBuilder highlightedText;

        public CachedData(String formattedJson, SpannableStringBuilder highlightedText) {
            this.formattedJson = formattedJson;
            this.highlightedText = highlightedText;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_viewer);

        // Hides both top and bottom bars
        View decorView = getWindow().getDecorView();
        int uiOptions =
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        decorView.setSystemUiVisibility(uiOptions);

        

        // CRITICAL FIX: Do not restore jsonData from savedInstanceState
        // Always get it from JsonDataHolder or Intent
        jsonData = JsonDataHolder.getInstance().getJsonData();

        if (jsonData == null) {
            jsonData = getIntent().getStringExtra("json_data");
            // Store it in holder for future use
            if (jsonData != null) {
                JsonDataHolder.getInstance().setJsonData(jsonData);
            }
        }

        if (savedInstanceState != null) {
            // Only restore UI state, not data
            currentSearchQuery = savedInstanceState.getString("search_query", "");

            for (int i = 0; i < TAGS.length; i++) {
                fragments[i] = getSupportFragmentManager().findFragmentByTag(TAGS[i]);
            }
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("JSON Viewer");

        fragmentContainer = findViewById(R.id.fragmentContainer);
        tabLayout = findViewById(R.id.tabLayout);

        setupTabs();

        if (savedInstanceState != null) {
            int selectedTab = savedInstanceState.getInt("selected_tab", 0);
            TabLayout.Tab tab = tabLayout.getTabAt(selectedTab);
            if (tab != null) {
                tab.select();
                loadFragment(selectedTab);
            }
        } else {
            loadFragment(0);
        }

        logMemoryUsage();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // CRITICAL FIX: Do NOT save jsonData - it's too large and causes
        // TransactionTooLargeException
        // Only save UI state
        outState.putString("search_query", currentSearchQuery);
        outState.putInt("selected_tab", tabLayout.getSelectedTabPosition());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) {
            // Clear all fragment caches
            for (Fragment fragment : fragments) {
                if (fragment != null) {
                    trimFragmentCache(fragment);
                }
            }

            JsonDataHolder.getInstance().clear();
            fragmentCache.clear();

            System.gc();
        }
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);

        // System is asking us to trim memory
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            // Trim caches of hidden fragments
            for (Fragment fragment : fragments) {
                if (fragment != null && fragment != currentFragment) {
                    trimFragmentCache(fragment);
                }
            }

            fragmentCache.clear();
            System.gc();
        }
    }

    private void setupTabs() {
        tabLayout.addTab(tabLayout.newTab().setText("Tree").setIcon(R.drawable.ic_tree));
        tabLayout.addTab(tabLayout.newTab().setText("Pretty").setIcon(R.drawable.ic_format));
        tabLayout.addTab(tabLayout.newTab().setText("Flow").setIcon(R.drawable.ic_flow));
        tabLayout.addTab(tabLayout.newTab().setText("Cards").setIcon(R.drawable.ic_cards));
        tabLayout.addTab(tabLayout.newTab().setText("Raw").setIcon(R.drawable.ic_code));

        tabLayout.addOnTabSelectedListener(
                new TabLayout.OnTabSelectedListener() {
                    @Override
                    public void onTabSelected(TabLayout.Tab tab) {
                        loadFragment(tab.getPosition());
                        updateSearchNavigationVisibility();
                    }

                    @Override
                    public void onTabUnselected(TabLayout.Tab tab) {}

                    @Override
                    public void onTabReselected(TabLayout.Tab tab) {}
                });
    }

    private void loadFragment(int position) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

        if (currentFragment != null) {
            // IMPORTANT: Trim cache of hidden fragment
            trimFragmentCache(currentFragment);
            transaction.hide(currentFragment);
        }

        Fragment targetFragment = fragments[position];
        String tag = TAGS[position];

        if (targetFragment == null) {
            targetFragment = getSupportFragmentManager().findFragmentByTag(tag);

            if (targetFragment == null) {
                switch (position) {
                    case 0:
                        targetFragment = TreeViewFragment.newInstance();
                        break;
                    case 1:
                        targetFragment = PrettyViewFragment.newInstance();
                        break;
                    case 2:
                        targetFragment = FlowChartViewFragment.newInstance();
                        break;
                    case 3:
                        targetFragment = CardViewFragment.newInstance();
                        break;
                    case 4:
                        targetFragment = RawViewFragment.newInstance();
                        break;
                    default:
                        targetFragment = TreeViewFragment.newInstance();
                        break;
                }
                transaction.add(R.id.fragmentContainer, targetFragment, tag);
            } else {
                transaction.show(targetFragment);
            }
            fragments[position] = targetFragment;
        } else {
            transaction.show(targetFragment);
        }

        currentFragment = targetFragment;
        transaction.commit();

        logMemoryUsage();

        if (!currentSearchQuery.isEmpty() && currentFragment instanceof SearchableFragment) {
            final Fragment f = currentFragment;
            fragmentContainer.post(
                    () -> {
                        if (f.isVisible()) {
                            ((SearchableFragment) f).onSearch(currentSearchQuery);
                            updateSearchCounter();
                        }
                    });
        }
    }

    /** IMPORTANT: Trim cache of hidden fragments */
    private void trimFragmentCache(Fragment fragment) {
        try {
            if (fragment instanceof PrettyViewFragment) {
                PrettyViewFragment prettyFrag = (PrettyViewFragment) fragment;
                // Trim via reflection or add public method
                // For now, handled in fragment's onPause()
            }
            // Other fragments handle their own trimming in onPause()
        } catch (Exception e) {
            Log.e("ViewerActivity", "Error trimming cache", e);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.viewer_menu, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();

        setupSearchNavigation(searchView);

        if (!currentSearchQuery.isEmpty()) {
            searchItem.expandActionView();
            searchView.setQuery(currentSearchQuery, false);
            searchView.clearFocus();
        }

        searchView.setOnQueryTextListener(
                new SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        performSearch(query);
                        searchView.clearFocus();
                        return true;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        performSearch(newText);
                        return true;
                    }
                });

        searchItem.setOnActionExpandListener(
                new MenuItem.OnActionExpandListener() {
                    @Override
                    public boolean onMenuItemActionExpand(MenuItem item) {
                        updateSearchNavigationVisibility();
                        return true;
                    }

                    @Override
                    public boolean onMenuItemActionCollapse(MenuItem item) {
                        currentSearchQuery = "";
                        performSearch("");
                        return true;
                    }
                });

        updateSearchNavigationVisibility();

        return true;
    }

    private void setupSearchNavigation(SearchView searchView) {
        LinearLayout mainContainer = new LinearLayout(this);
        mainContainer.setOrientation(LinearLayout.VERTICAL);
        mainContainer.setLayoutParams(
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
        mainContainer.setGravity(Gravity.CENTER);
        mainContainer.setVisibility(View.GONE);

        searchNavContainer = new LinearLayout(this);
        searchNavContainer.setOrientation(LinearLayout.HORIZONTAL);
        searchNavContainer.setLayoutParams(
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
        searchNavContainer.setGravity(Gravity.CENTER);

        searchPreviousBtn = new TextView(this);
        searchPreviousBtn.setText("<");
        searchPreviousBtn.setTextColor(0xFFFFFFFF);
        searchPreviousBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        searchPreviousBtn.setPadding(16, 8, 30, 8);
        searchPreviousBtn.setBackground(createRippleDrawable());
        searchPreviousBtn.setOnClickListener(v -> navigateSearchPrevious());

        searchNextBtn = new TextView(this);
        searchNextBtn.setText(">");
        searchNextBtn.setTextColor(0xFFFFFFFF);
        searchNextBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        searchNextBtn.setPadding(30, 8, 16, 8);
        searchNextBtn.setBackground(createRippleDrawable());
        searchNextBtn.setOnClickListener(v -> navigateSearchNext());

        searchNavContainer.addView(searchPreviousBtn);
        searchNavContainer.addView(searchNextBtn);

        searchCounterText = new TextView(this);
        searchCounterText.setText("0/0");
        searchCounterText.setTextColor(0xFFAAAAAA);
        searchCounterText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        searchCounterText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams counterParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
        counterParams.topMargin = -4;
        searchCounterText.setLayoutParams(counterParams);

        mainContainer.addView(searchNavContainer);
        mainContainer.addView(searchCounterText);

        this.searchNavContainer = mainContainer;

        LinearLayout searchLayout = (LinearLayout) searchView.getChildAt(0);
        searchLayout.addView(mainContainer);
    }

    private android.graphics.drawable.Drawable createRippleDrawable() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            return new android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(0x40FFFFFF), null, null);
        } else {
            android.graphics.drawable.StateListDrawable drawable =
                    new android.graphics.drawable.StateListDrawable();
            android.graphics.drawable.ColorDrawable pressed =
                    new android.graphics.drawable.ColorDrawable(0x40FFFFFF);
            drawable.addState(new int[] {android.R.attr.state_pressed}, pressed);
            return drawable;
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

    private void performSearch(String query) {
        currentSearchQuery = query;
        if (currentFragment instanceof SearchableFragment) {
            ((SearchableFragment) currentFragment).onSearch(query);
        }
        updateSearchNavigationVisibility();
        updateSearchCounter();
    }

    private void updateSearchNavigationVisibility() {
        if (searchNavContainer == null) return;

        boolean showNavigation =
                !currentSearchQuery.isEmpty() && currentFragment instanceof TreeViewFragment;

        searchNavContainer.setVisibility(showNavigation ? View.VISIBLE : View.GONE);
    }

    private void updateSearchCounter() {
        if (searchCounterText == null) return;

        if (currentFragment instanceof TreeViewFragment) {
            TreeViewFragment treeFragment = (TreeViewFragment) currentFragment;
            if (treeFragment.getAdapter() != null) {
                int current = treeFragment.getAdapter().getCurrentMatchIndex() + 1;
                int total = treeFragment.getAdapter().getTotalMatches();

                if (total > 0) {
                    searchCounterText.setText(current + "/" + total);
                } else {
                    searchCounterText.setText("0/0");
                }
            }
        }
    }

    private void navigateSearchNext() {
        if (currentFragment instanceof TreeViewFragment) {
            TreeViewFragment treeFragment = (TreeViewFragment) currentFragment;
            if (treeFragment.getAdapter() != null) {
                treeFragment.getAdapter().nextMatch();
                updateSearchCounter();
            }
        }
    }

    private void navigateSearchPrevious() {
        if (currentFragment instanceof TreeViewFragment) {
            TreeViewFragment treeFragment = (TreeViewFragment) currentFragment;
            if (treeFragment.getAdapter() != null) {
                treeFragment.getAdapter().previousMatch();
                updateSearchCounter();
            }
        }
    }

    private void copyToClipboard() {
        try {
            ClipboardManager clipboard =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("JSON", jsonData);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
        } catch (Exception err) {
            Toast.makeText(getApplicationContext(), err.toString(), 1).show();
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
        return currentSearchQuery;
    }

    public CachedData getCachedData(String key) {
        return fragmentCache.get(key);
    }

    public void setCachedData(
            String key, String formattedJson, SpannableStringBuilder highlightedText) {
        fragmentCache.put(key, new CachedData(formattedJson, highlightedText));
    }

    public interface SearchableFragment {
        void onSearch(String query);
    }
}
