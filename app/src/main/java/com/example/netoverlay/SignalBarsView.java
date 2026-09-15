package com.example.netoverlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * Рисует классический индикатор сигнала из 4 палочек нарастающей высоты.
 * level: -1 (нет сигнала), 0..4 (кол-во активных палочек)
 */
public class SignalBarsView extends View {

    private int level = 0;
    private final Paint activePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint inactivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public SignalBarsView(Context context) {
        this(context, null);
    }

    public SignalBarsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        activePaint.setColor(Color.parseColor("#00E676"));
        inactivePaint.setColor(Color.parseColor("#55FFFFFF"));
    }

    public void setLevel(int level) {
        this.level = level;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int barsCount = 4;
        float w = getWidth();
        float h = getHeight();
        float gap = w * 0.06f;
        float barWidth = (w - gap * (barsCount - 1)) / barsCount;

        for (int i = 0; i < barsCount; i++) {
            float barHeight = h * ((i + 1) / (float) barsCount);
            float left = i * (barWidth + gap);
            float top = h - barHeight;
            float right = left + barWidth;
            float bottom = h;
            Paint p = (i < level) ? activePaint : inactivePaint;
            canvas.drawRoundRect(left, top, right, bottom, barWidth * 0.25f, barWidth * 0.25f, p);
        }
    }
}
