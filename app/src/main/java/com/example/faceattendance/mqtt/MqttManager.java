package com.example.faceattendance.mqtt;

import android.os.Handler;
import android.util.Log;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class MqttManager {
    private static final String TAG = "MqttManager";
    private static final String BROKER_HOST = "broker.hivemq.com";
    private static final int BROKER_PORT = 1883;
    private static final String TOPIC = "attendance/logs";
    private static final long TIMEOUT = 5 * 60 * 1000; // 5 phút

    private final Mqtt3AsyncClient mqttClient;
    private final Handler handler = new Handler();
    private Runnable disconnectRunnable;

    public MqttManager() {
        mqttClient = MqttClient.builder()
                .useMqttVersion3()
                .identifier(UUID.randomUUID().toString())
                .serverHost(BROKER_HOST)
                .serverPort(BROKER_PORT)
                .buildAsync();
    }

    public void connectAndSend(String message, MqttCallbackListener listener) {
        if (!mqttClient.getState().isConnected()) {
            mqttClient.connectWith()
                    .keepAlive(60)
                    .send()
                    .whenComplete((connAck, throwable) -> {
                        if (throwable != null) {
                            Log.e(TAG, "MQTT connection failed", throwable);
                            if (listener != null) listener.onSendFailure((Exception) throwable);
                        } else {
                            Log.d(TAG, "MQTT connected");
                            publish(message, listener);
                            resetDisconnectTimer();
                        }
                    });
        } else {
            publish(message, listener);
            resetDisconnectTimer();
        }
    }

    private void publish(String message, MqttCallbackListener listener) {
        mqttClient.publishWith()
                .topic(TOPIC)
                .qos(MqttQos.AT_LEAST_ONCE)
                .payload(message.getBytes(StandardCharsets.UTF_8))
                .send()
                .whenComplete((publishResult, throwable) -> {
                    if (throwable != null) {
                        Log.e(TAG, "Publish failed", throwable);
                        if (listener != null) listener.onSendFailure((Exception) throwable);
                    } else {
                        Log.d(TAG, "Message published: " + message);
                        if (listener != null) listener.onSendSuccess();
                    }
                });
    }

    private void resetDisconnectTimer() {
        if (disconnectRunnable != null) handler.removeCallbacks(disconnectRunnable);

        disconnectRunnable = () -> mqttClient.disconnect()
                .whenComplete((aVoid, throwable) -> {
                    if (throwable != null) {
                        Log.e(TAG, "Disconnection failed", throwable);
                    } else {
                        Log.d(TAG, "MQTT disconnected after timeout");
                    }
                });

        handler.postDelayed(disconnectRunnable, TIMEOUT);
    }
}
