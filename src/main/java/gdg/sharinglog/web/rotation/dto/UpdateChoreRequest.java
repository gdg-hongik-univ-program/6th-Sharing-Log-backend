package gdg.sharinglog.web.rotation.dto;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import gdg.sharinglog.domain.rotation.ChoreFrequency;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateChoreRequest(
        @Size(max = 100, message = "업무명은 100자 이하여야 합니다.")
        String name,

        @Valid
        ScheduleRequest schedule
) {

    @JsonIgnore
    @AssertTrue(message = "업무명 또는 일정 중 하나 이상을 변경해야 합니다.")
    public boolean isChangePresent() {
        return name != null || schedule != null;
    }

    @JsonIgnore
    @AssertTrue(message = "업무명은 공백일 수 없습니다.")
    public boolean isNameValid() {
        return name == null || !name.trim().isEmpty();
    }

    public record ScheduleRequest(
            @NotNull(message = "반복 주기는 필수입니다.")
            ChoreFrequency frequency,

            @NotNull(message = "마감 시각은 필수입니다.")
            LocalTime dueTime,

            DayOfWeek weeklyDueDay,
            LocalDate biweeklyAnchorDate
    ) {

        @JsonIgnore
        @AssertTrue(message = "반복 주기에 맞는 일정 필드를 설정해야 합니다.")
        public boolean isFrequencySpecificFieldsValid() {
            if (frequency == null) {
                return true;
            }
            return switch (frequency) {
                case DAILY -> weeklyDueDay == null && biweeklyAnchorDate == null;
                case WEEKLY -> weeklyDueDay != null && biweeklyAnchorDate == null;
                case BIWEEKLY -> weeklyDueDay == null && biweeklyAnchorDate != null;
            };
        }
    }
}
