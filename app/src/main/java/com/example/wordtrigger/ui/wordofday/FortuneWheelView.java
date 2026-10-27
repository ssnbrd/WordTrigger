package com.example.wordtrigger.ui.wordofday;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Колесо фортуны: круг из секторов с подписями. Вращается целиком (setRotation), стрелка рисуется отдельно. */
public class FortuneWheelView extends View {
    private static final int[] FILL = {
            0xFF0000FF, 0xFF1F2D70, 0xFF5B7BFF, 0xFF1C2347,
            0xFF0000FF, 0xFF1F2D70, 0xFF5B7BFF, 0xFF1C2347};

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private String[] labels = new String[0];
    private ObjectAnimator spin;

    public FortuneWheelView(Context c) { this(c, null); }

    public FortuneWheelView(Context c, AttributeSet a) {
        super(c, a);
        line.setStyle(Paint.Style.STROKE);
        line.setColor(0xFFF6F6F6);
        text.setColor(0xFFF6F6F6);
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
    }

    public void setLabels(String[] labels) {
        this.labels = labels;
        invalidate();
    }

    public boolean isSpinning() {
        return spin != null && spin.isRunning();
    }

    /** Крутит колесо до угла finalRotation и после остановки вызывает onEnd. */
    public void spinTo(float finalRotation, long durationMs, Runnable onEnd) {
        cancelSpin();
        spin = ObjectAnimator.ofFloat(this, View.ROTATION, getRotation(), finalRotation);
        spin.setDuration(durationMs);
        spin.setInterpolator(new DecelerateInterpolator(1.8f));
        spin.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override public void onAnimationCancel(Animator a) { cancelled = true; }

            @Override public void onAnimationEnd(Animator a) {
                if (!cancelled && onEnd != null) onEnd.run();
            }
        });
        spin.start();
    }

    public void cancelSpin() {
        if (spin != null) spin.cancel();
        spin = null;
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelSpin();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int w, int h) {
        int size = Math.min(MeasureSpec.getSize(w), MeasureSpec.getSize(h == 0 ? w : h));
        if (size == 0) size = Math.max(MeasureSpec.getSize(w), MeasureSpec.getSize(h));
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int n = Math.max(1, labels.length);
        float d = getResources().getDisplayMetrics().density;
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float r = Math.min(cx, cy) - 4 * d;
        oval.set(cx - r, cy - r, cx + r, cy + r);
        float sweep = 360f / n;
        line.setStrokeWidth(2 * d);

        for (int i = 0; i < n; i++) {
            fill.setColor(FILL[i % FILL.length]);
            canvas.drawArc(oval, i * sweep - 90, sweep, true, fill);
            canvas.drawArc(oval, i * sweep - 90, sweep, true, line);
        }

        float hub = r * 0.16f;
        float textStart = hub + 10 * d;
        float textEnd = r - 12 * d;
        for (int i = 0; i < labels.length; i++) {
            canvas.save();
            canvas.rotate(i * sweep + sweep / 2 - 90, cx, cy);   // ось X указывает в центр сектора
            fitText(labels[i], textEnd - textStart, 16 * d);
            Paint.FontMetrics fm = text.getFontMetrics();
            canvas.drawText(labels[i], cx + textStart, cy - (fm.ascent + fm.descent) / 2f, text);
            canvas.restore();
        }

        fill.setColor(0xFFF6F6F6);
        canvas.drawCircle(cx, cy, hub, fill);
        fill.setColor(0xFF0000FF);
        canvas.drawCircle(cx, cy, hub * 0.45f, fill);
        canvas.drawOval(oval, line);
    }

    /** Подбирает размер шрифта, чтобы подпись поместилась вдоль радиуса. */
    private void fitText(String s, float maxWidth, float startSize) {
        float size = startSize;
        text.setTextSize(size);
        while (text.measureText(s) > maxWidth && size > 9 * getResources().getDisplayMetrics().density) {
            size -= 1;
            text.setTextSize(size);
        }
    }
}
