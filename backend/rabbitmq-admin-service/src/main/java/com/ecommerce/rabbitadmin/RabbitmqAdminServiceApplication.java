package com.ecommerce.rabbitadmin;

import com.ecommerce.rabbitadmin.config.RabbitAdminProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(RabbitAdminProperties.class)
public class RabbitmqAdminServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RabbitmqAdminServiceApplication.class, args);
    }
}
