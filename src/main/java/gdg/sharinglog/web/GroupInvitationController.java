package gdg.sharinglog.web;

import java.net.URI;

import gdg.sharinglog.service.GroupInvitationService;
import gdg.sharinglog.service.IssuedInvitation;
import gdg.sharinglog.web.dto.InvitationResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.util.StringUtils;

@RequestMapping("/api/groups/{groupId}/invitations")
@RestController
public class GroupInvitationController {

    private final GroupInvitationService invitationService;
    private final String publicBaseUrl;

    public GroupInvitationController(GroupInvitationService invitationService,
                                     @Value("${app.public-base-url:}") String publicBaseUrl) {
        this.invitationService = invitationService;
        this.publicBaseUrl = publicBaseUrl;
    }

    @PostMapping
    public ResponseEntity<InvitationResponse> issueInvitation(
            @PathVariable Long groupId,
            OAuth2AuthenticationToken authentication) {
        IssuedInvitation invitation = invitationService.issue(
                groupId,
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getPrincipal()
        );
        String inviteUrl = buildInviteUrl(invitation.code());
        InvitationResponse response = InvitationResponse.from(invitation, inviteUrl);

        return ResponseEntity
                .created(URI.create(response.inviteUrl()))
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    private String buildInviteUrl(String code) {
        if (StringUtils.hasText(publicBaseUrl)) {
            String normalizedBaseUrl = publicBaseUrl.strip().replaceFirst("/+$", "");
            return UriComponentsBuilder
                    .fromUriString(normalizedBaseUrl)
                    .path("/invite/{code}")
                    .buildAndExpand(code)
                    .toUriString();
        }

        return ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path("/invite/{code}")
                .buildAndExpand(code)
                .toUriString();
    }
}
