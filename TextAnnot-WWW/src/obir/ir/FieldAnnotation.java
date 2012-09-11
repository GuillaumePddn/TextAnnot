package obir.ir;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import javax.xml.transform.TransformerException;

import obir.misc.XMLToHTML;
import obir.otr.OTR;
import obir.otr.ObirProject;

//import edu.stanford.smi.protegex.owl.model.OWLDatatypeProperty;
import edu.stanford.smi.protegex.owl.model.OWLIndividual;
import edu.stanford.smi.protegex.owl.model.OWLNamedClass;
import edu.stanford.smi.protegex.owl.model.OWLObjectProperty;
import edu.stanford.smi.protegex.owl.model.RDFProperty;

public class FieldAnnotation {
	/**
	 * The OWLIndividual representing this annotation
	 */
	private OWLIndividual fieldAnnotation;
	/**
	 * The offset of this field from the start of the document
	 */
	private Integer fieldOffset;
	/**
	 * Annotation of the content of this Field as a DocumentAnnotation
	 */
	private DocumentAnnotation docAnnotation;

	public FieldAnnotation(DocumentAnnotation docAnnot,String field)
	{
		this(docAnnot, field, new HashSet<OWLIndividual>(), new HashSet<OWLIndividual>(), new Float(0));
	}

	public FieldAnnotation(DocumentAnnotation docAnnot,OWLIndividual fieldAnnot)
	{
		fieldAnnotation = fieldAnnot;
		docAnnotation = docAnnot;
	}

	@SuppressWarnings("unchecked")
	public FieldAnnotation(DocumentAnnotation docAnnot,String field,Set<OWLIndividual> termOccurrences,Set<OWLIndividual> conceptInstances,float coverage)
	{
		docAnnotation = docAnnot;
		OWLNamedClass fieldAnnotClass = ObirProject.getOWLModel().getOWLNamedClass(OTR.FIELD_ANNOTATION);
		OWLNamedClass correctFieldClass = (OWLNamedClass) fieldAnnotClass.getNamedSubclasses(true).iterator().next();
		for (OWLNamedClass subclass:(Collection<OWLNamedClass>)fieldAnnotClass.getNamedSubclasses(true))
		{
			if (subclass.getPropertyValue(ObirProject.getOWLModel().getOWLDatatypeProperty(OTR.FIELD_NAME)).equals(field))
			{
				correctFieldClass = subclass;
				break;
			}
		}
		fieldAnnotation = correctFieldClass.createOWLIndividual(ObirProject.generateNextIndName());

		if (termOccurrences!=null)
		{
			OWLObjectProperty hasTermOcc = ObirProject.getOWLModel().getOWLObjectProperty(OTR.DOC_HAS_TERM_OCCS);
			for (OWLIndividual termOcc:termOccurrences)
			{
				fieldAnnotation.addPropertyValue(hasTermOcc, termOcc);
				docAnnotation.getOWLEquivalent().removePropertyValue(hasTermOcc, termOcc);
			}
		}

		if (conceptInstances!=null)
		{
			OWLObjectProperty hasCptInst = ObirProject.getOWLModel().getOWLObjectProperty(OTR.DOC_HAS_CONCEPT_INSTS);
			for (OWLIndividual cptInst:conceptInstances)
			{
				fieldAnnotation.addPropertyValue(hasCptInst, cptInst);
				docAnnotation.getOWLEquivalent().removePropertyValue(hasCptInst, cptInst);
			}
		}

		fieldAnnotation.setPropertyValue(ObirProject.getOWLModel().getOWLDatatypeProperty(OTR.fieldCoverage), coverage);
//		OWLDatatypeProperty doc_cover = ObirProject.getOWLModel().getOWLDatatypeProperty(OTR.DOC_HAS_COVERAGE);
//		docAnnotation.getOWLEquivalent().removePropertyValue(doc_cover,docAnnotation.getOWLEquivalent().getPropertyValue(doc_cover));
		fieldAnnotation.setPropertyValue(fieldAnnotation.getOWLModel().getOWLDatatypeProperty(OTR.FIELD_NAME),field);
	}
	
