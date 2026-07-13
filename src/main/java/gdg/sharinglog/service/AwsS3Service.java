/*
package gdg.sharinglog.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.UUID;

@Profile("!local") // local 프로파일이 아닐 때만 이 서비스가 활성화 되도록 설정
@Service
@RequiredArgsConstructor
public class AwsS3Service implements FileStorageService {

    private final S3Client s3Client;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Override
    public UploadResponse store(byte[] bytes, String filename) {
        // 업로드된 파일명에서 확장자를 추출
        String ext = filename.contains(".")
                ? filename.substring(filename.lastIndexOf('.') + 1).toLowerCase()
                : "png";

        String key = "uploads/" + UUID.randomUUID() + "." + ext;

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .contentType("image/" + ext)
                .key(key)
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(bytes)); // S3 버킷에 파일을 업로드

        String url = s3Client.utilities() //업로드된 객체의 S3 접근 URL을 생성
                .getUrl(builder -> builder.bucket(bucket).key(key))
                .toExternalForm();

        return new UploadResponse(url);
    }
}

*/
