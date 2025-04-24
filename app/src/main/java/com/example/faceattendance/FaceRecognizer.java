package com.example.faceattendance;

import android.graphics.Bitmap;
import android.util.Pair;
import android.widget.ImageView;

import org.tensorflow.lite.Interpreter;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

public class FaceRecognizer {
    private static final float IMAGE_MEAN = 128.0f;
    private static final float IMAGE_STD = 128.0f;
    private static final int INPUT_SIZE = 112;
    private static final int OUTPUT_SIZE = 192;

    private final ModelManager modelManager;
    private final ImageView previewImg;
    private final HashMap<String, SimilarityClassifier.Recognition> registered = new HashMap<>();
    private float[][] embeddings;

    public FaceRecognizer(ModelManager modelManager, ImageView previewImg) {
        this.modelManager = modelManager;
        this.previewImg = previewImg;
    }

    public void registerFace(String name) {
        if (embeddings != null) {
            //Create and Initialize new object with Face embeddings and Name.
            SimilarityClassifier.Recognition result = new SimilarityClassifier.Recognition(
                    "0", "", -1f);
            result.setExtra(embeddings);

            registered.put(name, result);
        }
    }

    public String recognizeImage(final Bitmap bitmap) {
        // set image to preview
        previewImg.setImageBitmap(bitmap);

        //Create ByteBuffer to store normalized image
        ByteBuffer imgData = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3 * 4);
        imgData.order(ByteOrder.nativeOrder());

        int[] intValues = new int[INPUT_SIZE * INPUT_SIZE];

        //get pixel values from Bitmap to normalize
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        imgData.rewind();

        for (int i = 0; i < INPUT_SIZE; ++i) {
            for (int j = 0; j < INPUT_SIZE; ++j) {
                int pixelValue = intValues[i * INPUT_SIZE + j];
                imgData.putFloat((((pixelValue >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                imgData.putFloat((((pixelValue >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                imgData.putFloat(((pixelValue & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
            }
        }

        //imgData is input to our model
        Object[] inputArray = {imgData};

        Map<Integer, Object> outputMap = new HashMap<>();
        embeddings = new float[1][OUTPUT_SIZE]; //output of model will be stored in this variable
        outputMap.put(0, embeddings);

        // Run model
        Interpreter tfLite = modelManager.getInterpreter();
        if (tfLite != null) {
            tfLite.runForMultipleInputsOutputs(inputArray, outputMap);
        }

        float distance;

        //Compare new face with saved Faces.
        if (registered.size() > 0) {
            final Pair<String, Float> nearest = findNearest(embeddings[0]); //Find closest matching face

            if (nearest != null) {
                final String name = nearest.first;
                distance = nearest.second;
                if (distance < 1.000f) { //If distance between Closest found face is more than 1.000, then output UNKNOWN face.
                    return name;
                } else {
                    return "unknown";
                }
            }
        }

        return null;
    }

    //Compare Faces by distance between face embeddings
    private Pair<String, Float> findNearest(float[] emb) {
        Pair<String, Float> ret = null;
        for (Map.Entry<String, SimilarityClassifier.Recognition> entry : registered.entrySet()) {
            final String name = entry.getKey();
            final float[] knownEmb = ((float[][]) entry.getValue().getExtra())[0];

            float distance = 0;
            for (int i = 0; i < emb.length; i++) {
                float diff = emb[i] - knownEmb[i];
                distance += diff * diff;
            }
            distance = (float) Math.sqrt(distance);
            if (ret == null || distance < ret.second) {
                ret = new Pair<>(name, distance);
            }
        }

        return ret;
    }
}
