package com.thexr.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;
import java.util.List;

public class OverlayView extends View {
    private HandLandmarkerResult result;
    private final Paint paint = new Paint();
    private final Paint pinchPaint = new Paint();

    public OverlayView(Context context) {
        super(context);
        paint.setColor(Color.GREEN);
        paint.setStrokeWidth(8f);
        paint.setStyle(Paint.Style.FILL);
        pinchPaint.setColor(Color.RED);
        pinchPaint.setStrokeWidth(12f);
        pinchPaint.setStyle(Paint.Style.STROKE);
    }

    public void setResult(HandLandmarkerResult result) {
        this.result = result;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (result == null) return;
        for (List<NormalizedLandmark> hand : result.landmarks()) {
            if (hand.size() < 21) continue;
            NormalizedLandmark thumb = hand.get(4);   // большой палец
            NormalizedLandmark index = hand.get(8);   // указательный
            float dx = (thumb.x() - index.x()) * getWidth();
            float dy = (thumb.y() - index.y()) * getHeight();
            float dist = (float) Math.sqrt(dx * dx + dy * dy);

            boolean isPinch = dist < 60f; // порог в пикселях

            for (NormalizedLandmark lm : hand) {
                canvas.drawCircle(lm.x() * getWidth(), lm.y() * getHeight(), 10f,
                        isPinch ? pinchPaint : paint);
            }

            if (isPinch) {
                canvas.drawCircle(thumb.x() * getWidth(), thumb.y() * getHeight(),
                        30f, pinchPaint);
            }
        }
    }
}
