package com.demo.abtest.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Web 配置：CORS + 前端静态资源托管。
 *
 * 教学说明：
 *  - 生产环境静态资源一般交给 Nginx / CDN，这里由 Spring MVC 的 ResourceHandler 代劳，
 *    保持"一个端口同时跑页面和 API"的演示体验；
 *  - Vite 开发服务器（5173）跨域访问后端（8080）时需要 CORS 放行。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** 前端构建产物目录：默认 ../frontend/dist，可用 -Ddist=xxx 或 --dist=xxx 覆盖 */
    @Value("${dist:}")
    private String distProp;

    @Bean
    public Path distDir() {
        Path dir = (distProp == null || distProp.isBlank())
                ? Paths.get("..", "frontend", "dist")
                : Paths.get(distProp);
        return dir.toAbsolutePath().normalize();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String loc = distDir().toUri().toString();
        // Vite 产物带内容哈希（文件名即版本号），可以放心缓存一年
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(loc + "assets/")
                .setCachePeriod(31536000);
        // index.html 等入口文件：no-store，每次进来都取最新入口
        // （setCachePeriod(0) 会输出 Cache-Control: no-store），
        // 否则前端重新构建后，浏览器还在用启发式缓存的旧入口页，看不到新功能
        registry.addResourceHandler("/**")
                .addResourceLocations(loc)
                .setCachePeriod(0);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // 根路径 → 首页
        registry.addViewController("/").setViewName("forward:/index.html");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("Content-Type", "If-None-Match")
                // 必须把 ETag 暴露给浏览器 JS，否则前端读不到缓存协商头
                .exposedHeaders("ETag");
    }
}
