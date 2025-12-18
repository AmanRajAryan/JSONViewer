package aman.jsonviewer;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;

public class SearchNavigator {

    private final Context context;
    private final FragmentController fragmentController;
    
    private TextView searchPreviousBtn;
    private TextView searchNextBtn;
    private TextView searchCounterText;
    private LinearLayout searchNavContainer;
    
    private String currentSearchQuery = "";

    public SearchNavigator(Context context, FragmentController fragmentController) {
        this.context = context;
        this.fragmentController = fragmentController;
    }

    public void attachToSearchView(SearchView searchView) {
        setupUI(searchView);
        setupListeners(searchView);
        updateVisibility();
    }

    public void restoreState(android.os.Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            currentSearchQuery = savedInstanceState.getString("search_query", "");
        }
    }

    public void saveState(android.os.Bundle outState) {
        outState.putString("search_query", currentSearchQuery);
    }
    
    public String getCurrentQuery() {
        return currentSearchQuery;
    }

    private void setupUI(SearchView searchView) {
        LinearLayout mainContainer = new LinearLayout(context);
        mainContainer.setOrientation(LinearLayout.VERTICAL);
        mainContainer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        mainContainer.setGravity(Gravity.CENTER);
        mainContainer.setVisibility(View.GONE);

        searchNavContainer = new LinearLayout(context);
        searchNavContainer.setOrientation(LinearLayout.HORIZONTAL);
        searchNavContainer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        searchNavContainer.setGravity(Gravity.CENTER);

        searchPreviousBtn = createNavButton("<");
        searchPreviousBtn.setOnClickListener(v -> navigatePrevious());

        searchNextBtn = createNavButton(">");
        searchNextBtn.setOnClickListener(v -> navigateNext());

        searchNavContainer.addView(searchPreviousBtn);
        searchNavContainer.addView(searchNextBtn);

        searchCounterText = new TextView(context);
        searchCounterText.setText("0/0");
        searchCounterText.setTextColor(0xFFAAAAAA);
        searchCounterText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        searchCounterText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams counterParams = new LinearLayout.LayoutParams(
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

    private TextView createNavButton(String text) {
        TextView btn = new TextView(context);
        btn.setText(text);
        btn.setTextColor(0xFFFFFFFF);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        btn.setPadding(text.equals("<") ? 16 : 30, 8, text.equals("<") ? 30 : 16, 8);
        
        TypedValue outValue = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        btn.setBackgroundResource(outValue.resourceId);
        
        return btn;
    }

    private void setupListeners(SearchView searchView) {
        if (!currentSearchQuery.isEmpty()) {
            searchView.setQuery(currentSearchQuery, false);
            searchView.clearFocus();
        }

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
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

        searchView.setOnCloseListener(() -> {
            currentSearchQuery = "";
            performSearch("");
            return false;
        });
    }

    public void performSearch(String query) {
        currentSearchQuery = query;
        Fragment current = fragmentController.getCurrentFragment();
        
        if (current instanceof ViewerActivity.SearchableFragment) {
            ((ViewerActivity.SearchableFragment) current).onSearch(query);
        }
        
        updateVisibility();
        // Delay to allow adapter to process search before counting
        if (searchCounterText != null) {
            searchCounterText.postDelayed(this::updateCounter, 50);
        }
    }

    public void updateVisibility() {
        if (searchNavContainer == null) return;
        
        Fragment current = fragmentController.getCurrentFragment();
        boolean isSearchable = current instanceof TreeViewFragment || 
                               current instanceof CardViewFragment || 
                               current instanceof PrettyViewFragment;

        boolean show = !currentSearchQuery.isEmpty() && isSearchable;
        searchNavContainer.setVisibility(show ? View.VISIBLE : View.GONE);
        
        if (show) updateCounter();
    }

    public void updateCounter() {
        if (searchCounterText == null) return;
        
        Fragment current = fragmentController.getCurrentFragment();
        int currentIdx = 0;
        int total = 0;

        if (current instanceof TreeViewFragment) {
            TreeViewFragment.TreeAdapter adapter = ((TreeViewFragment) current).getAdapter();
            if (adapter != null) {
                currentIdx = adapter.getCurrentMatchIndex() + 1;
                total = adapter.getTotalMatches();
            }
        } else if (current instanceof CardViewFragment) {
            CardViewFragment.CardAdapter adapter = ((CardViewFragment) current).getAdapter();
            if (adapter != null) {
                currentIdx = adapter.getCurrentMatchIndex() + 1;
                total = adapter.getTotalMatches();
            }
        } else if (current instanceof PrettyViewFragment) {
            PrettyViewFragment frag = (PrettyViewFragment) current;
            currentIdx = frag.getCurrentMatchIndex() + 1;
            total = frag.getTotalMatches();
            if (total == 0) currentIdx = 0;
        }

        searchCounterText.setText(total > 0 ? currentIdx + "/" + total : "0/0");
    }

    private void navigateNext() {
        hideKeyboard();
        Fragment current = fragmentController.getCurrentFragment();
        
        if (current instanceof TreeViewFragment) {
            TreeViewFragment.TreeAdapter adapter = ((TreeViewFragment) current).getAdapter();
            if (adapter != null) adapter.nextMatch();
        } else if (current instanceof CardViewFragment) {
            CardViewFragment.CardAdapter adapter = ((CardViewFragment) current).getAdapter();
            if (adapter != null) adapter.nextMatch();
        } else if (current instanceof PrettyViewFragment) {
            ((PrettyViewFragment) current).nextMatch();
        }
        updateCounter();
    }

    private void navigatePrevious() {
        hideKeyboard();
        Fragment current = fragmentController.getCurrentFragment();

        if (current instanceof TreeViewFragment) {
            TreeViewFragment.TreeAdapter adapter = ((TreeViewFragment) current).getAdapter();
            if (adapter != null) adapter.previousMatch();
        } else if (current instanceof CardViewFragment) {
            CardViewFragment.CardAdapter adapter = ((CardViewFragment) current).getAdapter();
            if (adapter != null) adapter.previousMatch();
        } else if (current instanceof PrettyViewFragment) {
            ((PrettyViewFragment) current).previousMatch();
        }
        updateCounter();
    }

    private void hideKeyboard() {
        View view = ((android.app.Activity)context).getCurrentFocus();
        if (view == null) view = new View(context);
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }
}
