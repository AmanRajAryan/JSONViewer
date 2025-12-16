package aman.jsonviewer;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import org.json.JSONArray;
import org.json.JSONObject;

public class PrettyViewFragment extends Fragment implements ViewerActivity.SearchableFragment {

    private TextViewerLayout textViewerLayout;
    private String formattedJson;

    public static PrettyViewFragment newInstance() {
        return new PrettyViewFragment();
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        String jsonData = null;
        if (getActivity() instanceof ViewerActivity) {
            jsonData = ((ViewerActivity) getActivity()).getJsonData();
        }

        textViewerLayout = new TextViewerLayout(getContext());

        if (jsonData != null) {
            try {
                String trimmed = jsonData.trim();
                if (trimmed.startsWith("{")) {
                    JSONObject json = new JSONObject(jsonData);
                    formattedJson = json.toString(2);
                } else if (trimmed.startsWith("[")) {
                    JSONArray json = new JSONArray(jsonData);
                    formattedJson = json.toString(2);
                } else {
                    formattedJson = jsonData;
                }
            } catch (Exception e) {
                formattedJson = jsonData;
            }

            textViewerLayout.loadText(formattedJson);
        }

        return textViewerLayout;
    }

    @Override
    public void onSearch(String query) {
        if (textViewerLayout != null) {
            textViewerLayout.search(query);
        }
    }
    
    @Override
    public void onPause() {
        super.onPause();
        // Trim cache when fragment is hidden
        if (textViewerLayout != null) {
            textViewerLayout.trimCache();
        }
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        
        // Only clean up if Activity is finishing
        if (getActivity() != null && getActivity().isFinishing()) {
            cleanup();
        }
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        cleanup();
    }
    
    private void cleanup() {
        textViewerLayout = null;
        formattedJson = null;
    }
}