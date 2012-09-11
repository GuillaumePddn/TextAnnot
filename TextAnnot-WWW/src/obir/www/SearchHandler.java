package obir.www;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Properties;
import java.util.Vector;

import edu.stanford.smi.protegex.owl.model.OWLIndividual;
import obir.ir.Corpus;
import obir.ir.DocumentAnnotation;
import obir.ir.SemanticSimilarity;
import obir.ir.indexing.AnnotationIndexFactory;
import obir.ir.indexing.RelationIndexFactory;
import obir.otr.ObirProject;
/**
 * Class that implements the search process
 * @author davide buscaldi
 *
 */
public class SearchHandler {
	/**
	 * The query
	 */
	private String query;
	/**
	 * The query annotation
	 */
	private DocumentAnnotation queryAnnot;
	/**
	 * Variable that specifies whether we chose classic search or not
	 */
	private String classic;
	/**
	 * Variable that specifies whether we chose semantic search or not
	 */
	private String semSearch;
	/**
	 * The weighting scheme
	 */
	private String domainPartner;
	/**
	 * The filter size
	 */
	int luceneFilterLimit;
	/**
	 * The properties (coefficients and type of concept similarity)
	 */
	private Properties prop;
	
	/**
	 * Instantiates a new SearchHandler using a given query, the annotation produced by the AnnotationHandler and the properties
	 * @param query the input query
	 * @param queryAnnot the annotation produced by the AnnotationHandler
	 * @param prop the properties for this session (plugin.properties or the version modified in the options page)
	 */
	public SearchHandler(String query, DocumentAnnotation queryAnnot, Properties prop){
		this.queryAnnot=queryAnnot;
		this.query=query;
		this.classic= prop.getProperty("field.searchable.classic");
		this.semSearch = prop.getProperty("field.searchable.semantic");
		this.domainPartner = prop.getProperty("plugin.partner");
		luceneFilterLimit = Integer.parseInt(prop.getProperty("lucene.filtersize"));
		this.prop=prop;
	}
	/**
	 * This method returns a list of result for the given query
	 * @return the list of results
	 */
	public Vector<String> getResults() {
		//query = CustomAnalyzer.removeAccents(req);
		Vector<String> ret = new Vector<String>();
		Vector<Vector<String>> results = doRequestSearch();
		if(queryAnnot != null && !semSearch.equals("")){ // semantic search
			HashSet<OWLIndividual> documentInstances = queryAnnot.getIsolatedInstances("description");
			StringBuffer annTerms = new StringBuffer();
			for(OWLIndividual docInst : documentInstances){
				String className=docInst.getProtegeType().getLocalName();
				annTerms.append(className);
				System.err.println(className);
				annTerms.append(" ");
			}
			ret.add(annTerms.toString().trim());
			Collections.sort(results, new ResultComparator());
			//int i=0;
		}
		if(queryAnnot == null && !semSearch.equals("")) {
			ret.add("Pas d'annotations pour la requête");
		}
		for(Vector<String> v : results) {
			if(v.size()==3) ret.add(v.elementAt(0)+" : "+v.elementAt(1)+" : "+v.elementAt(2));
			else ret.add(v.elementAt(0)+" : "+v.elementAt(1));
		}
		
		return ret;
		
	}
	/**
	 * Method that activates the TextViz code for the search process
	 * @return a vector of results (document name, weight, justification) for the visualization
	 */
	private Vector<Vector<String>> doRequestSearch()
	{
		Vector<Vector<String>> unfilteredCurrentResults;
		
		float threshold = new Float(TextVizWrapper.GENERAL_THRESHOLD);
		
		String field = TextVizWrapper.getSearchedDocumentField();
		String queryLang = ObirProject.getDefaultLanguage();

        unfilteredCurrentResults= new Vector<Vector<String>>();
        if(!classic.equals("")){
        	queryAnnot = null;
			unfilteredCurrentResults = SemanticSimilarity.classicSearchAlgorithm(query, field, ObirProject.getIndexingProcessor().getAnalyzer(queryLang), TextVizWrapper.basicIndexPath+"/index_"+field+"/"+queryLang,ObirProject.getCorpus());
        } else if(!semSearch.equals("") && queryAnnot!=null){
        	
        	if(domainPartner.equals(ObirProject.ARKEO)){
        		Corpus filteredCorpus = AnnotationIndexFactory.getFilteredCorpus(queryAnnot, ObirProject.getCorpus(), luceneFilterLimit); //filtering corpus to speed up search
        		unfilteredCurrentResults = SemanticSimilarity.genericSimilarityAlgorithm(queryAnnot.getIsolatedInstances(field),filteredCorpus, threshold, field, prop); 
        	} else if (domainPartner.equals(ObirProject.ARTAL)){
        		Corpus filteredCorpus = RelationIndexFactory.getRelationFilteredCorpus(queryAnnot, ObirProject.getCorpus(), luceneFilterLimit);
        		unfilteredCurrentResults = SemanticSimilarity.newRelationBasedSimilarityAlgorithm(queryAnnot.getIsolatedInstances(field), queryAnnot.getFieldRelations(field), filteredCorpus, threshold, field, ObirProject.getPluginProperties());
				//unfilteredCurrentResults = SemanticSimilarity.genericSimilarityAlgorithm(queryAnnot.getFieldTriples(field), corpus,threshold,field,true, prop);
        	}

        }
        
		return (unfilteredCurrentResults);
	}
}
/**
 * Class that provides a comparator for the results obtained from the doRequestSearch()
 */
class ResultComparator implements Comparator<Vector<String>> {

	@Override
	public int compare(Vector<String> o1, Vector<String> o2) {
		Double d1=Double.parseDouble(o1.elementAt(1).replace(',', '.'));
		Double d2=Double.parseDouble(o2.elementAt(1).replace(',', '.'));
		return d2.compareTo(d1); //sort from max to min
	}
	
}
