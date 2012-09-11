package obir.ir.analysis.fr;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;


public class StemTest {

	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		
		InputStreamReader isr = new InputStreamReader(System.in, Charset.forName("ISO-8859-1"));
		BufferedReader br = new BufferedReader(isr);
		System.out.println(isr.getEncoding());
		/*char [] rca = new char [20];
		while(br.read(rca)!=-1){
			for(int c : rca){
				System.err.println(Integer.toHexString(c));
			}
		}*/
		
		while(true){
			System.out.println("Word to stem: ");
			
			String line = br.readLine();
			CustomFrenchStemmer stemmer = new CustomFrenchStemmer();
			
			String stem = PaiceHuskFrenchStemmer.stem(line.trim());
			String stem2 = stemmer.stem(line.trim());
			System.out.println("stemmed form (Paice/Husk): "+stem);
			System.out.println("stemmed form (Snowball): "+stem2);
			
		}
		
		
	}

}
