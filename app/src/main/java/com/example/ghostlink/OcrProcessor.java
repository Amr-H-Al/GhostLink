package com.example.ghostlink;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

/**
 * Thin wrapper around ML Kit Text Recognition (on-device).
 */
public class OcrProcessor {

    public interface Callback {
        void onSuccess(@NonNull String text);
        void onFailure(@NonNull Exception e);
    }

    private final TextRecognizer recognizer;

    public OcrProcessor() {
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    }

    /**
     * Runs OCR on a Bitmap.
     *
     * NOTE: Do not recycle the bitmap until the callbacks fire.
     */
    public void process(@NonNull Bitmap bitmap, @NonNull Callback cb) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);

        recognizer.process(image)
                .addOnSuccessListener((Text result) -> cb.onSuccess(result.getText() == null ? "" : result.getText()))
                .addOnFailureListener(cb::onFailure);
    }

    public void close() {
        recognizer.close();
    }
}
