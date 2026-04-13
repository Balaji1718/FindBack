package com.balaji.findback;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class ImagePreviewActivity extends AppCompatActivity {

    ImageView imagePreview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_preview);

        imagePreview = findViewById(R.id.imagePreview);

        String base64 = getIntent().getStringExtra("imageBase64");
        String status = getIntent().getStringExtra("status");

        // SECURITY CHECK
        if (status == null ||
                (!status.equalsIgnoreCase("CLAIMED")
                        && !status.equalsIgnoreCase("RETURNED"))) {

            Toast.makeText(this,
                    "Image access restricted",
                    Toast.LENGTH_SHORT).show();

            finish();
            return;
        }

        if (base64 != null && !base64.isEmpty()) {

            byte[] decodedBytes = Base64.decode(base64, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);

            imagePreview.setImageBitmap(bitmap);
        }
    }
}