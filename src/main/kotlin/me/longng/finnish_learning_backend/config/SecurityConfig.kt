package me.longng.finnish_learning_backend.config

import me.longng.finnish_learning_backend.security.JwtAuthenticationFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter


/**
 * Security configuration for the Finnish Learning App.
 *
 * The [JwtAuthenticationFilter] runs before Spring Security's default
 * [UsernamePasswordAuthenticationFilter] to populate the SecurityContext
 * from the JWT token before authorization checks happen.
 */
@Configuration
@EnableWebSecurity
class WebSecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    // Public endpoints — browsing and authentication
                    .requestMatchers(HttpMethod.GET, "/api/topics/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/cards/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/images/**").permitAll()
                    .requestMatchers("/api/auth/**").permitAll()
                    .requestMatchers("/swagger-ui/**", "/api-docs/**", "/v3/api-docs/**").permitAll()
                    // Admin-only — card content management
                    .requestMatchers(HttpMethod.POST, "/api/cards/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.PUT, "/api/cards/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.DELETE, "/api/cards/**").hasRole("ADMIN")
                    // Authenticated — quiz and progress
                    .requestMatchers("/api/quiz/**").authenticated()
                    .requestMatchers("/api/progress/**").authenticated()
                    // Default deny — any unlisted route requires authentication
                    .anyRequest().authenticated()
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .exceptionHandling {
                it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }

        return http.build()
    }
}