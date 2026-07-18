package gdg.sharinglog;

import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.GroupRole;
import gdg.sharinglog.domain.OAuthProvider;
import gdg.sharinglog.domain.SharingGroup;
import gdg.sharinglog.domain.User;
import gdg.sharinglog.repository.GroupMemberRepository;
import gdg.sharinglog.repository.SharingGroupRepository;
import gdg.sharinglog.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GroupCreationApiTest {

    private static final String GOOGLE_USER_ID = "group-creator-google-id";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    SharingGroupRepository groupRepository;

    @Autowired
    GroupMemberRepository groupMemberRepository;

    private User creator;

    @BeforeEach
    void saveCreator() {
        creator = userRepository.save(User.builder()
                .provider(OAuthProvider.GOOGLE)
                .providerUserId(GOOGLE_USER_ID)
                .email("creator@example.com")
                .build());
    }

    @Test
    void createsGroupAndRegistersCreatorAsOwner() throws Exception {
        mockMvc.perform(post("/api/groups")
                        .with(csrf())
                        .with(oauth2Login()
                                .clientRegistration(googleClientRegistration())
                                .attributes(attributes -> {
                                    attributes.put("sub", GOOGLE_USER_ID);
                                    attributes.put("email", "creator@example.com");
                                }))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  우리 집  "}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/groups/")))
                .andExpect(jsonPath("$.groupId", notNullValue()))
                .andExpect(jsonPath("$.name").value("우리 집"))
                .andExpect(jsonPath("$.membershipId", notNullValue()))
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andExpect(jsonPath("$.createdAt", notNullValue()));

        assertEquals(1, groupRepository.count());
        assertEquals(1, groupMemberRepository.count());

        SharingGroup group = groupRepository.findAll().getFirst();
        GroupMember membership = groupMemberRepository
                .findByGroup_IdAndUser_Id(group.getId(), creator.getId())
                .orElseThrow();

        assertEquals(GroupRole.OWNER, membership.getRole());
        assertEquals(creator.getId(), group.getCreatedBy().getId());
    }

    @Test
    void rejectsBlankGroupNameWithoutCreatingData() throws Exception {
        mockMvc.perform(post("/api/groups")
                        .with(csrf())
                        .with(oauth2Login()
                                .clientRegistration(googleClientRegistration())
                                .attributes(attributes -> attributes.put("sub", GOOGLE_USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"   "}
                                """))
                .andExpect(status().isBadRequest());

        assertEquals(0, groupRepository.count());
        assertEquals(0, groupMemberRepository.count());
    }

    @Test
    void requiresCsrfTokenForGroupCreation() throws Exception {
        mockMvc.perform(post("/api/groups")
                        .with(oauth2Login()
                                .clientRegistration(googleClientRegistration())
                                .attributes(attributes -> attributes.put("sub", GOOGLE_USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"우리 집"}
                                """))
                .andExpect(status().isForbidden());

        assertEquals(0, groupRepository.count());
        assertEquals(0, groupMemberRepository.count());
    }

    @Test
    void rejectsAuthenticatedUserMissingFromDatabase() throws Exception {
        mockMvc.perform(post("/api/groups")
                        .with(csrf())
                        .with(oauth2Login()
                                .clientRegistration(googleClientRegistration())
                                .attributes(attributes -> attributes.put("sub", "missing-user-id")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"우리 집"}
                                """))
                .andExpect(status().isUnauthorized());

        assertTrue(groupRepository.findAll().isEmpty());
        assertTrue(groupMemberRepository.findAll().isEmpty());
    }

    private ClientRegistration googleClientRegistration() {
        return ClientRegistration.withRegistrationId("google")
                .clientId("test-google-client-id")
                .clientSecret("test-google-client-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
                .userNameAttributeName("sub")
                .clientName("Google")
                .build();
    }
}
