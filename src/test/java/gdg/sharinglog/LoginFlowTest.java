package gdg.sharinglog;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class LoginFlowTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void loginPageShowsOAuth2LoginButtons() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("구글로 로그인하기")))
                .andExpect(content().string(containsString("/oauth2/authorization/google")))
                .andExpect(content().string(containsString("네이버로 로그인하기")))
                .andExpect(content().string(containsString("/oauth2/authorization/naver")));
    }

    @Test
    void googleAuthorizationEndpointRedirectsToGoogle() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location",
                        startsWith("https://accounts.google.com/o/oauth2/v2/auth?")));
    }

    @Test
    void naverAuthorizationEndpointRedirectsToNaver() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/naver"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location",
                        startsWith("https://nid.naver.com/oauth2.0/authorize?")));
    }

    @Test
    void homeShowsAuthenticatedGoogleUser() throws Exception {
        mockMvc.perform(get("/").with(oauth2Login()
                        .attributes(attributes -> {
                            attributes.put("name", "Test User");
                            attributes.put("email", "test@example.com");
                        })))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Test User님, 로그인되었습니다.")))
                .andExpect(content().string(containsString("test@example.com")));
    }

    // POST /api/auth/logout 호출 시 204가 오고 세션이 invalid 처리되는지 확인
    @Test
    void apiLogoutInvalidatesSessionAndReturnsNoContent() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/api/auth/logout")
                        .session(session)
                        .with(oauth2Login()))
                .andExpect(status().isNoContent());

        assertTrue(session.isInvalid());
    }
}
