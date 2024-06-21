package com.surendramaran.yolov8tflite;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

public class SelectionActivity extends AppCompatActivity {

    private ActivityResultLauncher<Intent> galleryLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_selection);

        // Initialize the gallery launcher
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        Intent data = result.getData();
                        if (data != null) {
                            Uri selectedImageUri = data.getData();
                            // Pass the selected image URI to MainActivity
                            Intent intent = new Intent(SelectionActivity.this, MainActivity.class);
                            intent.putExtra("mode", "gallery");
                            intent.setData(selectedImageUri);
                            startActivity(intent);
                        }
                    }
                }
        );
    }

    public void onRealTimeDetectionClick(View view) {
        Intent intent = new Intent(SelectionActivity.this, MainActivity.class);
        intent.putExtra("mode", "realtime");
        startActivity(intent);
    }

    public void onImportFromGalleryClick(View view) {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryLauncher.launch(intent);
    }
}