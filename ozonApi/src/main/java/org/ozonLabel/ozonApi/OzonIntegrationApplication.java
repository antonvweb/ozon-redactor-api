package org.ozonLabel.ozonApi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(
        basePackages = {
                "org.ozonLabel.ozonApi",
                "org.ozonLabel.common",
                "org.ozonLabel.user.service",
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "org\\.ozonLabel\\.common\\.exception\\..*\\.GlobalExceptionHandler"
        )
)
@EntityScan(basePackages = {
        "org.ozonLabel.ozonApi.entity",
        "org.ozonLabel.user.entity"
})
@EnableJpaRepositories(basePackages = {
        "org.ozonLabel.ozonApi.repository",
        "org.ozonLabel.user.repository",
})
public class OzonIntegrationApplication {

    public static void main(String[] args) {
        SpringApplication.run(OzonIntegrationApplication.class, args);
    }
}