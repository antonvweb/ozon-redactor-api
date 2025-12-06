package org.ozonLabel.ozonApi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackages = {
        "org.ozonLabel.ozonApi",      // свои сущности (если будут)
        "org.ozonLabel.common.model",
        "org.ozonLabel.domain.model" // <-- вот эта строка решает проблему
})
@EnableJpaRepositories(basePackages = {
        "org.ozonLabel.ozonApi.repository",
        "org.ozonLabel.domain.repository"   // ← ДОБАВЬ ЭТУ СТРОКУ!
})
public class OzonIntegrationApplication {

    public static void main(String[] args) {
        SpringApplication.run(OzonIntegrationApplication.class, args);
    }
}