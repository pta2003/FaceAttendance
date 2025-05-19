package com.example.faceattendance.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;

/**
 * Helper class for face recognition using TensorFlow Lite
 */
public class FaceRecognitionHelper {
    private static final String TAG = "FaceRecognitionHelper";

    private static final int INPUT_IMAGE_SIZE = 112;
    private static final int EMBEDDING_SIZE = 192; // 192-dimensional face embeddings
    private static final float RECOGNITION_THRESHOLD = 0.7f; // Threshold for face matching

    private Interpreter tfLite;

    /**
     * Initializes the TensorFlow Lite interpreter
     */
    public FaceRecognitionHelper(Context context) {
        try {
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(4);
            tfLite = new Interpreter(loadModelFile(context, "mobile_face_net.tflite"), options);
        } catch (IOException e) {
            Log.e(TAG, "Error loading face recognition model", e);
        }
    }

    /**
     * Loads the TFLite model from assets folder
     */
    private MappedByteBuffer loadModelFile(Context context, String modelPath) throws IOException {
        FileInputStream fileInputStream = new FileInputStream(context.getAssets().openFd(modelPath).getFileDescriptor());
        FileChannel fileChannel = fileInputStream.getChannel();
        long startOffset = context.getAssets().openFd(modelPath).getStartOffset();
        long declaredLength = context.getAssets().openFd(modelPath).getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    /**
     * Preprocesses face image for the model
     */
    private ByteBuffer preprocessFace(Bitmap faceBitmap) {
        // Resize bitmap to required input size
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(faceBitmap, INPUT_IMAGE_SIZE, INPUT_IMAGE_SIZE, false);

        // Allocate ByteBuffer for input data
        // 4 bytes per float, 3 channels (RGB)
        ByteBuffer inputBuffer = ByteBuffer.allocateDirect(1 * INPUT_IMAGE_SIZE * INPUT_IMAGE_SIZE * 3 * 4);
        inputBuffer.order(ByteOrder.nativeOrder());
        inputBuffer.rewind();

        // Convert bitmap to float values normalized between -1 and 1
        int[] pixels = new int[INPUT_IMAGE_SIZE * INPUT_IMAGE_SIZE];
        resizedBitmap.getPixels(pixels, 0, INPUT_IMAGE_SIZE, 0, 0, INPUT_IMAGE_SIZE, INPUT_IMAGE_SIZE);

        for (int pixel : pixels) {
            // Extract RGB values and normalize
            float r = ((pixel >> 16) & 0xFF) / 127.5f - 1.0f;
            float g = ((pixel >> 8) & 0xFF) / 127.5f - 1.0f;
            float b = (pixel & 0xFF) / 127.5f - 1.0f;

            // TensorFlow model expects RGB
            inputBuffer.putFloat(r);
            inputBuffer.putFloat(g);
            inputBuffer.putFloat(b);
        }

        return inputBuffer;
    }

    /**
     * Extracts face embedding from bitmap
     */
    public float[] getFaceEmbedding(Bitmap faceBitmap) {
        if (tfLite == null) {
            Log.e(TAG, "TFLite interpreter not initialized");
            return null;
        }

        // Preprocess face image
        ByteBuffer inputBuffer = preprocessFace(faceBitmap);

        // Output buffer for face embedding (192-dimensional)
        float[][] outputEmbedding = new float[1][EMBEDDING_SIZE];

        // Run inference
        Map<Integer, Object> outputMap = new HashMap<>();
        outputMap.put(0, outputEmbedding);

        tfLite.run(inputBuffer, outputEmbedding);

        // Normalize the embedding
        float[] embedding = outputEmbedding[0];
        normalize(embedding);

        return embedding;
    }

    /**
     * L2 normalization of embedding vector
     */
    private void normalize(float[] embedding) {
        float sum = 0;
        for (float val : embedding) {
            sum += val * val;
        }
        float norm = (float) Math.sqrt(sum);

        for (int i = 0; i < embedding.length; i++) {
            embedding[i] = embedding[i] / norm;
        }
    }

    /**
     * Calculate cosine similarity between two embeddings
     */
    public float calculateSimilarity(float[] embedding1, float[] embedding2) {
        float dotProduct = 0;

        for (int i = 0; i < embedding1.length; i++) {
            dotProduct += embedding1[i] * embedding2[i];
        }

        // Since vectors are normalized, dot product equals cosine similarity
        return dotProduct;
    }

    /**
     * Check if faces match based on similarity threshold
     */
    public boolean matchFace(float[] embedding1, float[] embedding2) {
        float similarity = calculateSimilarity(embedding1, embedding2);
        return similarity > RECOGNITION_THRESHOLD;
    }

    /**
     * Crop face from the original bitmap using face bounds
     */
    public Bitmap cropFace(Bitmap originalBitmap, Rect faceBounds, int rotationDegrees) {
        // Adjust bounds if needed to ensure they're within the bitmap dimensions
        int left = Math.max(0, faceBounds.left);
        int top = Math.max(0, faceBounds.top);
        int right = Math.min(originalBitmap.getWidth(), faceBounds.right);
        int bottom = Math.min(originalBitmap.getHeight(), faceBounds.bottom);

        // Add some margin to ensure the full face is captured
        int margin = (int) (Math.min(faceBounds.width(), faceBounds.height()) * 0.3);
        left = Math.max(0, left - margin);
        top = Math.max(0, top - margin);
        right = Math.min(originalBitmap.getWidth(), right + margin);
        bottom = Math.min(originalBitmap.getHeight(), bottom + margin);

        // Crop the face region
        Bitmap faceBitmap = Bitmap.createBitmap(originalBitmap, left, top, right - left, bottom - top);

        // Rotate if needed
        if (rotationDegrees != 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(rotationDegrees);
            faceBitmap = Bitmap.createBitmap(faceBitmap, 0, 0, faceBitmap.getWidth(), faceBitmap.getHeight(), matrix, true);
        }

        return faceBitmap;
    }

    /**
     * Release resources when done
     */
    public void close() {
        if (tfLite != null) {
            tfLite.close();
            tfLite = null;
        }
    }
}