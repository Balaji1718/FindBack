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

    private static final String API_URL = "https://integrate.api.nvidia.com/v1/chat/completions";
    // 🔥 FIXED: Using correct model identifier to prevent 404 errors
    private static final String PRIMARY_MODEL = "meta/llama-3.1-8b-instruct"; 
    
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface ChatCallback {
        void onSuccess(String response);
        void onFailure(String error);
    }

    public void sendMessageStructured(String context, List<ChatMessage> history, String userPrompt, ChatCallback callback) {
        executorService.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(API_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + BuildConfig.NVIDIA_API_KEY);
                conn.setDoOutput(true);
                
                // Increased timeouts for stability
                conn.setConnectTimeout(60000);
                conn.setReadTimeout(60000);

                JSONObject jsonBody = new JSONObject();
                jsonBody.put("model", PRIMARY_MODEL);
                jsonBody.put("temperature", 0.2);
                jsonBody.put("top_p", 0.7);
                jsonBody.put("max_tokens", 1024);

                JSONArray messages = new JSONArray();

                JSONObject systemMessage = new JSONObject();
                String systemPrompt = "You are a helpful Lost and Found AI assistant. " +
                    "Use the provided institution data to answer accurately. " +
                    "Respond using this structure:\nTitle:\nSummary:\nKey Points:\nConclusion:\n\n" +
                    "Context:\n" + context;
                
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

                byte[] input = jsonBody.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(input, 0, input.length);
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
                    mainHandler.post(() -> callback.onFailure("AI Service Error: " + responseCode));
                }
            } catch (Exception e) {
                mainHandler.post(() -> callback.onFailure("Connection Failed: Network Error or Timeout"));
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    public void sendMessage(String userMessage, ChatCallback callback) {
        sendMessageStructured("No context provided.", new ArrayList<>(), userMessage, callback);
    }
}