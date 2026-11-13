package ru.moex.papertrader.infra.algoapi;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Сборка {@link SSLContext} из дополнительного хранилища доверенных сертификатов.
 *
 * <p>Шлюз AlgoPack ({@code apim.moex.com}) использует сертификат национального УЦ Минцифры
 * («Russian Trusted Root CA»), которого нет в стандартном {@code cacerts} JVM, — handshake падает с
 * {@code PKIX path building failed}. Контекст доверяет и стандартным корням JDK, и сертификатам из
 * указанного хранилища (JKS/PKCS12), поэтому смена УЦ площадкой ничего не ломает.
 *
 * <p>Местоположение поддерживает префикс {@code classpath:} (ресурс в приложении) и обычные
 * файловые пути. Сертификаты национального УЦ распространяются Минцифры (e-trust.gosuslugi.ru); в
 * репозитории лежит JKS с публичными корнями — это не секрет.
 */
final class TrustStoreSslContexts {

  private static final String CLASSPATH_PREFIX = "classpath:";

  private TrustStoreSslContexts() {}

  /**
   * Создаёт TLS-контекст, доверяющий стандартным корням JVM и хранилищу {@code location}.
   *
   * @param location {@code classpath:...} или файловый путь к JKS/PKCS12
   * @param password пароль хранилища (обязателен — JKS проверяет целостность)
   * @throws AlgoApiException хранилище не найдено, повреждено или пароль неверен
   */
  static SSLContext fromTrustStore(String location, String password) {
    if (password == null || password.isBlank()) {
      throw new AlgoApiException("AlgoPack: пароль truststore обязателен для " + location);
    }
    try {
      KeyStore trustStore = loadKeyStore(location, password.toCharArray());
      X509TrustManager additional = trustManagerOf(trustStore);
      X509TrustManager fallback = trustManagerOf(null);
      SSLContext context = SSLContext.getInstance("TLS");
      context.init(
          null, new TrustManager[] {new CompositeTrustManager(fallback, additional)}, null);
      return context;
    } catch (GeneralSecurityException | IOException e) {
      throw new AlgoApiException("AlgoPack: не удалось загрузить truststore " + location, e);
    }
  }

  private static KeyStore loadKeyStore(String location, char[] password)
      throws GeneralSecurityException, IOException {
    try (InputStream in = open(location)) {
      KeyStore store = KeyStore.getInstance("JKS");
      try {
        store.load(in, password);
        return store;
      } catch (IOException e) {
        // Формат может быть PKCS12: пробуем заново со свежим потоком
        try (InputStream retry = open(location)) {
          KeyStore pkcs12 = KeyStore.getInstance("PKCS12");
          pkcs12.load(retry, password);
          return pkcs12;
        }
      }
    }
  }

  private static InputStream open(String location) {
    if (location.startsWith(CLASSPATH_PREFIX)) {
      String name = location.substring(CLASSPATH_PREFIX.length());
      InputStream in =
          TrustStoreSslContexts.class
              .getClassLoader()
              .getResourceAsStream(name.startsWith("/") ? name.substring(1) : name);
      if (in == null) {
        throw new AlgoApiException("AlgoPack: truststore-ресурс не найден: " + location);
      }
      return in;
    }
    try {
      return Files.newInputStream(Path.of(location));
    } catch (IOException e) {
      throw new AlgoApiException("AlgoPack: truststore-файл не найден: " + location, e);
    }
  }

  private static X509TrustManager trustManagerOf(KeyStore store) throws GeneralSecurityException {
    TrustManagerFactory factory =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    factory.init(store);
    for (TrustManager manager : factory.getTrustManagers()) {
      if (manager instanceof X509TrustManager x509) {
        return x509;
      }
    }
    throw new GeneralSecurityException("X509TrustManager недоступен");
  }

  /**
   * Доверяет серверу, если цепочка валидна хотя бы для одного из менеджеров. Сначала проверяется
   * стандартное хранилище JVM, при отказе — дополнительное.
   */
  private record CompositeTrustManager(X509TrustManager fallback, X509TrustManager additional)
      implements X509TrustManager {

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType)
        throws CertificateException {
      fallback.checkClientTrusted(chain, authType);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType)
        throws CertificateException {
      try {
        fallback.checkServerTrusted(chain, authType);
      } catch (CertificateException e) {
        additional.checkServerTrusted(chain, authType);
      }
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
      X509Certificate[] first = fallback.getAcceptedIssuers();
      X509Certificate[] second = additional.getAcceptedIssuers();
      X509Certificate[] merged = new X509Certificate[first.length + second.length];
      System.arraycopy(first, 0, merged, 0, first.length);
      System.arraycopy(second, 0, merged, first.length, second.length);
      return merged;
    }
  }
}
