package util;

import com.gargoylesoftware.htmlunit.*;
import com.gargoylesoftware.htmlunit.httpclient.HtmlUnitSSLConnectionSocketFactory;
import com.gargoylesoftware.htmlunit.util.UrlUtils;
import info.DownloadContentInfo;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpContext;

import javax.net.ssl.SSLPeerUnverifiedException;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static com.gargoylesoftware.htmlunit.BrowserVersionFeatures.HTTP_REDIRECT_308;

/**
 * Created by Scruel on 2017/12/6 006.
 * GitHub : https://github.com/scruel
 * <p>
 * 该类适配于 HtmlUnit 爬虫框架，代码拆分于框架代码，用于在使用该框架爬取非网页页面等需下载类资源时，在建立连接时就获取
 * HTTP 响应信息（尤指长度等信息），而不是要等文件下载完毕后。
 * 可用于文件大小完整性检验(有时因网络原因会出现下载不完整情况)及显示进度条显示等。
 * <p>
 * A class built for adapting to the HtmlUnit crawler framework, that can be used to
 * get HTTP response information (especially content length) as soon as the connection is
 * established, rather than only can get it until resources downloaded.
 * This class can be used to check whether the local file (which you have already downloaded)
 * size is the same size or display the process bar with content length before starting
 * the download.
 */
public class DownloadContentUtils {
  private final Log LOG = LogFactory.getLog(getClass());

  private static final int ALLOWED_REDIRECTIONS_SAME_URL = 20;

  /**
   * Usage
   *
   * @param args
   */
  public static void main(String[] args) {
    WebClient webClient = new WebClient();
    String dlink = "localhost:8080/test.txt";
    DownloadContentInfo downloadContentInfo = DownloadContentUtils.getDownloadContentInfo(webClient, dlink, null);
    // get http response
    HttpResponse response = downloadContentInfo.getHttpResponse();
    long len = response.getEntity().getContentLength();
    // check local downloaded file by content length.
    File localFile = new File("c:test.txt");
    // download start if size not match
    if (localFile.length() != len) {
      Page page = DownloadContentUtils.downloadContentPage(webClient, downloadContentInfo.getWebRequest(), response);
      // output to file...

      // To ensure consistency, check it by Hash algorithms like md5 etc.
      // or check it by content length again.
      long exceptLen = Long.parseLong(page.getWebResponse().getResponseHeaderValue("Content-Length"));
      long actualLen = page.getWebResponse().getContentLength();
      if (exceptLen != actualLen) {
        //...
      }
    }
  }

  /**
   * Return {@link DownloadContentInfo} which contains HTTP response after
   * connection established and other useful attributes.
   *
   * @param webClient
   * @param urlStr
   * @param requestMap
   * @return
   */
  public static DownloadContentInfo getDownloadContentInfo(WebClient webClient, String urlStr, Map<String, String> requestMap) {
    DownloadContentInfo info = new DownloadContentInfo();
    try {
      WebRequest webRequest = recreateWebRequest(webClient, urlStr, requestMap);
      // try more times for sure.
      for (int i = 0; i < 25; i++) {
        try {
          HttpResponse response = DownloadContentUtils.getHttpResponse(webClient, webRequest, ALLOWED_REDIRECTIONS_SAME_URL);
          info.setHttpResponse(response);
          info.setWebRequest(webRequest);
          return info;
        } catch (Exception ignore) {
        }
      }
    } catch (MalformedURLException ignore) {
    }
    return null;
  }

  /**
   * Return {@link Page} via previous DownloadContentInfo attributes.
   *
   * @param webClient
   * @param webRequest
   * @param httpResponse
   * @param <P>
   * @return download content {@link Page}
   */
  public static <P extends Page> P downloadContentPage(final WebClient webClient, final WebRequest webRequest, final HttpResponse httpResponse) {
    try {
      WebResponse webResponse = getWebResponse(webClient, webRequest, httpResponse);
      return downloadContentPage(webClient, webResponse);
    } catch (InvocationTargetException | IllegalAccessException e) {
      return null;
    }
  }

  /**
   * Return {@link Page} via previous DownloadContentInfo attributes.
   *
   * @param webClient
   * @param webResponse
   * @param <P>
   * @return download content {@link Page}
   */
  @SuppressWarnings("unchecked")
  private static <P extends Page> P downloadContentPage(final WebClient webClient, final WebResponse webResponse) {
    WebWindow webWindow = webClient.getCurrentWindow().getTopWindow();
    // webClient.printContentIfNecessary(webResponse);
    try {
      webClient.loadWebResponseInto(webResponse, webWindow);
    } catch (IOException e) {
      return null;
    }
    return (P) webWindow.getEnclosedPage();
  }

