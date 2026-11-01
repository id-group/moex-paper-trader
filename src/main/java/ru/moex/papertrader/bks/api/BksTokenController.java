package ru.moex.papertrader.bks.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.moex.papertrader.bks.adapter.BksInvalidGrantException;
import ru.moex.papertrader.bks.adapter.BksTokenIssue;
import ru.moex.papertrader.bks.adapter.BksTokenService;
import ru.moex.papertrader.bks.contract.TokenErrorResponse;
import ru.moex.papertrader.bks.contract.TokenResponse;

/** Keycloak-подобный обмен refresh-токена на access-токен. */
@RestController
public class BksTokenController {

  private final BksTokenService tokenService;

  public BksTokenController(BksTokenService tokenService) {
    this.tokenService = tokenService;
  }

  @PostMapping(
      path =
          "${emulator.brokers.bks.base-path:/api/bks}/trade-api-keycloak/realms/tradeapi/"
              + "protocol/openid-connect/token",
      consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  public ResponseEntity<Object> token(
      @RequestParam(name = "client_id", required = false) String clientId,
      @RequestParam(name = "refresh_token", required = false) String refreshToken,
      @RequestParam(name = "grant_type", required = false) String grantType) {
    try {
      BksTokenIssue issue = tokenService.exchange(clientId, refreshToken, grantType);
      return ResponseEntity.ok(
          new TokenResponse(
              issue.accessToken(),
              issue.expiresIn(),
              issue.refreshExpiresIn(),
              issue.refreshToken(),
              issue.tokenType(),
              issue.notBeforePolicy(),
              issue.sessionState(),
              issue.scope()));
    } catch (BksInvalidGrantException exception) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(new TokenErrorResponse("invalid_grant", exception.getMessage()));
    }
  }
}
