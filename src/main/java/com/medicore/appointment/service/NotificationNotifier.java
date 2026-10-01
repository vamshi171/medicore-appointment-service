package com.medicore.appointment.service;

import com.medicore.appointment.entity.Appointment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Fire-and-forget async bridge to notification-service.
 * @Async moves the Feign call off the request thread (multithreading);
 * failures are logged, never propagated — a failed email must not fail a booking.
 */
@Component
public class NotificationNotifier {

    private static final Logger log = LoggerFactory.getLogger(NotificationNotifier.class);

    private final com.medicore.appointment.feign.NotificationClient notificationClient;

    public NotificationNotifier(com.medicore.appointment.feign.NotificationClient notificationClient) {
        this.notificationClient = notificationClient;
    }

    @Async("medicoreTaskExecutor")
    public CompletableFuture<Void> notifyAppointmentBooked(Appointment appointment, Long patientUserId) {
        try {
            notificationClient.sendNotification(new com.medicore.appointment.feign.dto.NotificationRequest(
                    patientUserId,
                    appointment.getPatientId(),
                    "APPOINTMENT_BOOKED",
                    "Appointment booked with Dr. " + appointment.getDoctorName()
                            + " at " + appointment.getAppointmentDate()));
            return CompletableFuture.completedFuture(null);
        } catch (Exception ex) {
            log.warn("Notification dispatch failed for appointment {}: {}",
                    appointment.getId(), ex.toString());
            return CompletableFuture.completedFuture(null);
        }
    }
}
