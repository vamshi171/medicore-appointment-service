package com.medicore.appointment.feign;

import com.medicore.appointment.feign.dto.Snapshots.DoctorSnapshotDto;
import com.medicore.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Declarative REST client to DOCTOR-SERVICE (load-balanced via Eureka).
 * Circuit breaker + fallback wired via Resilience4j (see application.yml).
 */
@FeignClient(name = "DOCTOR-SERVICE", configuration = FeignConfig.class,
        fallbackFactory = DoctorClientFallbackFactory.class)
public interface DoctorClient {

    @GetMapping("/internal/doctors/{id}")
    ApiResponse<DoctorSnapshotDto> getDoctor(@PathVariable("id") Long id);

    @GetMapping("/internal/doctors/by-user/{userId}")
    ApiResponse<DoctorSnapshotDto> getDoctorByUserId(@PathVariable("userId") Long userId);
}
