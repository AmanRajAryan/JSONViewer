package aman.jsonviewer;

/**
 * Singleton to hold large JSON data in memory instead of passing through Intent
 * This avoids TransactionTooLargeException for files > 1MB
 */
public class JsonDataHolder {
    private static JsonDataHolder instance;
    private String jsonData;
    
    private JsonDataHolder() {}
    
    public static JsonDataHolder getInstance() {
        if (instance == null) {
            instance = new JsonDataHolder();
        }
        return instance;
    }
    
    public void setJsonData(String data) {
        this.jsonData = data;
    }
    
    public String getJsonData() {
        return jsonData;
    }
    
    public void clear() {
        this.jsonData = null;
    }
}