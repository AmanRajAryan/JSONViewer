package aman.jsonviewer;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {
    
    private ActivityResultLauncher<String> filePickerLauncher;
    private MaterialCardView loadingCard;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    loadJsonFromFile(uri);
                }
            }
        );
        
        setupViews();
    }
    
    private void setupViews() {
        MaterialCardView pasteCard = findViewById(R.id.pasteCard);
        MaterialCardView fileCard = findViewById(R.id.fileCard);
        MaterialCardView urlCard = findViewById(R.id.urlCard);
        loadingCard = findViewById(R.id.loadingCard);
        
        animateCard(pasteCard, 0);
        animateCard(fileCard, 100);
        animateCard(urlCard, 200);
        
        pasteCard.setOnClickListener(v -> showPasteDialog());
        fileCard.setOnClickListener(v -> openFilePicker());
        urlCard.setOnClickListener(v -> showUrlDialog());
    }
    
    private void animateCard(View card, long delay) {
        card.setAlpha(0f);
        card.setTranslationY(50f);
        card.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(400)
            .setStartDelay(delay)
            .start();
    }
    
    private void showPasteDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_paste_json, null);
        
        EditText jsonInput = view.findViewById(R.id.jsonInput);
        MaterialButton btnParse = view.findViewById(R.id.btnParse);
        
        btnParse.setOnClickListener(v -> {
            String rawText = jsonInput.getText().toString();
            if (!rawText.isEmpty()) {
                dialog.dismiss();
                validateAndOpenViewer(rawText);
            }
        });
        
        dialog.setContentView(view);
        dialog.show();
    }
    
    private void showUrlDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_url_input, null);
        
        EditText urlInput = view.findViewById(R.id.urlInput);
        
        builder.setView(view)
            .setTitle("Load from URL")
            .setPositiveButton("Load", (d, w) -> {
                String url = urlInput.getText().toString().trim();
                if (!url.isEmpty()) {
                    loadJsonFromUrl(url);
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void openFilePicker() {
        filePickerLauncher.launch("application/json");
    }
    
    private void loadJsonFromFile(Uri uri) {
        setLoading(true);
        new Thread(() -> {
            try {
                InputStream inputStream = getContentResolver().openInputStream(uri);
                if (inputStream == null) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Cannot open file", Toast.LENGTH_SHORT).show();
                        setLoading(false);
                    });
                    return;
                }
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line).append("\n");
                }
                reader.close();
                validateJsonInBackground(builder.toString());
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error reading file", Toast.LENGTH_SHORT).show();
                    setLoading(false);
                });
            }
        }).start();
    }
    
    private void loadJsonFromUrl(String url) {
        setLoading(true);
        new Thread(() -> {
            try {
                java.net.URL urlObj = new java.net.URL(url);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) urlObj.openConnection();
                conn.setRequestMethod("GET");
                conn.connect();
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line).append("\n");
                }
                reader.close();
                validateJsonInBackground(builder.toString());
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error loading URL", Toast.LENGTH_SHORT).show();
                    setLoading(false);
                });
            }
        }).start();
    }
    
    private void validateAndOpenViewer(String jsonText) {
        setLoading(true);
        new Thread(() -> validateJsonInBackground(jsonText)).start();
    }
    
    private void validateJsonInBackground(String rawJson) {
        if (rawJson == null) {
            runOnUiThread(() -> {
                Toast.makeText(this, "No data", Toast.LENGTH_SHORT).show();
                setLoading(false);
            });
            return;
        }

        String jsonText = rawJson.trim();
        
        if (jsonText.isEmpty()) {
            runOnUiThread(() -> {
                Toast.makeText(this, "Empty JSON", Toast.LENGTH_SHORT).show();
                setLoading(false);
            });
            return;
        }
        
        try {
            if (jsonText.startsWith("{")) {
                new JSONObject(jsonText);
            } else if (jsonText.startsWith("[")) {
                new JSONArray(jsonText);
            } else {
                throw new Exception("Invalid start character");
            }
            
            // Success: Open default tab (0)
            runOnUiThread(() -> openViewer(jsonText, 0));
            
        } catch (Exception e) {
            String safeError = getFastTruncatedText(e.getMessage(), 250); 
            String safePreview = getFastTruncatedText(jsonText, 2000); 

            runOnUiThread(() -> showErrorDialog(jsonText, safeError, safePreview));
        }
    }

    // UPDATED: Accepts tabIndex to open specific tab (0=Tree, 4=Raw)
    private void openViewer(String jsonText, int tabIndex) {
        JsonDataHolder.getInstance().setJsonData(jsonText);
        Intent intent = new Intent(MainActivity.this, ViewerActivity.class);
        intent.putExtra("default_tab", tabIndex);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        setLoading(false); 
    }
    
    private void showErrorDialog(String jsonText, String errorMessage, String truncatedText) {
        setLoading(false);
        
        ScrollView scrollView = new ScrollView(this);
        scrollView.setPadding(50, 30, 50, 30); 
        
        TextView textView = new TextView(this);
        
        SpannableStringBuilder builder = new SpannableStringBuilder();
        
        String errorLabel = "Error:\n";
        int start = builder.length();
        builder.append(errorLabel);
        builder.setSpan(new ForegroundColorSpan(Color.parseColor("#FF5252")), start, builder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.setSpan(new StyleSpan(Typeface.BOLD), start, builder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        
        builder.append(errorMessage).append("\n\n");
        
        String previewLabel = "Content Preview:\n";
        start = builder.length();
        builder.append(previewLabel);
        builder.setSpan(new ForegroundColorSpan(Color.parseColor("#00BCD4")), start, builder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.setSpan(new StyleSpan(Typeface.BOLD), start, builder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        builder.append(truncatedText);
        
        textView.setText(builder);
        textView.setTextSize(14f);
        textView.setTextColor(0xFFEEEEEE); 
        textView.setTypeface(Typeface.MONOSPACE);
        
        scrollView.addView(textView);

        new AlertDialog.Builder(this)
            .setTitle("Invalid JSON")
            .setView(scrollView) 
            // UPDATED: Pass 4 to open the Raw View tab
            .setPositiveButton("Open Raw", (dialog, which) -> openViewer(jsonText, 4))
            .setNegativeButton("Close", (dialog, which) -> dialog.dismiss())
            .show();
    }

    private String getFastTruncatedText(String text, int maxChars) {
        if (text == null || text.isEmpty()) return "Unknown Error";
        if (text.length() > maxChars) {
            return text.substring(0, maxChars) + "\n... (Truncated)";
        }
        return text;
    }
    
    private void setLoading(boolean isLoading) {
        if (loadingCard != null) {
            loadingCard.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        }
    }
}
