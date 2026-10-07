package com.manacommunity.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {
        "com.manacommunity.payment",
        "com.manacommunity.common.security"
})
@EntityScan(basePackages = {
        "com.manacommunity.payment.model",
        "com.manacommunity.common.model",
        "com.manacommunity.common.user.model"
})
@EnableJpaRepositories(basePackages = {
        "com.manacommunity.payment.repository",
        "com.manacommunity.common.repository",
        "com.manacommunity.common.user.repository"
})
public class PaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentApplication.class, args);
    }
}
