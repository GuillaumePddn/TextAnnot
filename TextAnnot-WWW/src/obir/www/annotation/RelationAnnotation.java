package obir.www.annotation;

import java.util.Vector;

import edu.stanford.smi.protegex.owl.model.OWLIndividual;
import edu.stanford.smi.protegex.owl.model.OWLObjectProperty;
/**
 * Class that represents the annotation of a relation.
 * To be used by the Web Service ThemaStream
 * @author davide buscaldi
 *
 */
public class RelationAnnotation {
	OWLIndividual inst1;
	OWLIndividual inst2;
	OWLObjectProperty rel;
	String spottedWord="";
	String spottedLabel="";
	/**
	 * Constructor that creates a relation annotation with two instances and the relation
	 * @param inst1
	 * @param rel
	 * @param inst2
	 */
	public RelationAnnotation(OWLIndividual inst1, OWLObjectProperty rel, OWLIndividual inst2){
		this.inst1=inst1;
		this.rel=rel;
		this.inst2=inst2;
	}
	/**
	 * Constructor that creates a relation with the information about the relation label found
	 * @param inst1
	 * @param rel
	 * @param inst2
	 * @param spottedItems
	 */
	public RelationAnnotation(OWLIndividual inst1, OWLObjectProperty rel, OWLIndividual inst2, Vector<String> spottedItems){
		this(inst1, rel, inst2);
		this.spottedLabel=spottedItems.get(0);
		this.spottedWord=spottedItems.get(1);
	}
	/**
	 * Getter method for the domain instance
	 * @return the instance belonging to the domain of this relationship
	 */
	public OWLIndividual getDomainItem(){
		return this.inst1;
	}
	/**
	 * Getter method for the range instance
	 * @return the instance belonging to the range of this relationship
	 */
	public OWLIndividual getRangeItem(){
		return this.inst2;
	}
	/**
	 * Getter method for the relation
	 * @return the relation linking the two instances
	 */
	public OWLObjectProperty getRelation(){
		return this.rel;
	}
	/**
	 * Getter method for the spotted words
	 * @return the words found in text that correspond to the relation
	 */
	public String getSpottedWords(){
		return this.spottedWord;
	}
	/**
	 * Getter method for the spotted label
	 * @return the label matched in text for this relation
	 */
	public String getSpottedLabel(){
		return this.spottedLabel;
	}
}
