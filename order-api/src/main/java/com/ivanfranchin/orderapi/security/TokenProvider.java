package com.ivanfranchin.orderapi.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TokenProvider {

  public static final String TOKEN_TYPE = "JWT";
  public static final String TOKEN_ISSUER = "order-api";
  public static final String TOKEN_AUDIENCE = "order-app";

  @Value("${app.jwt.secret}")
  private String jwtSecret;

  private SecretKey signingKey;

  @Value("${app.jwt.expiration.minutes}")
  private long jwtExpirationMinutes;

  @PostConstruct
  void initializeSigningKey() {
    if (jwtSecret == null || jwtSecret.isBlank()) {
      throw new IllegalStateException("Invalid JWT secret configuration: value is required");
    }
    byte[] secretBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
    if (secretBytes.length < 64) {
      throw new IllegalStateException(
          "Invalid JWT secret configuration: at least 64 UTF-8 bytes are required");
    }
    signingKey = Keys.hmacShaKeyFor(secretBytes);
  }

  private SecretKey getSigningKey() {
    if (signingKey == null) {
      initializeSigningKey();
    }
    return signingKey;
  }

  public String generate(Authentication authentication) {
    CustomUserDetails user = (CustomUserDetails) authentication.getPrincipal();

    List<String> roles =
        user.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();

    Instant now = Instant.now();

    return Jwts.builder()
        .header()
        .add("typ", TOKEN_TYPE)
        .and()
        .signWith(getSigningKey(), Jwts.SIG.HS512)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(60 * jwtExpirationMinutes)))
        .id(UUID.randomUUID().toString())
        .issuer(TOKEN_ISSUER)
        .audience()
        .add(TOKEN_AUDIENCE)
        .and()
        .subject(user.getUsername())
        .claim("rol", roles)
        .claim("name", user.getName())
        .claim("preferred_username", user.getUsername())
        .claim("email", user.getEmail())
        .compact();
  }

  public Optional<Jws<Claims>> validateTokenAndGetJws(String token) {
    try {
      Jws<Claims> jws = Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token);

      return Optional.of(jws);
    } catch (ExpiredJwtException exception) {
      log.error("Request to parse expired JWT : {} failed : {}", token, exception.getMessage());
    } catch (UnsupportedJwtException exception) {
      log.error("Request to parse unsupported JWT : {} failed : {}", token, exception.getMessage());
    } catch (MalformedJwtException exception) {
      log.error("Request to parse invalid JWT : {} failed : {}", token, exception.getMessage());
    } catch (SignatureException exception) {
      log.error(
          "Request to parse JWT with invalid signature : {} failed : {}",
          token,
          exception.getMessage());
    } catch (IllegalArgumentException exception) {
      log.error(
          "Request to parse empty or null JWT : {} failed : {}", token, exception.getMessage());
    }
    return Optional.empty();
  }
}
