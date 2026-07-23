package gdg.sharinglog.web.rotation.error;

import java.util.Map;

import org.springframework.http.HttpStatus;

public final class RotationUnauthorizedException extends RotationApiException {

    public RotationUnauthorizedException(String detail) {
        this(detail, Map.of());
    }

    public RotationUnauthorizedException(String detail, Map<String, ?> properties) {
        super(
                HttpStatus.UNAUTHORIZED,
                RotationProblemCode.UNAUTHENTICATED,
                detail,
                properties
        );
    }
}
