package gdg.sharinglog.web;

import java.net.URI;

import gdg.sharinglog.service.GroupService;
import gdg.sharinglog.web.dto.CreateGroupRequest;
import gdg.sharinglog.web.dto.GroupResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/groups")
@RestController
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

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
}
