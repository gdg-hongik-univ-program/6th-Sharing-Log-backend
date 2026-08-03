package gdg.sharinglog.web;

import java.net.URI;

import gdg.sharinglog.service.group.GroupMemberQueryService;
import gdg.sharinglog.service.group.GroupService;
import gdg.sharinglog.web.dto.CreateGroupRequest;
import gdg.sharinglog.web.dto.GroupResponse;
import gdg.sharinglog.web.dto.MyGroupResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/groups")
@RestController
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;
    private final GroupMemberQueryService groupMemberQueryService;

    @PostMapping
    public ResponseEntity<GroupResponse> createGroup(@Valid @RequestBody CreateGroupRequest request,
                                                     OAuth2AuthenticationToken authentication) {
        GroupResponse response = GroupResponse.from(groupService.createGroup(
                request.name(),
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getPrincipal()
        ));

        return ResponseEntity
                .created(URI.create("/api/groups/" + response.groupId()))
                .body(response);
    }

    @GetMapping("/me")
    public ResponseEntity<MyGroupResponse> myGroup(OAuth2AuthenticationToken authentication) {
        return groupMemberQueryService.findMyGroup(
                        authentication.getAuthorizedClientRegistrationId(),
                        authentication.getPrincipal()
                )
                .map(MyGroupResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
