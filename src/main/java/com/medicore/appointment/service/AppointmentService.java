package com.medicore.appointment.service;

import com.medicore.appointment.dto.AppointmentDtos.AppointmentResponse;
import com.medicore.appointment.dto.AppointmentDtos.BookRequest;
import com.medicore.appointment.dto.AppointmentDtos.StatsResponse;
import com.medicore.appointment.entity.Appointment;
import com.medicore.appointment.event.AppointmentEventPublisher;
import com.medicore.appointment.feign.DoctorClient;
import com.medicore.appointment.feign.PatientClient;
import com.medicore.appointment.feign.dto.Snapshots.DoctorSnapshotDto;
import com.medicore.appointment.feign.dto.Snapshots.PatientSnapshotDto;
import com.medicore.appointment.repository.AppointmentRepository;
import com.medicore.common.dto.ApiResponse;
import com.medicore.common.dto.PageResponse;
import com.medicore.common.exception.BadRequestException;
import com.medicore.common.exception.ResourceNotFoundException;
import com.medicore.common.security.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Booking engine — the heart of the demo:
 *  1. OpenFeign calls doctor-service + patient-service (with fallbacks)
 *  2. Account-active checks via auth-service identity (JWT userId)
 *  3. @Transactional slot validation with optimistic-lock protected commit
 *  4. Streams API for admin statistics
 */
@Service
public class AppointmentService {

    private static final int SLOT_MINUTES = 30;

    private final AppointmentRepository appointmentRepository;
    private final DoctorClient doctorClient;
    private final PatientClient patientClient;
    private final AppointmentEventPublisher eventPublisher;

    public AppointmentService(AppointmentRepository appointmentRepository,
                              DoctorClient doctorClient,
                              PatientClient patientClient,
                              AppointmentEventPublisher eventPublisher) {
        this.appointmentRepository = appointmentRepository;
        this.doctorClient = doctorClient;
        this.patientClient = patientClient;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public AppointmentResponse book(BookRequest request) {
        // PATIENT identity comes from the validated JWT (CurrentUser thread-local)
        Long userId = CurrentUser.requireUserId();

        DoctorSnapshotDto doctor = fetchDoctor(request.doctorId());
        PatientSnapshotDto patient = fetchPatient(userId);

        validateDoctorBookable(doctor);
        validatePatientBookable(patient);
        validateSlotBasics(request.appointmentDate());
        validateWithinDoctorWindow(doctor, request.appointmentDate());
        ensureNoOverlap(doctor.id(), request.appointmentDate());

        Appointment appointment = new Appointment();
        appointment.setDoctorId(doctor.id());
        appointment.setDoctorName(doctor.fullName());
        appointment.setSpecialization(doctor.specialization());
        appointment.setPatientId(patient.id());
        appointment.setPatientName(patient.fullName());
        appointment.setAppointmentDate(request.appointmentDate());
        appointment.setFeeAtBooking(doctor.consultationFee());
        appointment.setReason(request.reason());
        appointment.setStatus(Appointment.Status.SCHEDULED);

        Appointment saved = appointmentRepository.save(appointment);

        // AFTER_COMMIT event → async Feign notification (never blocks/rolls back booking)
        eventPublisher.publishBooked(saved, patient.userId());

        return AppointmentResponse.from(saved);
    }

    @Transactional
    public AppointmentResponse cancel(Long id, String role) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", id));

        if ("DOCTOR".equals(role)) {
            // doctors may only manage appointments belonging to them
            Long doctorId = resolveDoctorId(CurrentUser.requireUserId());
            if (!doctorId.equals(appointment.getDoctorId())) {
                throw new com.medicore.common.exception.AccessDeniedException(
                        "You can only manage your own appointments");
            }
        } else if (!"ADMIN".equals(role)) {
            // patients may cancel only their own appointments
            PatientSnapshotDto me = fetchPatient(CurrentUser.requireUserId());
            if (!me.id().equals(appointment.getPatientId())) {
                throw new com.medicore.common.exception.AccessDeniedException(
                        "You can only cancel your own appointments");
            }
        }
        if (appointment.getStatus() == Appointment.Status.COMPLETED
                || appointment.getStatus() == Appointment.Status.CANCELLED) {
            throw new BadRequestException("Appointment is already " + appointment.getStatus());
        }
        appointment.setStatus(Appointment.Status.CANCELLED);
        return AppointmentResponse.from(appointment);
    }

    @Transactional
    public AppointmentResponse updateStatus(Long id, Appointment.Status status, String role) {
        if (!"DOCTOR".equals(role) && !"ADMIN".equals(role)) {
            throw new com.medicore.common.exception.AccessDeniedException("Only doctors or admins can confirm/complete");
        }
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", id));
        if (status == Appointment.Status.CANCELLED) {
            return cancel(id, role);
        }
        appointment.setStatus(status);
        return AppointmentResponse.from(appointment);
    }

    @Transactional(readOnly = true)
    public PageResponse<AppointmentResponse> myAppointments(String role, int page, int size) {
        Long userId = CurrentUser.requireUserId();
        Pageable pageable = PageRequest.of(page, size);

        if ("DOCTOR".equals(role)) {
            Long doctorId = resolveDoctorId(userId);
            Page<Appointment> result = appointmentRepository
                    .findByDoctorIdOrderByAppointmentDateDesc(doctorId, pageable);
            return toPageResponse(result);
        }
        // PATIENT (and ADMIN sees own-context; admins use listAll)
        Long patientId = resolvePatientId(userId);
        Page<Appointment> result = appointmentRepository
                .findByPatientIdOrderByAppointmentDateDesc(patientId, pageable);
        return toPageResponse(result);
    }

