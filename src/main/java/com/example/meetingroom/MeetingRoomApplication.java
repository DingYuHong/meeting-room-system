package com.example.meetingroom;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot 應用程式入口點
 *
 * @SpringBootApplication 是組合 Annotation，包含：
 *   - @Configuration     - 允許定義 Bean
 *   - @EnableAutoConfiguration - 自動配置（如 JPA、Web MVC）
 *   - @ComponentScan     - 掃描 com.example.meetingroom 下所有 @Component
 */
@SpringBootApplication
public class MeetingRoomApplication {
    public static void main(String[] args) {
        SpringApplication.run(MeetingRoomApplication.class, args);
    }
}
