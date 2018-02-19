package info;

import com.gargoylesoftware.htmlunit.WebRequest;
import org.apache.http.HttpResponse;

/**
 * Created by Scruel on 2017/12/6 006.
 * GitHub : https://github.com/scruel
 */
public class DownloadContentInfo {
  private HttpResponse httpResponse;
  private WebRequest webRequest;

  public HttpResponse getHttpResponse() {
    return httpResponse;
  }

  public void setHttpResponse(HttpResponse httpResponse) {
    this.httpResponse = httpResponse;
  }

  public WebRequest getWebRequest() {
    return webRequest;
  }

  public void setWebRequest(WebRequest webRequest) {
    this.webRequest = webRequest;
  }
}
