package obir.ir.indexing;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Scanner;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.lucene.analysis.TokenStream;
import org.xml.sax.SAXException;

import obir.ir.Corpus;
import obir.ir.DocumentAnnotation;
import obir.ir.SearchFiles;
import obir.ir.analysis.CustomAnalyzer;
import obir.misc.XMLToHTML;
import obir.otr.Entity;
import obir.otr.OTR;
import obir.otr.ObirProject;
import uk.ac.shef.wit.simmetrics.similaritymetrics.Levenshtein;

import edu.stanford.smi.protege.exception.OntologyLoadException;
import edu.stanford.smi.protege.model.Project;
import edu.stanford.smi.protegex.owl.ProtegeOWL;
import edu.stanford.smi.protegex.owl.jena.JenaOWLModel;
import edu.stanford.smi.protegex.owl.model.OWLIndividual;
import edu.stanford.smi.protegex.owl.model.OWLModel;

public class AnnotationCreator {
static File onto = null,corpus = null;
	/**
	 * Path of the Lucene index created by TextViz
	 */
	static String basicIndexPath;
	/**
	 * Reference to the Protégé project
	 */
	static Project myProject;
	/**
	 * The loaded OTR
	 */
	static private OTR otr;
	/**
	 * Maps a language to the documents written in that language
	 */
	static HashMap<String,HashMap<String,String>> correspondingFilesByLanguage;
	
	/**
	 * Set of files to reindex totally
	 */
	private static HashSet<File> filesToReindexTotally;

	/**
	 * Stores all file which has been added to the corpus since last indexing
	 */
	private static HashSet<File> newFiles;



	/**
	 * List of terms to project during next indexing
	 */
	private static HashMap<String, HashMap<String, HashSet<String>>> termsToProjectByLang;

	/**
	 * Analyzer used during the term projection
	 */
	private static HashMap<String, CustomAnalyzer> analyzerByLang;

	/**
	 * List of terms which changed over last indexing
	 */
	private static HashMap<String, HashMap<String,ArrayList<String>>> changedTermsByLanguage;

	/**
	 * List of link words (useful to avoid some incorrect concept pairings)
	 */
	private static HashMap<String, HashSet<String>> linkWordsByLang;

	/**
	 * List of signs which must be considered as a punctuation (useful to avoid
	 * some incorrect multi-word term recognitions)
	 */
	private static HashSet<String> punctuationMarks;


	/**
	 * Static list of punctuation marks
	 */
	public final static String[] punctuation = { ",", ".", ";", ":", "-" };
	
