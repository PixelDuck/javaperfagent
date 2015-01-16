import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.kevoree.library.nanohttp.NanoHTTPD;

/**
 * Class listening on socket to aggregate results locally.
 *
 * @author olivier martin
 */
public class SocketAggregator extends NanoHTTPD {

  public SocketAggregator() throws IOException {
    super(7999);
  }

  @Override
  public NanoHTTPD.Response serve(String uri, String method, Properties header, Properties parms, Properties files, InputStream body) {
    return new NanoHTTPD.Response(HTTP_OK, MIME_PLAINTEXT, "Listening for stats...");
  }


  public static void main(String[] args) {
    try {
      SocketAggregator socketAggregator = new SocketAggregator();
      while (true) {
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          e.printStackTrace();
          System.exit(0);
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
