package it.unicam.locationbasedgame.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Allows the Flutter app to call this backend from a web browser.
 *
 * When the app runs in Chrome it is served from an address like
 * http://localhost:54321, while this backend listens on
 * http://localhost:8080. For a browser those are two different origins,
 * and by default it refuses to send requests from one to the other: the
 * request fails before it even reaches Spring, which is why the app only
 * sees a generic "Failed to fetch" message.
 *
 * This class tells the browser that requests coming from anywhere are
 * acceptable. That is fine while developing, but in a real deployment the
 * allowed origins should be limited to the addresses the app is really
 * served from.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                // "*" means any origin. During development the port of the
                // Flutter app changes at every run, so listing them would
                // not be practical.
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE")
                .allowedHeaders("*");
    }
}