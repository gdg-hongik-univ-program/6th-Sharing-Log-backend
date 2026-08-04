package gdg.sharinglog;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gdg.sharinglog.domain.GroupMember;
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
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MyGroupApiTest {

    private static final String GOOGLE_USER_ID = "my-group-google-id";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    SharingGroupRepository groupRepository;

    @Autowired
    GroupMemberRepository groupMemberRepository;

    private User user;

    @BeforeEach
    void saveUser() {
        user = userRepository.save(User.builder()
                .provider(OAuthProvider.GOOGLE)
                .providerUserId(GOOGLE_USER_ID)
                .email("mygroup@example.com")
                .build());
    }

    @Test
    void returnsActiveGroupWithMembershipVersion() throws Exception {
        SharingGroup group = new SharingGroup("우리 집", user);
        group.updateAddress("서울시 강남구 역삼동");
        group = groupRepository.save(group);
        GroupMember membership = groupMemberRepository.save(GroupMember.owner(group, user));

        mockMvc.perform(get("/api/groups/me")
                        .with(oauth2Login()
                                .clientRegistration(googleClientRegistration())
                                .attributes(attributes -> attributes.put("sub", GOOGLE_USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupPublicId").value(group.getPublicId()))
                .andExpect(jsonPath("$.membershipPublicId").value(membership.getPublicId()))
                .andExpect(jsonPath("$.membershipVersion").value(membership.getVersion()))
                .andExpect(jsonPath("$.groupName").value("우리 집"))
                .andExpect(jsonPath("$.groupAddress").value("서울시 강남구 역삼동"))
                .andExpect(jsonPath("$.role").value("OWNER"));
    }

    @Test
    void returnsNotFoundWhenUserHasNoActiveGroup() throws Exception {
        mockMvc.perform(get("/api/groups/me")
                        .with(oauth2Login()
                                .clientRegistration(googleClientRegistration())
                                .attributes(attributes -> attributes.put("sub", GOOGLE_USER_ID))))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsUnauthorizedForUserMissingFromDatabase() throws Exception {
        mockMvc.perform(get("/api/groups/me")
                        .with(oauth2Login()
                                .clientRegistration(googleClientRegistration())
                                .attributes(attributes -> attributes.put("sub", "missing-user-id"))))
                .andExpect(status().isUnauthorized());
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
