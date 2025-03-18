package com.example.ricksmorty;

import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class GoogleTranslateAPI {
    private static final String TAG = "GoogleTranslateAPI";
    // Using a free translation API for demonstration
    private static final String TRANSLATION_API_URL = "https://translate.googleapis.com/translate_a/single";

    public static String translate(String text, String sourceLang, String targetLang) {
        if (text == null || text.trim().isEmpty()) {
            Log.w(TAG, "Empty text provided for translation");
            return text;
        }

        try {
            Log.d(TAG, "Translating from " + sourceLang + " to " + targetLang + ": " + text);
            
            // For demo purposes, we're using a simple approach that doesn't require API keys
            // In a production app, you should use the official Google Cloud Translation API with proper authentication
            
            // Build the query URL
            String encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8.toString());
            String urlStr = TRANSLATION_API_URL + 
                    "?client=gtx" +
                    "&sl=" + sourceLang +
                    "&tl=" + targetLang +
                    "&dt=t" +
                    "&q=" + encodedText;
            
            URL url = new URL(urlStr);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            connection.setConnectTimeout(10000); // 10 seconds timeout
            connection.setReadTimeout(10000);    // 10 seconds read timeout
            
            // Read the response
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                
                // Parse the response - it comes in a nested JSON array format
                // Format is typically: [[["translated text","original text",null,null,1]],null,"en"]
                String responseStr = response.toString();
                Log.d(TAG, "Raw translation response: " + responseStr);
                
                // Simple parsing for demo purposes
                if (responseStr.contains("\"")) {
                    String translatedText = responseStr.split("\"")[1];
                    Log.d(TAG, "Translation result: " + translatedText);
                    
                    // Verify we got a valid translation
                    if (translatedText != null && !translatedText.isEmpty() && 
                        !translatedText.equals(text)) {
                        return translatedText;
                    } else {
                        Log.w(TAG, "Translation returned same or empty text");
                        return text; // Return original if translation is same or empty
                    }
                } else {
                    Log.e(TAG, "Unexpected response format: " + responseStr);
                    return text; // Return original if parsing fails
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Translation error: " + e.getMessage(), e);
            // Return the original text if translation fails
            return text;
        }
    }
}