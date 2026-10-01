package com.medicore.appointment.feign.dto;

public record NotificationRequest(
        Long recipientUserId,
        Long patientId,
        String type,
        String message) {
}
