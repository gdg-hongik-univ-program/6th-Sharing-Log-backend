package gdg.sharinglog.config.oauth;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class OAuth2SuccessHandler
        extends SavedRequestAwareAuthenticationSuccessHandler {

    private final OAuth2UserPersistenceService userPersistenceService;

    public OAuth2SuccessHandler(
            OAuth2UserPersistenceService userPersistenceService,
            @Value("${app.oauth2-success-url:/}") String successUrl
    ) {
        this.userPersistenceService = userPersistenceService;
        setDefaultTargetUrl(successUrl);
        // 로그인 성공 후 이전 요청 주소를 무시하고
        // React 프론트엔드의 그룹 선택 화면으로 항상 이동
        setAlwaysUseDefaultTargetUrl(true);
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {

        if (authentication instanceof OAuth2AuthenticationToken oAuth2Token) {
            userPersistenceService.saveOrUpdate(
                    oAuth2Token.getAuthorizedClientRegistrationId(),
                    oAuth2Token.getPrincipal()
            );
        }

        super.onAuthenticationSuccess(
                request,
                response,
                authentication
        );
    }
}
