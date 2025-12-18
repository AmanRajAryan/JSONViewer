package aman.jsonviewer;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public class JsonLoader {

    private final Context context;

    public JsonLoader(Context context) {
        this.context = context;
    }

    public String loadJson(Intent intent) {
        // 1. Check Singleton (internal navigation)
        String jsonData = JsonDataHolder.getInstance().getJsonData();

        // 2. Check Intent Extras (Legacy/Backup)
        if (jsonData == null && intent != null) {
            jsonData = intent.getStringExtra("json_data");
            if (jsonData != null) {
                JsonDataHolder.getInstance().setJsonData(jsonData);
            }
        }

        // 3. Check Intent Data URI (Opened from External App)
        if (jsonData == null && intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri uri = intent.getData();
            if (uri != null) {
                jsonData = loadJsonFromUri(uri);
                if (jsonData != null) {
                    JsonDataHolder.getInstance().setJsonData(jsonData);
                }
            }
        }

        return jsonData;
    }

    private String loadJsonFromUri(Uri uri) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                Toast.makeText(context, "Cannot open file", Toast.LENGTH_SHORT).show();
                return null;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder builder = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                builder.append(line).append("\n");
            }
            reader.close();

            return builder.toString();

        } catch (Exception e) {
            Toast.makeText(context, "Error reading file: " + e.getMessage(), 
                Toast.LENGTH_LONG).show();
            e.printStackTrace();
            return null;
        }
    }
    
    public void clear() {
        JsonDataHolder.getInstance().clear();
    }
}
