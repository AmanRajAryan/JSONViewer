package aman.jsonviewer;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import org.json.JSONArray;
import org.json.JSONObject;

public class FlowChartViewFragment extends Fragment implements ViewerActivity.SearchableFragment {

    private FlowChartCanvas flowChartCanvas;
    private String jsonData;

    // CHANGED: No arguments needed
    public static FlowChartViewFragment newInstance() {
        return new FlowChartViewFragment();
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        // CHANGED: Fetch data from Activity
        if (getActivity() instanceof ViewerActivity) {
            jsonData = ((ViewerActivity) getActivity()).getJsonData();
        }

        // 1. Create the main container
        FrameLayout rootLayout = new FrameLayout(getContext());
        rootLayout.setLayoutParams(
                new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 2. Initialize the Canvas
        flowChartCanvas = new FlowChartCanvas(getContext());
        rootLayout.addView(flowChartCanvas);

        // 3. Create the Slider Control Panel
        View sliderPanel = createSliderPanel();
        FrameLayout.LayoutParams params =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.BOTTOM;
        params.setMargins(40, 0, 40, 40); // Left, Top, Right, Bottom margins
        rootLayout.addView(sliderPanel, params);

        // 4. Parse JSON
        if (jsonData != null) {
            try {
                String trimmed = jsonData.trim();
                if (trimmed.startsWith("{")) {
                    JSONObject jsonObject = new JSONObject(jsonData);
                    flowChartCanvas.buildFromJSON(jsonObject);
                } else if (trimmed.startsWith("[")) {
                    JSONArray jsonArray = new JSONArray(jsonData);
                    flowChartCanvas.buildFromJSONArray(jsonArray);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Set click listener
        flowChartCanvas.setOnCardClickListener(
                (key, value, type) -> {
                    showValueBottomSheet(key, value, type);
                });

        return rootLayout;
    }

    private View createSliderPanel() {
        // Container with semi-transparent background
        LinearLayout panel = new LinearLayout(getContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(40, 30, 40, 30);

        // rounded background
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#CC1E1E1E")); // Semi-transparent dark
        bg.setCornerRadius(24);
        bg.setStroke(2, Color.parseColor("#33FFFFFF"));
        panel.setBackground(bg);

        // Label
        TextView label = new TextView(getContext());
        label.setText("Vertical Height");
        label.setTextColor(Color.WHITE);
        label.setTextSize(12);
        panel.addView(label);

        // Slider (SeekBar)
        SeekBar seekBar = new SeekBar(getContext());
        seekBar.setMax(2000); // Max spacing
        seekBar.setProgress((int) LayoutConstants.VERTICAL_SPACING); // Current

        // Add minimal margin to seekBar
        LinearLayout.LayoutParams seekParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        seekParams.topMargin = 10;
        panel.addView(seekBar, seekParams);

        // Listener
        seekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        // Minimum spacing of 50 to prevent overlap
                        float newSpacing = Math.max(50, progress);
                        flowChartCanvas.updateVerticalSpacing(newSpacing);
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {}

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {}
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

    // Inside FlowChartViewFragment.java
    @Override
    public void onSearch(String query) {
        if (flowChartCanvas != null) {
            flowChartCanvas.performSearch(query);
        }
    }
}
