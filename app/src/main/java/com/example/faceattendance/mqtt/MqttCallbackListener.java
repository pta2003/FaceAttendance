package com.example.faceattendance.mqtt;

public interface MqttCallbackListener {
    void onSendSuccess();
    void onSendFailure(Exception e);
}
