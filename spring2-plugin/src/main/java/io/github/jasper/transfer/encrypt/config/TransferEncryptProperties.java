package io.github.jasper.transfer.encrypt.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 传输层加密配置项。
 *
 * <p>该配置同时服务于：</p>
 * <ul>
 *     <li>Spring MVC 入站请求/响应加解密</li>
 *     <li>OpenFeign 出站调用</li>
 *     <li>文件上传/下载 MD5 校验</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "transfer.encrypt")
public class TransferEncryptProperties {

    private boolean enabled = true;

    private boolean feignEnabled = true;

    private String privateKey;

    private String publicKey;

    private List<String> includePathRegex = new ArrayList<>(Arrays.asList("^/.*$"));

    private List<String> excludePathRegex = new ArrayList<>();

    private String multipartMd5FieldPrefix = "__md5_";

    private Map<String, String> feignPublicKeys = new LinkedHashMap<String, String>();

    private int filterOrder = -10;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isFeignEnabled() {
        return feignEnabled;
    }

    public void setFeignEnabled(final boolean feignEnabled) {
        this.feignEnabled = feignEnabled;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(final String privateKey) {
        this.privateKey = privateKey;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(final String publicKey) {
        this.publicKey = publicKey;
    }

    public List<String> getIncludePathRegex() {
        return includePathRegex;
    }

    public void setIncludePathRegex(final List<String> includePathRegex) {
        this.includePathRegex = includePathRegex;
    }

    public List<String> getExcludePathRegex() {
        return excludePathRegex;
    }

    public void setExcludePathRegex(final List<String> excludePathRegex) {
        this.excludePathRegex = excludePathRegex;
    }

    public String getMultipartMd5FieldPrefix() {
        return multipartMd5FieldPrefix;
    }

    public void setMultipartMd5FieldPrefix(final String multipartMd5FieldPrefix) {
        this.multipartMd5FieldPrefix = multipartMd5FieldPrefix;
    }

    public Map<String, String> getFeignPublicKeys() {
        return feignPublicKeys;
    }

    public void setFeignPublicKeys(final Map<String, String> feignPublicKeys) {
        this.feignPublicKeys = feignPublicKeys;
    }

    public int getFilterOrder() {
        return filterOrder;
    }

    public void setFilterOrder(final int filterOrder) {
        this.filterOrder = filterOrder;
    }
}
