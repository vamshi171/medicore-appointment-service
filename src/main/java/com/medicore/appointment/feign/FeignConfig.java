package com.medicore.appointment.feign;

import com.medicore.common.security.CurrentUser;
import com.medicore.common.security.InternalTokenFilter;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Propagates identity + the shared internal token on every Feign call so
 * downstream /internal/** endpoints accept service-to-service traffic.
 */
public class FeignConfig {

    @Bean
    public RequestInterceptor internalTokenInterceptor() {
        return template -> {
            template.header(InternalTokenFilter.HEADER, medicoreInternalToken());
            com.medicore.common.security.UserPrincipal principal = CurrentUser.get();
            if (principal != null) {
                template.header("X-User-Id", String.valueOf(principal.userId()));
                template.header("X-User-Email", principal.email());
                template.header("X-User-Role", principal.role());
            }
        };
    }

    private String medicoreInternalToken() {
        return System.getenv().getOrDefault("INTERNAL_TOKEN", "medicore-internal-dev-token");
    }
}
