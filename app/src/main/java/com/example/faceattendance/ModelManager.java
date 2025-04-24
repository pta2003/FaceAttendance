package com.example.faceattendance;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class ModelManager {
    private static final String TAG = "ModelManager";
    private static final String MODEL_FILE = "mobile_face_net.tflite";

    private final Context context;
    private Interpreter tfLite;

    public ModelManager(Context context) {
        this.context = context;
    }

    public void loadModel() {
        try {
            tfLite = new Interpreter(loadModelFile((Activity) context, MODEL_FILE));
        } catch (IOException e) {
            Log.e(TAG, "Error loading model", e);
        }
    }

    public Interpreter getInterpreter() {
        return tfLite;
    }

    private MappedByteBuffer loadModelFile(Activity activity, String modelFile) throws IOException {
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(modelFile);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }
}
