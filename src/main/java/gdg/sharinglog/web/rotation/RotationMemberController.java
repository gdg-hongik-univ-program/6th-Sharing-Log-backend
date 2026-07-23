package gdg.sharinglog.web.rotation;

import java.time.Instant;

import gdg.sharinglog.service.rotation.api.idempotency.CommandResponse;
import gdg.sharinglog.service.rotation.api.idempotency.IdempotentCommandExecutor;
import gdg.sharinglog.service.rotation.api.member.MemberLeaveApplicationService;
import gdg.sharinglog.service.rotation.api.member.RotationMemberQueryService;
import gdg.sharinglog.web.rotation.dto.MemberLeaveResponse;
import gdg.sharinglog.web.rotation.dto.RotationMemberListResponse;
import gdg.sharinglog.web.rotation.http.ExpectedVersion;
import gdg.sharinglog.web.rotation.http.IdempotencyKey;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/groups/{groupId}")
public class RotationMemberController {

    private final MemberLeaveApplicationService leaveService;
    private final RotationMemberQueryService queryService;
    private final IdempotentCommandExecutor idempotentExecutor;

    public RotationMemberController(
            MemberLeaveApplicationService leaveService,
            RotationMemberQueryService queryService,
            IdempotentCommandExecutor idempotentExecutor
    ) {
        this.leaveService = leaveService;
        this.queryService = queryService;
        this.idempotentExecutor = idempotentExecutor;
    }

    @GetMapping("/rotation-members")
    public RotationMemberListResponse activeMembers(
            @PathVariable String groupId,
            OAuth2AuthenticationToken authentication
    ) {
        return queryService.findActiveMembers(
                groupId,
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getPrincipal()
        );
    }

    @PostMapping("/members/{membershipId}/leave")
    public ResponseEntity<MemberLeaveResponse> leave(
            @PathVariable String groupId,
            @PathVariable String membershipId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(name = "If-Match", required = false) String ifMatch,
            OAuth2AuthenticationToken authentication,
            HttpServletRequest request
    ) {
        ExpectedVersion expectedVersion = ExpectedVersion.parse(ifMatch);
        var result = idempotentExecutor.execute(
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getPrincipal(),
                request.getMethod(),
                request.getRequestURI(),
                IdempotencyKey.parse(idempotencyKey),
                null,
                ifMatch,
                MemberLeaveResponse.class,
                () -> {
                    MemberLeaveResponse response = leaveService.leave(
                            groupId,
                            membershipId,
                            authentication.getAuthorizedClientRegistrationId(),
                            authentication.getPrincipal(),
                            expectedVersion.value(),
                            Instant.now()
                    );
                    return new CommandResponse<>(
                            200,
                            response,
                            new ExpectedVersion(response.member().version()).toStrongEtag(),
                            null
                    );
                }
        );
        return RotationHttpResponses.from(result);
    }
}
