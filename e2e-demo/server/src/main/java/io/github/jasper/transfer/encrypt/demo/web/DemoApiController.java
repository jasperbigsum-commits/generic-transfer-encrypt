package io.github.jasper.transfer.encrypt.demo.web;

import io.github.jasper.transfer.encrypt.config.TransferEncryptProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import io.github.jasper.transfer.encrypt.core.TransferConstants;
import io.github.jasper.transfer.encrypt.core.TransferRequestContext;
import javax.servlet.http.HttpServletRequest;

import io.github.jasper.transfer.encrypt.demo.dto.TransferTableDTO;
import org.bouncycastle.jcajce.provider.digest.SHA512;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class DemoApiController {

    private final TransferEncryptProperties properties;

    public DemoApiController(final TransferEncryptProperties properties) {
        this.properties = properties;
    }

    @GetMapping(value = "/demo/public-key", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> publicKey() {
        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("publicKey", properties.getPublicKey());
        payload.put("baseUrl", "http://localhost:8081");
        payload.put("message", "demo runtime key pair generated on startup");
        return payload;
    }

    @PostMapping(value = "/api/json")
    public Map<String, Object> json(@RequestBody TransferTableDTO body) {
        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("mode", "json");
        payload.put("received", body);
        payload.put("message", "JSON request decrypted successfully");
        return payload;
    }

    @PostMapping(value = "/api/form", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> form(@RequestParam final Map<String, String> form) {
        final Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("mode", "form");
        payload.put("received", form);
        payload.put("message", "Form request decrypted successfully");
        return payload;
    }

    @GetMapping(value = "/api/query", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> query(@RequestParam final Map<String, String> query) {
        final Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("mode", "query");
        payload.put("received", query);
        payload.put("message", "Query request decrypted successfully");
        return payload;
    }

    @GetMapping(value = "/api/table", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> table(@RequestParam(required = false) final Map<String, String> query) {
        final String keyword = query != null ? query.get("keyword") : null;
        final List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        rows.add(row(1, "alice"));
        rows.add(row(2, "bob"));
        rows.add(row(3, "carol"));

        final List<Map<String, Object>> filtered = new ArrayList<Map<String, Object>>();
        for (final Map<String, Object> row : rows) {
            if (keyword == null || String.valueOf(row.get("name")).contains(keyword)) {
                filtered.add(row);
            }
        }

        final Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("code", 0);
        payload.put("msg", "");
        payload.put("count", filtered.size());
        payload.put("data", filtered);
        return payload;
    }

    @GetMapping(value = "/api/profile", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> profile(@RequestParam(required = false, defaultValue = "1") final String userId) {
        final Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("userId", userId);
        payload.put("name", "alice");
        payload.put("city", "shanghai");
        payload.put("role", "demo-user");
        return payload;
    }

    @PostMapping(value = "/api/upload", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> upload(@RequestPart("file") final MultipartFile file,
            @RequestParam(required = false) final Map<String, String> params) throws IOException {
        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("mode", "upload");
        payload.put("fileName", file.getOriginalFilename());
        payload.put("size", file.getSize());
        payload.put("extra", params);
        SHA512.Digest digest = new SHA512.Digest();
        byte[] hash512 = digest.digest(file.getBytes());
        payload.put("preview", new String(Hex.encode(hash512), StandardCharsets.UTF_8));
        return payload;
    }

    @PostMapping(value = "/api/upload/multi", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> uploadMulti(@RequestPart("files") final MultipartFile[] files) {
        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("mode", "upload-multi");
        payload.put("count", files.length);
        payload.put("fileNames", Arrays.asList(
                files[0].getOriginalFilename(),
                files.length > 1 ? files[1].getOriginalFilename() : null));
        return payload;
    }

    @GetMapping(value = "/api/download", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> download() {
        final byte[] bytes = "demo-download-content".getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("demo-download.txt").build().toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bytes);
    }

    @PostMapping(value = "/downstream/feign/json", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> downstreamFeignJson(@org.springframework.web.bind.annotation.RequestBody
            final Map<String, Object> body, final HttpServletRequest request) {
        final Map<String, Object> payload = new LinkedHashMap<String, Object>();
        final TransferRequestContext context =
                (TransferRequestContext) request.getAttribute(TransferConstants.REQUEST_ATTRIBUTE);
        payload.put("name", body.get("name"));
        payload.put("from", body.get("from"));
        payload.put("encryptedRequest", context != null && context.isEncryptedRequest());
        payload.put("message", "OpenFeign downstream request decrypted successfully");
        return payload;
    }

    private Map<String, Object> row(final int id, final String name) {
        final Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("id", id);
        row.put("name", name);
        return row;
    }

}
