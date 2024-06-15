package com.surendramaran.yolov8tflite;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash); // Ensure you have an XML layout named activity_splash

        // Delay for 2 seconds before starting MainActivity
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                // Start MainActivity
                Intent intent = new Intent(SplashActivity.this, MainActivity.class);
                startActivity(intent);
                // Finish SplashActivity
                finish();
            }
        }, 5000); // 2000 milliseconds delay
    }
}


