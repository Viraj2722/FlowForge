package com.flowforge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FlowForge application entry point.
 *
 * <p>{@code @SpringBootApplication} is a convenience annotation that bundles three things:
 * <ul>
 *   <li>{@code @Configuration} — this class can declare Spring beans.</li>
 *   <li>{@code @EnableAutoConfiguration} — Spring Boot inspects the classpath and
 *       auto-wires sensible defaults (logging, etc.). Right now the classpath is tiny,
 *       so almost nothing is auto-configured — by design.</li>
 *   <li>{@code @ComponentScan} — scans this package and sub-packages for beans.</li>
 * </ul>
 *
 * <p>Design note: the actual workflow engine lives under {@code com.flowforge.engine}
 * and does <em>not</em> import Spring. This class is the only "framework aware" piece
 * in Phase 1. Keeping the core framework-free is a deliberate Ports-and-Adapters choice.
 */
@SpringBootApplication
public class FlowForgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlowForgeApplication.class, args);
    }
}
