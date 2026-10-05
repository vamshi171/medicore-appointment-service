package com.medicore.appointment.feign;

import com.medicore.appointment.feign.dto.Snapshots.PatientSnapshotDto;
import com.medicore.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Declarative REST client to PATIENT-SERVICE (load-balanced via Eureka).
 */
@FeignClient(name = "PATIENT-SERVICE", configuration = FeignConfig.class,
        fallbackFactory = PatientClientFallbackFactory.class)
public interface PatientClient {

    @GetMapping("/internal/patients/by-user/{userId}")
    ApiResponse<PatientSnapshotDto> getPatientByUserId(@PathVariable("userId") Long userId);
}