  /**
   * Get WebResponse via reflect.
   *
   * @param webClient
   * @param webRequest
   * @param httpResponse
   * @return
   * @throws InvocationTargetException
   * @throws IllegalAccessException
   */
  public static WebResponse getWebResponse(final WebClient webClient, final WebRequest webRequest, final HttpResponse httpResponse) throws InvocationTargetException, IllegalAccessException {
    final WebConnection webConnection = webClient.getWebConnection();
    final Class<?> webConnectionClazz = webConnection.getClass();
    final DownloadedContent downloadedBody = (DownloadedContent) invokeMethod(webConnectionClazz, webConnection, "downloadResponseBody", new Class[]{HttpResponse.class}, httpResponse);
    final WebResponse webResponse = (WebResponse) invokeMethod(webConnectionClazz, webConnection, "makeWebResponse", new Class[]{HttpResponse.class, WebRequest.class, DownloadedContent.class, long.class}, httpResponse, webRequest, downloadedBody, 0);
    webClient.getCache().cacheIfPossible(webRequest, webResponse, null);
    return webResponse;
  }

  /**
   * Return content length just, only for check.
   *
   * @param webClient
   * @param urlStr
   * @param requestMap
   * @return
   */
  public static long getContentLength(WebClient webClient, String urlStr, Map<String, String> requestMap) {
    DownloadContentInfo info = getDownloadContentInfo(webClient, urlStr, requestMap);
    if (info == null) return -1;
    HttpResponse response = info.getHttpResponse();
    webClient.close();
    return response.getEntity().getContentLength();
  }

  /**
   * A method separated from the HtmlUnit framework.
   * {@inheritDoc}
   */
  @SuppressWarnings("unchecked")
  public static HttpResponse getHttpResponse(final WebClient webClient, final WebRequest webRequest, int allowedRedirects) throws IOException {
    final URL url = webRequest.getUrl();
    final WebConnection webConnection = webClient.getWebConnection();
    final Class<?> webConnectionClazz = webConnection.getClass();

    try {
      // If the request settings don't specify a custom proxy, use the default client proxy...
      if (webRequest.getProxyHost() == null) {
        final ProxyConfig proxyConfig = webClient.getOptions().getProxyConfig();
        if (proxyConfig.getProxyAutoConfigUrl() != null) {
          if (!UrlUtils.sameFile(new URL(proxyConfig.getProxyAutoConfigUrl()), url)) {
            String content = (String) invokeMethod(proxyConfig.getClass(), proxyConfig, "getProxyAutoConfigContent");
            if (content == null) {
              content = webClient.getPage(proxyConfig.getProxyAutoConfigUrl())
                  .getWebResponse().getContentAsString();
              invokeMethod(proxyConfig.getClass(), proxyConfig, "setProxyAutoConfigContent", content);
            }
            final String allValue = ProxyAutoConfig.evaluate(content, url);
            String value = allValue.split(";")[0].trim();
            if (value.startsWith("PROXY")) {
              value = value.substring(6);
              final int colonIndex = value.indexOf(':');
              webRequest.setSocksProxy(false);
              webRequest.setProxyHost(value.substring(0, colonIndex));
              webRequest.setProxyPort(Integer.parseInt(value.substring(colonIndex + 1)));
            }
            else if (value.startsWith("SOCKS")) {
              value = value.substring(6);
              final int colonIndex = value.indexOf(':');
              webRequest.setSocksProxy(true);
              webRequest.setProxyHost(value.substring(0, colonIndex));
              webRequest.setProxyPort(Integer.parseInt(value.substring(colonIndex + 1)));
            }
          }
        }
        // ...unless the host needs to bypass the configured client proxy!
        else if (!((boolean) invokeMethod(proxyConfig.getClass(), proxyConfig, "shouldBypassProxy", webRequest.getUrl().getHost()))) {
          webRequest.setProxyHost(proxyConfig.getProxyHost());
          webRequest.setProxyPort(proxyConfig.getProxyPort());
          webRequest.setSocksProxy(proxyConfig.isSocksProxy());
        }
      }

      invokeMethod(webClient.getClass(), webClient, "addDefaultHeaders", webRequest);

      final HttpClientBuilder httpClientBuilder = (HttpClientBuilder) invokeMethod(webConnectionClazz, webConnection, "getHttpClientBuilder");
      final HttpClientBuilder builder = (HttpClientBuilder) invokeMethod(webConnectionClazz, webConnection, "reconfigureHttpClientIfNeeded", httpClientBuilder);
      final HttpContext httpContext = (HttpContext) invokeMethod(webConnectionClazz, webConnection, "getHttpContext");
      PoolingHttpClientConnectionManager connectionManager_ = (PoolingHttpClientConnectionManager) getField(webConnectionClazz, webConnection, "connectionManager_");
      if (connectionManager_ == null) {
        connectionManager_ = (PoolingHttpClientConnectionManager) invokeMethod(webConnectionClazz, webConnection, "createConnectionManager", builder);
      }
      assert builder != null;
      builder.setConnectionManager(connectionManager_);
      HttpUriRequest httpMethod;
      try {
        httpMethod = (HttpUriRequest) invokeMethod(webConnectionClazz, webConnection, "makeHttpMethod", webRequest, builder);
        final HttpHost hostConfiguration = (HttpHost) invokeMethod(webConnectionClazz, webConnection, "getHostConfiguration", webRequest);
        HttpResponse httpResponse;
        try {
          httpResponse = builder.build().execute(hostConfiguration, httpMethod, httpContext);
        } catch (final SSLPeerUnverifiedException s) {
          // Try to use only SSLv3 instead

          WebClient webClient_ = (WebClient) getField(webConnectionClazz, webConnection, "webClient_");
          assert webClient_ != null;
          if (webClient_.getOptions().isUseInsecureSSL()) {
            assert httpContext != null;
            HtmlUnitSSLConnectionSocketFactory.setUseSSL3Only(httpContext, true);
            httpResponse = builder.build().execute(hostConfiguration, httpMethod, httpContext);
          }
          else {
            throw s;
          }
        } catch (final Error e) {
          // in case a StackOverflowError occurs while the connection is leased, it won't get released.
          // Calling code may catch the StackOverflowError, but due to the leak, the httpClient_ may
          // come out of connections and throw a ConnectionPoolTimeoutException.
          // => best solution, discard the HttpClient instance.
          final Map<Thread, HttpClientBuilder> httpClientBuilder_ = (Map<Thread, HttpClientBuilder>) getField(webConnectionClazz, webConnection, "httpClientBuilder_");
          assert httpClientBuilder_ != null;
          httpClientBuilder_.remove(Thread.currentThread());
          throw e;
        }
        int status = httpResponse.getStatusLine().getStatusCode();
        if (status >= HttpStatus.SC_MOVED_PERMANENTLY
            && status <= (webClient.getBrowserVersion().hasFeature(HTTP_REDIRECT_308) ? 308 : 307)
            && status != HttpStatus.SC_NOT_MODIFIED
            && webClient.getOptions().isRedirectEnabled()) {

          String locationString = httpResponse.getFirstHeader("Location").getValue();
          if (locationString == null) {
            return httpResponse;
          }
          final URL newUrl = WebClient.expandUrl(webRequest.getUrl(), locationString);
          if (allowedRedirects == 0) {
            throw new FailingHttpStatusCodeException("Too much redirect for "
                + newUrl, null);
          }

          final WebRequest wrs = new WebRequest(newUrl, HttpMethod.GET);
          for (final Map.Entry<String, String> entry : webRequest.getAdditionalHeaders().entrySet()) {
            wrs.setAdditionalHeader(entry.getKey(), entry.getValue());
          }
          return getHttpResponse(webClient, wrs, allowedRedirects - 1);
        }
        return httpResponse;
      } finally {
        // if (httpMethod != null) {
        // onResponseGenerated(httpMethod);
        // }
      }

    } catch (IllegalAccessException | InvocationTargetException e) {
      // e.printStackTrace();
      return null;
    }
  }

