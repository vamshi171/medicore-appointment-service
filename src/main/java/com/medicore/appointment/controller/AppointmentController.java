package com.medicore.appointment.controller;

import com.medicore.appointment.dto.AppointmentDtos.AppointmentResponse;
import com.medicore.appointment.dto.AppointmentDtos.BookRequest;
import com.medicore.appointment.dto.AppointmentDtos.StatsResponse;
import com.medicore.appointment.entity.Appointment;
import com.medicore.appointment.service.AppointmentService;
import com.medicore.common.dto.ApiResponse;
import com.medicore.common.dto.PageResponse;
import com.medicore.common.exception.AccessDeniedException;
import com.medicore.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AppointmentResponse>> book(@Valid @RequestBody BookRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Appointment booked", appointmentService.book(request)));
    }

    /** ADMIN-only: all appointments, paginated. */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AppointmentResponse>>> all(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        if (!CurrentUser.hasRole("ADMIN")) {
            throw new AccessDeniedException("Only admins can list all appointments");
        }
        return ResponseEntity.ok(ApiResponse.ok(appointmentService.listAll(page, Math.min(size, 100))));
    }

    @GetMapping("/me/patient")
    public ResponseEntity<ApiResponse<PageResponse<AppointmentResponse>>> myPatientAppointments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.ok(appointmentService.myAppointments(CurrentUser.requireRole(), page, size)));
    }

    @GetMapping("/me/doctor")
    public ResponseEntity<ApiResponse<PageResponse<AppointmentResponse>>> myDoctorAppointments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.ok(appointmentService.myAppointments(CurrentUser.requireRole(), page, size)));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<AppointmentResponse>> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Appointment cancelled",
                appointmentService.cancel(id, CurrentUser.requireRole())));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<AppointmentResponse>> updateStatus(
            @PathVariable Long id,
            @RequestParam Appointment.Status status) {
        return ResponseEntity.ok(ApiResponse.ok("Status updated",
                appointmentService.updateStatus(id, status, CurrentUser.requireRole())));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<StatsResponse>> stats() {
        return ResponseEntity.ok(ApiResponse.ok(appointmentService.stats()));
    }
}
