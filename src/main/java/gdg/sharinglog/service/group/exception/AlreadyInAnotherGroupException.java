package gdg.sharinglog.service.group.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class AlreadyInAnotherGroupException extends RuntimeException {

    public AlreadyInAnotherGroupException() {
        super("이미 다른 그룹에 참여 중입니다. 먼저 그룹을 탈퇴해주세요.");
    }
}
