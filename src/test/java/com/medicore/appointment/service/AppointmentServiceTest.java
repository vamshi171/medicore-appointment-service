package com.medicore.appointment.service;

import com.medicore.appointment.dto.AppointmentDtos.BookRequest;
import com.medicore.appointment.entity.Appointment;
import com.medicore.appointment.event.AppointmentEventPublisher;
import com.medicore.appointment.feign.DoctorClient;
import com.medicore.appointment.feign.PatientClient;
import com.medicore.appointment.feign.dto.Snapshots.DoctorSnapshotDto;
import com.medicore.appointment.feign.dto.Snapshots.PatientSnapshotDto;
import com.medicore.appointment.repository.AppointmentRepository;
import com.medicore.common.dto.ApiResponse;
import com.medicore.common.exception.BadRequestException;
import com.medicore.common.security.CurrentUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the booking engine (JUnit 5 + Mockito).
 * The ThreadLocal CurrentUser simulates the JWT-filter identity.
 */
@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private DoctorClient doctorClient;

    @Mock
    private PatientClient patientClient;

    @Mock
    private AppointmentEventPublisher eventPublisher;

    @InjectMocks
    private AppointmentService appointmentService;

    private DoctorSnapshotDto doctor;
    private PatientSnapshotDto patient;
    private LocalDateTime slot;

    @BeforeEach
    void setUp() {
        CurrentUser.set(new com.medicore.common.security.UserPrincipal(10L, "patient@medicore.com", "PATIENT"));

        doctor = new DoctorSnapshotDto(100L, 2L, "Dr. House", "Dermatology",
                BigDecimal.valueOf(500.00), "09:00", "17:00", true, true);
        patient = new PatientSnapshotDto(200L, 10L, "Test Patient", true);
        slot = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
    }

    @AfterEach
    void tearDown() {
        CurrentUser.clear();
    }

    @Test
    @DisplayName("book: saves appointment and publishes AFTER_COMMIT event")
    void booksSuccessfully() {
        when(doctorClient.getDoctor(100L)).thenReturn(ApiResponse.ok(doctor));
        when(patientClient.getPatientByUserId(10L)).thenReturn(ApiResponse.ok(patient));
        when(appointmentRepository.findOverlapping(eq(100L), any(), any())).thenReturn(List.of());
        when(appointmentRepository.save(any(Appointment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var response = appointmentService.book(new BookRequest(100L, slot, "Skin rash"));

        assertEquals("Dr. House", response.doctorName());
        assertEquals(Appointment.Status.SCHEDULED, response.status());
        verify(eventPublisher).publishBooked(any(Appointment.class), eq(10L));
    }

    @Test
    @DisplayName("book: rejects a slot that overlaps an existing appointment")
    void rejectsOverlappingSlot() {
        when(doctorClient.getDoctor(100L)).thenReturn(ApiResponse.ok(doctor));
        when(patientClient.getPatientByUserId(10L)).thenReturn(ApiResponse.ok(patient));

        Appointment existing = new Appointment();
        existing.setDoctorId(100L);
        existing.setAppointmentDate(slot);
        when(appointmentRepository.findOverlapping(eq(100L), any(), any()))
                .thenReturn(List.of(existing));

        assertThrows(BadRequestException.class,
                () -> appointmentService.book(new BookRequest(100L, slot, "Skin rash")));

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("book: rejects booking with an off-duty doctor")
    void rejectsOffDutyDoctor() {
        DoctorSnapshotDto offDuty = new DoctorSnapshotDto(100L, 2L, "Dr. House", "Dermatology",
                BigDecimal.valueOf(500.00), "09:00", "17:00", false, true);
        when(doctorClient.getDoctor(100L)).thenReturn(ApiResponse.ok(offDuty));
        when(patientClient.getPatientByUserId(10L)).thenReturn(ApiResponse.ok(patient));

        assertThrows(BadRequestException.class,
                () -> appointmentService.book(new BookRequest(100L, slot, "Skin rash")));
    }

    @Test
    @DisplayName("book: rejects a deactivated patient account")
    void rejectsDeactivatedPatient() {
        when(doctorClient.getDoctor(100L)).thenReturn(ApiResponse.ok(doctor));
        PatientSnapshotDto deactivated = new PatientSnapshotDto(200L, 10L, "Test Patient", false);
        when(patientClient.getPatientByUserId(10L)).thenReturn(ApiResponse.ok(deactivated));

        assertThrows(BadRequestException.class,
                () -> appointmentService.book(new BookRequest(100L, slot, "Skin rash")));
    }

    @Test
    @DisplayName("book: rejects non-30-minute slot times")
    void rejectsOffGridSlot() {
        when(doctorClient.getDoctor(100L)).thenReturn(ApiResponse.ok(doctor));
        when(patientClient.getPatientByUserId(10L)).thenReturn(ApiResponse.ok(patient));

        LocalDateTime badSlot = slot.withMinute(17);
        assertThrows(BadRequestException.class,
                () -> appointmentService.book(new BookRequest(100L, badSlot, "Skin rash")));
    }

    @Test
    @DisplayName("cancel: patient cancels own appointment")
    void patientCancelsOwn() {
        Appointment appointment = new Appointment();
        appointment.setDoctorId(100L);
        appointment.setPatientId(200L);
        appointment.setStatus(Appointment.Status.SCHEDULED);
        appointment.setAppointmentDate(slot);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appointment));
        when(patientClient.getPatientByUserId(10L)).thenReturn(ApiResponse.ok(patient));

        var response = appointmentService.cancel(1L, "PATIENT");

        assertEquals(Appointment.Status.CANCELLED, response.status());
    }

    @Test
    @DisplayName("cancel: patient cannot cancel someone else's appointment")
    void patientCannotCancelOthers() {
        Appointment appointment = new Appointment();
        appointment.setDoctorId(100L);
        appointment.setPatientId(999L); // someone else
        appointment.setStatus(Appointment.Status.SCHEDULED);
        appointment.setAppointmentDate(slot);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appointment));
        when(patientClient.getPatientByUserId(10L)).thenReturn(ApiResponse.ok(patient));

        assertThrows(com.medicore.common.exception.AccessDeniedException.class,
                () -> appointmentService.cancel(1L, "PATIENT"));
    }
}
