package gdg.sharinglog.service.rotation.api.chore;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

import gdg.sharinglog.domain.rotation.ChoreFrequency;

public record UpdateChoreCommand(
        String name,
        Schedule schedule
) {

    public record Schedule(
            ChoreFrequency frequency,
            LocalTime dueTime,
            DayOfWeek weeklyDueDay,
            LocalDate biweeklyAnchorDate
    ) {
    }
}
