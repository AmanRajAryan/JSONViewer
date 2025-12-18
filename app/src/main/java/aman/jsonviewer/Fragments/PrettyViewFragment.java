package aman.jsonviewer;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;

import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.EditorSearcher;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;

// TextMate Imports
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme;
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage;
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel;
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver;

import org.eclipse.tm4e.core.registry.IThemeSource;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.IOException;

public class PrettyViewFragment extends Fragment implements ViewerActivity.SearchableFragment {

    private CodeEditor codeEditor;
    private String formattedJson;
    private static boolean isTextMateInitialized = false;
    private EditorSearcher searcher;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateCounterCallback;

    // A unique ID to track the latest search request
    private long currentSearchId = 0;

    public static PrettyViewFragment newInstance() {
        return new PrettyViewFragment();
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_pretty_view, container, false);
        codeEditor = root.findViewById(R.id.codeEditor);

        // --- 1. Initialize Asset Provider for TextMate ---
        initializeTextMateFileProvider();

        // --- 2. Basic Editor Configuration ---
        codeEditor.setTypefaceText(Typeface.MONOSPACE);
        codeEditor.setEditable(false);
        codeEditor.setLineNumberEnabled(true);
        codeEditor.setTextSize(14f);
        codeEditor.setWordwrap(false);
        codeEditor.setPinLineNumber(true);

