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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NvidiaApiService {

    private static final String TAG = "NvidiaAI";
    private static final String API_URL = "https://integrate.api.nvidia.com/v1/chat/completions";
    private static final String PRIMARY_MODEL = "meta/llama-3.1-8b-instruct"; 
    
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface ChatCallback {
        void onSuccess(String response);
        void onFailure(String error);
    }

    public void sendMessageStructured(String context, List<ChatMessage> history, String userPrompt, ChatCallback callback) {
        String apiKey = ApiConfig.getApiKey(BuildConfig.NVIDIA_API_KEY, ApiConfig.NVIDIA_API_KEY);
        if (apiKey == null) {
            mainHandler.post(() -> callback.onFailure("NVIDIA API Key missing"));
            return;
        }

        executorService.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(API_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setDoOutput(true);
                
                conn.setConnectTimeout(60000);
                conn.setReadTimeout(60000);

                JSONObject jsonBody = new JSONObject();
                jsonBody.put("model", PRIMARY_MODEL);
                jsonBody.put("temperature", 0.4);
                jsonBody.put("top_p", 0.85);
                jsonBody.put("max_tokens", 1500);

                JSONArray messages = new JSONArray();
                JSONObject systemMessage = new JSONObject();
                
                String systemPrompt = "You are 'FindBack AI', the smart coordinator for the FindBack Lost and Found system.\n\n" +
                    "APP NAVIGATION GUIDE:\n" +
                    "- 'Home': Main feed with filters and Global Search bar.\n" +
                    "- 'Post' (Plus icon): Where users report lost or found items.\n" +
                    "- 'Claims': Section to track your claims or manage requests you received.\n" +
                    "- 'AI Chat': This interface (supports PDF/Word downloads for admins).\n\n" +
                    "INTELLIGENCE PROTOCOL:\n" +
                    "1. PROACTIVE MATCHING: If a user lost something, scan [RECENTLY POSTED ITEMS] and suggest potential matches immediately.\n" +
                    "2. ADMIN DOWNLOAD TRIGGERS: To allow admins to download reports, you MUST include 'Summary:', 'Detailed Breakdown:', and 'Total Items' as headers.\n" +
                    "3. GLOBAL SEARCH ADVICE: If an item isn't in the context (top 60), advise: 'Use the Search bar on the Home screen to check the full archive.'\n" +
                    "4. INTENT OVER SYNTAX: Typos and brief queries like 'lost watch' should be treated as full requests for help.\n" +
                    "5. TONE: Empathetic for users, data-driven and professional for admins.\n\n" +
                    "--- INSTITUTION CONTEXT ---\n" + context;
                    
                systemMessage.put("role", "system");
                systemMessage.put("content", systemPrompt);
                messages.put(systemMessage);

                for (ChatMessage chat : history) {
                    if (chat.getType() == ChatMessage.TYPE_LOADING) continue;
                    JSONObject msg = new JSONObject();
                    msg.put("role", chat.getType() == ChatMessage.TYPE_USER ? "user" : "assistant");
                    msg.put("content", chat.getMessage());
                    messages.put(msg);
                }

                JSONObject userMessage = new JSONObject();
                userMessage.put("role", "user");
                userMessage.put("content", userPrompt);
                messages.put(userMessage);

                jsonBody.put("messages", messages);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(jsonBody.toString().getBytes(StandardCharsets.UTF_8));
                }

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) response.append(line);
                    in.close();

                    JSONObject jsonResponse = new JSONObject(response.toString());
                    String aiResponse = jsonResponse.getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content");

                    mainHandler.post(() -> callback.onSuccess(aiResponse));
                } else {
                    mainHandler.post(() -> callback.onFailure("Error: " + responseCode));
                }
            } catch (Exception e) {
                Log.e(TAG, "API Exception", e);
                mainHandler.post(() -> callback.onFailure("Network Error"));
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    public void sendMessage(String userMessage, ChatCallback callback) {
        sendMessageStructured("Basic context.", new ArrayList<>(), userMessage, callback);
    }
}
