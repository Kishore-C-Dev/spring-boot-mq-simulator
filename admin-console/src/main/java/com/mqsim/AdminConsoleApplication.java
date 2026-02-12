package com.mqsim;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableJpaAuditing
@EnableAsync
@EntityScan("com.mqsim.model")
@EnableJpaRepositories("com.mqsim.repository")
public class AdminConsoleApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminConsoleApplication.class, args);
    }
}
