package aman.jsonviewer;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FlowChartViewFragment extends Fragment implements ViewerActivity.SearchableFragment {

    private FlowChartCanvas flowChartCanvas;
    private ProgressBar progressBar;
    private String jsonData;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static FlowChartViewFragment newInstance() {
        return new FlowChartViewFragment();
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        if (getActivity() instanceof ViewerActivity) {
            jsonData = ((ViewerActivity) getActivity()).getJsonData();
        }

        // 1. Create the main container
        FrameLayout rootLayout = new FrameLayout(getContext());
        rootLayout.setLayoutParams(
                new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        rootLayout.setBackgroundColor(0xFF121212);

        // 2. Initialize the Canvas
        flowChartCanvas = new FlowChartCanvas(getContext());
        rootLayout.addView(flowChartCanvas);

        // 3. Create the Slider Control Panel
        View sliderPanel = createSliderPanel();
        FrameLayout.LayoutParams params =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.BOTTOM;
        params.setMargins(40, 0, 40, 40); 
        rootLayout.addView(sliderPanel, params);
        
        // 4. Create ProgressBar
        progressBar = new ProgressBar(getContext());
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, 
                ViewGroup.LayoutParams.WRAP_CONTENT);
        progressParams.gravity = Gravity.CENTER;
        progressBar.setLayoutParams(progressParams);
        progressBar.setVisibility(View.GONE);
        progressBar.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(0xFF00BCD4));
        rootLayout.addView(progressBar);

        // 5. Parse JSON Async
        if (jsonData != null) {
            loadGraphAsync();
        }

        // Set click listener
        flowChartCanvas.setOnCardClickListener(
                (key, value, type) -> {
                    showValueBottomSheet(key, value, type);
                });

        return rootLayout;
    }
    
    private void loadGraphAsync() {
        progressBar.setVisibility(View.VISIBLE);
        flowChartCanvas.setVisibility(View.GONE);
        
        executor.execute(() -> {
            try {
                String trimmed = jsonData.trim();
                final Object result;
                final boolean isObject;
                
                if (trimmed.startsWith("{")) {
                    result = new JSONObject(jsonData);
                    isObject = true;
                } else if (trimmed.startsWith("[")) {
                    result = new JSONArray(jsonData);
                    isObject = false;
                } else {
                    result = null;
                    isObject = false;
                }
                
                mainHandler.post(() -> {
                    if (result != null) {
                        try {
                            if (isObject) {
                                flowChartCanvas.buildFromJSON((JSONObject) result);
                            } else {
                                flowChartCanvas.buildFromJSONArray((JSONArray) result);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    progressBar.setVisibility(View.GONE);
                    flowChartCanvas.setVisibility(View.VISIBLE);
                });
                
            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> progressBar.setVisibility(View.GONE));
            }
        });
    }

    private View createSliderPanel() {
        LinearLayout panel = new LinearLayout(getContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(40, 30, 40, 30);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#CC1E1E1E"));
        bg.setCornerRadius(24);
        bg.setStroke(2, Color.parseColor("#33FFFFFF"));
        panel.setBackground(bg);

        TextView label = new TextView(getContext());
        label.setText("Vertical Height");
        label.setTextColor(Color.WHITE);
        label.setTextSize(12);
        panel.addView(label);

        SeekBar seekBar = new SeekBar(getContext());
        seekBar.setMax(2000); 
        seekBar.setProgress((int) LayoutConstants.VERTICAL_SPACING); 

        LinearLayout.LayoutParams seekParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        seekParams.topMargin = 10;
        panel.addView(seekBar, seekParams);

        seekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        float newSpacing = Math.max(50, progress);
                        flowChartCanvas.updateVerticalSpacing(newSpacing);
                    }
                    @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                    @Override public void onStopTrackingTouch(SeekBar seekBar) {}
                });

        return panel;
    }

    private void showValueBottomSheet(String key, String value, String type) {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(getContext());
        View sheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_value, null);

        TextView keyText = sheetView.findViewById(R.id.keyText);
        TextView valueText = sheetView.findViewById(R.id.valueText);
        TextView typeText = sheetView.findViewById(R.id.typeText);

        keyText.setText(key);
        valueText.setText(value);
        typeText.setText(type);

        bottomSheet.setContentView(sheetView);
        bottomSheet.show();
    }

    @Override
    public void onSearch(String query) {
        if (flowChartCanvas != null) {
            flowChartCanvas.performSearch(query);
        }
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
