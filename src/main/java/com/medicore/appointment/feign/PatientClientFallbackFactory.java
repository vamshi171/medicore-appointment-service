package com.medicore.appointment.feign;

import com.medicore.appointment.feign.dto.Snapshots.PatientSnapshotDto;
import com.medicore.common.dto.ApiResponse;
import com.medicore.common.exception.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Resilience4j fallback for the patient-service client.
 */
@Component
public class PatientClientFallbackFactory implements FallbackFactory<PatientClient> {

    private static final Logger log = LoggerFactory.getLogger(PatientClientFallbackFactory.class);

    @Override
    public PatientClient create(Throwable cause) {
        return new PatientClient() {
            @Override
            public ApiResponse<PatientSnapshotDto> getPatientByUserId(Long userId) {
                log.warn("PatientClient fallback for user {}: {}", userId, cause.toString());
                throw new ServiceUnavailableException(
                        "Patient service is temporarily unavailable. Please try again shortly.");
            }
        };
    }
}
