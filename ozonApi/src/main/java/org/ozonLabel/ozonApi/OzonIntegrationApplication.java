package org.ozonLabel.ozonApi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"org.ozonLabel.common"})
public class OzonIntegrationApplication {

    public static void main(String[] args) {
        SpringApplication.run(OzonIntegrationApplication.class, args);
    }
}