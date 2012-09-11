package obir.www;

import java.io.File;
import java.io.FileFilter;
import java.util.HashMap;
import java.util.HashSet;

import javax.servlet.ServletContext;

import obir.ir.Corpus;
import obir.ir.DocumentAnnotation;
import obir.ir.indexing.IndexFiles;
import obir.otr.OTR;
import obir.otr.ObirProject;
import edu.stanford.smi.protege.exception.OntologyLoadException;
import edu.stanford.smi.protegex.owl.ProtegeOWL;
import edu.stanford.smi.protegex.owl.model.OWLModel;
import edu.stanford.smi.protegex.owl.model.ProtegeNames;
import edu.stanford.smi.protegex.owl.model.RDFProperty;
/**
 * Commodity class that wraps the original TextViz methods conveniently for the web application
 * @author davide buscaldi
 *
 */
public class TextVizWrapper {
	/**
	 *  servlet path option
	 */
	static final String relativeWebPath = "/plugins/proto/plugin.properties";
	static String propertiesFilePath;
	
	/**
	 * Threshold indicating the weight from which a document is considered relevant
	 */
	static final double GENERAL_THRESHOLD=0.0;
	/**
	 * Ontology and corpus files
	 */
	static File onto = null,corpus = null;
	/**
	 * Lucene index path
	 */
	static String basicIndexPath;
	/**
	 * The OTR
	 */
	public static OTR otr;
	/**
	 * Probabilities used for the Lin weighting scheme
	 */
	static private HashMap<String,HashMap<String,Float>> resnikSubOntoProbas = new HashMap<String,HashMap<String,Float>>(); 
	
	/**
	 * Interesting score threshold
	 */
	public static float scoreThreshold = new Float(0);


	static HashMap<String,HashMap<String,String>> correspondingFilesByLanguage;
	static HashSet<DocumentAnnotation> searchableDocuments;
	
	/**
	 * Method that initialise all services
	 * @param context the Servlet configuration
	 */
	public static void initServices(ServletContext context){
		System.setProperty("http.proxyHost", "proxy.irit.fr");
		System.setProperty("http.proxyPort", "8001");
		
		propertiesFilePath = context.getRealPath(relativeWebPath);
		System.err.println("properties file located at:"+propertiesFilePath);
		
		String ontoPath = context.getRealPath("/Ontology/template.owl"); //path relative to the application path in Tomcat
		OWLModel model = null;
		try {
			String uriPath=ontoPath.replace('\\', '/');
			uriPath=uriPath.replace(' ', '+');
			model = ProtegeOWL.createJenaOWLModelFromURI("file:///"+uriPath);
		} catch (OntologyLoadException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		if (ontoPath!=null)
			onto = new File(ontoPath.replace("file:/", ""));

		String corpusDir = context.getRealPath("/CorpusVilmorin"); //path relative to the application path in Tomcat
		
		if (corpusDir!=null) corpus = new File(corpusDir.replace("file:/", ""));
		System.err.println("can read ontology: "+onto.exists());
		System.err.println("can read corpus: "+corpus.exists());
		
		initialize(model);
		
		RDFProperty defaultLanguage = otr.getOntology().getRDFProperty(ProtegeNames.getDefaultLanguageSlotName());
		otr.getOntology().getDefaultOWLOntology().setPropertyValue(defaultLanguage, ObirProject.getDefaultLanguage());

	}
	/**
	 * Method that prepare and initialize the OTR
	 * @param model the OWL model corresponding to the OTR
	 */
	private static void initialize(OWLModel model)
	{
		System.err.println("Initializing OTR...") ;
		//OTR rto = new OTR(onto);//,lang);
		
		new ObirProject(onto,corpus,propertiesFilePath, model);
		
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

		for (File foreignDir:foreignDirs)
		{
			String lang = foreignDir.getName();
			String [] names = foreignDir.list();
			HashMap<String,String> parallelFiles = new HashMap<String, String>();
			for (int i=0;i<names.length;i++)
			{
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
		IndexFiles.scanWholeCorpus(ObirProject.getCorpus(),basicIndex.getAbsolutePath(),ObirProject.getIndexingProcessor().getAnalyzerByLanguage(),null);//,htmlByXMLFields);

		System.err.println("Computing searchable documents...") ;
		searchableDocuments = computeSearchableDocuments();
	}
	/**
	 * Documents that can be searched
	 * @return the subset of the Corpus corresponding to indexed documents
	 */
	private static HashSet<DocumentAnnotation> computeSearchableDocuments()
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
	
	public static HashSet<String> getAllowedLanguages()
	{
		HashSet<String> allowedLanguages = new HashSet<String>();
		allowedLanguages.add(ObirProject.getDefaultLanguage());
		return allowedLanguages;
	}
	
	public static String getSearchedDocumentField()
	{
		String field = null;//(String)ObirProject.getCorpus().getSemanticFields().iterator().next();
		field = Corpus.getSemanticFields().iterator().next();
		return field;
	}

}