	/**
	 * Field name getter
	 * @return
	 */
	public String getFieldName()
	{
		return (String)fieldAnnotation.getPropertyValue(fieldAnnotation.getOWLModel().getOWLDatatypeProperty(OTR.FIELD_NAME));
	}
	/**
	 * Sets the offset for this field 
	 * @param offset
	 */
	public void setFieldOffset(Integer offset)
	{
		fieldOffset = offset;
	}
	/**
	 * Getter for the field offset
	 * @return
	 */
	public Integer getFieldOffset()
	{
		if (fieldOffset==null)
			fieldOffset = computeFieldOffset();
		return fieldOffset;
	}
	/**
	 * Calculates the offset of this field
	 * @return
	 */
	private Integer computeFieldOffset()
	{
		Corpus corpus = ObirProject.getCorpus();
		File associatedFile = new File(corpus.getDirectoryPath()+"\\"+docAnnotation.getDocumentRelativePath());
		Integer offset = null;
		try 
		{
			String transformedContent = SearchFiles.transformIntoStringWithCorrectPositions(XMLToHTML.fromXMLToHTML(associatedFile,docAnnotation.getLanguage()));
			offset = SearchFiles.computeHTMLSymptomOffset(transformedContent,corpus.getSemanticHTMLTag(getFieldName(),docAnnotation.getLanguage()));
			setFieldOffset(offset);
		} 
		catch (TransformerException e) {e.printStackTrace();}

		return offset;
	}
	/**
	 * Adds a term occurrence to the field
	 * @param termOcc
	 */
	public void addTermOccurrence(OWLIndividual termOcc)
	{
		if ((termOcc!=null)&&(this!=null))
		{
			if (! fieldAnnotation.hasPropertyValue(fieldAnnotation.getOWLModel().getOWLObjectProperty(OTR.DOC_HAS_TERM_OCCS), termOcc))
				fieldAnnotation.addPropertyValue(fieldAnnotation.getOWLModel().getOWLObjectProperty(OTR.DOC_HAS_TERM_OCCS), termOcc);
		}
	}
	/**
	 * Adds a concept instance to the field
	 * @param cptInst
	 */
	public void addConceptInstance(OWLIndividual cptInst)
	{
		if (cptInst!=null && ! fieldAnnotation.hasPropertyValue(fieldAnnotation.getOWLModel().getOWLObjectProperty(OTR.DOC_HAS_CONCEPT_INSTS), cptInst) )
			fieldAnnotation.addPropertyValue(docAnnotation.getOWLModel().getOWLObjectProperty(OTR.DOC_HAS_CONCEPT_INSTS), cptInst);
	}
	/**
	 * Methods that checks the presence of a hyponym of the given class in the field
	 * @param cpt
	 * @return true if the hyponym is found
	 */
	public boolean containsHyponymsOf(OWLNamedClass cpt)
	{
		for (OWLNamedClass foundcpt:getConcepts())
		{
			if (foundcpt.getNamedSuperclasses(true).contains(cpt))
				return true;
		}
		return false;
	}

	/**
	 * Gets all terms found in the document field
	 * @return all the types of the term occurrences found
	 */
	public HashSet<OWLNamedClass> getTerms()
	{
		HashSet<OWLNamedClass> result = new HashSet<OWLNamedClass>();
		for (OWLIndividual termOcc:getTermOccurrences())
			result.add((OWLNamedClass)termOcc.getRDFType());

		return result;
	}
	
	/**
	 * Gets all concepts found in the document field
	 * @return all the types of the concept instances found
	 */
	@SuppressWarnings("unchecked")
	public HashSet<OWLNamedClass> getConcepts()
	{
		HashSet<OWLNamedClass> result = new HashSet<OWLNamedClass>();
		for (OWLIndividual cptInst:(Collection<OWLIndividual>)fieldAnnotation.getPropertyValues(fieldAnnotation.getOWLModel().getOWLObjectProperty(OTR.DOC_HAS_CONCEPT_INSTS)))
			result.add((OWLNamedClass)cptInst.getRDFType());
		
		return result;
	}
	
	/**
	 * Term occurrences getter
	 * @return all term occurrences associated to the document field
	 */
	@SuppressWarnings("unchecked")
	public HashSet<OWLIndividual> getTermOccurrences() {
		return new HashSet<OWLIndividual>(fieldAnnotation.getPropertyValues(fieldAnnotation.getOWLModel().getOWLObjectProperty(OTR.DOC_HAS_TERM_OCCS)));
	}

	/**
	 * Concept instances getter
	 * @return all  concept instances associated to this document field
	 */
	@SuppressWarnings("unchecked")
	public HashSet<OWLIndividual> getConceptInstances() {
		return new HashSet<OWLIndividual>(fieldAnnotation.getPropertyValues(fieldAnnotation.getOWLModel().getOWLObjectProperty(OTR.DOC_HAS_CONCEPT_INSTS)));
	}
	
	/**
	 * Gets all the instances of the given concept which have been found in the given document field
	 * @param concept the given concept
	 * @param field the field
	 * @return all concept instances found in the document field with the appropriate type
	 */
	public HashSet<OWLIndividual> getAllInstancesOf(OWLNamedClass concept)
	{
		HashSet<OWLIndividual> result = new HashSet<OWLIndividual>();
		for (OWLIndividual inst:getConceptInstances())
		{
			OWLNamedClass instType = (OWLNamedClass)inst.getRDFType();
			if ((instType.getNamedSuperclasses(true).contains(concept))||(instType.equals(concept)))
				result.add(inst);
		}

		return result;
	}
	
