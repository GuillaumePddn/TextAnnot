package obir.ir;

import java.util.Vector;

import edu.stanford.smi.protegex.owl.model.OWLIndividual;
import edu.stanford.smi.protegex.owl.model.OWLObjectProperty;
import edu.stanford.smi.protegex.owl.model.RDFProperty;
/**
 * Class representing a triplet OWLIndividual (Domain instance), property name, OWLIndividual (Range instance) 
 * @author Davide Buscaldi
 *
 */
public class AnnotationTriple {
	/**
	 * The domain individual of the relation
	 */
	OWLIndividual domain;
	/**
	 * The property (relation)
	 */
	RDFProperty prop;
	/**
	 * The range individual of the relation
	 */
	OWLIndividual range;
	
	/**
	 * Constructor
	 * @param domain : the domain individual
	 * @param prop : the relation
	 * @param range : the range individual
	 */
	public AnnotationTriple(OWLIndividual domain, RDFProperty prop, OWLIndividual range){
		this.domain=domain;
		this.prop=prop;
		this.range=range;
	}
	/**
	 * Getter for the domain individual
	 * @return the OWL individual that constitutes the domain instance of this triplet
	 */
	public OWLIndividual getDomainInd(){
		return this.domain;
	}
	/**
	 * Getter for the range individual
	 * @return the OWL individual that constitutes the range instance of this triplet
	 */
	public OWLIndividual getRangeInd(){
		return this.range;
	}
	/**
	 * Getter for the property
	 * @return the property (relation) as RDFProperty
	 */
	public RDFProperty getRelatingProperty(){
		return this.prop;
	}
	/**
	 * Method that returns the domain class of this triplet
	 * @return
	 */
	public String getDomainClass(){
		return this.domain.getRDFType().getLocalName();
	}
	/**
	 * Method that returns the range class of this triplet
	 * @return
	 */
	public String getRangeClass(){
		return this.range.getRDFType().getLocalName();
	}
	/**
	 * Method that overrides the default equals() method
	 */
	public boolean equals(Object o){
		String o_repr = ((AnnotationTriple)o).classRepr();
		return o_repr.equals(this.classRepr());		
	}
	/**
	 * Method that overrides the default hashCode() method (needed to put triplets in a HashTable)
	 */
	public int hashCode(){
		return this.classRepr().hashCode();
	}
	/**
	 * Converts this triplet in a String representation (using individual IDs instead of class IDs)
	 * Example:
	 * OWLIndividual1 fleuritEn OWLIndividual2
	 * @return
	 */
	public String individualRepr(){
		String d = this.getDomainInd().getLocalName();
		String r = this.getRangeInd().getLocalName();
		String prop = this.getRelatingProperty().getLocalName();
		return (d+" "+prop+" "+r);
	}
	/**
	 * Converts this triplet in a String representation (using class IDs instead of individual IDs)
	 * example:
	 * G_Glaïeul fleuritEn été
	 * @return
	 */
	public String classRepr(){
		String d = this.getDomainInd().getRDFType().getLocalName();
		String r = this.getRangeInd().getRDFType().getLocalName();
		String prop = this.getRelatingProperty().getLocalName();
		return (d+" "+prop+" "+r);
	}
	/**
	 * This method is based on classRepr() but it returns the information in a vector instead of a single string
	 * @return
	 */
	public Vector<String> getTripleString(){
		Vector<String> ret = new Vector<String>();
		ret.add(this.getDomainClass());
		ret.add(this.getRelatingProperty().getLocalName());
		ret.add(this.getRangeClass());
		return ret;
	}
	
}
