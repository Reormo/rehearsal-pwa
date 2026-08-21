package com.bandclub.rehearsal.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class RealtimeWebMvcConfig implements WebMvcConfigurer {

    private final RealtimeMutationInterceptor interceptor;

    public RealtimeWebMvcConfig(RealtimeMutationInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor);
    }
}
