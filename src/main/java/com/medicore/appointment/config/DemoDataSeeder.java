package com.medicore.appointment.config;

import com.medicore.appointment.entity.Appointment;
import com.medicore.appointment.repository.AppointmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Demo-data bootstrap for showcases and interviews.
 *
 * Seeds a realistic appointment mix (past completed/cancelled, today's
 * confirmed, upcoming scheduled) across the demo cast. Direct repository
 * inserts keep boot fast and avoid Feign at startup.
 *
 * ID resolution: auth-service owns the emails, so we resolve email -> userId
 * there, then userId -> profile id via doctor/patient internal endpoints.
 * Idempotent by (doctorId + appointmentDate); restarts never duplicate.
 * Any unresolvable piece is skipped and filled in on the next boot — startup
 * is never fatal.
 */
@Component
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final AppointmentRepository appointmentRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    private final String internalToken;
    private final String authServiceUrl;
    private final String doctorServiceUrl;
    private final String patientServiceUrl;

    public DemoDataSeeder(AppointmentRepository appointmentRepository,
                          @Value("${INTERNAL_TOKEN:medicore-internal-dev-token}") String internalToken,
                          @Value("${medicore.auth-service-url:http://localhost:9081}") String authServiceUrl,
                          @Value("${medicore.doctor-service-url:http://localhost:8083}") String doctorServiceUrl,
                          @Value("${medicore.patient-service-url:http://localhost:8082}") String patientServiceUrl) {
        this.appointmentRepository = appointmentRepository;
        this.internalToken = internalToken;
        this.authServiceUrl = trim(authServiceUrl);
        this.doctorServiceUrl = trim(doctorServiceUrl);
        this.patientServiceUrl = trim(patientServiceUrl);
    }

    private static String trim(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    /** Demo cast — must mirror the seeders in auth/doctor/patient services. */
    private static final String[] DOCTOR_EMAILS = {
            "dr.sharma@medicore.com", "dr.mehta@medicore.com", "dr.reddy@medicore.com"};
    private static final String[] PATIENT_EMAILS = {
            "arjun@medicore.com", "priya@medicore.com", "rahul@medicore.com"};

    @Override
    public void run(ApplicationArguments args) {
        if (appointmentRepository.count() > 0) {
            return; // demo data only for a fresh appointment database
        }

        // email -> auth userId
        Map<String, Long> userIds = lookupUserIds();
        if (userIds.isEmpty()) {
            log.info("[demo-seed] appointment-service: auth-service not ready — seeding next boot");
            return;
        }

        // email -> doctor profile id
        Map<String, Long> doctors = new LinkedHashMap<>();
        for (String email : DOCTOR_EMAILS) {
            Long userId = userIds.get(email);
            if (userId == null) continue;
            Long profileId = getNumber(doctorServiceUrl + "/internal/doctors/by-user/" + userId, "id");
            if (profileId != null) doctors.put(email, profileId);
        }

        // email -> patient profile id
        Map<String, Long> patients = new LinkedHashMap<>();
        for (String email : PATIENT_EMAILS) {
            Long userId = userIds.get(email);
            if (userId == null) continue;
            Long profileId = getNumber(patientServiceUrl + "/internal/patients/by-user/" + userId, "id");
            if (profileId != null) patients.put(email, profileId);
        }

        if (doctors.isEmpty() || patients.isEmpty()) {
            log.info("[demo-seed] appointment-service: doctor/patient profiles not ready — seeding next boot");
            return;
        }

        String[][] doc = {
                {"dr.sharma@medicore.com", "Dr. Ananya Sharma", "Cardiology", "900"},
                {"dr.mehta@medicore.com",  "Dr. Vikram Mehta",  "Dermatology", "700"},
                {"dr.reddy@medicore.com",  "Dr. Kavya Reddy",   "Pediatrics",  "600"}};
        String[][] pat = {
                {"arjun@medicore.com", "Arjun Kumar"},
                {"priya@medicore.com", "Priya Nair"},
                {"rahul@medicore.com", "Rahul Verma"}};

        LocalDate today = LocalDate.now();
        int created = 0;

        // [doctorIdx, patientIdx, dayOffset, hour, minute, status, reason]
        Object[][] plan = {
                {0, 0, -14, 10, 0,  "COMPLETED", "Chest pain follow-up"},
                {0, 1, -7,  11, 30, "COMPLETED", "Hypertension review"},
                {1, 2, -5,  15, 0,  "CANCELLED", "Skin rash consultation"},
                {2, 0, -3,  9,  30, "COMPLETED", "Child immunisation"},
                {0, 2, 0,   10, 0,  "CONFIRMED", "Diabetes cardiology consult"},
                {1, 1, 0,   14, 0,  "CONFIRMED", "Acne treatment plan"},
                {2, 1, 1,   11, 0,  "SCHEDULED", "Well-baby checkup"},
                {1, 0, 2,   16, 0,  "SCHEDULED", "Allergy patch test"},
                {0, 0, 5,   12, 0,  "SCHEDULED", "ECG review"}};

        for (Object[] row : plan) {
            int di = (Integer) row[0], pi = (Integer) row[1], off = (Integer) row[2];
            LocalTime t = LocalTime.of((Integer) row[3], (Integer) row[4]);
            Appointment.Status status = Appointment.Status.valueOf((String) row[5]);
            String reason = (String) row[6];

            Long doctorId = doctors.get(doc[di][0]);
            Long patientId = patients.get(pat[pi][0]);
            if (doctorId == null || patientId == null) {
                continue; // that profile wasn't ready — next boot fills it in
            }
            LocalDateTime when = LocalDateTime.of(today.plusDays(off), t);

            if (appointmentRepository.existsByDoctorIdAndAppointmentDate(doctorId, when)) {
                continue;
            }

            Appointment a = new Appointment();
            a.setDoctorId(doctorId);
            a.setDoctorName(doc[di][1]);
            a.setPatientId(patientId);
            a.setPatientName(pat[pi][1]);
            a.setSpecialization(doc[di][2]);
            a.setAppointmentDate(when);
            a.setStatus(status);
            a.setFeeAtBooking(new BigDecimal(doc[di][3]));
            a.setReason(reason);
            appointmentRepository.save(a);
            created++;
        }
        if (created > 0) {
            log.info("[demo-seed] appointment-service: created {} demo appointments", created);
        }
    }

    /** Walks auth-service's user list (internal API) and matches demo emails. */
    private Map<String, Long> lookupUserIds() {
        Map<String, Long> result = new LinkedHashMap<>();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Token", internalToken);
            ResponseEntity<java.util.Map<String, Object>> resp = restTemplate.exchange(
                    authServiceUrl + "/internal/users?page=0&size=100",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {});
            if (resp.getBody() == null) {
                return result;
            }
            // flat list envelope: {success, message, data:[{id,email,...}]}
            Object dataObj = resp.getBody().get("data");
            if (!(dataObj instanceof java.util.List<?> content)) {
                return result;
            }
            for (Object o : content) {
                if (o instanceof Map<?, ?> u) {
                    Object email = u.get("email");
                    Object id = u.get("id");
                    if (email instanceof String e && id instanceof Number n) {
                        result.put(e.toLowerCase(), n.longValue());
                    }
                }
            }
        } catch (RestClientException ex) {
            log.warn("[demo-seed] appointment-service: auth-service unreachable ({})", ex.getMessage());
        }
        return result;
    }

    /** GET an internal endpoint and pull a numeric field from data.*; null on failure. */
    private Long getNumber(String url, String field) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Token", internalToken);
            ResponseEntity<java.util.Map<String, Object>> resp = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {});
            Object data = resp.getBody() == null ? null : resp.getBody().get("data");
            if (data instanceof Map<?, ?> m && m.get(field) instanceof Number n) {
                return n.longValue();
            }
        } catch (RestClientException ex) {
            log.info("[demo-seed] {} not ready: {}", url, ex.getMessage());
        }
        return null;
    }
}
