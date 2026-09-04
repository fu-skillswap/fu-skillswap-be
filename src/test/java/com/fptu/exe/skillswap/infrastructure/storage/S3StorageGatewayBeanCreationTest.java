package com.fptu.exe.skillswap.infrastructure.storage;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("prod")
@TestPropertySource(properties = {
        "DATABASE_URL=jdbc:h2:mem:teststorage;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "DATABASE_USERNAME=sa",
        "DATABASE_PASSWORD=",
        "spring.datasource.url=jdbc:h2:mem:teststorage;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "FLYWAY_ENABLED=false",
        "HIBERNATE_DDL_AUTO=create-drop",
        "application.production-validation.enabled=false",
        "application.storage.enabled=true",
        "application.storage.endpoint=https://test.r2.cloudflarestorage.com",
        "application.storage.access-key=test-access-key",
        "application.storage.secret-key=test-secret-key",
        "application.storage.bucket=test-bucket",
        "application.storage.region=auto",
        "JWT_SECRET_KEY=c2VjcmV0c2VjcmV0c2VjcmV0c2VjcmV0c2VjcmV0c2VjcmV0c2VjcmV0",
        "JWT_ISSUER=test",
        "JWT_AUDIENCE=test",
        "CORS_ALLOWED_ORIGIN_PATTERNS=http://localhost:3000",
        "CURSOR_AES_KEY=Q3Vyc29yUGhhc2UxQWVzS2V5Rm9yU2tpbGxTd2FwMDE=",
        "CURSOR_HMAC_KEY=Q3Vyc29yUGhhc2UxSG1hY0tleUZvclNraWxsU3dhcDAx"
})
class S3StorageGatewayBeanCreationTest {

    @Autowired(required = false)
    private StorageGateway storageGateway;

    @Autowired(required = false)
    private StorageProperties storageProperties;

    @Autowired(required = false)
    private S3Client s3Client;

    @Autowired(required = false)
    private S3Presigner s3Presigner;

    @Test
    void testStorageGatewayIsCreatedInProdWhenStorageEnabled() {
        System.out.println("=== DIAGNOSTIC START ===");
        System.out.println("StorageProperties: " + storageProperties);
        if (storageProperties != null) {
            System.out.println("StorageProperties enabled: " + storageProperties.isEnabled());
            System.out.println("StorageProperties endpoint: " + storageProperties.getEndpoint());
            System.out.println("StorageProperties bucket: " + storageProperties.getBucket());
        }
        System.out.println("S3Client bean: " + s3Client);
        System.out.println("S3Presigner bean: " + s3Presigner);
        System.out.println("StorageGateway bean: " + storageGateway);
        System.out.println("=== DIAGNOSTIC END ===");

        assertThat(storageProperties).isNotNull();
        assertThat(storageProperties.isEnabled()).isTrue();
        assertThat(s3Client).isNotNull();
        assertThat(s3Presigner).isNotNull();
        assertThat(storageGateway).isNotNull();
        assertThat(storageGateway).isInstanceOf(S3StorageGatewayImpl.class);
    }
}
