package io.github.jasper.transfer.encrypt.demo;

import io.github.jasper.transfer.encrypt.demo.feign.DemoEncryptedFeignClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(clients = DemoEncryptedFeignClient.class)
public class E2EDemoApplication {

    public static void main(final String[] args) {
        SpringApplication.run(E2EDemoApplication.class, args);
    }
}
