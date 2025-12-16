package aman.jsonviewer;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
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
        // Enable folding for {} and [] blocks
codeEditor.setPinLineNumber(true); // Optional: Puts the fold indicator in the line number area


        // --- 3. Custom Colors ---
        EditorColorScheme scheme = codeEditor.getColorScheme();
        scheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, Color.parseColor("#121212"));

        // --- 4. Load Data ---
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

                                // FIX: Implement BOTH required methods
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
        if (codeEditor != null && query != null && !query.isEmpty()) {
            EditorSearcher.SearchOptions options = new EditorSearcher.SearchOptions(true, false);
            codeEditor.getSearcher().search(query, options);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (codeEditor != null) {
            codeEditor.release();
        }
    }
}
