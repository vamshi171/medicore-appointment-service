package com.medicore.appointment.feign;

import com.medicore.appointment.feign.dto.NotificationRequest;
import com.medicore.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "NOTIFICATION-SERVICE", configuration = FeignConfig.class)
public interface NotificationClient {

    @PostMapping("/internal/notifications")
    ApiResponse<Void> sendNotification(@RequestBody NotificationRequest request);
}
