package com.balaji.findback;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GroqApiService {

    private static final String TAG = "GroqAI";
    private static final String API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL = "llama3-70b-8192";
    
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface ChatCallback {
        void onSuccess(String response);
        void onFailure(String error);
    }

    public void sendMessage(String context, List<ChatMessage> history, String userPrompt, ChatCallback callback) {
        String apiKey = ApiConfig.getApiKey(BuildConfig.GROQ_API_KEY, ApiConfig.GROQ_API_KEY);
        if (apiKey == null) {
            mainHandler.post(() -> callback.onFailure("GROQ API Key missing"));
            return;
        }

        executorService.execute(() -> {
            try {
                URL url = new URL(API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setDoOutput(true);
                
                // RELEASE HARDENING: Consistent timeouts
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(30000);

                JSONObject jsonBody = new JSONObject();
                jsonBody.put("model", MODEL);

                JSONArray messages = new JSONArray();
                String systemPrompt = "You are a helpful Lost and Found AI. Use the provided institution data to answer accurately. Respond concisely.\nContext:\n" + context;
                messages.put(new JSONObject().put("role", "system").put("content", systemPrompt));

                for (ChatMessage chat : history) {
                    if (chat.getType() == ChatMessage.TYPE_LOADING) continue;
                    messages.put(new JSONObject()
                            .put("role", chat.getType() == ChatMessage.TYPE_USER ? "user" : "assistant")
                            .put("content", chat.getMessage()));
                }

                messages.put(new JSONObject().put("role", "user").put("content", userPrompt));
                jsonBody.put("messages", messages);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(jsonBody.toString().getBytes(StandardCharsets.UTF_8));
                }

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) response.append(line);
                    in.close();

                    String aiResponse = new JSONObject(response.toString())
                            .getJSONArray("choices").getJSONObject(0)
                            .getJSONObject("message").getString("content");

                    mainHandler.post(() -> callback.onSuccess(aiResponse));
                } else {
                    mainHandler.post(() -> callback.onFailure("Service Error: " + responseCode));
                }
            } catch (Exception e) {
                Log.e(TAG, "Groq Error", e);
                mainHandler.post(() -> callback.onFailure("Network Error"));
            }
        });
    }
}
