package obir.www;

import java.util.Collection;
import java.util.HashSet;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import obir.ir.AnnotationTriple;
import obir.ir.DocumentAnnotation;
import obir.otr.AnnotationGraphCreator;
import obir.otr.Entity;
import obir.otr.OTR;
import obir.otr.ObirProject;
import obir.www.annotation.RelationAnnotation;
import obir.www.annotation.TermOccurrence;
import edu.stanford.smi.protegex.owl.model.OWLIndividual;
/**
 * Class that calculates the annotation of a given query/text for the WWW service
 * @author davide buscaldi
 *
 */
public class AnnotationHandler {
	/**
	 * The session ID
	 */
	private String sess_id;
	/**
	 * The input query
	 */
	private String query;
	/**
	 * The resulting query annotation
	 */
	private DocumentAnnotation queryAnnot;
	
	/**
	 * Builds a new AnnotationHandler with ID sess_id
	 * @param sess_id the session ID
	 */
	public AnnotationHandler(String sess_id){
		this.sess_id=sess_id;
	}
	/**
	 * Method that returns the query annotation. 
	 * @return the query annotation. If this method is called before the {@code doRequestIndexing()} or {@code getAnnotationVector()} methods, it will return {@code null}.
	 */
	public DocumentAnnotation getQueryAnnotation(){
		return queryAnnot;
	}
	/**
	 * This method returns a vector of term occurrences corresponding to the annotations of the input string.
	 * Most important method used for the annotation of a query
	 * @param req the input query
	 * @return a vector of Term occurrences
	 */
	public Vector<TermOccurrence> getAnnotationVector(String req) {
		//query = CustomAnalyzer.removeAccents(req);
		query = req;
		System.err.println("query: "+req);
		doRequestIndexing();
		Vector<TermOccurrence> ret = new Vector<TermOccurrence>();
		if(queryAnnot != null) {
			HashSet<OWLIndividual> termOccurences = queryAnnot.getTermOccurrences();
			for (OWLIndividual termOcc:termOccurences)
			{
				//String termName=termOcc.getProtegeType().getLocalName(); //term
				String termName="";
				for (OWLIndividual cptInst:(Collection<OWLIndividual>)termOcc.getPropertyValues(termOcc.getOWLModel().getOWLObjectProperty(OTR.designatesID)))
				{
					termName=cptInst.getProtegeType().getLocalName(); //instance class 
				}
				String offsetString=(String)termOcc.getPropertyValue(ObirProject.getOWLModel().getOWLDatatypeProperty(obir.otr.OTR.TERM_OFFSET));
				Vector<Integer> offsets = new Vector<Integer>();
				Pattern p = Pattern.compile("[0-9]+");
				Matcher m = p.matcher(offsetString);
				while (m.find()) {
				    Integer offset =  new Integer(m.group());
				    offsets.add(offset);
				}
				ret.add(new TermOccurrence(termName, offsets));
			}
		} 
		return ret;
		
	}
	/**
	 * This (fundamental) method activates the annotation of the query, using the annotation procedure of TextViz for the concepts,
	 * and the MOANO procedure for the relations.
	 */
	private void doRequestIndexing()
	{
		
		System.err.print("Starting query annotation...");
		ObirProject.getIndexingProcessor().deleteTemporaryInstances(this.sess_id);

		String field = TextVizWrapper.getSearchedDocumentField();
		String lang = ObirProject.getDefaultLanguage();

		HashSet<Entity> entitiesInQuery = ObirProject.getIndexingProcessor().indexQuery(this.query,field,lang, this.sess_id);
		AnnotationGraphCreator.automaticGraphCreation(TextVizWrapper.otr.createInstances(entitiesInQuery,true),new Float(0));

		queryAnnot = ObirProject.getCorpus().getDocument(this.sess_id+"_"+ObirProject.xmlQueryFile);
		//			this.querySymptoms = queryAnnot.getAllInstancesOf(otr.getOntology().getOWLNamedClass(OTR.SYMPTOME), field);
		queryAnnot.setFieldRelations(field);
			
			//indexPanel.updatePanelDisplay(getSearchedDocumentField());
			System.err.println("done.");


	}
	
	/**
	 * Concept annotation function to be called by ThemaStream
	 * @param req the query
	 * @return the set of term occurrences of the query annotation
	 */
	public HashSet<OWLIndividual> getOWLAnnotation(String req) {
		//query = CustomAnalyzer.removeAccents(req);
		query = req;
		doRequestIndexing();
		if(queryAnnot != null) {
			//HashSet<OWLIndividual> documentInstances = queryAnnot.getIsolatedInstances("description");
			HashSet<OWLIndividual> termOccurences = queryAnnot.getTermOccurrences();
			return termOccurences;
		}
		else return null;
	}
	
	/**
	 * Method that returns a relation annotation as a triple ClassName, RelationName, ClassName
	 * It must be called after {@code getAnnotationVector} otherwise it will have no concepts to work on
	 * @param req
	 * @return
	 */
	public Vector<Vector<String>> getRelations() {
		Vector<Vector<String>> ret = new Vector<Vector<String>>();
		if(queryAnnot != null) {
			HashSet<AnnotationTriple> triples = queryAnnot.getFieldRelations("description");
			for(AnnotationTriple t : triples){
				ret.add(t.getTripleString());
			}

		} 
		return ret;
	}
	
	/**
	 * Method to retrieve relation annotations to be used by ThemaStream
	 * @return a vector of {@link RelationAnnotation}
	 */
	public Vector<RelationAnnotation> getOWLAnnotatedRelations() { /*triple: domain, rel, range*/
		//query = CustomAnalyzer.removeAccents(req);
		//doRequestIndexing(); //we should have called getAnnotation before
		Vector<RelationAnnotation> relAnnot = new Vector<RelationAnnotation>();
		if(queryAnnot != null) {
			//HashSet<OWLIndividual> documentInstances = queryAnnot.getIsolatedInstances("description");
			relAnnot = queryAnnot.getRelationAnnotations("description");
		} 
		return relAnnot;
	}
}
