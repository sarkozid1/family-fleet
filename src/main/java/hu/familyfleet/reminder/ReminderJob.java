package hu.familyfleet.reminder;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Az ütemezés vékony rétege: csak időzít és meghívja a service-t (így a logika ütemező nélkül
 * tesztelhető). Az {@code app.reminder.enabled=false} kikapcsolja (pl. ha több példány fut, és
 * csak egyiken kell ütemezni).
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.reminder", name = "enabled", havingValue = "true", matchIfMissing = true)
class ReminderJob {

    private final ReminderService reminderService;

    @Scheduled(cron = "${app.reminder.cron}", zone = "${app.time-zone}")
    void sendDailyReminders() {
        reminderService.sendDailyReminders();
    }
}
