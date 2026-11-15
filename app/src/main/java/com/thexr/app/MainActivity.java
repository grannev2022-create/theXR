package com.thexr.app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements SensorEventListener {
    private static final int REQUEST_CAMERA = 100;
    private PreviewView leftView, rightView;
    private MenuView menuLeft, menuRight;
    private ExecutorService cameraExecutor;
    private HandLandmarker handLandmarker;
    private SensorManager sensorManager;
    private float yaw = 0, pitch = 0, roll = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);

        FrameLayout leftFrame = new FrameLayout(this);
        FrameLayout rightFrame = new FrameLayout(this);
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);

        leftView = new PreviewView(this);
        rightView = new PreviewView(this);
        menuLeft = new MenuView(this);
        menuRight = new MenuView(this);

        leftFrame.addView(leftView);
        leftFrame.addView(menuLeft);
        rightFrame.addView(rightView);
        rightFrame.addView(menuRight);
        root.addView(leftFrame, half);
        root.addView(rightFrame, half);
        setContentView(root);

        MenuView.OnMenuClickListener listener = new MenuView.OnMenuClickListener() {
            @Override public void onSettings() {
                Toast.makeText(MainActivity.this, "Настройки (заглушка)", Toast.LENGTH_SHORT).show();
            }
            @Override public void onExit() {
                finishAffinity();
            }
        };
        menuLeft.setListener(listener);
        menuRight.setListener(listener);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        cameraExecutor = Executors.newSingleThreadExecutor();
        setupHandLandmarker();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
        } else {
            startCamera();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
                SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_GAME);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            yaw += event.values[0] * 0.01f;
            pitch += event.values[1] * 0.01f;
            roll += event.values[2] * 0.01f;
            // Применяем поворот к картинке (имитация 6DOF)
            leftView.setRotationX(pitch * 50);
            leftView.setRotationY(yaw * 50);
            rightView.setRotationX(pitch * 50);
            rightView.setRotationY(yaw * 50);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void setupHandLandmarker() {
        BaseOptions options = BaseOptions.builder()
                .setModelAssetPath("hand_landmarker.task")
                .build();
        HandLandmarker.HandLandmarkerOptions handOptions =
                HandLandmarker.HandLandmarkerOptions.builder()
                        .setBaseOptions(options)
                        .setRunningMode(RunningMode.LIVE_STREAM)
                        .setNumHands(2)
                        .setResultListener(this::onHandResult)
                        .setErrorListener(e -> Log.e("theXR", "MediaPipe error", e))
                        .build();
        handLandmarker = HandLandmarker.createFromOptions(this, handOptions);
    }

    private void onHandResult(HandLandmarkerResult result, MPImage image) {
        runOnUiThread(() -> {
            if (result.landmarks().isEmpty()) {
                menuLeft.updatePinch(-1, -1, false);
                menuRight.updatePinch(-1, -1, false);
                return;
            }
            List<NormalizedLandmark> hand = result.landmarks().get(0);
            if (hand.size() < 21) return;
            NormalizedLandmark thumb = hand.get(4);
            NormalizedLandmark index = hand.get(8);
            float dx = thumb.x() - index.x();
            float dy = thumb.y() - index.y();
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            boolean isPinch = dist < 0.06f;
            float px = thumb.x() * menuLeft.getWidth();
            float py = thumb.y() * menuLeft.getHeight();
            menuLeft.updatePinch(px, py, isPinch);
            menuRight.updatePinch(px, py, isPinch);
        });
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(leftView.getSurfaceProvider());

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                analysis.setAnalyzer(cameraExecutor, this::analyzeImage);

                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA,
                        preview, analysis);
            } catch (Exception e) {
                Log.e("theXR", "Camera failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void analyzeImage(ImageProxy imageProxy) {
        try {
            Bitmap bitmap = Bitmap.createBitmap(
                    imageProxy.getWidth(), imageProxy.getHeight(), Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(imageProxy.getPlanes()[0].getBuffer());
            MPImage mpImage = new BitmapImageBuilder(bitmap).build();
            handLandmarker.detectAsync(mpImage, System.currentTimeMillis());
        } catch (Exception e) {
            Log.e("theXR", "Analyze failed", e);
        } finally {
            imageProxy.close();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
            @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        if (handLandmarker != null) handLandmarker.close();
    }
}
