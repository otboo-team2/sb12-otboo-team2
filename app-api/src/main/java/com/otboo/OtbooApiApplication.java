package com.otboo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// 패키지는 도메인 우선 구조를 따른다. (com.otboo.clothes, com.otboo.feed ...)
// common-core 의 com.otboo.common 도 같은 base package 라 컴포넌트 스캔에 함께 걸린다.
@SpringBootApplication
public class OtbooApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(OtbooApiApplication.class, args);
    }
}