	/**
	 * @param args
	 * @throws Exception 
	 * @throws URISyntaxException 
	 */
	public static void main(String[] args) throws URISyntaxException, Exception {
		String ontoPath="C:/Users/davide/Dropbox/MOANO/Ontology/RTO2007/template.owl";
		String corpusDir = "C:/Users/davide/Dropbox/git/dropbox-git/TextAnnot-test/WebContent/CorpusVilmorin";
		if(args.length != 2){
			System.err.println("Usage: java -jar AnnotationCreator.jar <Corpus dir> <RTO.owl>");
			System.exit(-1);
		} else {
			ontoPath= args[1];
			corpusDir = args[0];
		}
		
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
		
		if (corpusDir!=null) corpus = new File(corpusDir.replace("file:/", ""));
		System.err.println("can read ontology: "+onto.exists());
		System.err.println("can read corpus: "+corpus.exists());
		
		initialize(model);
		ObirProject.getIndexingProcessor().doIndexing(); //NUOVO METODO CREATO IN SemanticIndexing per gestire l'annotazione senza passare dall'interfaccia web
		initIndexModule();
		
		File corpusDirFile = ObirProject.getCorpus().getDirectory();
		String[] filesInCorpusDir = corpusDirFile.list();
		int length = filesInCorpusDir.length;
		for (int i = 0; i < length; i++) 
		{				
			File currentFile = new File(corpusDir, filesInCorpusDir[i]);
			if (currentFile.isFile() && filesInCorpusDir[i].endsWith(".xml")) 
			{
				DocumentAnnotation current = ObirProject.getCorpus().getDocument(filesInCorpusDir[i]); //QUI CONTROLLA SE IL DOCUMENTO E' GIA' STATO ANNOTATO
				if (current == null) 
				{
					System.err.println("New document: "+currentFile.getName());
					newFiles.add(currentFile);
					ObirProject.getCorpus().addDocument(filesInCorpusDir[i], true);
				}
			}
		}
		
		ArrayList<File> filesToIndex = new ArrayList<File>();
		
		HashSet<File> lastSessionNonValidated = new HashSet<File>();
		lastSessionNonValidated.addAll(newFiles);
		HashMap<String, ArrayList<File>> newFilesByLang = sortFilesByLanguage(lastSessionNonValidated, ObirProject.getCorpus());
		IndexFiles.addCorpusFilesToIndex(newFilesByLang, ObirProject.getIndexingProcessor().getAllIndexWriters());
		
		filesToReindexTotally.addAll(newFiles);
		filesToIndex.addAll(newFiles);
		
		HashMap<String, HashMap<String, ArrayList<Entity>>> entitiesByFileField = new HashMap<String, HashMap<String, ArrayList<Entity>>>();
		entitiesByFileField = processIndexing(filesToIndex);
		
		ArrayList<Entity> toCreate = new ArrayList<Entity>();
		for (String file : entitiesByFileField.keySet()) 
		{
			for (String field : entitiesByFileField.get(file).keySet()) 
			{
				toCreate.addAll(entitiesByFileField.get(file).get(field));
				if (!entitiesByFileField.get(file).get(field).isEmpty()) 
				{
					ObirProject.getCorpus().getDocument(file)
					.deleteAllAnnotations(field);
				}
			}
		}

		System.err.println("Creating instances...");

		HashMap<String, HashMap<String, HashMap<String, String[]>>> nonRelatedInstances = ObirProject.getOTR().createInstances(toCreate, false);
		ObirProject.getOWLModel().flushEvents();
		
		System.err.println("Validating annotations...");
		for (int i = 0; i < length; i++) 
		{				
			File currentFile = new File(corpusDir, filesInCorpusDir[i]);
			if (currentFile.isFile() && filesInCorpusDir[i].endsWith(".xml")) 
			{
				DocumentAnnotation current = ObirProject.getCorpus().getDocument(filesInCorpusDir[i]); //QUI CONTROLLA SE IL DOCUMENTO E' GIA' STATO ANNOTATO
				if (current == null) 
				{
					System.err.println("Not annotated document: "+currentFile.getName());
				} else {
					ObirProject.getIndexingProcessor().removeDocumentFromIndex(current);
					ObirProject.getCorpus().validateDocument(current);
				}
			}
		}
		
		String uriPath=ontoPath.replace('\\', '/');
		uriPath=uriPath.replace(' ', '+');
		String newOntoPath = uriPath.replace(".owl", "_annot.owl");
		System.err.println("Writing results: ");
		//ObirProject.doSaving(true); //saving on the same file
		//to save in another file:
		otr.setAutoSavePossible(false);
		((JenaOWLModel)ObirProject.getOWLModel()).save(new URI("file:///"+newOntoPath), "RDF/XML-ABBREV", new HashSet());
		otr.setAutoSavePossible(true);
		
		System.err.println("done.");
	}
	
