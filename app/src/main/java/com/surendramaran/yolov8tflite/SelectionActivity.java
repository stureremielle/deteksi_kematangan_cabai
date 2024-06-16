package com.surendramaran.yolov8tflite;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;

public class SelectionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_selection);
    }

    public void onRealTimeDetectionClick(View view) {
        Intent intent = new Intent(SelectionActivity.this, MainActivity.class);
        intent.putExtra("mode", "realtime");
        startActivity(intent);
    }

    public void onImportFromGalleryClick(View view) {
        Intent intent = new Intent(SelectionActivity.this, MainActivity.class);
        intent.putExtra("mode", "gallery");
        startActivity(intent);
    }
}
