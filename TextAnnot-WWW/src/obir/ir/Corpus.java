package obir.ir;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import javax.swing.SwingUtilities;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import obir.ir.indexing.SemanticIndexing;
import obir.otr.OTR;
import obir.otr.ObirProject;

import org.xml.sax.SAXException;

import edu.stanford.smi.protegex.owl.model.OWLIndividual;
import edu.stanford.smi.protegex.owl.model.OWLNamedClass;
import edu.stanford.smi.protegex.owl.model.OWLObjectProperty;
import edu.stanford.smi.protegex.owl.model.RDFProperty;
import edu.stanford.smi.protegex.owl.model.RDFResource;

/**
 * Class represents the set of documents on which the plugin is currently working. 
 * Each document is represented as an instance of the {@code Document} class (which is neither a {@code Term} or a {@code Concept}.
 * Each owns a validation state, a coverage, a set of term occurrences and a set of concept instances.
 * @author Axel Reymonet
 */
public class Corpus extends HashSet<DocumentAnnotation> {

	private static final long serialVersionUID = -6001715831614515742L;


	/**
	 * Directory where the corpus is stored
	 */
	private static File corpusDirectory;

	/**
	 * Fields to index semantically
	 */
	private static HashMap<String,HashMap<String,String>> semIR_HTMLFieldsByXMLTags;

	/**
	 * Fields to index classically
	 */
	private static HashMap<String,HashMap<String,String>> classicIR_HTMLFieldsByXMLTags;

	/**
	 * Indicates whether the corpus has already been indexed
	 */
	private boolean indexedOnce;

	private HashSet<String> unvalidatedDocsDuringSession;

	/**
	 * Static DTD file valid for all documents in the current corpus 
	 */
	public static final File dtdFile = new File("plugins/proto/document_model.dtd") ;

	@SuppressWarnings("unchecked")
	public Corpus()
	{
		indexedOnce = false;
		semIR_HTMLFieldsByXMLTags = ObirProject.getSemanticFields();//new HashMap<String, String>();
		classicIR_HTMLFieldsByXMLTags = ObirProject.getClassicFields();//new HashMap<String, String>();
		unvalidatedDocsDuringSession = new HashSet<String>();

		String corpusDir = ObirProject.getOWLModel().getOWLProject().getSettingsMap().getString("dynamo_corpus");
		if (corpusDir!=null && !corpusDir.equals("dynamo_corpus_value"))
		{
			corpusDirectory = new File(corpusDir);

			for (OWLIndividual inst:(Collection<OWLIndividual>)ObirProject.getOWLModel().getOWLIndividuals())
			{
				if (inst.getRDFType().getLocalName().equals(OTR.DOCUMENT))
				{
					DocumentAnnotation doc = addDocument(inst);
					if (!doc.isValidated() && doc.getTermOccurrences().isEmpty())
						remove(doc);
				}
			}
		}
		else
		{
			for (OWLIndividual inst:(Collection<OWLIndividual>)ObirProject.getOWLModel().getOWLIndividuals())
			{
				inst.delete();
			}
		}
	}

	@SuppressWarnings("unchecked")
	public Corpus(File corpusPath)
	{
		indexedOnce = false;
		corpusDirectory = corpusPath;
		semIR_HTMLFieldsByXMLTags = ObirProject.getSemanticFields();//new HashMap<String, String>();
		classicIR_HTMLFieldsByXMLTags = ObirProject.getClassicFields();//new HashMap<String, String>();
		
		for (OWLIndividual inst:(Collection<OWLIndividual>)ObirProject.getOWLModel().getOWLIndividuals())
		{
			if (inst.getRDFType().getLocalName().equals(OTR.DOCUMENT))
			{
				DocumentAnnotation doc = addDocument(inst);
				if (!doc.isValidated())
					remove(doc);
			}
		}
	}
	
