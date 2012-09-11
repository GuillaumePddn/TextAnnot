package obir.www.annotation;

import java.util.Vector;
/**
 * Class implementing a Term Occurrence as used by the Web Service ThemaStream
 * @author davide buscaldi
 *
 */
public class TermOccurrence {
	private String term;
	private String instanceOf;
	private Vector<Integer> positions;
	
	/**
	 * Constructor for a term occurrence with the term string and the position(s) of the spotted word(s)
	 * @param term the term string
	 * @param positions position of the spotted words
	 */
	public TermOccurrence(String term, Vector<Integer> positions){
		this.term=term;
		this.positions=positions;
	}
	/**
	 * Term string getter
	 * @return the term text
	 */
	public String getTerm(){
		return this.term;
	}
	/**
	 * Getter method for the spotted words positions
	 * @return the vector of positions of the spotted words
	 */
	public Vector<Integer> getPositions(){
		return this.positions;
	}
	/**
	 * Method that returns the OWL Class of this term
	 * @return the OWL class of this term
	 */
	public String getOWLClass(){
		return this.instanceOf;
	}
}
