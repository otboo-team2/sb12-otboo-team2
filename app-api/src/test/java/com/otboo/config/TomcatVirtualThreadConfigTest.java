package com.otboo.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.test.context.assertj.AssertableWebApplicationContext;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 실제 톰캣을 띄워 요청을 처리한 스레드가 무엇인지 확인한다. */
class TomcatVirtualThreadConfigTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner(
            AnnotationConfigServletWebServerApplicationContext::new)
            .withConfiguration(AutoConfigurations.of(ServletWebServerFactoryAutoConfiguration.class))
            .withUserConfiguration(TomcatVirtualThreadConfig.class, ThreadProbe.class)
            .withPropertyValues("server.port=0");

    @Test
    void handlesRequestsOnPlatformThreadsByDefault() {
        runner.run(context -> assertThat(requestThread(context)).isEqualTo("platform"));
    }

    @Test
    void handlesRequestsOnVirtualThreadsWhenEnabled() {
        runner.withPropertyValues("otboo.web.virtual-threads.enabled=true")
                .run(context -> assertThat(requestThread(context)).isEqualTo("virtual"));
    }

    private static String requestThread(AssertableWebApplicationContext context) throws Exception {
        int port = ((WebServerApplicationContext) context.getSourceApplicationContext()).getWebServer().getPort();
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/thread")).build(),
                HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    @Configuration(proxyBeanMethods = false)
    static class ThreadProbe {

        @Bean
        ServletRegistrationBean<HttpServlet> threadServlet() {
            return new ServletRegistrationBean<>(new HttpServlet() {
                @Override
                protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
                    response.getWriter().write(Thread.currentThread().isVirtual() ? "virtual" : "platform");
                }
            }, "/thread");
        }
    }
}