	/**
	 * If the parameter is set to true, this method creates an empty corpus
	 * @param makeEmpty 
	 */
	@SuppressWarnings("unchecked")
	public Corpus(boolean makeEmpty)
	{
		indexedOnce = false;
		corpusDirectory = ObirProject.getCorpus().getDirectory();
		semIR_HTMLFieldsByXMLTags = ObirProject.getSemanticFields();//new HashMap<String, String>();
		classicIR_HTMLFieldsByXMLTags = ObirProject.getClassicFields();//new HashMap<String, String>();
		
		if(!makeEmpty){
			for (OWLIndividual inst:(Collection<OWLIndividual>)ObirProject.getOWLModel().getOWLIndividuals())
			{
				if (inst.getRDFType().getLocalName().equals(OTR.DOCUMENT))
				{
					DocumentAnnotation doc = addDocument(inst);
					if (!doc.isValidated())
						remove(doc);
				}
			}
		}
		
	}

	/**
	 * Document getter
	 * @param filename the filename of the document
	 * @return the OWL individual corresponding to the document in the meta-model
	 */
	public DocumentAnnotation getDocument(String filename)
	{
		filename = filename.substring(filename.lastIndexOf("/")+1);
		if (filename.contains("\\"))
			filename = filename.substring(filename.lastIndexOf("\\")+1);

		for (DocumentAnnotation doc:this)
		{
			if (doc.getDocumentName().equals(filename))
				return doc;
		}
		return null;
	}


	/**
	 * Adds a document to the corpus (if not already existing)
	 * @param filepath the path to the document
	 * @return the OWL individual corresponding to the document in the meta-model
	 */
	public DocumentAnnotation addDocument(String filepath)//,Set<String> indexedFields)
	{
		return addDocument(filepath,false);
	}

	public DocumentAnnotation addDocument(String filepath,boolean isNewForSure)//,Set<String> indexedFields)
	{
		String dirPath = "";
		if (filepath.contains("/"))
		{
			dirPath = filepath.substring(0, filepath.lastIndexOf("/"));
			dirPath = dirPath.replaceAll("/", "\\\\");
		}
		else if (filepath.contains("\\"))
			dirPath = filepath.substring(0, filepath.lastIndexOf("\\"));
		String subDir = "";
		if (!dirPath.isEmpty() && ! dirPath.equals(getDirectoryPath()))
			subDir = dirPath.replace(getDirectoryPath(), "").substring(1);

		String filename = filepath;
		if (filename.contains("\\"))
			filename = filename.substring(filename.lastIndexOf("\\")+1,filename.length());
		else if (filename.contains("/"))
			filename = filename.substring(filename.lastIndexOf("/")+1,filename.length());

		DocumentAnnotation doc = null;
		if (!isNewForSure)
			doc = getDocument(filename);
		if (doc==null)
		{
			doc = createDocumentAnnotation(subDir,filename,ObirProject.getDefaultLanguage());
			add(doc);
		}
		/*if (doc==null)
		{
			doc = createDocumentAnnotation(ObirProject.getOWLModel().getOWLNamedClass(OTR.DOCUMENT).createOWLIndividual(ObirProject.generateNextIndName()), subDir,filename,ObirProject.getDefaultLanguage());
			add(doc);
		}*/
		return doc;
	}

	/**
	 * Creates a new (unvalidated) DocumentAnnotation (not added to the corpus)
	 * @param subDir the document directory (relative to the working path)
	 * @param docName the document name
	 * @param defaultLanguage the document language
	 * @return
	 */
	public DocumentAnnotation createDocumentAnnotation(String subDir, String docName, String defaultLanguage)
	{
		DocumentAnnotation docAnnot = new DocumentAnnotation(subDir, docName, defaultLanguage);
		docAnnot.unvalidate();
		return docAnnot;
	}

	/**
	 * Adds a document to the corpus
	 * @param ind the OWL individual
	 * @return the corresponding document annotation
	 */
	public DocumentAnnotation addDocument(OWLIndividual ind)//,Set<String> indexedFields)
	{
		for (DocumentAnnotation docAnn:this)
		{
			if (docAnn.getAnnotation().equals(ind))
				return docAnn;
		}
		DocumentAnnotation newDocAnn = new DocumentAnnotation(ind,ObirProject.getDefaultLanguage()); 
		add(newDocAnn);
		return newDocAnn;
	}


