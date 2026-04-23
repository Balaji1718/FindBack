package com.balaji.findback;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;

import com.balaji.findback.utils.ThemeManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class BaseActivity extends AppCompatActivity {

    protected View aiButton;
    protected String userRole = "user"; // Default
    private static boolean isFirstLaunch = true;
    
    // Static variables for transition
    private static Bitmap mOldBitmap = null;
    private static boolean mIsTransitioning = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);

        if (isFirstLaunch) {
            resetAiButtonPosition();
            isFirstLaunch = false;
        }
    }

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        // Start "Spatial Depth Zoom" transition if triggered
        if (mIsTransitioning && mOldBitmap != null) {
            performSpatialZoomTransition();
        }
    }

    private void performSpatialZoomTransition() {
        final ViewGroup root = (ViewGroup) getWindow().getDecorView();
        
        // 1. Prepare the OLD screen overlay
        final ImageView oldOverlay = new ImageView(this);
        oldOverlay.setImageBitmap(mOldBitmap);
        oldOverlay.setScaleType(ImageView.ScaleType.FIT_XY);
        root.addView(oldOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                ViewGroup.LayoutParams.MATCH_PARENT));

        mIsTransitioning = false;

        // 2. Prepare the NEW screen (root content)
        // We find the main content view (usually the first child of root that isn't the overlay)
        final View newContentView = root.getChildAt(0); 

        oldOverlay.post(() -> {
            // Animation for OLD screen: Scale UP and Fade OUT
            ObjectAnimator oldScaleX = ObjectAnimator.ofFloat(oldOverlay, View.SCALE_X, 1f, 1.2f);
            ObjectAnimator oldScaleY = ObjectAnimator.ofFloat(oldOverlay, View.SCALE_Y, 1f, 1.2f);
            ObjectAnimator oldAlpha = ObjectAnimator.ofFloat(oldOverlay, View.ALPHA, 1f, 0f);

            // Animation for NEW screen: Scale UP from 0.8 to 1.0 and Fade IN
            newContentView.setScaleX(0.8f);
            newContentView.setScaleY(0.8f);
            newContentView.setAlpha(0f);
            
            ObjectAnimator newScaleX = ObjectAnimator.ofFloat(newContentView, View.SCALE_X, 0.8f, 1f);
            ObjectAnimator newScaleY = ObjectAnimator.ofFloat(newContentView, View.SCALE_Y, 0.8f, 1f);
            ObjectAnimator newAlpha = ObjectAnimator.ofFloat(newContentView, View.ALPHA, 0f, 1f);

            AnimatorSet set = new AnimatorSet();
            set.playTogether(oldScaleX, oldScaleY, oldAlpha, newScaleX, newScaleY, newAlpha);
            set.setDuration(600);
            set.setInterpolator(new AccelerateDecelerateInterpolator());
            
            set.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    root.removeView(oldOverlay);
                    mOldBitmap = null;
                }
            });
            set.start();
        });
    }

    private void resetAiButtonPosition() {
        getSharedPreferences("ai_button_prefs", MODE_PRIVATE)
                .edit()
                .remove("pos_x")
                .remove("pos_y")
                .apply();
    }

    protected boolean shouldForceLightMode() {
        return false;
    }

    protected void setupToolbar(String title, boolean showBackButton) {
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(title);
                getSupportActionBar().setDisplayHomeAsUpEnabled(showBackButton);
            }
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem themeItem = menu.findItem(R.id.action_theme);
        if (themeItem != null) {
            if (shouldForceLightMode()) {
                themeItem.setVisible(false);
            } else {
                int currentTheme = ThemeManager.getSavedTheme(this);
                if (currentTheme == ThemeManager.DARK) {
                    themeItem.setIcon(R.drawable.ic_theme_moon);
                } else {
                    themeItem.setIcon(R.drawable.ic_theme_sun);
                }
            }
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_theme) {
            toggleTheme();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    protected void toggleTheme() {
        // Capture snapshot
        View rootView = getWindow().getDecorView();
        mOldBitmap = Bitmap.createBitmap(rootView.getWidth(), rootView.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(mOldBitmap);
        rootView.draw(canvas);
        
        mIsTransitioning = true;

        int currentTheme = ThemeManager.getSavedTheme(this);
        int newTheme = (currentTheme == ThemeManager.LIGHT) ? ThemeManager.DARK : ThemeManager.LIGHT;
        ThemeManager.setTheme(this, newTheme);
        
        Intent intent = getIntent();
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        finish();
        overridePendingTransition(0, 0);
        startActivity(intent);
    }

    protected void setupAIButton() {
        if (aiButton != null) return;

        aiButton = LayoutInflater.from(this).inflate(R.layout.view_ai_button, null);
        aiButton.setVisibility(View.INVISIBLE);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        
        addContentView(aiButton, params);

        fetchUserRole();

        aiButton.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                aiButton.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                restoreAiButtonPosition(aiButton);
            }
        });

        aiButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, AiChatActivity.class);
            intent.putExtra("userRole", userRole);
            startActivity(intent);
        });

        makeDraggable(aiButton);
    }

    private void fetchUserRole() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            FirebaseFirestore.getInstance().collection("users")
                    .document(auth.getCurrentUser().getUid())
                    .get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            userRole = doc.getString("role");
                            if (userRole == null) userRole = "user";
                        }
                    });
        }
    }

    private void makeDraggable(View view) {
        view.setOnTouchListener(new View.OnTouchListener() {
            float dX, dY;
            float initialX, initialY;
            boolean moved = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                View parent = (View) v.getParent();
                if (parent == null) return false;
                
                int parentWidth = parent.getWidth();
                int parentHeight = parent.getHeight();

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                        dX = v.getX() - event.getRawX();
                        dY = v.getY() - event.getRawY();
                        initialX = v.getX();
                        initialY = v.getY();
                        moved = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float newX = event.getRawX() + dX;
                        float newY = event.getRawY() + dY;

                        if (newX < 0) newX = 0;
                        if (newX > parentWidth - view.getWidth()) newX = parentWidth - view.getWidth();
                        if (newY < 0) newY = 0;
                        if (newY > parentHeight - view.getHeight()) newY = parentHeight - view.getHeight();

                        v.setX(newX);
                        v.setY(newY);

                        if (Math.abs(event.getRawX() + dX - initialX) > 10 || 
                            Math.abs(event.getRawY() + dY - initialY) > 10) {
                            moved = true;
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (!moved) {
                            view.performClick();
                        } else {
                            saveAiButtonPosition(view.getX(), view.getY());
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void restoreAiButtonPosition(View aiButton) {
        SharedPreferences prefs = getSharedPreferences("ai_button_prefs", MODE_PRIVATE);
        float savedX = prefs.getFloat("pos_x", -1);
        float savedY = prefs.getFloat("pos_y", -1);

        View parent = (View) aiButton.getParent();
        if (parent == null) return;

        if (savedX != -1 && savedY != -1) {
            if (savedX < 0) savedX = 16;
            if (savedX > parent.getWidth() - aiButton.getWidth()) savedX = parent.getWidth() - aiButton.getWidth() - 16;
            if (savedY < 0) savedY = 16;
            if (savedY > parent.getHeight() - aiButton.getHeight()) savedY = parent.getHeight() - aiButton.getHeight() - 16;
            
            aiButton.setX(savedX);
            aiButton.setY(savedY);
        } else {
            aiButton.setX(parent.getWidth() - aiButton.getWidth() - 80);
            aiButton.setY(parent.getHeight() * 0.78f - (aiButton.getHeight() / 2f));
        }

        aiButton.setAlpha(0f);
        aiButton.setVisibility(View.VISIBLE);
        aiButton.animate().alpha(1f).setDuration(300).start();
    }

    private void saveAiButtonPosition(float x, float y) {
        getSharedPreferences("ai_button_prefs", MODE_PRIVATE)
                .edit()
                .putFloat("pos_x", x)
                .putFloat("pos_y", y)
                .apply();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
