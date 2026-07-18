package gdg.sharinglog.config.oauth;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class OAuth2SuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final OAuth2UserPersistenceService userPersistenceService;

    public OAuth2SuccessHandler(OAuth2UserPersistenceService userPersistenceService) {
        this.userPersistenceService = userPersistenceService;
        setDefaultTargetUrl("/");
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        if (authentication instanceof OAuth2AuthenticationToken oAuth2Token) {
            userPersistenceService.saveOrUpdate(
                    oAuth2Token.getAuthorizedClientRegistrationId(),
                    oAuth2Token.getPrincipal()
            );
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
