package ru.moex.papertrader.infra.algoapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Тесты сборки {@link SSLContext} из дополнительного хранилища доверенных сертификатов. */
class TrustStoreSslContextsTest {

  private static final String TRUST_STORE = "classpath:truststore/moex-russian-trusted.jks";

  @Test
  void buildsSslContextFromClasspathTrustStore() {
    SSLContext context = TrustStoreSslContexts.fromTrustStore(TRUST_STORE, "changeit");

    assertThat(context).isNotNull();
    assertThat(context.getProtocol()).isEqualTo("TLS");
  }

  @Test
  void buildsSslContextFromFileTrustStore(@TempDir Path tempDir) throws IOException {
    Path copy = tempDir.resolve("truststore.jks");
    try (var in =
        getClass().getClassLoader().getResourceAsStream("truststore/moex-russian-trusted.jks")) {
      Files.copy(in, copy);
    }

    SSLContext context = TrustStoreSslContexts.fromTrustStore(copy.toString(), "changeit");

    assertThat(context).isNotNull();
  }

  @Test
  void missingTrustStoreBecomesAlgoApiException() {
    assertThatThrownBy(
            () -> TrustStoreSslContexts.fromTrustStore("classpath:no-such-store.jks", "changeit"))
        .isInstanceOf(AlgoApiException.class)
        .hasMessageContaining("truststore");

    assertThatThrownBy(() -> TrustStoreSslContexts.fromTrustStore("no-such-file.jks", "changeit"))
        .isInstanceOf(AlgoApiException.class)
        .hasMessageContaining("truststore");
  }

  @Test
  void wrongPasswordBecomesAlgoApiException() {
    assertThatThrownBy(() -> TrustStoreSslContexts.fromTrustStore(TRUST_STORE, "wrong-password"))
        .isInstanceOf(AlgoApiException.class)
        .hasMessageContaining("truststore");
  }
}
