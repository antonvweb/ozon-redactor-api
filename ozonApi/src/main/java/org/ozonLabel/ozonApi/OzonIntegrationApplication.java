package org.ozonLabel.ozonApi;

import org.ozonLabel.common.exception.user.GlobalExceptionHandler; // бин, который конфликтует
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication
@ComponentScan(
        basePackages = {
                "org.ozonLabel.ozonApi",   // пакет твоего сервиса
                "org.ozonLabel.common"     // весь common
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = GlobalExceptionHandler.class // исключаем только конфликтный бин
        )
)
public class OzonIntegrationApplication {

    public static void main(String[] args) {
        SpringApplication.run(OzonIntegrationApplication.class, args);
    }

}
