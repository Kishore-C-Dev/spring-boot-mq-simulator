package com.mqsim;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaAuditing
@EntityScan("com.mqsim.model")
@EnableJpaRepositories("com.mqsim.repository")
public class MqMockServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(MqMockServerApplication.class, args);
    }
}
