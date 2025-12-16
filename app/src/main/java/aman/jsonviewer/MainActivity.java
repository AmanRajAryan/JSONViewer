package aman.jsonviewer;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
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
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Initialize file picker launcher
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
        
        // Animate cards on start
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
            String jsonText = jsonInput.getText().toString().trim();
            if (validateAndOpenViewer(jsonText)) {
                dialog.dismiss();
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
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder builder = new StringBuilder();
            String line;
            
            while ((line = reader.readLine()) != null) {
                builder.append(line).append("\n"); // Preserve line breaks
            }
            reader.close();
            
            validateAndOpenViewer(builder.toString());
        } catch (Exception e) {
            Toast.makeText(this, "Error reading file: " + e.getMessage(), 
                Toast.LENGTH_LONG).show();
        }
    }
    
    private void loadJsonFromUrl(String url) {
        Toast.makeText(this, "Loading from URL...", Toast.LENGTH_SHORT).show();
        
        new Thread(() -> {
            try {
                java.net.URL urlObj = new java.net.URL(url);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) urlObj.openConnection();
                conn.setRequestMethod("GET");
                conn.connect();
                
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()));
                StringBuilder builder = new StringBuilder();
                String line;
                
                while ((line = reader.readLine()) != null) {
                    builder.append(line).append("\n"); // Preserve line breaks
                }
                reader.close();
                
                String jsonText = builder.toString();
                runOnUiThread(() -> validateAndOpenViewer(jsonText));
                
            } catch (Exception e) {
                runOnUiThread(() -> 
                    Toast.makeText(this, "Error loading URL: " + e.getMessage(), 
                        Toast.LENGTH_LONG).show());
            }
        }).start();
    }
    
    private boolean validateAndOpenViewer(String jsonText) {
        if (jsonText.isEmpty()) {
            Toast.makeText(this, "Please enter JSON data", Toast.LENGTH_SHORT).show();
            return false;
        }
        
        try {
            // Validate JSON
            String trimmed = jsonText.trim();
            if (trimmed.startsWith("{")) {
                new JSONObject(jsonText);
            } else if (trimmed.startsWith("[")) {
                new JSONArray(jsonText);
            } else {
                throw new Exception("Invalid JSON format");
            }
            
            // FIXED: Store in singleton instead of Intent extra to avoid TransactionTooLargeException
            JsonDataHolder.getInstance().setJsonData(jsonText);
            
            // Open viewer activity
            Intent intent = new Intent(this, ViewerActivity.class);
            // No longer passing data through intent - using singleton instead
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            return true;
            
        } catch (Exception e) {
            Toast.makeText(this, "Invalid JSON: " + e.getMessage(), 
                Toast.LENGTH_LONG).show();
            return false;
        }
    }
}