	/**
	 * Checks whether a document belongs to the current corpus
	 * @param filename the filename of the document
	 * @return {@code true} iff there is a {@code Document} individual in the corpus with the given filename
	 */
	public boolean containsDocument(String filename)
	{
		for (DocumentAnnotation doc:this)
		{
			if (doc.getDocumentName().equals(filename))
				return true;
		}
		return false;
	}

	/**
	 * Removes a document to the corpus AND DELETES ALL ASSOCIATED TERM OCCURRENCES AND CONCEPT INSTANCES
	 * @param filename the filename of the document
	 */
	public void removeDocument(String filename)
	{
		DocumentAnnotation docAnnot = getDocument(filename);
		removeDocument(docAnnot);
	}

	/**
	 * Removes a document to the corpus AND DELETES ALL ASSOCIATED TERM OCCURRENCES AND CONCEPT INSTANCES
	 * @param docAnnot the document to remove
	 */
	public void removeDocument(DocumentAnnotation docAnnot)
	{	
		if (docAnnot!=null)
		{
			for (OWLIndividual inst:docAnnot.getConceptInstances())
				inst.delete();
			for (OWLIndividual occ:docAnnot.getTermOccurrences())
				occ.delete();
			docAnnot.getAnnotation().delete();
			remove(docAnnot);
		}
	}

	/**
	 * Gets all validated documents in the corpus
	 * @return a set of documents with a validated state
	 */
	public HashSet<DocumentAnnotation> getValidatedDocuments()
	{
		HashSet<DocumentAnnotation> result = new HashSet<DocumentAnnotation>();
		for (DocumentAnnotation doc:this)
		{
			if (doc.isValidated())
				result.add(doc);
		}
		return result;
	}



	/**
	 * Gets all non-validated documents in the corpus
	 * @return a set of documents with a non-validated state
	 */
	public HashSet<DocumentAnnotation> getNonValidatedDocuments()
	{
		HashSet<DocumentAnnotation> result = new HashSet<DocumentAnnotation>();
		for (DocumentAnnotation doc:this)
		{
			if (!doc.isValidated())
				result.add(doc);
		}
		return result;
	}
	
	/**
	 * Gets a partition of the document set in two sets of validated and not validated documents
	 * @return
	 */
	public ArrayList<HashSet<DocumentAnnotation>> getCorpusPartition()
	{
		ArrayList<HashSet<DocumentAnnotation>> result = new ArrayList<HashSet<DocumentAnnotation>>();
		HashSet<DocumentAnnotation> valid = new HashSet<DocumentAnnotation>();
		HashSet<DocumentAnnotation> invalid = new HashSet<DocumentAnnotation>();
		for (DocumentAnnotation doc:this)
		{
			if (doc.isValidated())
				valid.add(doc);
			else
				invalid.add(doc);
		}
		result.add(valid);
		result.add(invalid);
		return result;
	}

	private String getField(OWLIndividual current, HashMap<OWLIndividual,HashSet<OWLIndividual>> associationMap, HashMap<OWLIndividual,HashSet<OWLIndividual>> termOccsByCptInsts)
	{
		if (!termOccsByCptInsts.get(current).isEmpty())
			return (String)termOccsByCptInsts.get(current).iterator().next().getPropertyValue(current.getOWLModel().getOWLDatatypeProperty(OTR.DOC_FIELD));
		else
		{

			for (OWLIndividual neighbor:associationMap.get(current))
			{
				String field = getField(neighbor, associationMap, termOccsByCptInsts);
				if (field!=null)
					return field;
			}

			return null;
		}
	}

	private void propagateField(OWLIndividual current, String field, HashSet<OWLIndividual> marked,HashMap<OWLIndividual,HashSet<OWLIndividual>> associationMap,DocumentAnnotation docAnnot)
	{
		if (!marked.contains(current))
		{
			docAnnot.addConceptInstance(current,field);
			marked.add(current);
		}

		for (OWLIndividual neighbor:associationMap.get(current))
			propagateField(neighbor,field,marked,associationMap,docAnnot);

	}

