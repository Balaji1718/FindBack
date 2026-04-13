package com.balaji.findback;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class AnalyticsWheelView extends View {

    private Paint paint;

    private float lostPercent;
    private float foundPercent;
    private float claimsPercent;
    private float returnedPercent;

    private float animatedLost;
    private float animatedFound;
    private float animatedClaims;
    private float animatedReturned;

    private float strokeWidth = 70f;

    public AnalyticsWheelView(Context context, AttributeSet attrs) {
        super(context, attrs);

        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
    }

    public void setPercentages(float lost, float found, float claims, float returned) {

        lostPercent = lost;
        foundPercent = found;
        claimsPercent = claims;
        returnedPercent = returned;

        startAnimation();
    }

    private void startAnimation() {

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(900);

        animator.addUpdateListener(animation -> {

            float progress = (float) animation.getAnimatedValue();

            animatedLost = lostPercent * progress;
            animatedFound = foundPercent * progress;
            animatedClaims = claimsPercent * progress;
            animatedReturned = returnedPercent * progress;

            invalidate();
        });

        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {

        float padding = strokeWidth / 2;

        RectF rect = new RectF(
                padding,
                padding,
                getWidth() - padding,
                getHeight() - padding
        );

        float startAngle = -90;

        paint.setColor(0xFFE53935);
        float lostAngle = animatedLost * 360 / 100;
        canvas.drawArc(rect, startAngle, lostAngle, false, paint);
        startAngle += lostAngle;

        paint.setColor(0xFF1E88E5);
        float foundAngle = animatedFound * 360 / 100;
        canvas.drawArc(rect, startAngle, foundAngle, false, paint);
        startAngle += foundAngle;

        paint.setColor(0xFFFDD835);
        float claimsAngle = animatedClaims * 360 / 100;
        canvas.drawArc(rect, startAngle, claimsAngle, false, paint);
        startAngle += claimsAngle;

        paint.setColor(0xFF9E9E9E);
        float returnedAngle = animatedReturned * 360 / 100;
        canvas.drawArc(rect, startAngle, returnedAngle, false, paint);
    }
}