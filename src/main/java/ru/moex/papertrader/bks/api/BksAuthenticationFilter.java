package ru.moex.papertrader.bks.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.moex.papertrader.bks.adapter.BksAccessSession;
import ru.moex.papertrader.bks.adapter.BksProperties;
import ru.moex.papertrader.bks.adapter.BksRights;
import ru.moex.papertrader.bks.adapter.BksTokenService;
import ru.moex.papertrader.bks.contract.ErrorType;
import tools.jackson.databind.json.JsonMapper;

/** Проверяет Bearer на API-путях БКС и запрещает торговые операции read-only токену (ADR-7). */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class BksAuthenticationFilter extends OncePerRequestFilter {

  public static final String SESSION_ATTRIBUTE = "bksAccessSession";

  private static final String BEARER_PREFIX = "Bearer ";
  private static final Set<String> TRADE_ONLY_PATHS =
      Set.of("/v1/orders", "/v1/orders/sync", "/v1/orders/cancel", "/v1/orders/cancel/sync");

  private final BksTokenService tokenService;
  private final BksProperties properties;
  private final BksErrorFactory errorFactory;
  private final JsonMapper jsonMapper;

  public BksAuthenticationFilter(
      BksTokenService tokenService,
      BksProperties properties,
      BksErrorFactory errorFactory,
      JsonMapper jsonMapper) {
    this.tokenService = tokenService;
    this.properties = properties;
    this.errorFactory = errorFactory;
    this.jsonMapper = jsonMapper;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String uri = request.getRequestURI();
    return !uri.startsWith(basePath() + "/v1/")
        && !uri.startsWith(BksContractPaths.API_V1_PREFIX + "/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header == null || !header.startsWith(BEARER_PREFIX)) {
      writeError(
          response, HttpStatus.UNAUTHORIZED, ErrorType.UNAUTHORIZED, "Требуется Bearer-токен");
      return;
    }
    String token = header.substring(BEARER_PREFIX.length()).trim();
    Optional<BksAccessSession> session = tokenService.resolve(token);
    if (session.isEmpty()) {
      writeError(response, HttpStatus.UNAUTHORIZED, ErrorType.UNAUTHORIZED, "Токен недействителен");
      return;
    }
    if (requiresTradeRights(request) && session.get().rights() != BksRights.TRADE) {
      writeError(
          response,
          HttpStatus.FORBIDDEN,
          ErrorType.FORBIDDEN,
          "Для торговой операции нужен токен trade-api-write");
      return;
    }
    request.setAttribute(SESSION_ATTRIBUTE, session.get());
    filterChain.doFilter(request, response);
  }

  private boolean requiresTradeRights(HttpServletRequest request) {
    if (!"POST".equalsIgnoreCase(request.getMethod())) {
      return false;
    }
    return TRADE_ONLY_PATHS.contains(normalizePath(request.getRequestURI()));
  }

  /**
   * Снимает известный префикс (изолированный base-path или боевой {@code /api/v1}) и возвращает
   * путь вида {@code /v1/...}: проверка прав не зависит от того, по какому префиксу пришёл запрос.
   */
  private String normalizePath(String uri) {
    String base = basePath();
    if (uri.startsWith(base + "/")) {
      return uri.substring(base.length());
    }
    if (uri.startsWith(BksContractPaths.API_V1_PREFIX + "/")) {
      // /api/v1/x -> /v1/x: боевой префикс уже содержит /v1
      return uri.substring("/api".length());
    }
    return uri;
  }

  private String basePath() {
    String basePath = properties.basePath();
    if (basePath.endsWith("/")) {
      return basePath.substring(0, basePath.length() - 1);
    }
    return basePath;
  }

  private void writeError(
      HttpServletResponse response, HttpStatus status, ErrorType type, String message)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    if (status == HttpStatus.UNAUTHORIZED) {
      response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
    }
    response
        .getWriter()
        .write(jsonMapper.writeValueAsString(errorFactory.error(type, null, message)));
  }
}
