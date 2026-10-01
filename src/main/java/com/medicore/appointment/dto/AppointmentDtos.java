package com.medicore.appointment.dto;

import com.medicore.appointment.entity.Appointment;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class AppointmentDtos {

    private AppointmentDtos() {
    }

    public record BookRequest(
            @NotNull Long doctorId,
            @NotNull LocalDateTime appointmentDate, // slot start, 30-minute duration
            @Size(max = 300) String reason) {
    }

    public record AppointmentResponse(
            Long id,
            Long doctorId,
            String doctorName,
            Long patientId,
            String patientName,
            String specialization,
            LocalDateTime appointmentDate,
            Appointment.Status status,
            BigDecimal feeAtBooking,
            String reason,
            String createdAt) {

        public static AppointmentResponse from(Appointment a) {
            return new AppointmentResponse(
                    a.getId(), a.getDoctorId(), a.getDoctorName(),
                    a.getPatientId(), a.getPatientName(),
                    a.getSpecialization(), a.getAppointmentDate(),
                    a.getStatus(), a.getFeeAtBooking(), a.getReason(),
                    a.getCreatedAt() == null ? null : a.getCreatedAt().toString());
        }
    }

    public record StatsResponse(
            long total,
            long scheduled,
            long confirmed,
            long completed,
            long cancelled) {
    }
}
