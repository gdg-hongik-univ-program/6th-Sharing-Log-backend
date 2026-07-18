package gdg.sharinglog.web.dto.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class LoginController {

    @GetMapping("/login")
    @ResponseBody
    public String login() {
        return """
                <!doctype html>
                <html lang="ko">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <title>Sharing Log 로그인</title>
                    <link rel="stylesheet" href="/css/app.css">
                </head>
                <body>
                    <main class="shell">
                        <section class="panel">
                            <p class="eyebrow">Sharing Log</p>
                            <h1>쉐어링 로그 시작하기</h1>
                            <p class="copy">공동생활 그룹을 관리하려면 Google 또는 네이버 계정으로 로그인하세요.</p>
                            <div class="login-buttons">
                                <a class="google-button" href="/oauth2/authorization/google">
                                    <span class="google-mark" aria-hidden="true">G</span>
                                    <span>구글로 로그인하기</span>
                                </a>
                                <a class="naver-button" href="/oauth2/authorization/naver">
                                    <span class="naver-mark" aria-hidden="true">N</span>
                                    <span>네이버로 로그인하기</span>
                                </a>
                            </div>
                        </section>
                    </main>
                </body>
                </html>
                """;
    }
}
