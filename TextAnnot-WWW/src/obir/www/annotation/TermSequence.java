package obir.www.annotation;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Vector;

import org.apache.lucene.analysis.Token;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.TermAttribute;
import org.apache.lucene.analysis.tokenattributes.TermAttributeImpl;
import org.apache.lucene.util.Version;

import obir.ir.analysis.CustomAnalyzer;
import obir.otr.ObirProject;

/**
 * Class that produces the code to visualize the annotation on the web page
 * @author davide buscaldi
 *
 */
public class TermSequence {
	HashMap<Integer, String> posMap; //maps positions to the contained term
	//Vector<String> terms;
	Vector<Integer> positions;
	HashMap<Integer, Vector<String>> annMap;
	HashMap<String, String> annColors;
	/**
	 * Constructor for a term sequence to be displayed as result of the annotation process
	 * @param text the query text
	 * @param lang the language
	 */
	public TermSequence(String text, String lang){
		text=text.replaceAll("\\s+", " ");
		//String phrase = CustomAnalyzer.removeApex(text);
		this.posMap=new HashMap<Integer,String>();
		//this.terms=new Vector<String>();
		this.positions=new Vector<Integer>();
		this.annMap= new HashMap<Integer, Vector<String>>();
		
		TokenStream ts = new StandardTokenizer(Version.LUCENE_29,new StringReader(text));
		
		int currentPosition=0;
		int totalPosition=0;
	
		try {
			ts.reset();  //The consumer calls reset().
			TermAttribute termAttribute = (TermAttribute)ts.getAttribute(TermAttribute.class); //the consumer retrieves attributes from the stream and stores local references to all attributes it wants to access
			while(ts.incrementToken()){ //The consumer calls incrementToken() until it returns false and consumes the attributes after each call.
				String term = termAttribute.term();
				positions.add(new Integer(currentPosition));
				posMap.put(new Integer(currentPosition), term);
				totalPosition=currentPosition+term.length()+1;
				currentPosition=totalPosition;
			}
			ts.end(); //The consumer calls end() so that any end-of-stream operations can be performed.
			ts.close(); //The consumer calls close() to release any resource when finished using the TokenStream
		} catch (IOException e) {
			e.printStackTrace();
		}

	}
	/**
	 * Method that sets the colors for each annotation
	 * @param occurrences the term occurrences
	 */
	public void setAnnotations(Vector<TermOccurrence> occurrences) {
		annColors=new HashMap<String, String>();
		String [] colours = HTMLColorManager.getColorArray(occurrences.size());
		for(int i=0; i < occurrences.size(); i++){
			String term = occurrences.elementAt(i).getTerm();
			Vector<Integer> positions = occurrences.elementAt(i).getPositions();
			//System.err.println("annotation term: "+term+" , pos:"+pos);
			annColors.put(term, colours[i]);
			for(Integer pos : positions) {
				Vector<String> annotForPos = annMap.get(pos);
				if(annotForPos==null){
					annotForPos = new Vector<String>();
				}
				annotForPos.add(term);
				annMap.put(pos,annotForPos);
			}
		}
	}
	/**
	 * Method that returns the closest annotation to a word to make the alignment
	 * word <-> annotation 
	 * @param value the word position
	 * @param candidates the positions of the annotation
	 * @param wordSize the word size
	 * @return the index of the annotation that corresponds to the given word (-1 if no annotation corresponds to the given word) 
	 */
	private Integer closestValue(Integer value, Set<Integer> candidates, int wordSize){
		Integer nearest = new Integer(-1);
		int bestDistanceFoundYet = Integer.MAX_VALUE;
		for (Integer i : candidates) {
		  // if we found the desired number, we return it.
		  //System.err.println("looking for closest annotation to word: '"+posMap.get(value)+"' at position "+value);
		  if (i.intValue()==value.intValue()) {
			//System.err.println("exact match at position"+i);
		    return i;
		  } else {
			if(i.intValue() > value.intValue()){
				int d = i-value;
			    if (d < bestDistanceFoundYet && (d < wordSize) ) {
			    	//System.err.println("near match at position"+i);
			    	nearest = i;
			    	bestDistanceFoundYet=d;
			    }
			}
			
		  }
		}
		return nearest;
	}
	/**
	 * Transforms the annotations in the HTML code used to visualize the annotation as a table
	 * (different annotations for the same word are placed in different rows of a table)
	 * @return the HTML code as a string to be included in the result page
	 */
	public String getHTMLCode(){
		StringBuffer buf = new StringBuffer();
		//show annotation labels
		for(String k : annColors.keySet()){
			buf.append("<span style='background-color:#"+annColors.get(k)+"'>"+k+"</span> ");
		}
		buf.append("\n<BR><BR>\n");
		
		//show annotated sentence
		
		HashMap<Integer, Integer> mappedPos = new HashMap<Integer, Integer>();
		Set<Integer> markedPos = annMap.keySet();
		int maxAnnotsPerPos=1;
		for(Integer p : positions){
			Integer v = closestValue(p, markedPos, posMap.get(p).length());
			if(v > -1) {
				mappedPos.put(p, v);
				int nAnnotForPos =(annMap.get(mappedPos.get(p))).size();
				if(nAnnotForPos > maxAnnotsPerPos) maxAnnotsPerPos=nAnnotForPos;
			}
		}
		
		String [][] tableContent = new String[maxAnnotsPerPos][positions.size()+1];
		int col=0;
		for(Integer p : positions){
			int line=0;
			Vector<String> annotForPos =annMap.get(mappedPos.get(p));
			if(annotForPos==null){
				tableContent[line][col]=posMap.get(p);
				/*
				buf.append(posMap.get(p));
				buf.append(" ");*/
			} else {
				for(String ann : annotForPos){
					tableContent[line][col]="<span style='background-color:#"+annColors.get(ann)+"'>"+posMap.get(p)+"</span> ";
					//buf.append("<span style='background-color:#"+annColors.get(ann)+"'>"+posMap.get(p)+"</span> ");//
					line++;
				}
			}
			col++;
		}
		buf.append("<TABLE>");
		for(int i=0; i< maxAnnotsPerPos; i++){
			buf.append("<TR>");
			for(int j=0; j < (positions.size()+1); j++){
				String cellCnt=tableContent[i][j];
				if(cellCnt !=null) buf.append("<TD>"+cellCnt+"</TD>");
				else buf.append("<TD></TD>");
			}
			buf.append("</TR>\n");
		}
		buf.append("</TABLE>");
		buf.append("\n<BR><HR>\n");
		return buf.toString();
	}
	
	public String getColorFor(String term){
		return annColors.get(term);
	}
}
