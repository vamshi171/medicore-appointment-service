package com.medicore.appointment.feign;

import com.medicore.appointment.feign.dto.DoctorSnapshotDto;
import com.medicore.common.dto.ApiResponse;
import com.medicore.common.exception.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Resilience4j fallback: converts downstream failure into a typed 503-style
 * domain exception instead of letting raw failures bubble up.
 */
@Component
public class DoctorClientFallbackFactory implements FallbackFactory<DoctorClient> {

    private static final Logger log = LoggerFactory.getLogger(DoctorClientFallbackFactory.class);

    @Override
    public DoctorClient create(Throwable cause) {
        return new DoctorClient() {
            @Override
            public ApiResponse<DoctorSnapshotDto> getDoctor(Long id) {
                log.warn("DoctorClient fallback for doctor {}: {}", id, cause.toString());
                throw new ServiceUnavailableException(
                        "Doctor service is temporarily unavailable. Please try again shortly.");
            }

            @Override
            public ApiResponse<DoctorSnapshotDto> getDoctorByUserId(Long userId) {
                log.warn("DoctorClient fallback for doctor-user {}: {}", userId, cause.toString());
                throw new ServiceUnavailableException(
                        "Doctor service is temporarily unavailable. Please try again shortly.");
            }
        };
    }
}
