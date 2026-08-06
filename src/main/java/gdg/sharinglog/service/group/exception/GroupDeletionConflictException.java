package gdg.sharinglog.service.group.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class GroupDeletionConflictException extends RuntimeException {

    public GroupDeletionConflictException() {
        super("다른 활성 멤버가 남아 있는 그룹은 삭제할 수 없습니다.");
    }
}
