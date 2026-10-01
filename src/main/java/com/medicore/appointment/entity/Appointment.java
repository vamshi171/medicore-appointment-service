package com.medicore.appointment.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Appointment aggregate. Optimistic locking via @Version prevents double-booking
 * races when two patients target the same doctor slot concurrently.
 */
@Entity
@Table(name = "appointments", indexes = {
        @Index(name = "idx_appt_doctor_date", columnList = "doctor_id, appointment_date"),
        @Index(name = "idx_appt_patient", columnList = "patient_id")
})
public class Appointment {

    public enum Status { SCHEDULED, CONFIRMED, COMPLETED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long doctorId;      // doctor-service profile id

    @Column(nullable = false)
    private String doctorName;  // denormalized snapshot for list views

    @Column(nullable = false)
    private Long patientId;     // patient-service profile id

    @Column(nullable = false)
    private String patientName; // denormalized snapshot

    @Column(nullable = false, length = 80)
    private String specialization;

    @Column(nullable = false)
    private LocalDateTime appointmentDate; // slot start (30-minute duration)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.SCHEDULED;

    @Column(precision = 8, scale = 2)
    private BigDecimal feeAtBooking; // price snapshot

    @Column(length = 300)
    private String reason;

    @Version
    private Long version; // optimistic lock

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return status == Status.SCHEDULED || status == Status.CONFIRMED;
    }

    // --- getters / setters ---
    public Long getId() { return id; }
    public Long getDoctorId() { return doctorId; }
    public void setDoctorId(Long doctorId) { this.doctorId = doctorId; }
    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }
    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }
    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }
    public String getSpecialization() { return specialization; }
    public void setSpecialization(String specialization) { this.specialization = specialization; }
    public LocalDateTime getAppointmentDate() { return appointmentDate; }
    public void setAppointmentDate(LocalDateTime appointmentDate) { this.appointmentDate = appointmentDate; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public BigDecimal getFeeAtBooking() { return feeAtBooking; }
    public void setFeeAtBooking(BigDecimal feeAtBooking) { this.feeAtBooking = feeAtBooking; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
