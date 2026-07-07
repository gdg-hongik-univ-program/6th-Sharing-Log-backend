## Google 로그인 실행 방법

Google Cloud Console에서 OAuth 2.0 Client ID를 만들고 승인된 리디렉션 URI에 아래 주소를 등록합니다.

```text
http://localhost:8080/login/oauth2/code/google
```

PowerShell에서 앱을 실행하기 전에 발급받은 값을 환경변수로 설정합니다.

```powershell
Set-Location -LiteralPath "C:\Users\SAMSUNG\Desktop\Sharing Log\sharingLog"
$env:GOOGLE_CLIENT_ID="발급받은-client-id"
$env:GOOGLE_CLIENT_SECRET="발급받은-client-secret"
$env:JAVA_HOME="C:\Users\SAMSUNG\.jdks\corretto-26.0.1"
.\gradlew.bat bootRun
```

그다음 브라우저에서 `http://localhost:8080/login`에 접속하고 `구글로 로그인하기`를 클릭합니다.

환경변수를 설정하지 않아도 앱은 개발용 기본값으로 실행되지만, Google에서는 `invalid_client` 오류가 표시됩니다.

### IntelliJ에서 실행하는 경우

PowerShell에 설정한 `$env:GOOGLE_CLIENT_ID`와 `$env:GOOGLE_CLIENT_SECRET`은 IntelliJ 실행 버튼에는 자동으로 전달되지 않습니다.

IntelliJ 오른쪽 위 실행 설정에서 `SharingLogApplication`을 선택한 뒤 `Edit Configurations...`를 열고, `Environment variables`에 아래처럼 추가합니다.

```text
GOOGLE_CLIENT_ID=발급받은-client-id;GOOGLE_CLIENT_SECRET=발급받은-client-secret
```

Google에서 `401 invalid_client`가 뜨면 대부분 아래 중 하나입니다.

- 환경변수가 IDE 실행 설정에 들어가지 않아 `client-id`가 비어 있거나 잘못 전달됨
- Google Cloud Console에서 만든 OAuth Client가 `웹 애플리케이션` 타입이 아님
- Client ID/Secret을 복사할 때 앞뒤 공백이나 따옴표가 들어감
- 삭제했거나 다른 프로젝트의 OAuth Client ID를 사용함