	/**
	 * Indexing score getter
	 * @return the field score calcolated using the Lin model
	 */
	public float getIndexingScore()
	{
		Float result = (Float)fieldAnnotation.getPropertyValue(ObirProject.getOWLModel().getOWLDatatypeProperty(OTR.fieldCoverage));
		if (result!=null)
			return result;
		return Float.NaN;
	}
	/**
	 * Indexing score setter
	 * @param indexingScore the field score calcolated using the Lin model
	 */
	public void setIndexingScore(Float indexingScore) {
		fieldAnnotation.setPropertyValue(ObirProject.getOWLModel().getOWLDatatypeProperty(OTR.fieldCoverage), indexingScore);
	}
	
	/**
	 * Removes a term occurrence from the document field
	 * @param termOcc the occurrence to remove
	 */
	public void removeTermOccurrence(OWLIndividual termOcc)
	{
		fieldAnnotation.removePropertyValue(fieldAnnotation.getOWLModel().getOWLObjectProperty(OTR.DOC_HAS_TERM_OCCS), termOcc);
	}
	
	/**
	 * Removes a concept instance from the document field
	 * @param cptInst the instance to remove
	 */
	public void removeConceptInstance(OWLIndividual cptInst)
	{
		fieldAnnotation.removePropertyValue(fieldAnnotation.getOWLModel().getOWLObjectProperty(OTR.DOC_HAS_CONCEPT_INSTS), cptInst);
	}
	
	/**
	 * Gets all the triples (subject instance - relation - object instance) spotted in a given field
	 * @return associated triples
	 */
	@SuppressWarnings("unchecked")
	public HashMap<OWLIndividual,HashMap<OWLObjectProperty,HashSet<OWLIndividual>>> getTriples()
	{
		HashMap<OWLIndividual,HashMap<OWLObjectProperty,HashSet<OWLIndividual>>> tripleGraph = new HashMap<OWLIndividual, HashMap<OWLObjectProperty,HashSet<OWLIndividual>>>();
		for (OWLIndividual inst:getConceptInstances())
		{
			for (RDFProperty prop:(Collection<RDFProperty>)inst.getRDFType().getUnionDomainProperties(true))//.getRDFProperties())
			{
				if ( ObirProject.isOTRProperty(prop) )
				{
					HashMap<OWLObjectProperty,HashSet<OWLIndividual>> related = new HashMap<OWLObjectProperty, HashSet<OWLIndividual>>();
					if (tripleGraph.containsKey(inst))
						related = tripleGraph.get(inst);
					HashSet<OWLIndividual> destinations = new HashSet<OWLIndividual>();
					for (OWLIndividual dest:(Collection<OWLIndividual>)inst.getPropertyValues(prop))
					{
						destinations.add(dest);
					}
					if (destinations.size()>0)
					{
						related.put((OWLObjectProperty)prop,destinations);
						tripleGraph.put(inst, related);
					}
				}
			}
		}
		return tripleGraph;
	}
	
	/**
	 * Gets all the isolated instances (ie not linked with any other instance) in the given field 
	 * @return the set of isolated instances
	 */
	@SuppressWarnings("unchecked")
	public HashSet<OWLIndividual> getIsolatedInstances()
	{
		HashSet<OWLIndividual> result = (HashSet<OWLIndividual>)getConceptInstances().clone();

		for (OWLIndividual inst:(Collection<OWLIndividual>)result.clone())
		{
			for (RDFProperty prop:(Collection<RDFProperty>)inst.getRDFType().getUnionDomainProperties(true))
			{
				if ( ObirProject.isOTRProperty(prop) )
				{
					if (inst.hasPropertyValue(prop))
					{
						result.remove(inst);

						for (OWLIndividual dest:(Collection<OWLIndividual>)inst.getPropertyValues(prop))
							result.remove(dest);
					}

				}
			}
		}

		return result;
	}
	
	/**
	 * Resets all concept instances and term occurrences in the document field
	 */
	@SuppressWarnings("rawtypes")
	public void resetAllOWLIndividuals()
	{
		for (Object value:(Collection)fieldAnnotation.getPropertyValues(fieldAnnotation.getOWLModel().getOWLObjectProperty(OTR.DOC_HAS_TERM_OCCS)))
			fieldAnnotation.removePropertyValue(fieldAnnotation.getOWLModel().getOWLObjectProperty(OTR.DOC_HAS_TERM_OCCS), value);
		for (Object value:(Collection)fieldAnnotation.getPropertyValues(fieldAnnotation.getOWLModel().getOWLObjectProperty(OTR.DOC_HAS_CONCEPT_INSTS)))
			fieldAnnotation.removePropertyValue(fieldAnnotation.getOWLModel().getOWLObjectProperty(OTR.DOC_HAS_CONCEPT_INSTS), value);
	}
	/**
	 * Annotation getter (in OWL format)
	 * @return the OWLIndividual corresponding to the annotation itself
	 */
	public OWLIndividual getOWLEquivalent()
	{
		return fieldAnnotation;
	}
}
