import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.prefs.Preferences;

import org.apache.commons.io.IOUtils;
import org.kevoree.library.nanohttp.NanoHTTPD;

/**
 * A default web controller which returns file found in the resource folder.
 *
 * @author olmartin
 */
public class DefaultWebController extends NanoHTTPD {

  public static final String MIME_JSON       = "application/json";
  public static final String MIME_JAVASCRIPT = "application/javascript";
  public static final String MIME_STYLESHEET = "text/css";
  public static final String MIME_GIF        = "image/gif";
  public static final String MIME_PNG        = "image/png";
  public static final String MIME_SVG        = "image/svg";
  public static final String MIME_WOFF       = "application/font-woff";
  public static final String MIME_EOT        = "font/opentype";
  public static final String MIME_TTF        = "pplication/octet-stream";

  private Map<String, String> cache      = new HashMap<>();
  private Map<String, byte[]> bytesCache = new HashMap<>();

  public DefaultWebController(int port) throws IOException {
    super(port);
  }

  @Override
  public NanoHTTPD.Response serve(String uri, String method, Properties header, Properties params, Properties files, InputStream body) {
    String url = uri.substring(1);
    if (url.equals("")) {
      return new NanoHTTPD.Response(HTTP_OK, MIME_HTML, content("index.html", false));
    } else if (url.endsWith(".json")) {
      return new NanoHTTPD.Response(HTTP_OK, MIME_JSON, content(url, false));
    } else if (url.endsWith(".js")) {
      return new NanoHTTPD.Response(HTTP_OK, MIME_JAVASCRIPT, content(url, false));
    } else if (url.endsWith(".html")) {
      return new NanoHTTPD.Response(HTTP_OK, MIME_HTML, content(url, false));
    } else if (url.endsWith(".css")) {
      return new NanoHTTPD.Response(HTTP_OK, MIME_STYLESHEET, content(url, false));
    } else if (url.endsWith(".gif")) {
      return new NanoHTTPD.Response(HTTP_OK, MIME_GIF, bytes(url, false));
    } else if (url.endsWith(".png")) {
      return new NanoHTTPD.Response(HTTP_OK, MIME_PNG, bytes(url, false));
    } else if (url.endsWith(".svg")) {
      return new NanoHTTPD.Response(HTTP_OK, MIME_SVG, bytes(url, false));
    } else if (url.endsWith(".woff")) {
      return new NanoHTTPD.Response(HTTP_OK, MIME_WOFF, bytes(url, false));
    } else if (url.endsWith(".eot")) {
      return new NanoHTTPD.Response(HTTP_OK, MIME_EOT, bytes(url, false));
    } else if (url.endsWith(".ttf")) {
      return new NanoHTTPD.Response(HTTP_OK, MIME_TTF, bytes(url, false));
    }
    return new NanoHTTPD.Response(HTTP_BADREQUEST, MIME_PLAINTEXT, "Cant handle url "+uri);
  }

  private String content(String path, boolean fromCache) {
    String content = cache.get(path);
    if (!fromCache || content == null) {
      InputStream resourceAsStream = getClass().getResourceAsStream(path);
      if (resourceAsStream != null) {
        java.util.Scanner s = new java.util.Scanner(resourceAsStream).useDelimiter("\\A");
        content = s.hasNext() ? s.next() : "";
        cache.put(path, content);
      } else {
        content = "";
      }
    }
    return content;
  }

  private ByteArrayInputStream bytes(String path, boolean fromCache) {
    byte[] content = bytesCache.get(path);
    if (!fromCache || content == null) {
      InputStream resourceAsStream = getClass().getResourceAsStream(path);

      if (resourceAsStream != null) {
        try {
          content = IOUtils.toByteArray(resourceAsStream);
          bytesCache.put(path, content);
        } catch (IOException e) {
          e.printStackTrace();
          content = new byte[0];
        }
      } else {
        content = new byte[0];
      }
    }
    return new ByteArrayInputStream(content);
  }

}