package io.github.jasper.transfer.encrypt.demo.feign;

import io.github.jasper.transfer.encrypt.annotation.TransferEncryptedFeignClient;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@TransferEncryptedFeignClient(md5Enabled = true)
@FeignClient(name = "demoEncryptedFeignClient", url = "${demo.feign-base-url:http://localhost:${server.port}}")
public interface DemoEncryptedFeignClient {

    @TransferEncryptedFeignClient
    @PostMapping(value = "/downstream/feign/json", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    Map<String, Object> relay(@RequestBody Map<String, Object> requestBody);
}
