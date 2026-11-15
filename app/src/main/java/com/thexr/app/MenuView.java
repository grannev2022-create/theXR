package com.thexr.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

public class MenuView extends View {
    private final Paint bgPaint = new Paint();
    private final Paint btnPaint = new Paint();
    private final Paint textPaint = new Paint();
    private final Paint pinchPaint = new Paint();
    private RectF settingsBtn, exitBtn;
    private float pinchX = -1, pinchY = -1;
    private boolean isPinch = false;
    private OnMenuClickListener listener;

    public interface OnMenuClickListener {
        void onSettings();
        void onExit();
    }

    public MenuView(Context context) {
        super(context);
        bgPaint.setColor(Color.argb(180, 0, 0, 0));
        btnPaint.setColor(Color.argb(220, 30, 100, 200));
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(48f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        pinchPaint.setColor(Color.RED);
        pinchPaint.setStyle(Paint.Style.STROKE);
        pinchPaint.setStrokeWidth(6f);
    }

    public void setListener(OnMenuClickListener l) { this.listener = l; }

    public void updatePinch(float x, float y, boolean pinch) {
        this.pinchX = x;
        this.pinchY = y;
        this.isPinch = pinch;
        if (pinch && listener != null) {
            if (settingsBtn != null && settingsBtn.contains(x, y)) listener.onSettings();
            else if (exitBtn != null && exitBtn.contains(x, y)) listener.onExit();
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();

        // Панель меню
        float panelW = w * 0.7f, panelH = h * 0.4f;
        float left = (w - panelW) / 2f, top = (h - panelH) / 2f;
        canvas.drawRect(left, top, left + panelW, top + panelH, bgPaint);

        // Кнопка "Настройки"
        settingsBtn = new RectF(left + 40, top + 40, left + panelW - 40, top + panelH / 2 - 20);
        canvas.drawRoundRect(settingsBtn, 20, 20, btnPaint);
        canvas.drawText("Настройки", settingsBtn.centerX(), settingsBtn.centerY() + 15, textPaint);

        // Кнопка "Выход"
        exitBtn = new RectF(left + 40, top + panelH / 2 + 20, left + panelW - 40, top + panelH - 40);
        canvas.drawRoundRect(exitBtn, 20, 20, btnPaint);
        canvas.drawText("Выход", exitBtn.centerX(), exitBtn.centerY() + 15, textPaint);

        // Визуализация щипка
        if (isPinch && pinchX >= 0 && pinchY >= 0) {
            canvas.drawCircle(pinchX, pinchY, 40f, pinchPaint);
        }
    }
}
