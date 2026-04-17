package io.github.jasper.transfer.encrypt.demo.web;

import io.github.jasper.transfer.encrypt.demo.feign.DemoEncryptedFeignClient;
import java.util.Collections;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoFeignController {

    private final DemoEncryptedFeignClient demoEncryptedFeignClient;

    public DemoFeignController(final DemoEncryptedFeignClient demoEncryptedFeignClient) {
        this.demoEncryptedFeignClient = demoEncryptedFeignClient;
    }

    @PostMapping(value = "/api/feign/json", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> feignJson(@RequestBody final Map<String, Object> requestBody) {
        return Collections.singletonMap("downstream", demoEncryptedFeignClient.relay(requestBody));
    }
}
