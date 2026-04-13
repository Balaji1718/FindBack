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
import android.view.animation.AlphaAnimation;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AiChatActivity extends AppCompatActivity implements HistoryAdapter.OnHistoryClickListener {

    private LinearLayout welcomeLayout;
    private EditText messageInput;
    private RecyclerView chatRecyclerView, historyRecyclerView;
    private TextView welcomeText;
    private ImageButton sendBtn;
    private ChatAdapter chatAdapter;
    private HistoryAdapter historyAdapter;
    private DrawerLayout drawerLayout;

    private final NvidiaApiService nvidiaApiService = new NvidiaApiService();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    
    private List<ChatSession> sessionList = new ArrayList<>();
    private Map<String, List<ChatMessage>> chatHistoryMap = new HashMap<>();
    private String currentSessionId = null;
    
    private String currentUserRole = "user";
    private String institutionId = null;

    private static final String PREFS_NAME = "ai_chat_prefs";
    private static final String KEY_SESSIONS = "sessions";
    private static final String KEY_HISTORY = "history";

    private String pendingDownloadContent = "";
    private String pendingFormat = ""; // "pdf" or "word"
    
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    startDownloadProcess();
                } else {
                    Toast.makeText(this, "Storage permission is required to save reports", Toast.LENGTH_LONG).show();
                }
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

        drawerLayout = findViewById(R.id.drawerLayout);
        welcomeLayout = findViewById(R.id.welcomeLayout);
        messageInput = findViewById(R.id.messageInput);
        chatRecyclerView = findViewById(R.id.chatRecyclerView);
        historyRecyclerView = findViewById(R.id.historyRecyclerView);
        welcomeText = findViewById(R.id.welcomeText);
        sendBtn = findViewById(R.id.sendBtn);

        chatAdapter = new ChatAdapter();
        chatRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        chatRecyclerView.setAdapter(chatAdapter);

        chatAdapter.setOnDownloadClickListener(new ChatAdapter.OnDownloadClickListener() {
            @Override
            public void onDownloadPdf(String content) {
                pendingDownloadContent = content;
                pendingFormat = "pdf";
                checkPermissionAndDownload();
            }

            @Override
            public void onDownloadWord(String content) {
                pendingDownloadContent = content;
                pendingFormat = "word";
                checkPermissionAndDownload();
            }
        });

        historyAdapter = new HistoryAdapter(this);
        historyRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        historyRecyclerView.setAdapter(historyAdapter);

        loadSavedData();
        reloadUserData(null);

        findViewById(R.id.menuIcon).setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        messageInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 0 && welcomeLayout.getVisibility() == View.VISIBLE) {
                    hideWelcome();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        sendBtn.setOnClickListener(v -> sendMessage());

        View.OnClickListener newChatAction = v -> startNewChat();

        findViewById(R.id.newChatBtn).setOnClickListener(newChatAction);
        findViewById(R.id.drawerNewChatBtn).setOnClickListener(newChatAction);
    }

    private void checkPermissionAndDownload() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startDownloadProcess();
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                startDownloadProcess();
            } else {
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }
    }

    private void startDownloadProcess() {
        String fileName = "Report_" + System.currentTimeMillis();
        if ("pdf".equals(pendingFormat)) {
            createPdfLauncher.launch(fileName + ".pdf");
        } else {
            createWordLauncher.launch(fileName + ".doc");
        }
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
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0, 1.2f)
                .setIncludePad(false)
                .build();

        canvas.translate(40, 40);
        staticLayout.draw(canvas);
        
        document.finishPage(page);

        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
            document.writeTo(os);
            Toast.makeText(this, "PDF saved successfully", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Log.e("AiChatActivity", "Error saving PDF", e);
            Toast.makeText(this, "Failed to save PDF", Toast.LENGTH_SHORT).show();
        } finally {
            document.close();
        }
    }

    private void saveWord(Uri uri, String content) {
        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
            os.write(content.getBytes());
            Toast.makeText(this, "Word document saved successfully", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Log.e("AiChatActivity", "Error saving Word", e);
            Toast.makeText(this, "Failed to save Word document", Toast.LENGTH_SHORT).show();
        }
    }

    private void reloadUserData(Runnable onComplete) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            String uid = auth.getCurrentUser().getUid();
            FirebaseFirestore.getInstance().collection("users")
                    .document(uid)
                    .get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            institutionId = doc.getString("institutionId");
                            currentUserRole = doc.getString("role");
                            if (currentUserRole == null) currentUserRole = "user";
                            
                            String name = doc.getString("name");
                            if (name != null) {
                                welcomeText.setText("Hey " + name + ", ready to dive in?");
                            }
                        }
                        if (onComplete != null) onComplete.run();
                    })
                    .addOnFailureListener(e -> {
                        if (onComplete != null) onComplete.run();
                    });
        } else {
            if (onComplete != null) onComplete.run();
        }
    }

    private void loadSavedData() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        Gson gson = new Gson();

        // Separate storage per user
        String sessionsJson = prefs.getString(KEY_SESSIONS + "_" + uid, null);
        if (sessionsJson != null) {
            Type type = new TypeToken<ArrayList<ChatSession>>() {}.getType();
            sessionList = gson.fromJson(sessionsJson, type);
        } else {
            sessionList = new ArrayList<>();
        }

        String historyJson = prefs.getString(KEY_HISTORY + "_" + uid, null);
        if (historyJson != null) {
            Type type = new TypeToken<HashMap<String, List<ChatMessage>>>() {}.getType();
            chatHistoryMap = gson.fromJson(historyJson, type);
        } else {
            chatHistoryMap = new HashMap<>();
        }

        if (sessionList.isEmpty()) {
            loadDefaultHistory();
        } else {
            updateHistoryUI();
        }
    }

    private void saveData() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        Gson gson = new Gson();

        // Separate storage per user
        editor.putString(KEY_SESSIONS + "_" + uid, gson.toJson(sessionList));
        editor.putString(KEY_HISTORY + "_" + uid, gson.toJson(chatHistoryMap));
        editor.apply();
    }

    private void loadDefaultHistory() {
        sessionList.clear();
        addSessionToHistory("1", "App Issue Summary", "admin");
        addSessionToHistory("2", "AI Admin Assistant", "admin");
        addSessionToHistory("3", "User Reporting Feature", "admin");

        List<ChatMessage> m1 = new ArrayList<>();
        m1.add(new ChatMessage("Summary of app issues", ChatMessage.TYPE_USER));
        m1.add(new ChatMessage("The main issue reported by users is the lag in loading the item list.", ChatMessage.TYPE_AI));
        chatHistoryMap.put("1", m1);

        List<ChatMessage> m2 = new ArrayList<>();
        m2.add(new ChatMessage("How can you help me?", ChatMessage.TYPE_USER));
        m2.add(new ChatMessage("I can help you manage claims and track items.", ChatMessage.TYPE_AI));
        chatHistoryMap.put("2", m2);

        List<ChatMessage> m3 = new ArrayList<>();
        m3.add(new ChatMessage("Details on reporting feature", ChatMessage.TYPE_USER));
        m3.add(new ChatMessage("Users can now upload photos with their reports.", ChatMessage.TYPE_AI));
        chatHistoryMap.put("3", m3);

        updateHistoryUI();
        saveData();
    }

    private void startNewChat() {
        currentSessionId = null;
        historyAdapter.setSelectedSessionId(null);
        messageInput.setText("");
        
        if (chatAdapter.getItemCount() > 0) {
            AlphaAnimation fadeOut = new AlphaAnimation(1.0f, 0.0f);
            fadeOut.setDuration(150);
            fadeOut.setAnimationListener(new android.view.animation.Animation.AnimationListener() {
                @Override public void onAnimationStart(android.view.animation.Animation animation) {}
                @Override public void onAnimationEnd(android.view.animation.Animation animation) {
                    chatAdapter.clearMessages();
                    showWelcome();
                }
                @Override public void onAnimationRepeat(android.view.animation.Animation animation) {}
            });
            chatRecyclerView.startAnimation(fadeOut);
        } else {
            showWelcome();
        }

        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        }
    }

    private void addSessionToHistory(String id, String title, String userId) {
        ChatSession session = new ChatSession(id, title, userId);
        sessionList.add(0, session);
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
        input.setSelection(input.getText().length());
        
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(60, 20, 60, 0);
        input.setLayoutParams(lp);
        container.addView(input);

        new AlertDialog.Builder(this)
                .setTitle("Rename Chat")
                .setView(container)
                .setPositiveButton("Rename", (d, w) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        updateLocalSessionTitle(session.getSessionId(), newName);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onDeleteClick(ChatSession session) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Chat?")
                .setMessage("Are you sure you want to delete '" + session.getTitle() + "'?")
                .setPositiveButton("Delete", (d, w) -> {
                    ChatSession toRemove = null;
                    for (ChatSession s : sessionList) {
                        if (s.getSessionId().equals(session.getSessionId())) {
                            toRemove = s;
                            break;
                        }
                    }

                    if (toRemove != null) {
                        sessionList.remove(toRemove);
                        chatHistoryMap.remove(toRemove.getSessionId());
                        if (toRemove.getSessionId().equals(currentSessionId)) {
                            startNewChat();
                        }
                        updateHistoryUI();
                        saveData();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateLocalSessionTitle(String id, String newTitle) {
        for (ChatSession s : sessionList) {
            if (s.getSessionId().equals(id)) {
                s.setTitle(newTitle);
                break;
            }
        }
        updateHistoryUI();
        saveData();
    }

    private void updateHistoryUI() {
        historyAdapter.setSessions(new ArrayList<>(sessionList));
    }

    private void hideWelcome() {
        if (welcomeLayout.getVisibility() == View.GONE) return;
        welcomeLayout.setVisibility(View.GONE);
    }

    private void showWelcome() {
        welcomeLayout.setVisibility(View.VISIBLE);
        welcomeLayout.setAlpha(1f);
    }

    private void sendMessage() {
        String text = messageInput.getText().toString().trim();
        if (text.isEmpty()) return;

        // Force reload user data before checking permissions
        reloadUserData(() -> {
            boolean isReportQuery = text.toLowerCase().contains("report") || text.toLowerCase().contains("analytics");
            
            if (!"admin".equals(currentUserRole) && isReportQuery) {
                ChatMessage userMsg = new ChatMessage(text, ChatMessage.TYPE_USER);
                chatAdapter.addMessage(userMsg);
                saveMessageToCurrentSession(userMsg);
                
                ChatMessage blockMsg = new ChatMessage("This feature is for administrative use only. Please contact an admin for formal reports.", ChatMessage.TYPE_AI);
                chatAdapter.addMessage(blockMsg);
                saveMessageToCurrentSession(blockMsg);
                messageInput.setText("");
                chatRecyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
                return;
            }

            executeAISend(text);
        });
    }

    private void executeAISend(String text) {
        final String userQuery = text.toLowerCase();
        messageInput.setText("");
        hideWelcome();

        if (currentSessionId == null) {
            currentSessionId = UUID.randomUUID().toString();
            String title = text.length() > 25 ? text.substring(0, 22) + "..." : text;
            addSessionToHistory(currentSessionId, title, currentUserRole);
            chatHistoryMap.put(currentSessionId, new ArrayList<>());
            historyAdapter.setSelectedSessionId(currentSessionId);
        } else {
            moveCurrentSessionToTop();
        }

        ChatMessage userMsg = new ChatMessage(text, ChatMessage.TYPE_USER);
        chatAdapter.addMessage(userMsg);
        saveMessageToCurrentSession(userMsg);
        
        chatRecyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);

        ChatMessage loadingMsg = new ChatMessage("Thinking...", ChatMessage.TYPE_LOADING);
        chatAdapter.addMessage(loadingMsg);
        chatRecyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);

        // Fetch context only if Admin or if it's a general non-report query for users
        InstitutionContextProvider.load(institutionId, context -> {
            String roleContext = "User Role: " + currentUserRole + "\n";
            
            // If user, hide sensitive institution metrics from AI context
            if (!"admin".equals(currentUserRole)) {
                roleContext += "System Data access limited. Only provide general guidance.";
            } else {
                roleContext += context;
            }
            
            int MAX_HISTORY = 50;
            List<ChatMessage> history = chatHistoryMap.get(currentSessionId);
            List<ChatMessage> limitedHistory = new ArrayList<>();
            if (history != null) {
                int start = Math.max(0, history.size() - MAX_HISTORY);
                limitedHistory.addAll(history.subList(start, history.size()));
            }

            nvidiaApiService.sendMessageStructured(roleContext, limitedHistory, text, new NvidiaApiService.ChatCallback() {
                @Override
                public void onSuccess(String response) {
                    chatAdapter.removeLoadingMessage();
                    String sanitized = sanitizeAiOutput(response);
                    ChatMessage aiMsg = new ChatMessage(sanitized, ChatMessage.TYPE_AI);
                    
                    // Format detection - only allowed for admins
                    if ("admin".equals(currentUserRole)) {
                        boolean wantsPdf = userQuery.contains("pdf");
                        boolean wantsWord = userQuery.contains("word") || userQuery.contains("doc");
                        boolean isLikelyData = userQuery.contains("report") || userQuery.contains("data") || 
                                             userQuery.contains("summary") || userQuery.contains("analytics") || 
                                             userQuery.contains("overview") || userQuery.contains("id") || 
                                             userQuery.contains("how many") || userQuery.contains("stats");
                        
                        boolean aiGaveTable = response.contains("|") || response.contains("---");

                        if (wantsPdf) {
                            aiMsg.setOfferPdf(true);
                        } else if (wantsWord) {
                            aiMsg.setOfferWord(true);
                        } else if (isLikelyData || aiGaveTable) {
                            aiMsg.setOfferPdf(true);
                            aiMsg.setOfferWord(true);
                        }
                    }

                    chatAdapter.addMessage(aiMsg);
                    saveMessageToCurrentSession(aiMsg);
                    chatRecyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
                    updateHistoryUI();
                    saveData();
                }

                @Override
                public void onFailure(String error) {
                    chatAdapter.removeLoadingMessage();
                    ChatMessage errorMsg = new ChatMessage("AI Error: " + error, ChatMessage.TYPE_AI);
                    chatAdapter.addMessage(errorMsg);
                    saveMessageToCurrentSession(errorMsg);
                    chatRecyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
                    saveData();
                }
            });
        });
    }

    private String sanitizeAiOutput(String input) {
        if (input == null) return "";
        String result = input.replace("**", "").replace("__", "");
        return result.trim();
    }

    private void moveCurrentSessionToTop() {
        ChatSession activeSession = null;
        for (ChatSession s : sessionList) {
            if (s.getSessionId().equals(currentSessionId)) {
                activeSession = s;
                break;
            }
        }
        if (activeSession != null) {
            sessionList.remove(activeSession);
            sessionList.add(0, activeSession);
        }
    }

    private void saveMessageToCurrentSession(ChatMessage msg) {
        if (currentSessionId != null) {
            List<ChatMessage> list = chatHistoryMap.get(currentSessionId);
            if (list == null) {
                list = new ArrayList<>();
                chatHistoryMap.put(currentSessionId, list);
            }
            list.add(msg);
        }
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
}