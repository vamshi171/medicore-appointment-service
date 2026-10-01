package com.medicore.appointment.event;

import com.medicore.appointment.entity.Appointment;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.stereotype.Service;

/**
 * Event-driven notification hook: AFTER_COMMIT — the Feign call to
 * notification-service fires only if the booking transaction actually committed,
 * so we never email about appointments that rolled back.
 */
@Service
public class AppointmentEventPublisher {

    private final ApplicationEventPublisher publisher;

    public AppointmentEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void publishBooked(Appointment appointment, Long patientUserId) {
        publisher.publishEvent(new AppointmentBookedEvent(appointment, patientUserId));
    }

    public record AppointmentBookedEvent(Appointment appointment, Long patientUserId) {
    }

    @Component
    static class BookedEventListener {

        private final com.medicore.appointment.service.NotificationNotifier notifier;

        BookedEventListener(com.medicore.appointment.service.NotificationNotifier notifier) {
            this.notifier = notifier;
        }

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void onBooked(AppointmentBookedEvent event) {
            notifier.notifyAppointmentBooked(event.appointment(), event.patientUserId());
        }
    }
}