    @Transactional(readOnly = true)
    public PageResponse<AppointmentResponse> listAll(int page, int size) {
        Page<Appointment> result = appointmentRepository.findAll(PageRequest.of(page, size));
        return toPageResponse(result);
    }

    /**
     * Admin statistics computed with the Streams API:
     * groupingBy + counting downstream collector over one DB fetch.
     */
    @Transactional(readOnly = true)
    public StatsResponse stats() {
        List<Appointment> all = appointmentRepository.findAll();

        Map<Appointment.Status, Long> byStatus = all.stream()
                .collect(Collectors.groupingBy(Appointment::getStatus, Collectors.counting()));

        long total = all.size();
        return new StatsResponse(
                total,
                byStatus.getOrDefault(Appointment.Status.SCHEDULED, 0L),
                byStatus.getOrDefault(Appointment.Status.CONFIRMED, 0L),
                byStatus.getOrDefault(Appointment.Status.COMPLETED, 0L),
                byStatus.getOrDefault(Appointment.Status.CANCELLED, 0L));
    }

    // ---------- helpers ----------

    private DoctorSnapshotDto fetchDoctor(Long doctorId) {
        ApiResponse<DoctorSnapshotDto> response = doctorClient.getDoctor(doctorId);
        if (response == null || !response.isSuccess() || response.getData() == null) {
            throw new ResourceNotFoundException("Doctor", doctorId);
        }
        return response.getData();
    }

    private PatientSnapshotDto fetchPatient(Long userId) {
        ApiResponse<PatientSnapshotDto> response = patientClient.getPatientByUserId(userId);
        if (response == null || !response.isSuccess() || response.getData() == null) {
            throw new BadRequestException("Please create your patient profile before booking an appointment.");
        }
        return response.getData();
    }

    private void validateDoctorBookable(DoctorSnapshotDto doctor) {
        if (!doctor.active() || !doctor.available()) {
            throw new BadRequestException("This doctor is not currently accepting appointments.");
        }
    }

    private void validatePatientBookable(PatientSnapshotDto patient) {
        if (!patient.active()) {
            throw new BadRequestException("This account is deactivated and cannot book appointments.");
        }
    }

    private void validateSlotBasics(LocalDateTime start) {
        LocalDateTime now = LocalDateTime.now();
        if (start.isBefore(now)) {
            throw new BadRequestException("Appointment time must be in the future");
        }
        if (start.getMinute() % SLOT_MINUTES != 0 || start.getSecond() != 0) {
            throw new BadRequestException("Appointments start on :00 or :30");
        }
        if (Duration.between(now, start).toDays() > 60) {
            throw new BadRequestException("Appointments can be booked at most 60 days ahead");
        }
    }

    /** The 30-minute slot must fit inside the doctor's configured availability window. */
    private void validateWithinDoctorWindow(DoctorSnapshotDto doctor, LocalDateTime start) {
        try {
            java.time.LocalTime from = java.time.LocalTime.parse(doctor.availableFrom());
            java.time.LocalTime to = java.time.LocalTime.parse(doctor.availableTo());
            java.time.LocalTime slotStart = start.toLocalTime();
            java.time.LocalTime slotEnd = slotStart.plusMinutes(SLOT_MINUTES);
            if (slotStart.isBefore(from) || slotEnd.isAfter(to)) {
                throw new BadRequestException(
                        "Dr. " + doctor.fullName() + " sees patients between " + doctor.availableFrom()
                                + " and " + doctor.availableTo());
            }
        } catch (java.time.format.DateTimeParseException parseEx) {
            // Doctor window not parseable — treat as no restriction rather than blocking bookings
        }
    }

    private void ensureNoOverlap(Long doctorId, LocalDateTime start) {
        LocalDateTime end = start.plusMinutes(SLOT_MINUTES);
        List<Appointment> overlapping = appointmentRepository.findOverlapping(
                doctorId,
                List.of(Appointment.Status.SCHEDULED, Appointment.Status.CONFIRMED),
                start, end);
        if (!overlapping.isEmpty()) {
            throw new BadRequestException("That slot has just been taken. Please choose another time.");
        }
    }

    private Long resolveDoctorId(Long userId) {
        // Doctor identity: appointment rows store the doctor profile id; the doctor's
        // own listing resolves it via their profile snapshot (internal Feign lookup).
        ApiResponse<DoctorSnapshotDto> response = doctorClient.getDoctorByUserId(userId);
        if (response == null || !response.isSuccess() || response.getData() == null) {
            throw new ResourceNotFoundException("Doctor profile for user", userId);
        }
        return response.getData().id();
    }

    private Long resolvePatientId(Long userId) {
        ApiResponse<PatientSnapshotDto> response = patientClient.getPatientByUserId(userId);
        if (response == null || !response.isSuccess() || response.getData() == null) {
            throw new ResourceNotFoundException("Patient profile for user", userId);
        }
        return response.getData().id();
    }

    private PageResponse<AppointmentResponse> toPageResponse(Page<Appointment> result) {
        List<AppointmentResponse> content = result.getContent().stream()
                .map(AppointmentResponse::from)
                .toList();
        return new PageResponse<>(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }
}
