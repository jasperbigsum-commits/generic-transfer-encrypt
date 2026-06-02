package io.github.jasper.transfer.encrypt.web;

import io.github.jasper.transfer.encrypt.config.TransferEncryptProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 路径正则匹配器。
 *
 * <p>同时支持 include / exclude 两组正则，便于做零侵入按路径启停。</p>
 */
public class TransferPathMatcher {

    private final List<Pattern> includePatterns;

    private final List<Pattern> excludePatterns;

    /**
     * @param properties externalized starter properties containing include and exclude regex lists
     */
    public TransferPathMatcher(final TransferEncryptProperties properties) {
        this.includePatterns = compile(properties.getIncludePathRegex());
        this.excludePatterns = compile(properties.getExcludePathRegex());
    }

    /**
     * Evaluates the request URI against include rules first and exclude rules second.
     *
     * @param requestUri current request URI without scheme or host
     * @return {@code true} when the URI should participate in transport-layer processing
     */
    public boolean matches(final String requestUri) {
        return matchesAny(includePatterns, requestUri) && !matchesAny(excludePatterns, requestUri);
    }

    private List<Pattern> compile(final List<String> expressions) {
        final List<Pattern> patterns = new ArrayList<Pattern>();
        for (final String expression : expressions) {
            patterns.add(Pattern.compile(expression));
        }
        return patterns;
    }

    private boolean matchesAny(final List<Pattern> patterns, final String requestUri) {
        if (patterns.isEmpty()) {
            return false;
        }
        for (final Pattern pattern : patterns) {
            if (pattern.matcher(requestUri).matches()) {
                return true;
            }
        }
        return false;
    }
}
