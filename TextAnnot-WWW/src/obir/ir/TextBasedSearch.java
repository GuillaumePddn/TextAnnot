package obir.ir;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Vector;

import obir.ir.indexing.IndexFiles;
import obir.otr.AnnotationGraphCreator;
import obir.otr.Entity;
import obir.otr.OTR;
import obir.otr.ObirProject;
import edu.stanford.smi.protege.exception.OntologyLoadException;
import edu.stanford.smi.protege.model.Project;
import edu.stanford.smi.protegex.owl.ProtegeOWL;
import edu.stanford.smi.protegex.owl.model.OWLModel;
import edu.stanford.smi.protegex.owl.model.ProtegeNames;
import edu.stanford.smi.protegex.owl.model.RDFProperty;

public class TextBasedSearch {
	static final int MAX_RESULTS=10;
	
	static final int SEMANTIC=0;
	static final int CLASSIC=1;
	static final int ACTIA_SPECIFIC=2;
	static int SEARCH_TYPE=SEMANTIC;
	
	static final int WU=0;
	static final int LIN=1;
	static final int ACTIA=2;
	static int CONCEPT_SIMILARITY=WU;
	
	static final double GENERAL_THRESHOLD=0.0;
	
	static File onto = null,corpus = null;
	
	static String basicIndexPath;
	static Project myProject;
	static private OTR otr;
	static Vector<Vector<String>> unfilteredCurrentResults;
	static private HashMap<String,HashMap<String,Float>> resnikSubOntoProbas = new HashMap<String,HashMap<String,Float>>(); 
	static DocumentAnnotation queryAnnot;
	/**
	 * Static boolean to display or not classic filters
	 */
	private static boolean filteringEnabled = true;
	
	/**
	 * Static Float concerning the relative importance of a conceptual similarity with respect to a relation similarity
	 */
	public static float cptRelSimImportance = new Float(0.5);
	public static float oblRelImportance = new Float(0.5);
	/**
	 * Static Float concerning the relative importance of a mandatory relation similarity with respect to a facultative relation similarity
	 */
	public static float necFacRelImportance = new Float(0.1);

	/**
	 * Static Float concerning the relative importance of a service with respect to a problem	 */
	public static float servPbImportance = new Float(1);	

	/**
	 * Interesting score threshold
	 */
	public static float scoreThreshold = new Float(0);


	static HashMap<String,HashMap<String,String>> correspondingFilesByLanguage;
	static HashSet<DocumentAnnotation> searchableDocuments;
	
	private static String query;
	
	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		
		String ontoPath = "Z:/Moano/Pau/JardinVilmorinD/template.owl";
		OWLModel model = null;
		try {
			model = ProtegeOWL.createJenaOWLModelFromURI("file:///"+ontoPath.replace('\\', '/'));
		} catch (OntologyLoadException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		if (ontoPath!=null)
			onto = new File(ontoPath.replace("file:/", ""));

		String corpusDir = "Z:/Moano/corpus/CorpusVilmorin";
		if (corpusDir!=null) corpus = new File(corpusDir.replace("file:/", ""));
		System.err.println("can read ontology: "+onto.exists());
		System.err.println("can read corpus: "+corpus.exists());
		
		initialize(model);
		
		unfilteredCurrentResults = new Vector<Vector<String>>();
		//HashSet<String> semIndexed = (HashSet<String>) Corpus.getSemanticFields();
		//HashSet<String> availableLanguages = ObirProject.getIndexingProcessor().getAvailableLanguages();
		RDFProperty defaultLanguage = otr.getOntology().getRDFProperty(ProtegeNames.getDefaultLanguageSlotName());
		otr.getOntology().getDefaultOWLOntology().setPropertyValue(defaultLanguage, ObirProject.getDefaultLanguage());
		
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
		while (true) {
			System.out.println("Enter query: ");
		    query = in.readLine().trim();
		    if (query == null || query.length() == -1) {
		        break;
		    }
		    doRequestIndexing();
		    doRequestSearch();
		}
	}
	
