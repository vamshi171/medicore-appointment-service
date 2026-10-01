package com.medicore.appointment.feign.dto;

import java.math.BigDecimal;

public final class Snapshots {

    private Snapshots() {
    }

    public record DoctorSnapshotDto(
            Long id,
            Long userId,
            String fullName,
            String specialization,
            BigDecimal consultationFee,
            String availableFrom,
            String availableTo,
            boolean available,
            boolean active) {
    }

    public record PatientSnapshotDto(
            Long id,
            Long userId,
            String fullName,
            boolean active) {
    }
}
