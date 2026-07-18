package gdg.sharinglog.web.dto.web;

import gdg.sharinglog.service.GroupMemberQueryService;
import gdg.sharinglog.web.dto.web.dto.GroupMembersResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/groups/{groupId}/members")
@RestController
public class GroupMemberController {

    private final GroupMemberQueryService groupMemberQueryService;

    public GroupMemberController(GroupMemberQueryService groupMemberQueryService) {
        this.groupMemberQueryService = groupMemberQueryService;
    }

    @GetMapping
    public ResponseEntity<GroupMembersResponse> members(
            @PathVariable Long groupId,
            OAuth2AuthenticationToken authentication) {
        GroupMembersResponse response = GroupMembersResponse.from(
                groupMemberQueryService.findMembers(
                        groupId,
                        authentication.getAuthorizedClientRegistrationId(),
                        authentication.getPrincipal()
                )
        );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }
}