	protected static void initialize(OWLModel model)
	{
		System.err.println("Initializing OTR...") ;
		//OTR rto = new OTR(onto);//,lang);
		new ObirProject(onto,corpus, "plugins/proto/plugin.properties", model);
		
		System.err.println("Initializing corpus...") ;
		
		otr=ObirProject.getOTR();
		otr.setAutoSavePossible(true);

		basicIndexPath = OTR.getTemporaryDirectory()+"search";
		File basicIndex = new File(basicIndexPath);
		if (!basicIndex.exists())
			basicIndex.mkdir();

		
		System.err.println("Matching multilingual documents...") ;
		correspondingFilesByLanguage = new HashMap<String, HashMap<String,String>>();
		File [] foreignDirs = ObirProject.getCorpus().getDirectory().listFiles(new FileFilter() {

			@Override
			public boolean accept(File pathname) {
				if (pathname.isDirectory())
					return true;
				else
					return false;
			}
		});

		String [] mainDocNames = ObirProject.getCorpus().getDirectory().list();

		int total = 0;
		for (File foreignDir:foreignDirs)
		{
			total+= foreignDir.list().length;
		}
		
		int cpt = 0;
		for (File foreignDir:foreignDirs)
		{
			String lang = foreignDir.getName();
			String [] names = foreignDir.list();
			HashMap<String,String> parallelFiles = new HashMap<String, String>();
			for (int i=0;i<names.length;i++)
			{
				cpt++;
//				progressBar.setProgress((int)((float) cpt/total *100 ));
				for (int j=0;j<mainDocNames.length;j++)
				{
					if (names[i].toLowerCase().equals(lang+"_"+mainDocNames[j].toLowerCase()))
					{
						parallelFiles.put(mainDocNames[j], names[i]);
						break;
					}
				}
			}
			correspondingFilesByLanguage.put(lang, parallelFiles);
		}
		
		System.err.println("Scanning document fields...") ;
		if (filteringEnabled)
			IndexFiles.scanWholeCorpus(ObirProject.getCorpus(),basicIndex.getAbsolutePath(),ObirProject.getIndexingProcessor().getAnalyzerByLanguage(),correspondingFilesByLanguage);//,htmlByXMLFields);
		else
			IndexFiles.scanWholeCorpus(ObirProject.getCorpus(),basicIndex.getAbsolutePath(),ObirProject.getIndexingProcessor().getAnalyzerByLanguage(),null);//,htmlByXMLFields);

		System.err.println("Computing searchable documents...") ;
		searchableDocuments = computeSearchableDocuments();
	}
	
	public static HashSet<DocumentAnnotation> computeSearchableDocuments()
	{
		HashSet<String> allowedLangs = getAllowedLanguages();
		if (allowedLangs.contains(ObirProject.getDefaultLanguage()))
			return ObirProject.getCorpus().getValidatedDocuments();
		else
		{
			HashSet<DocumentAnnotation> result = new HashSet<DocumentAnnotation>();
			for (String lang:allowedLangs)
			{
				if (correspondingFilesByLanguage.containsKey(lang))
					for (String docName:correspondingFilesByLanguage.get(lang).keySet())
					{
						result.add(ObirProject.getCorpus().getDocument(docName));
					}
			}
			return result;
		}
	}
	
	private static HashSet<String> getAllowedLanguages()
	{
		HashSet<String> allowedLanguages = new HashSet<String>();
		allowedLanguages.add(ObirProject.getDefaultLanguage());
		return allowedLanguages;
	}
	
	public static void doRequestIndexing()
	{
		
		if (SEARCH_TYPE==SEMANTIC)
		{
			System.err.print("Starting query annotation...");
			ObirProject.getIndexingProcessor().deleteTemporaryInstances("text");
//			ObirProject.getColorManager().removeQueryEntities();

			String field = getSearchedDocumentField();
			String lang = ObirProject.getDefaultLanguage();

			HashSet<Entity> entitiesInQuery = ObirProject.getIndexingProcessor().indexQuery(query,field,lang, "text");
			AnnotationGraphCreator.automaticGraphCreation(otr.createInstances(entitiesInQuery,true),new Float(0));

			queryAnnot = ObirProject.getCorpus().getDocument(ObirProject.xmlQueryFile);
			//			this.querySymptoms = queryAnnot.getAllInstancesOf(otr.getOntology().getOWLNamedClass(OTR.SYMPTOME), field);

			//indexPanel.updatePanelDisplay(getSearchedDocumentField());
			System.err.println("done.");
		}
		else //cas de RI classique
		{
			queryAnnot = null;
			//			this.querySymptoms = new HashSet<OWLIndividual>();
			//				indexPanel.updatePanelDisplay();
			//indexPanel.clearPanelDisplay();
			doRequestSearch();
		}
	}

