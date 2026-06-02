package io.github.jasper.transfer.encrypt.demo.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class DemoWebMvcConfig implements WebMvcConfigurer {

    /*@Override
    public void addResourceHandlers(final ResourceHandlerRegistry registry) {
        final Path repoRoot = Paths.get(System.getProperty("user.dir")).getParent().getParent().normalize();
        final String vendorRoot = repoRoot.resolve("vanilla-js-plugin").resolve("vendor").toUri() + "/";
        registry.addResourceHandler("/demo-assets/vanilla/vendor/**")
                .addResourceLocations(vendorRoot);
    }*/
}
