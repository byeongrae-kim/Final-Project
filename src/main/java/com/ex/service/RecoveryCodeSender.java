package com.ex.service;

public interface RecoveryCodeSender {
    void send(String username, String destinationPhone, String verificationCode);
}
