package gdg.sharinglog.web;

import gdg.sharinglog.service.AcceptedInvitation;
import gdg.sharinglog.service.InvitationAcceptanceService;
import gdg.sharinglog.service.InvitationPreview;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/invitations")
@RestController
public class InvitationApiController {

    private final InvitationAcceptanceService acceptanceService;

    public InvitationApiController(
            InvitationAcceptanceService acceptanceService
    ) {
        this.acceptanceService = acceptanceService;
    }

    @GetMapping("/{code}")
    public ResponseEntity<InvitationPreview> previewInvitation(
            @PathVariable String code,
            OAuth2AuthenticationToken authentication
    ) {
        InvitationPreview preview = acceptanceService.preview(
                code,
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getPrincipal()
        );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(preview);
    }

    @PostMapping("/{code}/accept")
    public ResponseEntity<AcceptedInvitation> acceptInvitation(
            @PathVariable String code,
            OAuth2AuthenticationToken authentication
    ) {
        AcceptedInvitation result = acceptanceService.accept(
                code,
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getPrincipal()
        );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(result);
    }
}