package com.example.faceattendance.mqtt;

import android.os.Handler;
import android.util.Log;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.message.connect.Mqtt5Connect;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class MqttManager {
    private static final String TAG = "MqttManager";

    // Final MQTT broker (TLS + auth)
    private static final String BROKER_HOST = "b45b0c700ea04cc08e6c8d0e4ac3e3de.s1.eu.hivemq.cloud";
    private static final int BROKER_PORT = 8883;
    private static final String USERNAME = "he_thong_cham_cong";
    private static final String PASSWORD = "Hethongchamcong123";

    private static final long TIMEOUT = 5 * 60 * 1000; // 5 phút

    private final Mqtt5AsyncClient mqttClient;
    private final Handler handler = new Handler();
    private Runnable disconnectRunnable;

    public MqttManager() {
        mqttClient = MqttClient.builder()
                .useMqttVersion5()
                .identifier(UUID.randomUUID().toString())
                .serverHost(BROKER_HOST)
                .serverPort(BROKER_PORT)
                .sslWithDefaultConfig()
                .buildAsync();
    }

    public void connectAndSend(String topic, String message, MqttCallbackListener listener) {
        if (!mqttClient.getState().isConnected()) {
            mqttClient.connect(Mqtt5Connect.builder()
                            .cleanStart(true)
                            .keepAlive(60)
                            .simpleAuth()
                            .username(USERNAME)
                            .password(PASSWORD.getBytes(StandardCharsets.UTF_8))
                            .applySimpleAuth()
                            .build())

                    .whenComplete((connAck, throwable) -> {
                        if (throwable != null) {
                            Log.e(TAG, "MQTT connection failed", throwable);
                            if (listener != null) listener.onSendFailure((Exception) throwable);
                        } else {
                            Log.d(TAG, "MQTT connected");
                            publish(topic, message, listener);
                            resetDisconnectTimer();
                        }
                    });
        } else {
            publish(topic, message, listener);
            resetDisconnectTimer();
        }
    }

    private void publish(String topic, String message, MqttCallbackListener listener) {
        mqttClient.publishWith()
                .topic(topic)
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

        byte[] payloadBytes = message.getBytes(StandardCharsets.UTF_8);
        int payloadSizeKb = payloadBytes.length / 1024;
        Log.d("MqttSize", "Size payload: " + payloadSizeKb + " KB");
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
    public void connect(MqttCallbackListener listener) {
        if (!mqttClient.getState().isConnected()) {
            mqttClient.connect(Mqtt5Connect.builder()
                            .cleanStart(true)
                            .keepAlive(60)
                            .simpleAuth()
                            .username(USERNAME)
                            .password(PASSWORD.getBytes(StandardCharsets.UTF_8))
                            .applySimpleAuth()
                            .build())
                    .whenComplete((connAck, throwable) -> {
                        if (throwable != null) {
                            Log.e(TAG, "MQTT connection failed", throwable);
                            if (listener != null) listener.onSendFailure((Exception) throwable);
                        } else {
                            Log.d(TAG, "MQTT connected");
                            if (listener != null) listener.onSendSuccess();
                            resetDisconnectTimer();
                        }
                    });
        } else {
            Log.d(TAG, "MQTT already connected");
            if (listener != null) listener.onSendSuccess();
            resetDisconnectTimer();
        }
    }

    public void sendMessage(String topic, String message, MqttCallbackListener listener) {
        if (!mqttClient.getState().isConnected()) {
            Log.e(TAG, "MQTT not connected. Cannot send message.");
            if (listener != null) listener.onSendFailure(new Exception("MQTT not connected"));
            return;
        }

        publish(topic, message, listener);
        resetDisconnectTimer();
    }

}

//package com.example.faceattendance.mqtt;
//
//import android.os.Handler;
//import android.util.Log;
//
//import com.hivemq.client.mqtt.MqttClient;
//import com.hivemq.client.mqtt.datatypes.MqttQos;
//import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
//import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
//import com.hivemq.client.mqtt.mqtt5.message.connect.Mqtt5Connect;
//
//import java.nio.charset.StandardCharsets;
//import java.util.UUID;
//
//
//public class MqttManager {
//    private static final String TAG = "MqttManager";
//    private static final String BROKER_HOST = "broker.hivemq.com";
//    private static final int BROKER_PORT = 1883;
//    private static final long TIMEOUT = 5 * 60 * 1000; // 5 phút
//
//    private final Mqtt3AsyncClient mqttClient;
//    private final Handler handler = new Handler();
//    private Runnable disconnectRunnable;
//
//    public MqttManager() {
//        mqttClient = MqttClient.builder()
//                .useMqttVersion3()
//                .identifier(UUID.randomUUID().toString())
//                .serverHost(BROKER_HOST)
//                .serverPort(BROKER_PORT)
//                .buildAsync();
//    }
//
//    public void connectAndSend(String topic, String message, MqttCallbackListener listener) {
//        if (!mqttClient.getState().isConnected()) {
//            mqttClient.connectWith()
//                    .keepAlive(60)
//                    .send()
//                    .whenComplete((connAck, throwable) -> {
//                        if (throwable != null) {
//                            Log.e(TAG, "MQTT connection failed", throwable);
//                            if (listener != null) listener.onSendFailure((Exception) throwable);
//                        } else {
//                            Log.d(TAG, "MQTT connected");
//                            publish(topic, message, listener);
//                            resetDisconnectTimer();
//                        }
//                    });
//        } else {
//            publish(topic, message, listener);
//            resetDisconnectTimer();
//        }
//    }
//
//    private void publish(String topic, String message, MqttCallbackListener listener) {
//        mqttClient.publishWith()
//                .topic(topic)
//                .qos(MqttQos.AT_LEAST_ONCE)
//                .payload(message.getBytes(StandardCharsets.UTF_8))
//                .send()
//                .whenComplete((publishResult, throwable) -> {
//                    if (throwable != null) {
//                        Log.e(TAG, "Publish failed", throwable);
//                        if (listener != null) listener.onSendFailure((Exception) throwable);
//                    } else {
//                        Log.d(TAG, "Message published: " + message);
//                        if (listener != null) listener.onSendSuccess();
//                    }
//                });
//        byte[] payloadBytes = message.getBytes(StandardCharsets.UTF_8);
//        int payloadSizeKb = payloadBytes.length / 1024;
//        Log.d("MqttSize","Size payload: "+ payloadSizeKb);
//    }
//
//    private void resetDisconnectTimer() {
//        if (disconnectRunnable != null) handler.removeCallbacks(disconnectRunnable);
//
//        disconnectRunnable = () -> mqttClient.disconnect()
//                .whenComplete((aVoid, throwable) -> {
//                    if (throwable != null) {
//                        Log.e(TAG, "Disconnection failed", throwable);
//                    } else {
//                        Log.d(TAG, "MQTT disconnected after timeout");
//                    }
//                });
//
//        handler.postDelayed(disconnectRunnable, TIMEOUT);
//    }
//}
