package gdg.sharinglog.config.oauth;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Component
public class OAuth2FailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2FailureHandler.class);

    public OAuth2FailureHandler(
            @Value("${app.oauth2-failure-url:/?error=true}")
            String failureUrl
    ) {
        setDefaultFailureUrl(failureUrl);
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException, ServletException {
        if (exception instanceof OAuth2AuthenticationException oAuth2Exception) {
            log.error(
                    "OAuth2 authentication failed: errorCode={}",
                    oAuth2Exception.getError().getErrorCode(),
                    exception
            );
        } else {
            log.error(
                    "OAuth2 authentication failed: exceptionType={}",
                    exception.getClass().getSimpleName(),
                    exception
            );
        }

        super.onAuthenticationFailure(request, response, exception);
    }
}