	public static void doRequestSearch()
	{
		
		Corpus corpus = ObirProject.getCorpus();
		float threshold = new Float(GENERAL_THRESHOLD);
		String cptMeasure="";
		if (CONCEPT_SIMILARITY==LIN)
			cptMeasure="Lin";
		else if (CONCEPT_SIMILARITY==WU)
			cptMeasure="Wu";
		else
			cptMeasure="Actia";
		String field = getSearchedDocumentField();
		String queryLang = ObirProject.getDefaultLanguage();
		HashSet<String> allowedLanguages = getAllowedLanguages();

		if (SEARCH_TYPE==CLASSIC)
		{
			queryAnnot = null;
			
			unfilteredCurrentResults = SemanticSimilarity.classicSearchAlgorithm(query, field, ObirProject.getIndexingProcessor().getAnalyzer(queryLang), basicIndexPath+"\\index_"+field+"\\"+queryLang,ObirProject.getCorpus());//"classic\\index",ObirProject.getCorpus().getValidatedDocuments());
		}
		else if (SEARCH_TYPE==SEMANTIC)
		{
			if (ObirProject.getDomainPartner().equals(ObirProject.ARKEO))
				unfilteredCurrentResults = SemanticSimilarity.genericSimilarityAlgorithm(queryAnnot.getIsolatedInstances(field),corpus, threshold, field, ObirProject.getPluginProperties()); 
			else if (ObirProject.getDomainPartner().equals(ObirProject.ARTAL))
				unfilteredCurrentResults = SemanticSimilarity.newRelationBasedSimilarityAlgorithm(queryAnnot.getIsolatedInstances(field), queryAnnot.getFieldRelations(field), corpus, threshold, field, ObirProject.getPluginProperties());
		}

		Vector<Vector<String>> filteredResults = unfilteredCurrentResults;
		showResultList(filteredResults);
	}
	
	public static String getSearchedDocumentField()
	{
		String field = null;//(String)ObirProject.getCorpus().getSemanticFields().iterator().next();
		field = Corpus.getSemanticFields().iterator().next();
		return field;
	}
	
	/**
	 * Removes all semantically relevant documents which do not include the appropriate text content with respect to the filters  
	 */
	private static Vector<Vector<String>> filterResults(HashSet<String> authorizedLanguages)
	{
		HashSet<String> unfilteredResultFiles = new HashSet<String>();

		DocumentAnnotation queryAnnot = ObirProject.getCorpus().getDocument(ObirProject.xmlQueryFile);
		String searchedField = getSearchedDocumentField();
		if (query.isEmpty() && ! queryAnnot.containsHyponymsOf(otr.getOntology().getOWLNamedClass(OTR.SYMPTOME), searchedField))//indexPanel.symptoms.keySet().size()==0)
		{
			for (DocumentAnnotation doc:ObirProject.getCorpus().getValidatedDocuments())
			{
				//				unfilteredResultFiles.add(ObirProject.getCorpus().getName(doc));
				unfilteredResultFiles.add(doc.getDocumentName());
			}

		}
		else
		{
			for (Vector<String>resultCouple:unfilteredCurrentResults)
			{
				unfilteredResultFiles.add(resultCouple.get(0));
			}
		}
		HashSet<String> adequateFiles = unfilteredResultFiles;

		Vector<Vector<String>> filteredResults = new Vector<Vector<String>>();

		if (!query.isEmpty() || queryAnnot.containsHyponymsOf(otr.getOntology().getOWLNamedClass(OTR.SYMPTOME),searchedField))//indexPanel.symptoms.keySet().size()!=0)
		{
			for (Vector<String>resultCouple:unfilteredCurrentResults)
			{
				if (adequateFiles.contains(resultCouple.get(0)))
					filteredResults.add(resultCouple);
			}
		}
		else
		{
			for (String result:unfilteredResultFiles)
			{
				if (adequateFiles.contains(result))
				{
					Vector<String> resultCouple = new Vector<String>();
					resultCouple.add(result);
					resultCouple.add("N/A");
					filteredResults.add(resultCouple);
				}
			}
		}

		return filteredResults;
	}
	
	private static void showResultList(Vector<Vector<String>> results)
	{
		Collections.sort(results, new ResultComparator());
		int i=0;
		for(Vector<String> v : results) {
			System.out.println(v.elementAt(0)+" , "+v.elementAt(1));
			i++;
			if(i > MAX_RESULTS) break;
		}
	}

	


}


class ResultComparator implements Comparator<Vector<String>> {

	@Override
	public int compare(Vector<String> o1, Vector<String> o2) {
		Double d1=Double.parseDouble(o1.elementAt(1).replace(',', '.'));
		Double d2=Double.parseDouble(o2.elementAt(1).replace(',', '.'));
		return d2.compareTo(d1); //sort from max to min
	}
	
}