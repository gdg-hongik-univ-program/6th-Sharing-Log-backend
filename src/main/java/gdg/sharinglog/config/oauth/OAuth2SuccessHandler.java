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
            @Value("${app.oauth2-success-url:/}")
                    // app.frontend-base-url:http://localhost:5173 을
                    // 다음과 같이 설정
                    // app.oauth2-success-url:/

            String successUrl
                    // frontendBaseUrl 을 successUrl 로
    ) {
        this.userPersistenceService = userPersistenceService;

        /* 주석처리로
        String normalizedFrontendUrl =
                frontendBaseUrl.replaceFirst("/+$", "");
        */

        // Google 로그인 성공 후 React 하우스 선택 화면으로 이동
        // normalizedFrontendUrl + "/house-choice" 을 successUrl로
        setDefaultTargetUrl(successUrl);



        // 로그인 성공 후 이전 요청 주소를 무시하고
        // React 프론트엔드의 그룹 선택 화면으로 항상 이동
        // 초대 링크의 코드가 이전 요청 URL에만 포함되어 있다면
        // 이 과정에서 코드가 유지되지 않을 수 있다.
        setAlwaysUseDefaultTargetUrl(true);

        // 백엔드만 단독으로 테스트할 때는 frontendBaseUrl을 백엔드 주소로 변경하고,
        // 기본 이동 경로도 "/house-choice" 대신 "/" 등 테스트용 경로로 수정해야 한다.

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
