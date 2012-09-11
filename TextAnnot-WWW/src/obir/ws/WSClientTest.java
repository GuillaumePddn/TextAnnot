package obir.ws;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/**
 * Simple client to test the connection to the ThemaStream server
 * @author davide buscaldi
 *
 */
public class WSClientTest {

	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		String url = "http://themat2i.univ-pau.fr:8080/TextAnnot-WWW/ThemaStream";
		String query = String.format("text=%s", URLEncoder.encode("glaieuls avec des fleurs rouges", "UTF-8"));
		HttpURLConnection connection = (HttpURLConnection) new URL(url+"?"+query).openConnection();
		connection.setRequestMethod("GET");
        connection.setDoOutput(true);
        connection.connect();
        
		BufferedReader rd = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		StringBuffer sb = new StringBuffer();
		String line;
		while ((line = rd.readLine()) != null)
		{
			sb.append(line);
		}
		rd.close();
		System.out.println(sb.toString());
	}

}
