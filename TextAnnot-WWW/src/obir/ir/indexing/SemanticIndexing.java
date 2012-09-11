package obir.ir.indexing;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Scanner;

import javax.swing.SwingUtilities;
import javax.xml.parsers.ParserConfigurationException;

import obir.ir.Corpus;
import obir.ir.DocumentAnnotation;
import obir.ir.SearchFiles;
import obir.ir.analysis.CustomAnalyzer;
import obir.misc.XMLToHTML;
import obir.otr.AnnotationGraphCreator;
import obir.otr.Entity;
import obir.otr.OTR;
import obir.otr.ObirProject;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.FSDirectory;
import org.xml.sax.SAXException;

import uk.ac.shef.wit.simmetrics.similaritymetrics.Levenshtein;
import edu.stanford.smi.protegex.owl.model.OWLIndividual;
import edu.stanford.smi.protegex.owl.model.OWLModel;
import edu.stanford.smi.protegex.owl.model.OWLNamedClass;

/**
 * This class aims at indexing semantically the corpus of a given project.
 * 
 * @author Axel Reymonet
 */
public class SemanticIndexing {

	/**
	 * Set of files to reindex totally during next indexing
	 */
	private HashSet<File> filesToReindexTotally;

	/**
	 * Stores all file which has been added to the corpus since last indexing
	 */
	private HashSet<File> newFiles;

	/**
	 * Indice to name the entities
	 */
	private Integer entityIndice;

	/**
	 * List of terms to project during next indexing
	 */
	private HashMap<String, HashMap<String, HashSet<String>>> termsToProjectByLang;

	/**
	 * Analyzer used during the term projection
	 */
	private HashMap<String, CustomAnalyzer> analyzerByLang;

	/**
	 * List of terms which changed over last indexing
	 */
	private HashMap<String, HashMap<String,ArrayList<String>>> changedTermsByLanguage;

	/**
	 * An indexing task
	 */
	public IndexTask task;

	public static AutoIndexTask autoIndexTask;

	/**
	 * List of link words (useful to avoid some incorrect concept pairings)
	 */
	private HashMap<String, HashSet<String>> linkWordsByLang;

	/**
	 * List of signs which must be considered as a punctuation (useful to avoid
	 * some incorrect multi-word term recognitions)
	 */
	private HashSet<String> punctuationMarks;

	private static HashMap<String, IndexWriter> indexWritersByLang;

	/**
	 * Static list of punctuation marks
	 */
	public final static String[] punctuation = { ",", ".", ";", ":", "-" };

	public final static float minimumCoverage = new Float(0.5);

	public SemanticIndexing(boolean autoIndex) {
		filesToReindexTotally = new HashSet<File>();
		newFiles = new HashSet<File>();
		entityIndice = 0;

		termsToProjectByLang = new HashMap<String, HashMap<String, HashSet<String>>>();
		if (indexWritersByLang==null)
			indexWritersByLang = new HashMap<String, IndexWriter>();

		changedTermsByLanguage = new HashMap<String, HashMap<String,ArrayList<String>>>();

		punctuationMarks = new HashSet<String>();
		for (String punct : punctuation) 
		{
			punctuationMarks.add(punct);
		}

		analyzerByLang = new HashMap<String, CustomAnalyzer>();
		linkWordsByLang = new HashMap<String, HashSet<String>>();
		java.util.Properties lingProps = new java.util.Properties();

		File paramDir = new File(ObirProject.getParameterDir());
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
		if (autoIndex)
		{
			//			Thread [] threads = new Thread[Thread.activeCount()];
			//			Thread.enumerate(threads);
			autoIndexTask =new AutoIndexTask();//Thread.currentThread()); 
			autoIndexTask.run();
		}
	}