	@SuppressWarnings("unchecked")
	public void loadDocuments()
	{
		int nb_instances = ObirProject.getOWLModel().getOWLNamedClass(OTR.DOCUMENT).getInstances(false).size();
		int i = 1 ;

		HashSet<OWLIndividual> missingDocs = new HashSet<OWLIndividual>();

		for (OWLIndividual doc:(Collection<OWLIndividual>)ObirProject.getOWLModel().getOWLNamedClass(OTR.DOCUMENT).getInstances(false))
		{
			String filename = (String)doc.getPropertyValue(ObirProject.getOWLModel().getOWLDatatypeProperty(OTR.DOC_NAME));
			filename = filename.substring(filename.lastIndexOf("/")+1);

			File associatedCorpusFile = new File(corpusDirectory,filename);

			if (!associatedCorpusFile.exists())
			{
				missingDocs.add(doc);
			}
			else
			{
				DocumentAnnotation docAnnot = addDocument(doc);

				HashSet<OWLIndividual> conceptInstances=new HashSet<OWLIndividual>();
				for (OWLIndividual ind:(Collection<OWLIndividual>)doc.getPropertyValues(doc.getOWLModel().getOWLObjectProperty(OTR.DOC_HAS_CONCEPT_INSTS)))
					conceptInstances.add(ind);

				if (semIR_HTMLFieldsByXMLTags.keySet().size()==1)
				{
					for (OWLIndividual cptInst:conceptInstances)
						docAnnot.addConceptInstance(cptInst,semIR_HTMLFieldsByXMLTags.keySet().iterator().next());
				}
				else
				{
					HashMap<OWLIndividual, HashSet<OWLIndividual>> termOccsByCptInsts = OTR.getTermOccsByCptInstMap(docAnnot.getTermOccurrences(), conceptInstances);
					HashMap<OWLIndividual, HashSet<OWLIndividual>> assocCptInsts = new HashMap<OWLIndividual, HashSet<OWLIndividual>>();

					for (OWLIndividual cptInst:conceptInstances)
					{
						HashSet<OWLIndividual> assocs = new HashSet<OWLIndividual>();
						for (RDFProperty prop:(Collection<RDFProperty>)cptInst.getRDFProperties())
						{
							if (prop instanceof OWLObjectProperty)
							{
								assocs.addAll(cptInst.getPropertyValues(prop));
							}
						}
						assocCptInsts.put(cptInst, assocs);
					}

					HashSet<OWLIndividual> marked = new HashSet<OWLIndividual>();
					HashSet<OWLIndividual> remaining = (HashSet<OWLIndividual>)conceptInstances.clone();
					//FIXME there seems to be some cases where an artificial element is not connected to any field info ( => infinite "while" loop)
					while (!remaining.isEmpty())
					{
						marked = new HashSet<OWLIndividual>();
						for (OWLIndividual rem:remaining)
						{
							String field = getField(rem, assocCptInsts, termOccsByCptInsts);
							if (field !=null)
							{
								propagateField(rem, field, marked, assocCptInsts, docAnnot);
							}
							if (marked.equals(remaining))
								break;
						}
						remaining.removeAll(marked);
					}
				}


				if (filename.endsWith(".xml"))
				{
					String filepath = getDirectoryPath()+"\\"+filename;//m_IndexPath.getAbsolutePath()+"\\"+filename;

					try
					{
						for (String xmlField:getSemanticFields())//semIR_HTMLFieldsByXMLTags.keySet())
						{
							getDocument(filename).addFieldOffset(xmlField, obir.ir.SearchFiles.computeHTMLSymptomOffset(obir.ir.SearchFiles.transformIntoStringWithCorrectPositions(obir.misc.XMLToHTML.fromXMLToHTML(new java.io.File(filepath),getDocument(filename).getLanguage())),getSemanticHTMLTag(xmlField,getDocument(filename).getLanguage())));
						}
					}
					catch (TransformerException te){te.printStackTrace();}
				}
			}
		}

		System.err.println("Deleting incorrect entries...");

		ObirProject.getOWLModel().setDispatchEventsEnabled(false) ;

		for (OWLIndividual doc:missingDocs)
		{
			System.out.println("file infos to delete: "+(String)doc.getPropertyValue(ObirProject.getOWLModel().getOWLDatatypeProperty(OTR.DOC_NAME)));
			for (OWLIndividual inst:(Collection<OWLIndividual>)doc.getPropertyValues(doc.getOWLModel().getOWLObjectProperty(OTR.DOC_HAS_CONCEPT_INSTS)))
				inst.delete();
			for (OWLIndividual occ:(Collection<OWLIndividual>)doc.getPropertyValues(doc.getOWLModel().getOWLObjectProperty(OTR.DOC_HAS_TERM_OCCS)))
				occ.delete();
			doc.delete();
		}

		ObirProject.getOWLModel().setDispatchEventsEnabled(true) ;
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				ObirProject.getOWLModel().flushEvents();
			}
		});
	}
	
	/**
	 * Method that forces validation of a document annotation
	 * @param docAnnot
	 */
	public void validateDocument(DocumentAnnotation docAnnot)
	{
		if (docAnnot!=null)
		{
			docAnnot.validate();
		}
	}

	/**
	 * Method that forces the un-validation of a given document annotation
	 * @param docAnnot
	 */
	public void unvalidateDocument(DocumentAnnotation docAnnot)
	{
		if (docAnnot!=null)
		{
			boolean necessary = false;
			if (docAnnot.isValidated())
			{
				necessary = true;
				docAnnot.unvalidate();
			}
			
			docAnnot.deleteAllAnnotations();
			
		}
	}

	/**
	 * From a list of authorized ranges, computes the documents containing any concept instance whose type is an incorrect range for a given relation, on a given domain
	 * @param origin the concept, subdomain of the given relation
	 * @param relation an object property modelled in the OTR
	 * @param authorizedRanges the ranges which must be considered as correct for the relation on the subdomain defined by the origin.
	 * @return the list of all documents containing unauthorized ranges for the relation.
	 */
	@SuppressWarnings("unchecked")
	public HashSet<DocumentAnnotation> computeIllIndexedDocs(OWLNamedClass origin,OWLObjectProperty relation, Set<OWLNamedClass> authorizedRanges)
	{
		HashSet<DocumentAnnotation> docsToReindex = new HashSet<DocumentAnnotation>();

		HashSet<OWLIndividual> incorrectInstances = new HashSet<OWLIndividual>();
		for (OWLIndividual originInstance:(Collection<OWLIndividual>)origin.getInstances(true))
		{
			if (originInstance.hasPropertyValue(relation))
			{
				for (OWLIndividual destinationInstance:(Collection<OWLIndividual>)originInstance.getPropertyValues(relation))
				{
					Collection<OWLNamedClass> parents = destinationInstance.getRDFType().getNamedSuperclasses(true);
					parents.add((OWLNamedClass)destinationInstance.getRDFType());
					parents.retainAll(authorizedRanges);
					if (parents.isEmpty())
					{
						incorrectInstances.add(originInstance);
						break;
					}
				}
			}
		}

		for (DocumentAnnotation validDoc:getValidatedDocuments())
		{
			HashSet<OWLIndividual> docInsts = new HashSet<OWLIndividual>(); 
			//				for (OWLIndividual i:ObirProject.getCorpus().getConceptInstances(validDoc))
			for (OWLIndividual i:validDoc.getConceptInstances())
				docInsts.add(i);
			docInsts.retainAll(incorrectInstances);
			if (!docInsts.isEmpty())
			{
				docsToReindex.add(validDoc);
			}
		}

		return docsToReindex;
	}

	/**
	 * Removes all documents from the corpus
	 */
	@SuppressWarnings("unchecked")
	public void clear()
	{
		for (RDFResource inst:(Collection<RDFResource>)ObirProject.getOWLModel().getOWLNamedClass(OTR.DOCUMENT).getInstances(false))
			inst.delete();
		super.clear();
	}
	
	/**
	 * Returns the absolute path of the corpus directory
	 * @return
	 */
	public static String getDirectoryPath()
	{
		return corpusDirectory.getAbsolutePath();
	}
	
	/**
	 * Returns the corpus directory as a file object
	 * @return
	 */
	public File getDirectory(){
		return corpusDirectory;
	}
	/**
	 * Sets the corpus directory to the given parameter
	 * @param f the corpus directory
	 */
	public void setCorpusDirectory(File f)
	{
		indexedOnce = false;
		unvalidatedDocsDuringSession = new HashSet<String>();
		corpusDirectory = f;
	}
	/**
	 * Returns the semantic fields for the documents in this corpus (specified in plugin.properties)
	 * @return
	 */
	public static HashSet<String> getSemanticFields()
	{
		HashSet<String> result = new HashSet<String>();
		for (String s:semIR_HTMLFieldsByXMLTags.keySet())
			result.add(s);
		return result;
	}
	/**
	 * Returns the classic fields for the documents in this corpus (specified in plugin.properties)
	 * @return
	 */
	public static HashSet<String> getClassicFields()
	{
		HashSet<String> result = new HashSet<String>();
		for (String s:classicIR_HTMLFieldsByXMLTags.keySet())
			result.add(s);
		return result;
	}

	public String getSemanticHTMLTag(String xmlTag, String lang)
	{
		if (semIR_HTMLFieldsByXMLTags.get(xmlTag)!=null)
			return semIR_HTMLFieldsByXMLTags.get(xmlTag).get(lang);
		else return null;
	}

	public boolean indexedOnce()
	{
		return indexedOnce;
	}

	public void warnedIndexedOnce()
	{
		indexedOnce = true;
	}
	
	/**
	 * returns the language of a given filename
	 * @param filename
	 * @return
	 */
	public String getDocumentLanguage(String filename)
	{
		String lang;
		if (containsDocument(filename))
			lang = getDocument(filename).getLanguage();
		else
			lang = computeExplicitFileLanguage(filename);

		if ((lang == null)||lang.isEmpty()) //no language explicitly mentioned => return default one
			lang = ObirProject.getDefaultLanguage();
		return lang;
	}

	/**
	 * Static method to get the language explicitly mentioned in the document root. WARNING: may be null
	 * @param filename a given file name
	 * @return the associated language if mentioned, null otherwise
	 */
	public static String computeExplicitFileLanguage(String filename)
	{
		return computeExplicitFileLanguage("", filename);
	}
	/**
	 * Attempts to find the language of an xml document
	 * @param subDir
	 * @param filename
	 * @return
	 */
	public static String computeExplicitFileLanguage(String subDir,String filename)
	{
		File file;
		if (filename.endsWith(".xml"))
		{
			if (!filename.endsWith("query.xml"))
			{
				if (subDir.isEmpty())
					file = new File(corpusDirectory,filename);
				else
					file = new File(new File(corpusDirectory,subDir),filename);
			}
			else
				file = new File(OTR.getTemporaryDirectory(),filename);

			String language = null;
			DocumentBuilderFactory xmlFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder xmlBuilder;
			try 
			{
				xmlBuilder = xmlFactory.newDocumentBuilder();
				org.w3c.dom.Document xmlDoc = xmlBuilder.parse(file);
				language = xmlDoc.getDocumentElement().getAttribute("lang");
			} 
			catch (ParserConfigurationException e) {e.printStackTrace();}
			catch (IOException e) {e.printStackTrace();}
			catch (SAXException e) {e.printStackTrace();}

			return language;
		}
		return null;
	}

	public void notifyDocumentInvalidation(String filename)
	{
		unvalidatedDocsDuringSession.add(filename);
	}

	public HashSet<String> getUnvalidatedDocsDuringSession()
	{
		return unvalidatedDocsDuringSession;
	}

	/**
	 * Language getter for the corpus
	 * @return the language of the indexed corpus
	 */
	public String getDefaultLanguage() {
		return ObirProject.getDefaultLanguage();
	}
}
