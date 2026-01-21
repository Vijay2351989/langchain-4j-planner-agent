package com.krista.kme.agent.planner.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Spring Boot application for Planner Agent Web UI
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.krista.kme.agent.planner"})
public class PlannerWebApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(PlannerWebApplication.class, args);
    }
}