	/**
	 * Counts the number of words included in a certain field of a given file
	 * 
	 * @param f
	 *            the file to check
	 * @param field
	 *            the field that needs the word count
	 * @return the number of words in the field
	 */
	private int countWords(File f, String field) {
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

	/**
	 * Transforms a term into a set of queries including operators understood by
	 * Lucene. The method returns a set rather than a single query because
	 * looking for a term in different ways increases the recall.
	 * 
	 * @param term
	 * @return an HashSet of corresponding String queries
	 */
	public static HashSet<String> transformTermIntoQueries(OWLNamedClass term) {
		String termText = OTR.getTermLabel(term);
		HashSet<String> queries = new HashSet<String>();
		String modTerm = termText.toLowerCase();

		queries.add(modTerm);

		return queries;
	}

	/**
	 * Computes which terms have to be projected during the next indexing
	 * process
	 */
	private void updateChangedTerms() {

		for (DocumentAnnotation doc : ObirProject.getCorpus())
		{
			for (String termName : changedTermsByLanguage.get(doc.getLanguage()).get("modified")) 
			{
				OWLNamedClass term = ObirProject.getOWLModel().getOWLNamedClass(termName);
				if (doc.getTerms().contains(term))
					notifyDocumentToReindexTotally(doc.getDocumentName(), doc.getLanguage());
			}
		}

		for (String lang:changedTermsByLanguage.keySet())
		{
			ArrayList<String> addedTermNames = this.changedTermsByLanguage.get(lang).get("added");
			for (String term : addedTermNames) 
			{
				ObirProject.getColorManager().addColorMapping(term);
			}

			ArrayList<String> addOrModTermNames = addedTermNames;
			addOrModTermNames.addAll(this.changedTermsByLanguage.get(lang).get("modified"));

			for (String tName : addOrModTermNames) 
			{
				OWLNamedClass term = ObirProject.getOWLModel().getOWLNamedClass(tName);
				HashMap<String, HashSet<String>> tempMap = new HashMap<String, HashSet<String>>();
				if (termsToProjectByLang.containsKey(lang))
					tempMap = termsToProjectByLang.get(lang);
				tempMap.put(tName, transformTermIntoQueries(term));
				termsToProjectByLang.put(lang, tempMap);
			}

			HashMap<String,ArrayList<String>> changedTerms = new HashMap<String, ArrayList<String>>();
			changedTerms.put("added", new ArrayList<String>());
			changedTerms.put("deleted", new ArrayList<String>());
			changedTerms.put("modified", new ArrayList<String>());
			changedTermsByLanguage.put(lang,changedTerms);
		}
	}

	/**
	 *  renvoie une liste vide si rien n'a changé (=> pas de suppr+recréation d'occ+inst)
	 * @param docAnnot
	 * @param field
	 * @param entities
	 * @return
	 */
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
	
	/**
	 * Checks whether a concept annotation is part of a greater concept annotation
	 * @param baseList
	 * @param stringsFromId
	 * @return
	 */
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

	/**
	 * Method to delete instances created for the sake of a semantic query
	 */
	public void deleteTemporaryInstances(String id) 
	{
		ObirProject.getCorpus().removeDocument(id+"_"+ObirProject.xmlQueryFile);
	}

	/**
	 * Deletes all term occurrences and concept instances related to any
	 * non-validated document
	 * 
	 * @param ontology
	 *            the model (which may be different from the currently used one)
	 */
	public void deleteNonValidatedIndividuals(OWLModel ontology) {
		for (DocumentAnnotation doc : ObirProject.getCorpus().getNonValidatedDocuments()) 
		{
			HashSet<OWLIndividual> toDel = new HashSet<OWLIndividual>();
			// toDel.addAll(ObirProject.getCorpus().getTermOccurrences(doc));
			// toDel.addAll(ObirProject.getCorpus().getConceptInstances(doc));
			toDel.addAll(doc.getTermOccurrences());
			toDel.addAll(doc.getConceptInstances());
			for (OWLIndividual inst : toDel)
				ontology.getOWLIndividual(inst.getBrowserText()).delete();

			if (ontology.equals(ObirProject.getOWLModel()))
				doc.resetAllOWLIndividuals();
			else
				ontology.getOWLIndividual(doc.getAnnotation().getLocalName())
				.delete();
		}

		if (ontology.equals(ObirProject.getOWLModel()))
			ObirProject.restartIndividualNumbering();
	}

	/**
	 * Launches the indexing process in background and pops up a progress
	 * monitor
	 * 
	 * @param indexPath
	 *            the path where the files to be indexed all lie
	 */
	public void launchIndexing(File indexPath, boolean collectNewDocs, boolean firstTime) {
		ObirProject.getOTR().setAutoSavePossible(false);

		if (!ObirProject.getCorpus().indexedOnce() || (ObirProject.getCorpus().getDirectoryPath() == null) || !ObirProject.getCorpus().getDirectoryPath().equals(indexPath.getAbsolutePath()))// (firstIndexing||(ObirProject.getCorpus().getDirectoryPath()==null)||!ObirProject.getCorpus().getDirectoryPath().equals(indexPath.getAbsolutePath()))
		{
			ObirProject.getCorpus().warnedIndexedOnce();

			for (String lang:changedTermsByLanguage.keySet())
			{
				HashMap<String, ArrayList<String>> changedTerms = new HashMap<String, ArrayList<String>>(); 
				changedTerms.put("added", ObirProject.getOTR().getAllTermNames(lang));
				changedTerms.put("deleted", new ArrayList<String>());
				changedTerms.put("modified", new ArrayList<String>());
				changedTermsByLanguage.put(lang,changedTerms);
			}
		}

		task = new IndexTask(collectNewDocs,firstTime);
		task.run();
		
	}

	/**
	 * Method to index semantically a given string
	 * 
	 * @param s
	 *            a query string
	 * @param field
	 *            the appropriate document field which is going to be searched
	 *            with the query
	 * @param id
	 *            the session ID of the originating servlet
	 * @return a set of entities (which are going to trigger the creation of
	 *         appropriate term occurrences)
	 */
	public HashSet<Entity> indexQuery(String s, String field, String language, String id) 
	{
		HashSet<Entity> entitiesFound = new HashSet<Entity>();

		File tempFile = new File(OTR.getTemporaryDirectory() + id+"_"+ObirProject.xmlQueryFile);
		try {
			if (tempFile.exists())
				tempFile.delete();
			else
				tempFile.createNewFile();

			PrintWriter pw = new PrintWriter(OTR.getTemporaryDirectory() + id+"_"+ObirProject.xmlQueryFile, "ISO-8859-1");
			pw.print("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><document lang=\"" + language + "\"><" + field + ">" + s	+ "</" + field + "></document>");// ("<html><title>Query</title><body><br><b>"+symptomFieldName+"</b><br>"+s+"\n<br><b>other</b><br>???<br></body></html>");//+diagFieldName+"</b><br>???<br></body></html>");
			pw.close();

			File index_dir = new File(OTR.getTemporaryDirectory() + "query");
			System.err.println("Lucene index dir: "+OTR.getTemporaryDirectory());
			if (!index_dir.exists()) 
			{
				index_dir.mkdir();
			}

			try 
			{
				CustomAnalyzer analyzer = ObirProject.getIndexingProcessor().getAnalyzer(language);
				IndexWriter queryWriter = new IndexWriter(FSDirectory.open(new File(index_dir,language)), analyzer, true,IndexWriter.MaxFieldLength.LIMITED);
				HashSet<String> xmlFields = new HashSet<String>();
				xmlFields.add(field);
				IndexFiles.updateDocumentInIndex(tempFile, xmlFields,queryWriter, analyzer);
				queryWriter.close();
				entitiesFound.addAll(SearchFiles.projectOnQuery(tempFile,language, field));
			} 
			catch (IOException e) {e.printStackTrace();}

		} 
		catch (IOException ioe) {System.out.println("IO Exception during the indexing process");ioe.printStackTrace();}

		return (entitiesFound);
	}
	
	/**
	 * Indexes semantically all files in the {@link OTR#current_dir} directory. Version not to be used in Protege or TextViz
	 * 
	 * @return the appropriate result table to display in the authoring tool.
	 */
	public void doIndexing() {
		File dir = new File(OTR.getTemporaryDirectory());
		dir.mkdir();

		ArrayList<File> filesToIndex = new ArrayList<File>();
		HashSet<DocumentAnnotation> invalidDocs = ObirProject.getCorpus().getNonValidatedDocuments();
		File corpusDir = ObirProject.getCorpus().getDirectory();
		// newFiles = new HashSet<File>();

		HashSet<File> lastSessionNonValidated = new HashSet<File>();
		String[] filesInCorpusDir = corpusDir.list();// m_IndexPath.list();
		int length = filesInCorpusDir.length;
		for (int i = 0; i < length; i++) 
		{				
			File currentFile = new File(corpusDir, filesInCorpusDir[i]);
			if (currentFile.isFile() && filesInCorpusDir[i].endsWith(".xml")) 
			{
				DocumentAnnotation current = ObirProject.getCorpus().getDocument(filesInCorpusDir[i]);
				if (current == null) 
				{
					newFiles.add(currentFile);
					ObirProject.getCorpus().addDocument(filesInCorpusDir[i], true);
				} 
				else if (invalidDocs.contains(current))
				{
					lastSessionNonValidated.add(currentFile);
				}
			}
		} 

		System.err.println("Scanning files...");
		lastSessionNonValidated.addAll(newFiles);
		HashMap<String, ArrayList<File>> newFilesByLang = sortFilesByLanguage(lastSessionNonValidated, ObirProject.getCorpus());
		IndexFiles.addCorpusFilesToIndex(newFilesByLang, indexWritersByLang);

		filesToIndex.addAll(newFiles);
		filesToIndex.addAll(filesToReindexTotally);

		// fill the term map with the new terms id and their associated text for
		// the projection to come
		System.err.println("Processing OTR changes...");
		this.updateChangedTerms();

		System.err.println("Cleaning documents to reindex totally...");
		for (File file : this.filesToReindexTotally) {
			DocumentAnnotation doc = ObirProject.getCorpus().getDocument(file.getName());
			doc.deleteAllAnnotations();
		}

		System.err.println("Computing documents to reindex...");
		HashMap<String, HashMap<String, ArrayList<Entity>>> entitiesByFileField = new HashMap<String, HashMap<String, ArrayList<Entity>>>();
		if (this.termsToProjectByLang.isEmpty()) 
		{
			filesToReindexTotally.addAll(newFiles);

			if (!filesToReindexTotally.isEmpty()) 
			{
				ArrayList<File> editedFiles = new ArrayList<File>();
				for (File file : filesToReindexTotally)
					editedFiles.add(file);

				entitiesByFileField = processIndexing(editedFiles);
			}
		} 

		else 
		{
			entitiesByFileField = processIndexing(filesToIndex); //estrazione termini
		}

		this.termsToProjectByLang = new HashMap<String, HashMap<String, HashSet<String>>>();// new
		// HashMap<String,HashSet<String>>();

		ObirProject.getOWLModel().setDispatchEventsEnabled(false);

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

		HashMap<String, HashMap<String, HashMap<String, String[]>>> nonRelatedInstances = ObirProject.getOTR().createInstances(toCreate, false);// instanceTab,false);
		ObirProject.getOWLModel().flushEvents();

		this.filesToReindexTotally = new HashSet<File>();

	}
	
	/**
	 * Indexes semantically all files in the {@link OTR#current_dir} directory.
	 * 
	 */
	public void doIndexing(boolean collectNewDocs,boolean firstOpening) {
		File dir = new File(OTR.getTemporaryDirectory());
		dir.mkdir();

		ArrayList<File> filesToIndex = new ArrayList<File>();
		HashSet<DocumentAnnotation> invalidDocs = ObirProject.getCorpus().getNonValidatedDocuments();
		File corpusDir = ObirProject.getCorpus().getDirectory();
		// newFiles = new HashSet<File>();

		HashSet<File> lastSessionNonValidated = new HashSet<File>();
		if (collectNewDocs) 
		{
			/*progressMonitor.setIndeterminate(false);
			progressMonitor.setNote("Collecting new documents...");*/

			String[] filesInCorpusDir = corpusDir.list();// m_IndexPath.list();

			int length = filesInCorpusDir.length;
			for (int i = 0; i < length; i++) 
			{				
				File currentFile = new File(corpusDir, filesInCorpusDir[i]);
				if (currentFile.isFile() && filesInCorpusDir[i].endsWith(".xml")) 
				{
					DocumentAnnotation current = ObirProject.getCorpus().getDocument(filesInCorpusDir[i]);
					if (current == null) 
					{
						newFiles.add(currentFile);
						ObirProject.getCorpus().addDocument(filesInCorpusDir[i], true);
					} 
					else if (!firstOpening && invalidDocs.contains(current))
						filesToIndex.add(currentFile);
					else if (invalidDocs.contains(current))
					{
						lastSessionNonValidated.add(currentFile);
					}
				}
			}
		} 
		else if (!firstOpening)
		{
			for (DocumentAnnotation docAnnot : invalidDocs) 
			{
				filesToIndex.add(new File(corpusDir, docAnnot.getDocumentName()));
			}
		}

		System.err.println("Scanning files...");

		lastSessionNonValidated.addAll(newFiles);
		HashMap<String, ArrayList<File>> newFilesByLang = sortFilesByLanguage(lastSessionNonValidated, ObirProject.getCorpus());
		IndexFiles.addCorpusFilesToIndex(newFilesByLang, indexWritersByLang);

		filesToIndex.addAll(newFiles);
		filesToIndex.addAll(filesToReindexTotally);

		// fill the term map with the new terms id and their associated text for
		// the projection to come
		System.err.println("Processing OTR changes...");
		this.updateChangedTerms();

		System.err.println("Cleaning documents to reindex totally...");
		for (File file : this.filesToReindexTotally) {
			DocumentAnnotation doc = ObirProject.getCorpus().getDocument(file.getName());
			doc.deleteAllAnnotations();
		}

		System.err.println("Computing documents to reindex...");
		HashMap<String, HashMap<String, ArrayList<Entity>>> entitiesByFileField = new HashMap<String, HashMap<String, ArrayList<Entity>>>();
		if (this.termsToProjectByLang.isEmpty()) 
		{
			filesToReindexTotally.addAll(newFiles);

			if (!filesToReindexTotally.isEmpty()) 
			{
				ArrayList<File> editedFiles = new ArrayList<File>();
				for (File file : filesToReindexTotally)
					editedFiles.add(file);

				entitiesByFileField = processIndexing(editedFiles);
			}
		} 

		else 
		{
			entitiesByFileField = processIndexing(filesToIndex); //estrazione termini
		}

		this.termsToProjectByLang = new HashMap<String, HashMap<String, HashSet<String>>>();// new
		// HashMap<String,HashSet<String>>();

		ObirProject.getOWLModel().setDispatchEventsEnabled(false);

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

		HashMap<String, HashMap<String, HashMap<String, String[]>>> nonRelatedInstances = ObirProject.getOTR().createInstances(toCreate, false);// instanceTab,false);

		AnnotationGraphCreator.automaticGraphCreation(nonRelatedInstances,minimumCoverage);

		ObirProject.getOWLModel().setDispatchEventsEnabled(true);
		SwingUtilities.invokeLater(new Runnable() 
		{
			public void run() {
				ObirProject.getOWLModel().flushEvents();
			}
		});

		this.filesToReindexTotally = new HashSet<File>();

	}

	/**
	 * Actually indexes/reindexes the list of provided files with entities
	 * 
	 * @param filesToIndex
	 *            the list of files to index
	 * @return the list of entities resulting from the projection of (part of)
	 *         the terminology onto each document
	 */
	private HashMap<String, HashMap<String, ArrayList<Entity>>> processIndexing(ArrayList<File> filesToIndex)// , HashMap<String,String[]> advices)
	{
		HashMap<String, HashMap<String, ArrayList<Entity>>> result = new HashMap<String, HashMap<String, ArrayList<Entity>>>();
		System.err.println("Sorting files...");
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
			System.err.println("Projecting terms...");
			try 
			{
				HashSet<String> termsToProject = new HashSet<String>();
				if (termsToProjectByLang.containsKey(lang))
					termsToProject.addAll(termsToProjectByLang.get(lang).keySet());

				tempEntities = SearchFiles.projectOnCorpusByField(indexWholeFiles.get(lang), filesByLang.get(lang),termsToProject, lang, Corpus.getSemanticFields());

			} 
			catch (IOException e) {e.printStackTrace();}
			
			System.err.println("Computing longest terms...");
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
							int wordsTotal = this.countWords(file, field);
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

	/**
	 * Creates/refreshes the result table to display (either validated or
	 * non-validated files, according to the parameter)
	 * 
	 * @param filesValidated
	 *            input to know which result table to give as an output
	 * @return the output resulting advice table
	 */
	public static HashMap<String, HashMap<String, String>> createAdvice(boolean filesValidated) 
	{
		HashMap<String, HashMap<String, String>> result = new HashMap<String, HashMap<String, String>>();

		HashSet<DocumentAnnotation> currentHandledDocs;
		if (filesValidated)
			currentHandledDocs = ObirProject.getCorpus().getValidatedDocuments();
		else
			currentHandledDocs = ObirProject.getCorpus().getNonValidatedDocuments();

		for (DocumentAnnotation doc : currentHandledDocs) 
		{
			HashMap<String, String> fileInfos = new HashMap<String, String>();

			for (String xmlField : Corpus.getSemanticFields())// semIR_HTMLFieldsByXMLTags.keySet())
			{
				NumberFormat nf = NumberFormat.getInstance(Locale.FRENCH);
				nf.setMaximumFractionDigits(2);
				fileInfos.put(xmlField, nf.format(doc.getIndexingScore(xmlField)));
			}
			result.put(doc.getDocumentName(), fileInfos);// resultTab);
		}
		return result;
	}

	protected boolean isIndexingProcessWorthLaunching() {
		if ( ! filesToReindexTotally.isEmpty() || ! newFiles.isEmpty() )
			return true;
		//		String defaultLang = ObirProject.getDefaultLanguage();
		for (String lang:changedTermsByLanguage.keySet())
			for (String key : changedTermsByLanguage.get(lang).keySet()) 
			{
				if (!changedTermsByLanguage.get(lang).get(key).isEmpty())
					return true;
			}
		return false;
	}


	/**
	 * Link words getter
	 * 
	 * @param language
	 *            a given language code
	 * @return all link words in a given language which can be considered as
	 *         delimiters when pairing two concepts.
	 */
	public HashSet<String> getLinkWords(String language) {
		return linkWordsByLang.get(language);
	}

	/**
	 * Punctuation marks getter
	 * 
	 * @return all marks which can be considered as delimiters when looking for
	 *         a multiword term
	 */
	public HashSet<String> getPunctuationMarks() {
		return punctuationMarks;
	}

	/**
	 * Entity indice getter
	 * 
	 * @return the appropriate indice to name an entity after it
	 */
	public Integer getEntityIndice() {
		return entityIndice;
	}

	/**
	 * Increments the entity indice
	 */
	public void incrementEntityIndice() {
		entityIndice++;
	}

	/**
	 * Linguistic analyzer getter
	 * 
	 * @param language
	 *            a given language code
	 * @return the appropriate analyzer to use when indexing/searching a file
	 *         written in a given language
	 */
	public CustomAnalyzer getLinguisticAnalyzer(String language) {
		return analyzerByLang.get(language);
	}

	/**
	 * Method called when a term has been added to the OTR.
	 * 
	 * @param term a new term
	 */
	private void notifyTermAddition(OWLNamedClass term) {
		String termLang = (String)term.getPropertyValue(term.getOWLModel().getOWLDatatypeProperty(OTR.LANGUAGE));
		ArrayList<String> added = new ArrayList<String>();
		if (changedTermsByLanguage.get(termLang).containsKey("added"))
			added = changedTermsByLanguage.get(termLang).get("added");

		added.add(term.getLocalName());
		changedTermsByLanguage.get(termLang).put("added", added);
	}

	public void notifyElementAddition(OWLNamedClass elt,OWLNamedClass type)
	{
		ObirProject.getColorManager().addColorMapping(elt.getLocalName());
		if (type.getNamedSuperclasses(true).contains(ObirProject.getOWLModel().getOWLNamedClass(OTR.TERM)))
			notifyTermAddition(elt);
	}

	
	public void notifyNewDocument(File f)
	{
		ObirProject.getCorpus().addDocument(f.getAbsolutePath());
		newFiles.add(f);
	}

	/**
	 * Method called when a term has been modified in the OTR.
	 * 
	 * @param term
	 *            a modified term
	 */
	public void notifyTermModification(OWLNamedClass term) 
	{
		String termLang = (String)term.getPropertyValue(term.getOWLModel().getOWLDatatypeProperty(OTR.LANGUAGE));
		ArrayList<String> modified = new ArrayList<String>();
		if (changedTermsByLanguage.get(termLang).containsKey("modified"))
			modified = changedTermsByLanguage.get(termLang).get("modified");
		modified.add(term.getLocalName());// .replaceAll("'", ""));
		changedTermsByLanguage.get(termLang).put("modified", modified);
	}

	/**
	 * Method called when a term has been deleted.
	 * 
	 * @param term
	 *            the deleted term
	 */
	public void notifyTermDeletion(OWLNamedClass term) 
	{
		String termLang = (String)term.getPropertyValue(term.getOWLModel().getOWLDatatypeProperty(OTR.LANGUAGE));
		ArrayList<String> deleted = new ArrayList<String>();
		if (changedTermsByLanguage.get(termLang).containsKey("deleted"))
			deleted = changedTermsByLanguage.get(termLang).get("deleted");
		deleted.add(term.getLocalName());// .replaceAll("'", ""));
		changedTermsByLanguage.get(termLang).put("deleted", deleted);
	}

	/**
	 * Method called when a document needs to be totally reindexed.
	 * 
	 * @param file
	 *            the name of the document
	 */
	public void notifyDocumentToReindexTotally(String file, String lang) 
	{
		String filename = file.substring(file.lastIndexOf("/") + 1);
		filename = filename.substring(filename.lastIndexOf("\\") + 1);
		notifyDocumentToReindexTotally(new File(ObirProject.getCorpus().getDirectory(), filename), lang);
	}

	public void removeDocumentFromIndex(DocumentAnnotation docAnnot) 
	{
		try 
		{
			IndexFiles.removeDocumentFromIndex(new File(ObirProject.getCorpus().getDirectory(), docAnnot.getDocumentName()),indexWritersByLang.get(docAnnot.getLanguage()));
		} 
		catch (IOException e) {e.printStackTrace();}
	}

	public void notifyDocumentToReindexTotally(File file, String lang) {
		filesToReindexTotally.add(file);
		try 
		{
			IndexFiles.updateDocumentInIndex(file, Corpus.getSemanticFields(), indexWritersByLang.get(lang),analyzerByLang.get(lang));
		} 
		catch (IOException e) {e.printStackTrace();}

		ObirProject.getCorpus().notifyDocumentInvalidation(file.getName());
	}

	public HashSet<File> getFilesToReindexTotally() {
		return filesToReindexTotally;
	}

	public void reset() {
		filesToReindexTotally = new HashSet<File>();
		newFiles = new HashSet<File>();
		entityIndice = 0;
	}

	public CustomAnalyzer getAnalyzer(String language) {
		return analyzerByLang.get(language);
	}

	public CustomAnalyzer getAnalyzer(File document) {
		String language = ObirProject.getCorpus().getDocumentLanguage(document.getName());
		if (language != null)
			return getAnalyzer(language);
		else
			return null;
	}

	public HashSet<String> getAvailableLanguages() {
		HashSet<String> langs = new HashSet<String>();
		for (String lang : analyzerByLang.keySet())
			langs.add(lang);
		return langs;
	}

	public HashMap<String, CustomAnalyzer> getAnalyzerByLanguage() {
		return analyzerByLang;
	}

	public void addIndexWriter(String lang, IndexWriter writer) {
		indexWritersByLang.put(lang, writer);
	}


	public IndexWriter getIndexWriter(String lang)
	{
		return indexWritersByLang.get(lang);
	}

	@SuppressWarnings("unchecked")
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
	
	public static HashMap<String, IndexWriter> getAllIndexWriters(){
		return indexWritersByLang;
	}

	public static void addInAlphabeticalOrder(File toAdd, ArrayList<File> list) 
	{
		if (list.size() == 0)
			list.add(toAdd);
		else 
		{
			String nameToAdd = toAdd.getName().toLowerCase();
			nameToAdd = Normalizer.normalize(nameToAdd, Normalizer.Form.NFD);
			String elt = list.get(0).getName().toLowerCase();
			elt = Normalizer.normalize(elt, Normalizer.Form.NFD);
			int correctIndice = 0;
			while (nameToAdd.compareTo(elt) > 0) {
				correctIndice++;
				if (correctIndice >= list.size())
					break;
				else {
					elt = list.get(correctIndice).getName().toLowerCase();
					elt = Normalizer.normalize(elt, Normalizer.Form.NFD);
				}
			}
			list.add(correctIndice, toAdd);
		}
	}

	/**
	 * A class representing a task running in background in order to index the
	 * files selected by the user
	 */
	public class IndexTask extends Thread {
		boolean collectNewDocs;
		boolean firstCorpusOpening;
		
		public IndexTask(boolean getNewDocs, boolean isCorpusOpening) {
			collectNewDocs = getNewDocs;
			firstCorpusOpening = isCorpusOpening;
		}

		/**
		 * M�thode appel�e dans une autre thread lors de la construction d'un
		 * objet de type Task
		 * 
		 * @return the table linking the documents and the consepts
		 */
		public void run() {
			doIndexing(collectNewDocs,firstCorpusOpening);
		}

		protected void onFailure(Throwable t) {
			System.out.println("Error during indexing (IndexTask)");
			t.printStackTrace();
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

	public class AutoIndexTask extends Thread {

		/**
		 * M�thode appel�e dans une autre thread lors de la construction d'un
		 * objet de type Task
		 */
		@Override
		public void run() {
			while (true) {
				try {
					java.lang.Thread.sleep(1000);
				
				if (getState().equals(Thread.State.WAITING)) 
				{
					if (ObirProject.getOTR()!=null && ObirProject.getCorpus()!=null && ObirProject.getCorpus().getDirectory() != null && ObirProject.getOTR().isAutoSavePossible()	&& !ObirProject.getOTR().isAutoSaveInProgress()	&& isIndexingProcessWorthLaunching()) 
					{
						launchIndexing(ObirProject.getCorpus().getDirectory(),false,false);
						java.lang.Thread.sleep(4000);
					}
				}
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}


		protected void onFailure(Throwable t) {
			t.printStackTrace();
		}

	}
}
