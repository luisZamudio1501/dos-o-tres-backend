package com.dosotres.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final CurrentGroupIdResolver currentGroupIdResolver;
    private final AuthUserResolver authUserResolver;

    public WebMvcConfig(CurrentGroupIdResolver currentGroupIdResolver, AuthUserResolver authUserResolver) {
        this.currentGroupIdResolver = currentGroupIdResolver;
        this.authUserResolver = authUserResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentGroupIdResolver);
        resolvers.add(authUserResolver);
    }
}
