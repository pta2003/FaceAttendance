package com.example.faceattendance.utils;

import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceLandmark;

/**
 * Helper class for liveness detection
 */
public class LivenessDetector {
    private static final String TAG = "LivenessDetector";

    private static final float SMILE_THRESHOLD = 0.6f;
    private static final float BLINK_THRESHOLD = 0.2f;

    private boolean smileDetected = false;
    private boolean blinkDetected = false;
    private boolean livenessVerified = false;

    /**
     * Reset liveness detection state
     */
    public void reset() {
        smileDetected = false;
        blinkDetected = false;
        livenessVerified = false;
    }

    /**
     * Process face to detect smile and eye blink
     */
    public void processFace(Face face) {
        // Check for smile
        if (!smileDetected && face.getSmilingProbability() != null && face.getSmilingProbability() > SMILE_THRESHOLD) {
            smileDetected = true;
        }

        // Check for eye blink
        if (!blinkDetected) {
            Float leftEyeOpenProb = face.getLeftEyeOpenProbability();
            Float rightEyeOpenProb = face.getRightEyeOpenProbability();

            // If both eyes are detected and at least one is blinking
            if (leftEyeOpenProb != null && rightEyeOpenProb != null) {
                if (leftEyeOpenProb < BLINK_THRESHOLD || rightEyeOpenProb < BLINK_THRESHOLD) {
                    blinkDetected = true;
                }
            }
        }

        // Check if both liveness checks passed
        if (smileDetected && blinkDetected) {
            livenessVerified = true;
        }
    }

    /**
     * Check if smile has been detected
     */
    public boolean isSmileDetected() {
        return smileDetected;
    }

    /**
     * Check if blink has been detected
     */
    public boolean isBlinkDetected() {
        return blinkDetected;
    }

    /**
     * Check if liveness verification is complete
     */
    public boolean isLivenessVerified() {
        return livenessVerified;
    }

    /**
     * Get current liveness detection status message
     */
    public String getStatusMessage() {
        if (!smileDetected) {
            return "Please smile";
        } else if (!blinkDetected) {
            return "Please blink your eyes";
        } else {
            return "Liveness verified!";
        }
    }
}