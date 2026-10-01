package com.medicore.appointment.repository;

import com.medicore.appointment.entity.Appointment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    Page<Appointment> findByPatientIdOrderByAppointmentDateDesc(Long patientId, Pageable pageable);

    Page<Appointment> findByDoctorIdOrderByAppointmentDateDesc(Long doctorId, Pageable pageable);

    /**
     * Overlap check for a 30-minute slot: any active appointment for this doctor
     * whose [start, start+30) window intersects [start, end). Uses the composite
     * index (doctor_id, appointment_date) for range scoping.
     */
    @Query("""
            SELECT a FROM Appointment a
            WHERE a.doctorId = :doctorId
              AND a.status IN (com.medicore.appointment.entity.Appointment.Status.SCHEDULED,
                               com.medicore.appointment.entity.Appointment.Status.CONFIRMED)
              AND a.appointmentDate < :end
              AND a.appointmentDate >= :start
            """)
    List<Appointment> findOverlapping(@Param("doctorId") Long doctorId,
                                      @Param("start") LocalDateTime start,
                                      @Param("end") LocalDateTime end);

    @Query("""
            SELECT a FROM Appointment a
            WHERE a.doctorId = :doctorId
              AND a.appointmentDate BETWEEN :dayStart AND :dayEnd
            """)
    List<Appointment> findDoctorDay(@Param("doctorId") Long doctorId,
                                    @Param("dayStart") LocalDateTime dayStart,
                                    @Param("dayEnd") LocalDateTime dayEnd);

    long countByStatus(Appointment.Status status);

    long countByDoctorIdAndStatus(Long doctorId, Appointment.Status status);
}
