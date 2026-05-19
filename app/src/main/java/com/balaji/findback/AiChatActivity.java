package com.balaji.findback;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.google.firebase.firestore.SetOptions;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    private final ExecutorService reportExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
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

        String roleFromIntent = getIntent().getStringExtra("userRole");
        if (roleFromIntent != null) userRole = roleFromIntent;

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
        String fileName = "Report_" + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(new Date());
        if ("pdf".equals(pendingFormat)) createPdfLauncher.launch(fileName + ".pdf");
        else createWordLauncher.launch(fileName + ".doc");
    }

    private void savePdf(Uri uri, String content) {
        reportExecutor.execute(() -> {
            PdfDocument document = new PdfDocument();
            int pageWidth = 595;
            int pageHeight = 842;
            int margin = 50;
            int usableWidth = pageWidth - (2 * margin);

            // Modern Typography Setup
            TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            titlePaint.setTextSize(26);
            titlePaint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
            titlePaint.setColor(android.graphics.Color.rgb(33, 33, 33));

            TextPaint headerPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            headerPaint.setTextSize(15);
            headerPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            headerPaint.setColor(android.graphics.Color.rgb(66, 66, 66));

            TextPaint bodyPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            bodyPaint.setTextSize(11.5f);
            bodyPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            bodyPaint.setColor(android.graphics.Color.rgb(75, 75, 75));

            TextPaint footerPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            footerPaint.setTextSize(9);
            footerPaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            footerPaint.setColor(android.graphics.Color.GRAY);

            Paint accentPaint = new Paint();
            accentPaint.setColor(android.graphics.Color.rgb(0, 102, 204));
            accentPaint.setStrokeWidth(2f);

            Paint linePaint = new Paint();
            linePaint.setColor(android.graphics.Color.LTGRAY);
            linePaint.setStrokeWidth(0.8f);

            String[] lines = content.split("\n");
            final int[] pageNumber = { 1 };
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber[0]).create();
            final PdfDocument.Page[] page = { document.startPage(pageInfo) };
            Canvas canvas = page[0].getCanvas();
            int currentY = margin;

            // Header Section
            canvas.drawText("OFFICIAL CAMPUS REPORT", margin, currentY - 10, footerPaint);
            canvas.drawLine(margin, currentY - 5, pageWidth - margin, currentY - 5, accentPaint);
            currentY += 10;

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) {
                    currentY += 12;
                    continue;
                }

                // Table Detection
                if (line.startsWith("|") && line.endsWith("|")) {
                    List<String[]> tableRows = new ArrayList<>();
                    while (i < lines.length && lines[i].trim().startsWith("|")) {
                        String rowStr = lines[i].trim();
                        if (!rowStr.contains("---")) {
                            String[] cells = rowStr.split("\\|");
                            List<String> cleanedCells = new ArrayList<>();
                            for (String c : cells) if (!c.isEmpty()) cleanedCells.add(c.trim());
                            tableRows.add(cleanedCells.toArray(new String[0]));
                        }
                        i++;
                    }
                    i--;

                    if (!tableRows.isEmpty()) {
                        currentY = drawTable(page, pageNumber, tableRows, margin, currentY, usableWidth, bodyPaint, linePaint, document, pageInfo);
                        canvas = page[0].getCanvas();
                        continue;
                    }
                }

                TextPaint currentPaint = bodyPaint;
                boolean isTitle = false;
                boolean isHeader = false;
                int xOffset = margin;

                if (line.startsWith("# ")) {
                    currentPaint = titlePaint;
                    line = line.substring(2).toUpperCase();
                    isTitle = true;
                } else if (line.startsWith("## ") || line.startsWith("### ")) {
                    currentPaint = headerPaint;
                    line = line.substring(line.indexOf(" ") + 1);
                    isHeader = true;
                } else if (line.startsWith("- ") || line.startsWith("* ")) {
                    xOffset += 15;
                    canvas.drawCircle(margin + 5, currentY + 7, 2, currentPaint);
                }

                StaticLayout sl = StaticLayout.Builder.obtain(line, 0, line.length(), currentPaint, usableWidth - (xOffset - margin))
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(0, 1.4f)
                        .build();

                if (currentY + sl.getHeight() > pageHeight - margin - 40) {
                    drawFooter(canvas, pageWidth, pageHeight, pageNumber[0]);
                    document.finishPage(page[0]);
                    pageNumber[0]++;
                    pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber[0]).create();
                    page[0] = document.startPage(pageInfo);
                    canvas = page[0].getCanvas();
                    currentY = margin;
                }

                canvas.save();
                canvas.translate(xOffset, currentY);
                sl.draw(canvas);
                canvas.restore();

                currentY += sl.getHeight() + 8;

                if (isTitle) {
                    currentY += 5;
                    canvas.drawLine(margin, currentY, margin + 60, currentY, accentPaint);
                    currentY += 15;
                } else if (isHeader) {
                    currentY += 4;
                }
            }

            drawFooter(canvas, pageWidth, pageHeight, pageNumber[0]);
            document.finishPage(page[0]);

            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                document.writeTo(os);
                mainHandler.post(() -> Toast.makeText(this, "Professional Report Generated", Toast.LENGTH_SHORT).show());
            } catch (IOException e) { 
                Log.e(TAG, "Error saving PDF", e);
                mainHandler.post(() -> Toast.makeText(this, "Failed to save report", Toast.LENGTH_SHORT).show());
            } finally {
                document.close();
            }
        });
    }

    private int drawTable(PdfDocument.Page[] pageRef, int[] pageNumberRef, List<String[]> rows, int startX, int startY, int width, TextPaint paint, Paint linePaint, PdfDocument doc, PdfDocument.PageInfo info) {
        int rowHeight = 28;
        int colCount = rows.get(0).length;
        int colWidth = width / colCount;
        int currentY = startY;
        Canvas canvas = pageRef[0].getCanvas();

        Paint headerBg = new Paint();
        headerBg.setColor(android.graphics.Color.rgb(245, 245, 245));

        TextPaint tableHeaderPaint = new TextPaint(paint);
        tableHeaderPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tableHeaderPaint.setTextSize(11);

        for (int r = 0; r < rows.size(); r++) {
            String[] row = rows.get(r);
            
            if (currentY + rowHeight > info.getPageHeight() - 60) {
                drawFooter(canvas, info.getPageWidth(), info.getPageHeight(), pageNumberRef[0]);
                doc.finishPage(pageRef[0]);
                pageNumberRef[0]++;
                pageRef[0] = doc.startPage(new PdfDocument.PageInfo.Builder(info.getPageWidth(), info.getPageHeight(), pageNumberRef[0]).create());
                canvas = pageRef[0].getCanvas();
                currentY = 50;
            }

            if (r == 0) {
                canvas.drawRect(startX, currentY, startX + width, currentY + rowHeight, headerBg);
            }

            for (int c = 0; c < colCount; c++) {
                String text = (c < row.length) ? row[c] : "";
                TextPaint p = (r == 0) ? tableHeaderPaint : paint;
                
                // Truncate text if too long for column
                float textWidth = p.measureText(text);
                if (textWidth > colWidth - 10) {
                    text = text.substring(0, Math.min(text.length(), 15)) + "...";
                }

                canvas.drawText(text, startX + (c * colWidth) + 8, currentY + 18, p);
            }
            
            canvas.drawLine(startX, currentY, startX + width, currentY, linePaint);
            currentY += rowHeight;
        }
        canvas.drawLine(startX, currentY, startX + width, currentY, linePaint);
        
        // Vertical lines
        for(int c=0; c<=colCount; c++){
            canvas.drawLine(startX + (c * colWidth), startY, startX + (c * colWidth), currentY, linePaint);
        }
        
        return currentY + 15;
    }

    private void drawFooter(Canvas canvas, int width, int height, int pageNum) {
        TextPaint footerPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        footerPaint.setTextSize(10);
        footerPaint.setColor(android.graphics.Color.GRAY);
        String dateStr = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(new Date());
        String footer = "Generated by FindBack AI Assistant | " + dateStr + " | Page " + pageNum;
        float footerWidth = footerPaint.measureText(footer);
        canvas.drawText(footer, (width - footerWidth) / 2, height - 25, footerPaint);
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
                })
                .addOnFailureListener(e -> fetchSessionsWithoutOrder(uid));
    }

    private void fetchSessionsWithoutOrder(String uid) {
        db.collection("users").document(uid).collection("chat_sessions")
                .get()
                .addOnSuccessListener(query -> {
                    sessionList.clear();
                    for (DocumentSnapshot doc : query) {
                        ChatSession session = doc.toObject(ChatSession.class);
                        if (session != null) {
                            sessionList.add(session);
                            loadMessagesFromFirestore(uid, session.getSessionId());
                        }
                    }
                    Collections.sort(sessionList, (a, b) -> Long.compare(b.getLastTimestamp(), a.getLastTimestamp()));
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

    private void saveSessionObject(String sessionId) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        ChatSession target = null;
        for (ChatSession s : sessionList) {
            if (s.getSessionId().equals(sessionId)) { target = s; break; }
        }
        if (target != null) {
            db.collection("users").document(uid).collection("chat_sessions").document(sessionId).set(target, SetOptions.merge());
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
        saveSessionObject(id);
        updateHistoryUI();
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

    @Override public void onRenameClick(ChatSession session) {
        EditText input = new EditText(this);
        input.setText(session.getTitle());
        new AlertDialog.Builder(this).setTitle("Rename Chat").setView(input)
                .setPositiveButton("Rename", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        for (ChatSession s : sessionList) {
                            if (s.getSessionId().equals(session.getSessionId())) { s.setTitle(name); break; }
                        }
                        updateHistoryUI();
                        saveSessionObject(session.getSessionId());
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    @Override public void onDeleteClick(ChatSession session) {
        new AlertDialog.Builder(this).setTitle("Delete Chat?").setMessage("Are you sure?")
                .setPositiveButton("Delete", (d, w) -> {
                    ChatSession toRemove = null;
                    for (ChatSession s : sessionList) {
                        if (s.getSessionId().equals(session.getSessionId())) { toRemove = s; break; }
                    }
                    if (toRemove != null) {
                        String id = toRemove.getSessionId();
                        sessionList.remove(toRemove);
                        chatHistoryMap.remove(id);
                        if (id.equals(currentSessionId)) startNewChat();
                        String uid = FirebaseAuth.getInstance().getUid();
                        if (uid != null) db.collection("users").document(uid).collection("chat_sessions").document(id).delete();
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
        boolean isAdmin = "admin".equals(userRole);
        final boolean[] contextLoaded = {false};
        new android.os.Handler().postDelayed(() -> {
            if (!contextLoaded[0]) {
                contextLoaded[0] = true;
                callNvidia("Offline/Cached Mode", requestSessionId, text);
            }
        }, 4000);
        InstitutionContextProvider.load(finalInstId, isAdmin, context -> {
            if (!contextLoaded[0]) {
                contextLoaded[0] = true;
                String roleContext = "User Role: " + userRole + "\n" + context;
                callNvidia(roleContext, requestSessionId, text);
            }
        });
    }

    private void handleAiSuccess(String response, String userPrompt) {
        chatAdapter.removeLoadingMessage();
        ChatMessage aiMsg = new ChatMessage(response, ChatMessage.TYPE_AI);
        if ("admin".equals(userRole)) {
            boolean isReport = response.contains("Summary:") || response.contains("Overview") || response.contains("Report") || response.contains("Total Items") || userPrompt.toLowerCase().contains("report");
            if (isReport) { aiMsg.setOfferPdf(true); aiMsg.setOfferWord(true); }
        }
        chatAdapter.addMessage(aiMsg);
        saveMessageToCurrentSession(aiMsg);
        chatRecyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
    }

    private void saveMessageToCurrentSession(ChatMessage msg) {
        if (currentSessionId != null) {
            List<ChatMessage> history = chatHistoryMap.get(currentSessionId);
            if (history == null) { history = new ArrayList<>(); chatHistoryMap.put(currentSessionId, history); }
            history.add(msg);
            for (ChatSession s : sessionList) {
                if (s.getSessionId().equals(currentSessionId)) { s.setLastTimestamp(msg.getTimestamp()); break; }
            }
            Collections.sort(sessionList, (a, b) -> Long.compare(b.getLastTimestamp(), a.getLastTimestamp()));
            updateHistoryUI();
            saveLocalCache();
            String uid = FirebaseAuth.getInstance().getUid();
            if (uid != null) {
                db.collection("users").document(uid).collection("chat_sessions").document(currentSessionId)
                        .collection("messages").document(String.valueOf(history.size() - 1)).set(msg);
                saveSessionObject(currentSessionId);
            }
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        reportExecutor.shutdown();
    }
}
