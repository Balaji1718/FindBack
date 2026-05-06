package com.balaji.findback;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.balaji.findback.utils.NetworkUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AiChatActivity extends BaseActivity implements HistoryAdapter.OnHistoryClickListener {

    private static final String TAG = "AiChatActivity";
    private LinearLayout welcomeLayout;
    private EditText messageInput;
    private RecyclerView chatRecyclerView;
    private TextView welcomeText, noHistoryText;
    private ImageButton sendBtn;
    private ChatAdapter chatAdapter;
    private HistoryAdapter historyAdapter;
    private DrawerLayout drawerLayout;

    private final NvidiaApiService nvidiaApiService = new NvidiaApiService();
    private final GroqApiService groqApiService = new GroqApiService();
    private final OpenRouterApiService openRouterApiService = new OpenRouterApiService();
    private final CohereApiService cohereApiService = new CohereApiService();
    
    private List<ChatSession> sessionList = new ArrayList<>();
    private Map<String, List<ChatMessage>> chatHistoryMap = new HashMap<>();
    private String currentSessionId = null;
    
    private String institutionId = null;
    private FirebaseFirestore db;

    private static final String PREFS_NAME = "ai_chat_prefs";
    private static final String KEY_SESSIONS = "sessions";
    private static final String KEY_HISTORY = "history";

    private String pendingDownloadContent = "";
    private String pendingFormat = ""; 
    
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) startDownloadProcess();
                else Toast.makeText(this, "Storage permission required", Toast.LENGTH_LONG).show();
            });

    private final ActivityResultLauncher<String> createPdfLauncher = 
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/pdf"), uri -> {
                if (uri != null) savePdf(uri, pendingDownloadContent);
            });

    private final ActivityResultLauncher<String> createWordLauncher = 
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/msword"), uri -> {
                if (uri != null) saveWord(uri, pendingDownloadContent);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_chat);

        db = FirebaseFirestore.getInstance();

        SharedPreferences appPrefs = getSharedPreferences("app", MODE_PRIVATE);
        institutionId = appPrefs.getString("institutionId", null);

        drawerLayout = findViewById(R.id.drawerLayout);
        welcomeLayout = findViewById(R.id.welcomeLayout);
        messageInput = findViewById(R.id.messageInput);
        chatRecyclerView = findViewById(R.id.chatRecyclerView);
        RecyclerView historyRecyclerView = findViewById(R.id.historyRecyclerView);
        welcomeText = findViewById(R.id.welcomeText);
        noHistoryText = findViewById(R.id.noHistoryText);
        sendBtn = findViewById(R.id.sendBtn);

        chatAdapter = new ChatAdapter();
        chatRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        chatRecyclerView.setAdapter(chatAdapter);

        chatAdapter.setOnDownloadClickListener(new ChatAdapter.OnDownloadClickListener() {
            @Override public void onDownloadPdf(String content) { 
                pendingDownloadContent = content; pendingFormat = "pdf"; checkPermissionAndDownload(); 
            }
            @Override public void onDownloadWord(String content) { 
                pendingDownloadContent = content; pendingFormat = "word"; checkPermissionAndDownload(); 
            }
        });

        historyAdapter = new HistoryAdapter(this);
        historyRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        historyRecyclerView.setAdapter(historyAdapter);

        loadSavedData();
        refreshUserData();

        findViewById(R.id.menuIcon).setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        messageInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 0 && welcomeLayout.getVisibility() == View.VISIBLE) hideWelcome();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        sendBtn.setOnClickListener(v -> sendMessage());
        findViewById(R.id.newChatBtn).setOnClickListener(v -> startNewChat());
        findViewById(R.id.drawerNewChatBtn).setOnClickListener(v -> startNewChat());
    }

    private void checkPermissionAndDownload() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startDownloadProcess();
        else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                startDownloadProcess();
            } else {
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }
    }

    private void startDownloadProcess() {
        String fileName = "Report_" + System.currentTimeMillis();
        if ("pdf".equals(pendingFormat)) createPdfLauncher.launch(fileName + ".pdf");
        else createWordLauncher.launch(fileName + ".doc");
    }

    private void savePdf(Uri uri, String content) {
        PdfDocument document = new PdfDocument();
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();
        TextPaint paint = new TextPaint();
        paint.setTextSize(12);
        paint.setColor(android.graphics.Color.BLACK);
        StaticLayout staticLayout = StaticLayout.Builder.obtain(content, 0, content.length(), paint, 515)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0, 1.2f).setIncludePad(false).build();
        canvas.translate(40, 40);
        staticLayout.draw(canvas);
        document.finishPage(page);
        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
            document.writeTo(os);
            Toast.makeText(this, "PDF saved successfully", Toast.LENGTH_SHORT).show();
        } catch (IOException e) { document.close(); }
    }

    private void saveWord(Uri uri, String content) {
        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
            os.write(content.getBytes());
            Toast.makeText(this, "Word document saved successfully", Toast.LENGTH_SHORT).show();
        } catch (IOException e) { Log.e(TAG, "Error saving Word", e); }
    }

    private void refreshUserData() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            db.collection("users").document(auth.getUid()).get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            institutionId = doc.getString("institutionId");
                            userRole = doc.getString("role");
                            if (userRole == null) userRole = "user";
                            String name = doc.getString("name");
                            if (name != null && welcomeText != null) welcomeText.setText("Hey " + name + ", ready to dive in?");
                            getSharedPreferences("app", MODE_PRIVATE).edit().putString("institutionId", institutionId).apply();
                        }
                    });
        }
    }

    private void loadSavedData() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getUid();

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        Gson gson = new Gson();
        String sessionsJson = prefs.getString(KEY_SESSIONS + "_" + uid, null);
        if (sessionsJson != null) {
            try {
                Type type = new TypeToken<ArrayList<ChatSession>>() {}.getType();
                sessionList = gson.fromJson(sessionsJson, type);
            } catch (Exception e) { sessionList = new ArrayList<>(); }
        }
        
        String historyJson = prefs.getString(KEY_HISTORY + "_" + uid, null);
        if (historyJson != null) {
            try {
                Type type = new TypeToken<HashMap<String, List<ChatMessage>>>() {}.getType();
                chatHistoryMap = gson.fromJson(historyJson, type);
            } catch (Exception e) { chatHistoryMap = new HashMap<>(); }
        }

        updateHistoryUI();
        syncFromFirestore(uid);
    }

    private void syncFromFirestore(String uid) {
        db.collection("users").document(uid).collection("chat_sessions")
                .orderBy("lastTimestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) return;
                    
                    sessionList.clear();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        ChatSession session = doc.toObject(ChatSession.class);
                        if (session != null) {
                            sessionList.add(session);
                            loadMessagesFromFirestore(uid, session.getSessionId());
                        }
                    }
                    updateHistoryUI();
                    saveLocalCache();
                });
    }

    private void loadMessagesFromFirestore(String uid, String sessionId) {
        db.collection("users").document(uid).collection("chat_sessions")
                .document(sessionId).collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(query -> {
                    List<ChatMessage> messages = new ArrayList<>();
                    for (DocumentSnapshot doc : query) {
                        ChatMessage msg = doc.toObject(ChatMessage.class);
                        if (msg != null) messages.add(msg);
                    }
                    chatHistoryMap.put(sessionId, messages);
                    if (sessionId.equals(currentSessionId)) {
                        chatAdapter.setMessages(new ArrayList<>(messages));
                    }
                    saveLocalCache();
                });
    }

    private void saveLocalCache() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getUid();
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        Gson gson = new Gson();
        editor.putString(KEY_SESSIONS + "_" + uid, gson.toJson(sessionList));
        editor.putString(KEY_HISTORY + "_" + uid, gson.toJson(chatHistoryMap));
        editor.apply();
    }

    private void saveData(String sessionId) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null || sessionId == null) return;
        String uid = auth.getUid();

        saveLocalCache();

        ChatSession targetSession = null;
        for (ChatSession s : sessionList) {
            if (s.getSessionId().equals(sessionId)) {
                targetSession = s;
                break;
            }
        }

        if (targetSession != null) {
            db.collection("users").document(uid).collection("chat_sessions")
                    .document(sessionId).set(targetSession);
            
            List<ChatMessage> messages = chatHistoryMap.get(sessionId);
            if (messages != null) {
                WriteBatch batch = db.batch();
                for (int i = 0; i < messages.size(); i++) {
                    ChatMessage m = messages.get(i);
                    batch.set(db.collection("users").document(uid).collection("chat_sessions")
                            .document(sessionId).collection("messages").document(String.valueOf(i)), m);
                }
                batch.commit();
            }
        }
    }

    private void startNewChat() {
        currentSessionId = null;
        historyAdapter.setSelectedSessionId(null);
        messageInput.setText("");
        chatAdapter.clearMessages();
        showWelcome();
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) drawerLayout.closeDrawer(GravityCompat.START);
    }

    private void addSessionToHistory(String id, String title, String userId) {
        ChatSession session = new ChatSession(id, title, userId, institutionId);
        sessionList.add(0, session);
        saveData(id);
    }

    @Override
    public void onChatClick(ChatSession session) {
        if (session.getSessionId().equals(currentSessionId)) {
            drawerLayout.closeDrawer(GravityCompat.START);
            return;
        }
        currentSessionId = session.getSessionId();
        historyAdapter.setSelectedSessionId(currentSessionId);
        List<ChatMessage> messages = chatHistoryMap.get(currentSessionId);
        
        if (messages != null && !messages.isEmpty()) {
            welcomeLayout.setVisibility(View.GONE);
            chatAdapter.setMessages(new ArrayList<>(messages));
            chatRecyclerView.scrollToPosition(chatAdapter.getItemCount() - 1);
        } else {
            chatAdapter.clearMessages();
            showWelcome();
        }
        drawerLayout.closeDrawer(GravityCompat.START);
    }

    @Override
    public void onRenameClick(ChatSession session) {
        EditText input = new EditText(this);
        input.setText(session.getTitle());
        new AlertDialog.Builder(this).setTitle("Rename Chat").setView(input)
                .setPositiveButton("Rename", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        for (ChatSession s : sessionList) {
                            if (s.getSessionId().equals(session.getSessionId())) {
                                s.setTitle(name);
                                break;
                            }
                        }
                        updateHistoryUI();
                        saveData(session.getSessionId());
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    @Override
    public void onDeleteClick(ChatSession session) {
        new AlertDialog.Builder(this).setTitle("Delete Chat?").setMessage("Are you sure?")
                .setPositiveButton("Delete", (d, w) -> {
                    ChatSession toRemove = null;
                    for (ChatSession s : sessionList) {
                        if (s.getSessionId().equals(session.getSessionId())) {
                            toRemove = s;
                            break;
                        }
                    }
                    if (toRemove != null) {
                        String id = toRemove.getSessionId();
                        sessionList.remove(toRemove);
                        chatHistoryMap.remove(id);
                        if (id.equals(currentSessionId)) startNewChat();
                        String uid = FirebaseAuth.getInstance().getUid();
                        if (uid != null) {
                            db.collection("users").document(uid).collection("chat_sessions").document(id).delete();
                        }
                        updateHistoryUI();
                        saveLocalCache();
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    private void updateHistoryUI() {
        if (noHistoryText != null) noHistoryText.setVisibility(sessionList.isEmpty() ? View.VISIBLE : View.GONE);
        historyAdapter.setSessions(new ArrayList<>(sessionList));
    }

    private void hideWelcome() { welcomeLayout.setVisibility(View.GONE); }
    private void showWelcome() { welcomeLayout.setVisibility(View.VISIBLE); }

    private void sendMessage() {
        if (!NetworkUtils.isNetworkAvailable(this)) {
            Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show();
            return;
        }
        String text = messageInput.getText().toString().trim();
        if (text.isEmpty()) return;
        executeAISendChain(text);
    }

    private void executeAISendChain(String text) {
        final String requestSessionId;
        messageInput.setText("");
        hideWelcome();

        if (currentSessionId == null) {
            currentSessionId = UUID.randomUUID().toString();
            addSessionToHistory(currentSessionId, text.length() > 25 ? text.substring(0, 22) + "..." : text, FirebaseAuth.getInstance().getUid());
            chatHistoryMap.put(currentSessionId, new ArrayList<>());
            historyAdapter.setSelectedSessionId(currentSessionId);
            updateHistoryUI();
        }
        
        requestSessionId = currentSessionId;
        ChatMessage userMsg = new ChatMessage(text, ChatMessage.TYPE_USER);
        chatAdapter.addMessage(userMsg);
        saveMessageToCurrentSession(userMsg);
        
        ChatMessage loadingMsg = new ChatMessage("Thinking...", ChatMessage.TYPE_LOADING);
        chatAdapter.addMessage(loadingMsg);
        chatRecyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);

        String finalInstId = institutionId != null ? institutionId : "default";

        // 🔥 RELEASE FIX: If Firestore takes more than 3 seconds, proceed with partial context 
        // to prevent App Check from hanging the AI assistant.
        final boolean[] contextLoaded = {false};
        new android.os.Handler().postDelayed(() -> {
            if (!contextLoaded[0]) {
                contextLoaded[0] = true;
                callNvidia("Offline/Cached Mode", requestSessionId, text);
            }
        }, 3000);

        InstitutionContextProvider.load(finalInstId, context -> {
            if (!contextLoaded[0]) {
                contextLoaded[0] = true;
                String roleContext = "User Role: " + userRole + "\n" + ("admin".equals(userRole) ? context : "Limited info.");
                callNvidia(roleContext, requestSessionId, text);
            }
        });
    }

    private void handleAiSuccess(String response, String userPrompt) {
        chatAdapter.removeLoadingMessage();
        ChatMessage aiMsg = new ChatMessage(response, ChatMessage.TYPE_AI);
        chatAdapter.addMessage(aiMsg);
        saveMessageToCurrentSession(aiMsg);
        chatRecyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
        saveData(currentSessionId);
    }

    private void saveMessageToCurrentSession(ChatMessage msg) {
        if (currentSessionId != null) {
            List<ChatMessage> history = chatHistoryMap.get(currentSessionId);
            if (history == null) {
                history = new ArrayList<>();
                chatHistoryMap.put(currentSessionId, history);
            }
            history.add(msg);
        }
    }

    private void callNvidia(String context, String sessionId, String text) {
        nvidiaApiService.sendMessageStructured(context, chatAdapter.getMessages(), text, new NvidiaApiService.ChatCallback() {
            @Override public void onSuccess(String response) { if (sessionId.equals(currentSessionId)) handleAiSuccess(response, text); }
            @Override public void onFailure(String error) { callGroq(context, sessionId, text); }
        });
    }

    private void callGroq(String context, String sessionId, String text) {
        groqApiService.sendMessage(context, chatAdapter.getMessages(), text, new GroqApiService.ChatCallback() {
            @Override public void onSuccess(String response) { if (sessionId.equals(currentSessionId)) handleAiSuccess(response, text); }
            @Override public void onFailure(String error) { callOpenRouter(context, sessionId, text); }
        });
    }

    private void callOpenRouter(String context, String sessionId, String text) {
        openRouterApiService.sendMessage(context, chatAdapter.getMessages(), text, new OpenRouterApiService.ChatCallback() {
            @Override public void onSuccess(String response) { if (sessionId.equals(currentSessionId)) handleAiSuccess(response, text); }
            @Override public void onFailure(String error) { callCohere(context, sessionId, text); }
        });
    }

    private void callCohere(String context, String sessionId, String text) {
        cohereApiService.sendMessage(context, chatAdapter.getMessages(), text, new CohereApiService.ChatCallback() {
            @Override public void onSuccess(String response) { if (sessionId.equals(currentSessionId)) handleAiSuccess(response, text); }
            @Override public void onFailure(String error) { 
                if (sessionId.equals(currentSessionId)) {
                    chatAdapter.removeLoadingMessage();
                    Toast.makeText(AiChatActivity.this, "All AI services failed. Check internet.", Toast.LENGTH_LONG).show();
                }
            }
        });
    }
}
