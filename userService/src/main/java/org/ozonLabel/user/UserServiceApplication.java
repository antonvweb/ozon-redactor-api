package org.ozonLabel.user;

import org.ozonLabel.common.exception.ozon.GlobalExceptionHandler; // бин из ozon, который конфликтует
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication
@ComponentScan(
        basePackages = {
                "org.ozonLabel.user",
                "org.ozonLabel.common"  // весь common
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = GlobalExceptionHandler.class // исключаем конфликтный бин из ozon
        )
)
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }

}
