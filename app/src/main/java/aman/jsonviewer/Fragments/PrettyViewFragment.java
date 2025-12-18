package aman.jsonviewer;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import androidx.fragment.app.Fragment;

import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.EditorSearcher;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PrettyViewFragment extends Fragment implements ViewerActivity.SearchableFragment {

    private CodeEditor codeEditor;
    private ProgressBar progressBar;
    private String formattedJson;
    private static boolean isTextMateInitialized = false;
    private EditorSearcher searcher;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateCounterCallback;
    private long currentSearchId = 0;
    
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static PrettyViewFragment newInstance() {
        return new PrettyViewFragment();
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_pretty_view, container, false);
        codeEditor = root.findViewById(R.id.codeEditor);
        progressBar = root.findViewById(R.id.progressBar);

        initializeTextMateFileProvider();

        codeEditor.setTypefaceText(Typeface.MONOSPACE);
        codeEditor.setEditable(false);
        codeEditor.setLineNumberEnabled(true);
        codeEditor.setTextSize(14f);
        codeEditor.setWordwrap(false);
        codeEditor.setPinLineNumber(true);
        codeEditor.setBlockLineEnabled(true);

        EditorColorScheme scheme = codeEditor.getColorScheme();
        scheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, Color.parseColor("#121212"));

        searcher = codeEditor.getSearcher();

        String jsonData = null;
        if (getActivity() instanceof ViewerActivity) {
            jsonData = ((ViewerActivity) getActivity()).getJsonData();
        }

        if (jsonData != null) {
            formatAndLoadAsync(jsonData);
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

    private void formatAndLoadAsync(String jsonData) {
        progressBar.setVisibility(View.VISIBLE);
        codeEditor.setVisibility(View.GONE);
        
        executor.execute(() -> {
            try {
                String trimmed = jsonData.trim();
                String result;
                if (trimmed.startsWith("{")) {
                    JSONObject json = new JSONObject(jsonData);
                    result = json.toString(4);
                } else if (trimmed.startsWith("[")) {
                    JSONArray json = new JSONArray(jsonData);
                    result = json.toString(4);
                } else {
                    result = jsonData;
                }
                formattedJson = result;
                
                handler.post(() -> {
                    if (codeEditor != null) {
                        codeEditor.setText(formattedJson);
                        loadJsonLanguage();
                        progressBar.setVisibility(View.GONE);
                        codeEditor.setVisibility(View.VISIBLE);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                handler.post(() -> {
                    if (codeEditor != null) {
                        codeEditor.setText(jsonData); // Fallback to raw
                        progressBar.setVisibility(View.GONE);
                        codeEditor.setVisibility(View.VISIBLE);
                    }
                });
            }
        });
    }

    private void loadJsonLanguage() {
        new Thread(() -> {
            try {
                GrammarRegistry.getInstance().loadGrammars("textmate/languages.json");
                String themePath = "textmate/darcula.json";

                IThemeSource themeSource = new IThemeSource() {
                    @Override public String getFilePath() { return themePath; }
                    @Override public java.io.InputStreamReader getReader() throws java.io.IOException {
                        java.io.InputStream stream = FileProviderRegistry.getInstance().tryGetInputStream(themePath);
                        return new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8);
                    }
                };

                ThemeModel themeModel = new ThemeModel(themeSource, "darcula");
                ThemeRegistry.getInstance().loadTheme(themeModel);
                TextMateLanguage language = TextMateLanguage.create("source.json", true);

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        try {
                            ThemeRegistry.getInstance().setTheme("darcula");
                            codeEditor.setColorScheme(TextMateColorScheme.create(ThemeRegistry.getInstance()));
                        } catch (Exception e) { e.printStackTrace(); }
                        codeEditor.setEditorLanguage(language);
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        currentSearchId++;
        if (codeEditor != null) {
            codeEditor.release();
        }
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    @Override
    public void onSearch(String query) {
        if (codeEditor != null && searcher != null) {
            currentSearchId++;
            long mySearchId = currentSearchId;

            if (query == null || query.isEmpty()) {
                searcher.stopSearch();
                notifyCounterUpdate();
                handler.postDelayed(() -> performSafeStop(mySearchId), 100);
            } else {
                EditorSearcher.SearchOptions options = new EditorSearcher.SearchOptions(false, false);
                searcher.search(query, options);
                handler.postDelayed(() -> notifyCounterUpdate(), 50);
                handler.postDelayed(() -> {
                    if (mySearchId == currentSearchId && searcher != null && searcher.hasQuery()) {
                        if (searcher.getMatchedPositionCount() > 0) {
                            boolean oldFocusable = codeEditor.isFocusable();
                            boolean oldFocusableInTouch = codeEditor.isFocusableInTouchMode();
                            codeEditor.setFocusable(false);
                            try { searcher.gotoNext(); } catch (Exception e) { e.printStackTrace(); } 
                            finally {
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
    
    private void performSafeStop(long searchId) {
        if (searchId == currentSearchId) {
            if (searcher != null) searcher.stopSearch();
            if (codeEditor != null) codeEditor.postInvalidate();
        }
    }

    private void notifyCounterUpdate() {
        if (updateCounterCallback != null) updateCounterCallback.run();
    }
    
    public void setCounterUpdateCallback(Runnable callback) {
        this.updateCounterCallback = callback;
    }
    
    public EditorSearcher getSearcher() { return searcher; }
    
    public void nextMatch() {
        if (searcher != null && searcher.hasQuery()) {
            searcher.gotoNext();
            notifyCounterUpdate();
        }
    }
    
    public void previousMatch() {
        if (searcher != null && searcher.hasQuery()) {
            searcher.gotoPrevious();
            notifyCounterUpdate();
        }
    }
    
    public int getCurrentMatchIndex() {
        if (searcher != null && searcher.hasQuery()) {
            try { return searcher.getCurrentMatchedPositionIndex(); } catch (Exception e) { return 0; }
        }
        return 0;
    }
    
    public int getTotalMatches() {
        if (searcher != null && searcher.hasQuery()) {
            try { return searcher.getMatchedPositionCount(); } catch (Exception e) { return 0; }
        }
        return 0;
    }
}
