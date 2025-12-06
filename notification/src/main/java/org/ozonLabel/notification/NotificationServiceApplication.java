package org.ozonLabel.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories(basePackages = {
        "org.ozonLabel.user.repository",
        "org.ozonLabel.ozonApi.repository",
        "org.ozonLabel.domain.repository"   // ← ДОБАВЬ ЭТУ СТРОКУ!
})
@EntityScan(basePackages = {
        "org.ozonLabel.user.model",
        "org.ozonLabel.ozonApi.model",
        "org.ozonLabel.domain.model"        // ← И ЭТУ ТОЖЕ, на всякий случай
})
public class NotificationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}