	/**
	 * Method that loads the OTR and initialize the OWL model
	 * @param model
	 */
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
		IndexFiles.scanWholeCorpus(ObirProject.getCorpus(),basicIndex.getAbsolutePath(),ObirProject.getIndexingProcessor().getAnalyzerByLanguage(),null);
		/*
		System.err.println("Computing searchable documents...") ;
		searchableDocuments = computeSearchableDocuments();
		*/
	}
	/*
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
	*/
	private static HashSet<String> getAllowedLanguages()
	{
		HashSet<String> allowedLanguages = new HashSet<String>();
		allowedLanguages.add(ObirProject.getDefaultLanguage());
		return allowedLanguages;
	}
	
	protected static void initIndexModule(){
		filesToReindexTotally = new HashSet<File>();
		newFiles = new HashSet<File>();

		termsToProjectByLang = new HashMap<String, HashMap<String, HashSet<String>>>();

		changedTermsByLanguage = new HashMap<String, HashMap<String,ArrayList<String>>>();

		punctuationMarks = new HashSet<String>();
		for (String punct : punctuation) 
		{
			punctuationMarks.add(punct);
		}

		analyzerByLang = new HashMap<String, CustomAnalyzer>();
		linkWordsByLang = new HashMap<String, HashSet<String>>();
		java.util.Properties lingProps = new java.util.Properties();

		File paramDir = new File("plugins/proto/");
		String[] paramFiles = paramDir.list();
		for (String filename : paramFiles) 
		{
			if (filename.startsWith("settings_") && filename.endsWith(".txt")) 
			{
				String language = filename.substring(filename.indexOf("settings_") + 9, filename.lastIndexOf(".txt"));
				File paramFile = new File(paramDir, filename);

				try 
				{
					java.io.FileInputStream lingConfigFile = new java.io.FileInputStream(paramFile);
					lingProps.load(lingConfigFile);
					System.err.println("loading stopwords from file "+paramFile.getAbsolutePath());
				} 
				catch (IOException e) 
				{
					System.out.println("Impossible to find file "+ paramFile.getAbsolutePath());
				}

				String stopWords = lingProps.getProperty("ling.stopWords");
				if (stopWords != null)
					stopWords = stopWords.replaceAll(",", "\n");

				try 
				{
					analyzerByLang.put(language, new CustomAnalyzer(language,new StringReader(stopWords)));
				} 
				catch (IOException e1) {e1.printStackTrace();}

				Scanner scan = new Scanner(lingProps.getProperty("ling.linkWords"));
				scan.useDelimiter(",");
				HashSet<String> linkWords = new HashSet<String>();
				while (scan.hasNext()) {
					String next = scan.next();
					while (next.startsWith(" "))
						next = next.substring(1);
					while (next.endsWith(" "))
						next = next.substring(0, next.length() - 1);
					linkWords.add(next);
				}
				linkWordsByLang.put(language, linkWords);

				HashMap<String, ArrayList<String>> changedTerms = new HashMap<String, ArrayList<String>>(); 
				changedTerms.put("added", new ArrayList<String>());
				changedTerms.put("deleted", new ArrayList<String>());
				changedTerms.put("modified", new ArrayList<String>());
				changedTermsByLanguage.put(language,changedTerms);
			}
		}
	}
	
	/**
	 * Actually indexes/reindexes the list of provided files with entities
	 * 
	 * @param filesToIndex
	 *            the list of files to index
	 * @return the list of entities resulting from the projection of (part of)
	 *         the terminology onto each document
	 */
	private static HashMap<String, HashMap<String, ArrayList<Entity>>> processIndexing(ArrayList<File> filesToIndex)// , HashMap<String,String[]> advices)
	{
		HashMap<String, HashMap<String, ArrayList<Entity>>> result = new HashMap<String, HashMap<String, ArrayList<Entity>>>();
		java.util.Collections.sort(filesToIndex, new MyComparator());

		HashMap<String, ArrayList<File>> filesByLang = sortFilesByLanguage(filesToIndex, ObirProject.getCorpus());
		// progressMonitor.setNote("Scanning files...");
		// IndexFiles.addCorpusFilesToIndex(filesByLang,
		// indexWritersByLang,ObirProject);

		ArrayList<File> filesNeedingFullProjection = new ArrayList<File>();
		filesNeedingFullProjection.addAll(newFiles);
		filesNeedingFullProjection.addAll(filesToReindexTotally);
		HashMap<String, ArrayList<File>> indexWholeFiles = sortFilesByLanguage(filesNeedingFullProjection, ObirProject.getCorpus());

		for (String lang : filesByLang.keySet()) 
		{
			HashMap<File, HashMap<String, ArrayList<Entity>>> tempEntities = new HashMap<File, HashMap<String, ArrayList<Entity>>>();

			try 
			{
				HashSet<String> termsToProject = new HashSet<String>();
				if (termsToProjectByLang.containsKey(lang))
					termsToProject.addAll(termsToProjectByLang.get(lang).keySet());

				tempEntities = SearchFiles.projectOnCorpusByField(indexWholeFiles.get(lang), filesByLang.get(lang),termsToProject, lang, Corpus.getSemanticFields());

			} 
			catch (IOException e) {e.printStackTrace();}

			int cpt = 0;
			for (File file : tempEntities.keySet()) 
			{
				cpt++;
				HashMap<String, ArrayList<Entity>> addableEntities = new HashMap<String, ArrayList<Entity>>();
				DocumentAnnotation currentDocument = ObirProject.getCorpus().getDocument(file.getName());
				for (String field : tempEntities.get(file).keySet()) 
				{
					ArrayList<Entity> entities = computeUsefulEntities(currentDocument, field, tempEntities.get(file).get(field));
					addableEntities.put(field, entities);

					if (entities.size() > 0	|| currentDocument.getTermOccurrences(field).isEmpty()) 
					{
						int foundWords = 0;
						HashSet<Integer> offsets = new HashSet<Integer>();
						for (Entity inst : entities) 
						{
							for (int k = 0; k < inst.text.size(); k++) 
							{
								int off = inst.offset.get(k);
								if (!offsets.contains(off)) 
								{
									offsets.add(off);
									foundWords++;
								}
							}
						}
						if (foundWords != 0) 
						{
							int wordsTotal = countWords(file, field);
							currentDocument.setIndexingScore(field, new Float(foundWords) / wordsTotal);
						} else
							currentDocument.setIndexingScore(field,	new Float(0));
					}
				}
				result.put(file.getName(), addableEntities);
			}
		}
		return (result);
	}
	
	public static HashMap<String, ArrayList<File>> sortFilesByLanguage(HashSet<?> fileSet, Corpus corpus) 
	{
		ArrayList fileList = new ArrayList();
		fileList.addAll(fileSet);
		return sortFilesByLanguage(fileList, corpus);
	}
	
	public static HashMap<String, ArrayList<File>> sortFilesByLanguage(ArrayList<?> fileList, Corpus corpus) 
	{
		HashMap<String, ArrayList<File>> result = new HashMap<String, ArrayList<File>>();


		for (String language : ObirProject.getIndexingProcessor().getAvailableLanguages())
			result.put(language, new ArrayList<File>());

		String lang;
		boolean isFile = false;
		if (!fileList.isEmpty() && fileList.get(0) instanceof File)
			isFile = true;

		for (Object obj : fileList) 
		{
			String name;
			File f;
			if (isFile) 
			{
				f = (File) obj;
				name = f.getName();
			} 
			else 
			{
				name = (String) obj;
				f = new File(corpus.getDirectory(), name);
			}

			if (corpus.containsDocument(name))
				lang = corpus.getDocument(name).getLanguage();
			else 
			{
				lang = Corpus.computeExplicitFileLanguage(name);
				if (lang.isEmpty())
					lang = corpus.getDefaultLanguage();
			}

			ArrayList<File> list = new ArrayList<File>();
			if (result.containsKey(lang))
				list = result.get(lang);
			list.add(f);
			result.put(lang, list);
		}

		return result;
	}
	
	private static int countWords(File f, String field) {
		int wordsTotal = 0;
		try 
		{
			String symptContent = XMLToHTML.parseXMLField(f, field);
			TokenStream tStream = getAnalyzer(f).tokenStream(field,new StringReader(symptContent));// fileContent+"\""));

			while (tStream.incrementToken())
			{
				wordsTotal++;
			}
			//			wordsTotal--;
		} 
		catch (ParserConfigurationException pce) {pce.printStackTrace();} 
		catch (SAXException se) {se.printStackTrace();} 
		catch (IOException ioe) {ioe.printStackTrace();}

		return wordsTotal;
	}
	
	public static CustomAnalyzer getAnalyzer(String language) {
		return analyzerByLang.get(language);
	}

	public static CustomAnalyzer getAnalyzer(File document) {
		String language = ObirProject.getCorpus().getDocumentLanguage(document.getName());
		if (language != null)
			return getAnalyzer(language);
		else
			return null;
	}
	
	// renvoie une liste vide si rien n'a chang� (=> pas de suppr+recr�ation
		// d'occ+inst)
		public static ArrayList<Entity> computeUsefulEntities(DocumentAnnotation docAnnot, String field,ArrayList<Entity> entities) 
		{
			HashSet<OWLIndividual> docExistingTermOccs = docAnnot.getTermOccurrences(field);
			ArrayList<Entity> oldEntities = new ArrayList<Entity>();
			String docURL = ObirProject.getCorpus().getDirectoryPath() + "\\" + docAnnot.getDocumentName();

			for (OWLIndividual termOcc : docExistingTermOccs) 
			{
				Entity entToAdd = new Entity();
				entToAdd.docURL = docURL;
				entToAdd.ind_name = termOcc.getBrowserText();
				entToAdd.offset = OTR.getTermOccurrenceOffset(termOcc);
				entToAdd.text = OTR.getTermOccurrenceWords(termOcc);
				entToAdd.type = termOcc.getRDFType().getLocalName();
				entToAdd.field = field;
				oldEntities.add(entToAdd);
			}
			ArrayList<Entity> allEntities = new ArrayList<Entity>();
			allEntities.addAll(oldEntities);
			allEntities.addAll(entities);

			ArrayList<Entity> addableEntities = computeLongestEntities(allEntities,null);

			for (Entity entity : addableEntities) 
			{
				Entity match = null;
				for (Entity oldEnt : oldEntities) 
				{
					if (entity.offset.equals(oldEnt.offset)) {
						match = oldEnt;
						break;
					}
				}

				if (match != null)
					oldEntities.remove(match); // suppression car cette occ ne matchera plus aucune autre entit� (hypoth�se: pas de doublon)
				else
					return addableEntities;
			}
			return new ArrayList<Entity>();
		}
		
		public static ArrayList<Entity> computeLongestEntities(ArrayList<Entity> baseList, HashMap<String, String> stringsFromId) 
		{
			ArrayList<Entity> addableEntities = new ArrayList<Entity>();
			ArrayList<Entity> unAddableEntities = new ArrayList<Entity>();
			for (int i = 0; i < baseList.size(); i++) 
			{
				Entity candidateEntity = baseList.get(i);

				if (!unAddableEntities.contains(candidateEntity)) 
				{
					for (int j = 0; j < baseList.size(); j++) 
					{
						if (j != i) 
						{
							Entity otherEntity = baseList.get(j);
							if (otherEntity.field.equals(candidateEntity.field)) 
							{
								if ((otherEntity.offset.containsAll(candidateEntity.offset)) && (!candidateEntity.offset.containsAll(otherEntity.offset))) // moteur VS moteur de clim
								{
									unAddableEntities.add(candidateEntity);
									break;
								} 
								else if ((candidateEntity.offset.containsAll(otherEntity.offset)) && (!otherEntity.offset.containsAll(candidateEntity.offset))) 
								{
									unAddableEntities.add(otherEntity);
								} 
								else if (otherEntity.offset.equals(candidateEntity.offset)) 
								{
									String origin = "";
									for (String s : candidateEntity.text)
										origin += s + " ";
									origin = origin.substring(0,origin.length() - 1);
									Levenshtein lev = new Levenshtein();
									String candidateText, otherText;
									if (stringsFromId == null || stringsFromId.isEmpty()) 
									{
										candidateText = (String) ObirProject.getOWLModel().getOWLNamedClass(candidateEntity.type).getPropertyValue(ObirProject.getOWLModel().getOWLDatatypeProperty(OTR.TEXTE));
										otherText = (String) ObirProject.getOWLModel().getOWLNamedClass(otherEntity.type).getPropertyValue(ObirProject.getOWLModel().getOWLDatatypeProperty(OTR.TEXTE));
									} 
									else 
									{
										candidateText = stringsFromId.get(candidateEntity.type);
										otherText = stringsFromId.get(otherEntity.type);
									}

									float candidateSim = lev.getSimilarity(origin, candidateText);
									float otherSim = lev.getSimilarity(origin, otherText);
									if (otherSim > candidateSim) 
									{
										unAddableEntities.add(candidateEntity);
										break;
									} 
									else if (otherSim < candidateSim) 
									{
										unAddableEntities.add(otherEntity);
									}

								}
							}
						}
					}

					if (!unAddableEntities.contains(candidateEntity))
						addableEntities.add(candidateEntity);
				}
			}
			return addableEntities;
		}

}

class MyComparator implements Comparator<Object> {
	public int compare(Object a, Object b) {
		String first, second;
		if (a instanceof File && b instanceof File) 
		{
			first = ((File) a).getName().toLowerCase();
			second = ((File) b).getName().toLowerCase();
		} 
		else 
		{
			first = a.toString().toLowerCase();
			second = b.toString().toLowerCase();
		}
		first = Normalizer.normalize(first, Normalizer.Form.NFD);
		second = Normalizer.normalize(second, Normalizer.Form.NFD);
		return first.compareTo(second);
	}
}