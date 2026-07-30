package gdg.sharinglog.web.rotation.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

public record UpdateChoreRequest(
        @Size(max = 100, message = "업무명은 100자 이하여야 합니다.")
        String name,

        @Valid
        ChoreScheduleRequest schedule
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
}
