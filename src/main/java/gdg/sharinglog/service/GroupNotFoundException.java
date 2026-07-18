package gdg.sharinglog.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class GroupNotFoundException extends RuntimeException {

    public GroupNotFoundException(Long groupId) {
        super("그룹을 찾을 수 없습니다: " + groupId);
    }
}
