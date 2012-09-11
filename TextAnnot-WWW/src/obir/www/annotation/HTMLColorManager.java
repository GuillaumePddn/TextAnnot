package obir.www.annotation;

//import java.util.Random;
/**
 * Class that generates the colors used to visualize the annotations
 * @author davide buscaldi
 *
 */
public class HTMLColorManager {
	
	static private String[] colors = new String[]{
		"F5A3A3",
		"F5DEA3",
		"F5F5A3",
		"B5F5A3",
		"A3F5F1",
		"A3D5F5",
		"A3B1F5",
		"D5A3F5",
		"F46262",
		"F4C662",
		"F4ED62",
		"7FF462",
		"62F4E3",
		"62B4F4",
		"7362F4",
		"F162F4",
		"D02727",
		"D0AB27",
		"2DD027",
		"27D0C2",
		"2787D0",
		"A827D0"
	};
	
	
	
	public static String [] getColorArray(int size){
		String [] cls = new String [size];
		int max=colors.length;
		for(int i=0; i < size; i++) {
			cls[i]=colors[i%max]; //if size is > than colors.length, I pick already used colors
		}
		return cls;
	}

}