  public static WebRequest recreateWebRequest(WebClient webClient, String urlStr, Map<String, String> requestMap) throws MalformedURLException {
    WebRequest request = new WebRequest(UrlUtils.toUrlUnsafe(urlStr), webClient.getBrowserVersion().getHtmlAcceptHeader());
    if (requestMap != null) {
      for (Map.Entry<String, String> entry : requestMap.entrySet()) {
        request.setAdditionalHeader(entry.getKey(), entry.getValue());
      }
    }
    return request;
  }

  private static Object invokeMethod(Class<?> clazz, Object obj, String name, Class[] parameterTypes, Object... args) throws InvocationTargetException, IllegalAccessException {
    Method method;
    try {
      method = clazz.getDeclaredMethod(name, parameterTypes);
    } catch (NoSuchMethodException e) {
      return null;
    }
    method.setAccessible(true);
    return method.invoke(obj, args);

  }

  private static Object invokeMethod(Class<?> clazz, Object obj, String name, Object... args) throws InvocationTargetException, IllegalAccessException {
    // MethodUtils.invokeExactMethod();
    Method method;
    List<Class<?>> list = new LinkedList<>();
    for (Object arg : args) {
      list.add(arg == null ? null : arg.getClass());
    }
    Class<?>[] classes = new Class[list.size()];
    try {
      method = clazz.getDeclaredMethod(name, list.toArray(classes));
    } catch (NoSuchMethodException e) {
      return null;
    }
    method.setAccessible(true);
    return method.invoke(obj, args);
  }

  private static Object getField(Class<?> clazz, Object obj, String name) throws IllegalAccessException {
    Field filed;
    try {
      filed = clazz.getDeclaredField(name);
    } catch (NoSuchFieldException e) {
      return null;
    }
    filed.setAccessible(true);
    return filed.get(obj);
  }
}