        // --- 3. Custom Colors ---
        EditorColorScheme scheme = codeEditor.getColorScheme();
        scheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, Color.parseColor("#121212"));

        // --- 4. Initialize Searcher ---
        searcher = codeEditor.getSearcher();

        // --- 5. Load Data ---
        String jsonData = null;
        if (getActivity() instanceof ViewerActivity) {
            jsonData = ((ViewerActivity) getActivity()).getJsonData();
        }

        if (jsonData != null) {
            formatAndLoad(jsonData);
        }

        return root;
    }

    private void initializeTextMateFileProvider() {
        if (!isTextMateInitialized) {
            FileProviderRegistry.getInstance()
                    .addFileProvider(new AssetsFileResolver(requireContext().getAssets()));
            isTextMateInitialized = true;
        }
    }

    private void formatAndLoad(String jsonData) {
        try {
            String trimmed = jsonData.trim();
            if (trimmed.startsWith("{")) {
                JSONObject json = new JSONObject(jsonData);
                formattedJson = json.toString(4);
            } else if (trimmed.startsWith("[")) {
                JSONArray json = new JSONArray(jsonData);
                formattedJson = json.toString(4);
            } else {
                formattedJson = jsonData;
            }
        } catch (Exception e) {
            formattedJson = jsonData;
        }

        codeEditor.setText(formattedJson);
        loadJsonLanguage();
    }

    private void loadJsonLanguage() {
        new Thread(
                        () -> {
                            try {
                                // 1. Load Grammar
                                GrammarRegistry.getInstance()
                                        .loadGrammars("textmate/languages.json");

                                // 2. Load Theme (Darcula)
                                String themePath = "textmate/darcula.json";

                                IThemeSource themeSource =
                                        new IThemeSource() {
                                            @Override
                                            public String getFilePath() {
                                                return themePath;
                                            }

                                            @Override
                                            public java.io.InputStreamReader getReader()
                                                    throws java.io.IOException {
                                                java.io.InputStream stream =
                                                        FileProviderRegistry.getInstance()
                                                                .tryGetInputStream(themePath);
                                                if (stream == null) {
                                                    throw new java.io.IOException(
                                                            "Theme not found: " + themePath);
                                                }
                                                return new java.io.InputStreamReader(
                                                        stream,
                                                        java.nio.charset.StandardCharsets.UTF_8);
                                            }
                                        };

                                ThemeModel themeModel = new ThemeModel(themeSource, "darcula");
                                ThemeRegistry.getInstance().loadTheme(themeModel);

                                // 3. Create Language
                                TextMateLanguage language =
                                        TextMateLanguage.create("source.json", true);

                                // 4. Apply to Editor
                                if (getActivity() != null) {
                                    getActivity()
                                            .runOnUiThread(
                                                    () -> {
                                                        try {
                                                            ThemeRegistry.getInstance()
                                                                    .setTheme("darcula");
                                                            codeEditor.setColorScheme(
                                                                    TextMateColorScheme.create(
                                                                            ThemeRegistry
                                                                                    .getInstance()));
                                                        } catch (Exception e) {
                                                            e.printStackTrace();
                                                        }
                                                        codeEditor.setEditorLanguage(language);
                                                    });
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                Log.e("loadJsonLanguage" , e.toString());
                            }
                        })
                .start();
    }

    @Override
    public void onSearch(String query) {
        if (codeEditor != null && searcher != null) {
            // Increment ID to invalidate any previous pending tasks
            currentSearchId++;
            long mySearchId = currentSearchId;

            if (query == null || query.isEmpty()) {
                // Immediate stop
                searcher.stopSearch();
                notifyCounterUpdate();
                
                // Cleanup passes to remove "ghost" highlights
                handler.postDelayed(() -> performSafeStop(mySearchId), 100);
                handler.postDelayed(() -> performSafeStop(mySearchId), 300);
                handler.postDelayed(() -> performSafeStop(mySearchId), 600);

            } else {
                // Start new search
                EditorSearcher.SearchOptions options = new EditorSearcher.SearchOptions(false, false);
                searcher.search(query, options);
                
                // 1. Update counter
                handler.postDelayed(() -> notifyCounterUpdate(), 50);

                // 2. Trigger auto-scroll to first match after 200ms
                handler.postDelayed(() -> {
                    // Check if this search is still valid
                    if (mySearchId == currentSearchId && searcher != null && searcher.hasQuery()) {
                        if (searcher.getMatchedPositionCount() > 0) {
                            
                            // CRITICAL FIX: Prevent gotoNext() from stealing focus from the SearchView.
                            // We save the current state, disable focus, jump, and then restore.
                            boolean oldFocusable = codeEditor.isFocusable();
                            boolean oldFocusableInTouch = codeEditor.isFocusableInTouchMode();

                            codeEditor.setFocusable(false);
                            try {
                                searcher.gotoNext();
                            } catch (Exception e) {
                                e.printStackTrace();
                            } finally {
                                codeEditor.setFocusable(oldFocusable);
                                codeEditor.setFocusableInTouchMode(oldFocusableInTouch);
                            }
                            
                            notifyCounterUpdate();
                        }
                    }
                }, 200);
            }
        }
    }
    
    // Safely stop search only if the user hasn't changed the query since this was scheduled
    private void performSafeStop(long searchId) {
        if (searchId == currentSearchId) {
            if (searcher != null) {
                searcher.stopSearch();
            }
            if (codeEditor != null) {
                // Force a view update to remove any visual artifacts
                codeEditor.postInvalidate();
            }
        }
    }

    // Notify ViewerActivity to update the counter
    private void notifyCounterUpdate() {
        if (updateCounterCallback != null) {
            updateCounterCallback.run();
        }
    }
    
    // Set callback from ViewerActivity
    public void setCounterUpdateCallback(Runnable callback) {
        this.updateCounterCallback = callback;
    }
    
    // Public method to allow ViewerActivity to access searcher for navigation
    public EditorSearcher getSearcher() {
        return searcher;
    }
    
    // Method to navigate to next match
    public void nextMatch() {
        if (searcher != null && searcher.hasQuery()) {
            searcher.gotoNext();
            notifyCounterUpdate();
        }
    }
    
    // Method to navigate to previous match
    public void previousMatch() {
        if (searcher != null && searcher.hasQuery()) {
            searcher.gotoPrevious();
            notifyCounterUpdate();
        }
    }
    
    // Get current match info
    public int getCurrentMatchIndex() {
        if (searcher != null && searcher.hasQuery()) {
            try {
                return searcher.getCurrentMatchedPositionIndex();
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }
    
    public int getTotalMatches() {
        if (searcher != null && searcher.hasQuery()) {
            try {
                return searcher.getMatchedPositionCount();
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Increment ID to invalidate any pending runnables
        currentSearchId++;
        if (codeEditor != null) {
            codeEditor.release();
        }
    }
}
