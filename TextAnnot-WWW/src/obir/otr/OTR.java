package obir.otr;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

import obir.ir.DocumentAnnotation;

import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import edu.stanford.smi.protegex.owl.model.OWLIndividual;
import edu.stanford.smi.protegex.owl.model.OWLModel;
import edu.stanford.smi.protegex.owl.model.OWLNamedClass;
import edu.stanford.smi.protegex.owl.model.OWLObjectProperty;
import edu.stanford.smi.protegex.owl.model.OWLUnionClass;
import edu.stanford.smi.protegex.owl.model.ProtegeNames;
import edu.stanford.smi.protegex.owl.model.RDFProperty;
import edu.stanford.smi.protegex.owl.model.RDFResource;
import edu.stanford.smi.protegex.owl.model.RDFSClass;
import edu.stanford.smi.protegex.owl.model.RDFSLiteral;
import edu.stanford.smi.protegex.owl.model.RDFSNamedClass;
import edu.stanford.smi.protegex.owl.model.event.ClassAdapter;
import edu.stanford.smi.protegex.owl.util.OWLBrowserSlotPattern;

/**
 * Represents an Ontological and Terminological Resource.
 * @author Axel Reymonet  
 */
public class OTR {//implements PropertyChangeListener {

	/**
	 * The current model
	 */
	protected OWLModel onto;

	/**
	 * Indicates whether the OTR is currently in a savable mode (for auto/manual saving methods)
	 */
	private boolean savable;


	private boolean saveInProgress;

	/**
	 * Absolute pathname where the OTR is stored (on the HDD)
	 */
	private String otrPath;


	/**
	 * Subdirectory of the base working directory, in which the queries from a particular OTR
	 * and the Lucene index of a particular corpus will be stored
	 */
	protected static String current_dir;

	private MyClassListener elementListener;

	/**
	 * Stores the concept corresponding to a given label (useful when the browser text is different from the local name)
	 */
	private HashMap<String,OWLNamedClass> conceptByLabel; 

	public final static String contextID = "contexte_d_apparition";
	public final static String problemID = "problème";
	public final static String serviceID = "prestation";
	public final static String componentID = "composant";
	public final static String controlComponentID = "composant_de_contrôle";
	public final static String participatesToID = "participeA";
	public final static String controlsID = "contrôle";
	public final static String denotesID = "dénote";
	public final static String designatesID = "désigne";
	public final static String affectsServiceID = "affectePrestation";
	public final static String hasContextID = "aContexte";	
	public final static String UNOBSERVED_SYMPTOM = "unobserved_symptom";
	public final static String OBSERVED_SYMPTOM = "observed_symptom";
	public final static String SYMPTOME = "symptom";
	public final static String SYMPTOM_XML_FIELD = "symptoms";
	public final static String DEFINED_BY_PROBLEM = "definedByProblem";
	public final static String DEFINED_BY_SERVICE = "definedByService";

	public final static String DOCUMENT = "Document";
	public final static String DOC_NAME = "filename";
	public final static String DOC_VALIDATION = "isValidated";
	public final static String DOC_HAS_TERM_OCCS = "hasTermOccurrence";
	public final static String DOC_HAS_CONCEPT_INSTS = "hasConceptInstance";
	public final static String DOC_HAS_COVERAGE = "hasCoverage";
	public final static String DOC_WORDS = "spotted_words";	
	public final static String DOC_ID = "document_id";
	public final static String DOC_FIELD = "field_id";

	public final static String TERM_SPAN = "term_span";
	public final static String TEXTE = "texte";	
	public final static String TERM = "Term";
	public final static String TERM_OCCURRENCE = "TermOccurrence";
	public final static String TERM_OFFSET = "term_offset";
	
	public final static String FIELD = "Field";
	public final static String FIELD_ANNOTATION = "FieldAnnotation";
	public final static String FIELD_NAME = "fieldName";
	public final static String hasFieldAnnotation = "hasFieldAnnotation";
	public final static String fieldCoverage = "fieldCoverage";

	public final static String DOMAIN_THING = "DomainThing";
	public final static String CONCEPT = "Concept";
	public final static String LANGUAGE = "language";
	public final static String LOWER_THAN = "lowerThan";
	public final static String CATEGORY_CONCEPT = "CategoryConcept";
	public final static String COMPARABLE_CONCEPT = "ComparableConcept";
	//	public final static String FILS_COMPARABLES = "comparableChildren";
	public final static float minimumCoverage = new Float(0.5);

	public final static String ARTAL_DEFAULT = "Default";
	public final static String ARTAL_FUNCTION = "Function";
	public final static String ARTAL_COMPONENT = "Component";
	public final static String ARTAL_AFFECTS_COMPONENT = "affects_Component";
	public final static String ARTAL_CONCERNS_COMPONENT = "concerns_Component";
	public final static String ARTAL_CONCERNS_DEFAULT = "concerns_Default";
	public final static String ARTAL_CONCERNS_FUNCTION = "concerns_Function";
	public final static String ARTAL_CAUSES = "causes";
	public final static String ARTAL_TRIGGER_EVENT = "Trigger_Event";
	//=========================================================================================================



	public OTR(File ontoFile, String pluginsPath)
	{

		otrPath = ontoFile.getAbsolutePath();

		this.onto = ObirProject.getOWLModel();
		
		String tmpDirPath=pluginsPath.replace("/plugins/proto/", "/tmp/");
		this.initialize(tmpDirPath);

	}
	
	/**
	 * Convenience method for the initialisation of the RTO
	 * @param obir_working_dir
	 */
	@SuppressWarnings("unchecked")
	public void initialize(String obir_working_dir)
	{
		ObirProject.restartIndividualNumbering();

		savable = true;
		saveInProgress = false;

		String fs = System.getProperty("file.separator");
		current_dir = obir_working_dir + "default" + fs;

		elementListener = new MyClassListener();

		File index_dir = new File(getTemporaryDirectory()+"index");
		if (index_dir.exists())
		{
			for (String f:index_dir.list())
			{
				File file = new File (index_dir,f);
				if (file.isDirectory())
				{
					for (String sf:file.list())
						(new File(file,sf)).delete();
				}
				file.delete();
			}
		}
		else
			index_dir.mkdir();

		for (String lang:ObirProject.getIndexingProcessor().getAvailableLanguages())
		{
			ObirProject.getColorManager().initColors(getLinguisticTermRoot(lang));
			getLinguisticTermRoot(lang).addClassListener(elementListener);	

			try 
			{
				IndexWriter	writer = null;
				Directory dir = FSDirectory.open(new File(index_dir,lang));
				if (IndexWriter.isLocked(dir))
				{
					writer = ObirProject.getIndexingProcessor().getIndexWriter(lang);
					writer.deleteAll();
					writer.commit();
				}
				else
				{
					writer = new IndexWriter(dir,ObirProject.getIndexingProcessor().getAnalyzer(lang),true,IndexWriter.MaxFieldLength.LIMITED);
					ObirProject.getIndexingProcessor().addIndexWriter(lang, writer);
				}

			} 
			catch (IOException e) {e.printStackTrace();}

		}


		onto.getOWLNamedClass(CONCEPT).addClassListener(elementListener);
		//		PropValListener propValList = new PropValListener();
		//		this.onto.addPropertyValueListener(propValList);


		RDFProperty defaultLanguage = onto.getRDFProperty(ProtegeNames.getDefaultLanguageSlotName());
		if (defaultLanguage == null)
			defaultLanguage = onto.createRDFProperty(ProtegeNames.getDefaultLanguageSlotName());
		onto.getDefaultOWLOntology().setPropertyValue(defaultLanguage, ObirProject.getDefaultLanguage());


		RDFProperty labelProp = onto.getRDFSLabelProperty();
		OWLBrowserSlotPattern rdfsLabelBrowserPattern = new OWLBrowserSlotPattern(labelProp);
		onto.getOWLNamedClass(CONCEPT).setDirectBrowserSlotPattern(rdfsLabelBrowserPattern);
		//		onto.getRDFPropertyClass().setDirectBrowserSlotPattern(rdfsLabelBrowserPattern);
		onto.getOWLObjectPropertyClass().setDirectBrowserSlotPattern(rdfsLabelBrowserPattern);
		onto.getOWLDatatypePropertyClass().setDirectBrowserSlotPattern(rdfsLabelBrowserPattern);
		conceptByLabel = new HashMap<String, OWLNamedClass>();
		for (OWLNamedClass cpt:(Collection<OWLNamedClass>)onto.getOWLNamedClass(CONCEPT).getInstances(true))
		{
			String key = translateLabel(cpt.getBrowserText());
			conceptByLabel.put(key, cpt);//.replaceAll("'",""), cpt);
			for (Object label:(Collection<Object>)cpt.getLabels())
			{
				String lbl = null;
				if (label instanceof RDFSLiteral)
					lbl = translateLabel(((RDFSLiteral)label).getString());
				else if (label instanceof String)
					lbl = translateLabel((String)label);
				if (lbl!=null)
					conceptByLabel.put(lbl, cpt);
			}
		}
	}

	/**
	 * In case the displayed concept label does not correspond to the concept ID, this method allows to retrieve the concept.
	 * @param label the displayed label
	 * @return the corresponding concept
	 */
	public OWLNamedClass getConceptFromLabel(String label) {
		String correctLabel = translateLabel(label);
		OWLNamedClass result = conceptByLabel.get(correctLabel);
		if (result==null)
			result = onto.getOWLNamedClass(correctLabel);
		return result;
	}

	/**
	 * Static method to transform a string for it to be acceptable as a term/concept ID. 
	 * @param s the initial string
	 * @return the appropriate string
	 */
	public static String translateLabel(String s)
	{
		String result = s;
		if (result.startsWith("'")&&result.endsWith("'"))
			result = result.substring(1, result.length()-1);
		return result;
	}

	/**
	 * Gets all existing labels for concepts
	 * @return the concept labels for the current OTR
	 */
	public Set<String> getExistingConceptLabels()
	{
		return conceptByLabel.keySet();
	}

	/**
	 * Removes all labels associated to a given concept (because of a concept deletion)
	 * @param concept a concept
	 */
	@SuppressWarnings("unchecked")
	public void removeLabels(OWLNamedClass concept)
	{
		conceptByLabel.remove(obir.otr.OTR.translateLabel(concept.getBrowserText()));//.replaceAll("'",""), concept);
		for (Object label:(Collection<Object>)concept.getLabels())
			if (label instanceof RDFSLiteral)
				conceptByLabel.remove(obir.otr.OTR.translateLabel(((RDFSLiteral)label).getString()));
			else if (label instanceof String)
				conceptByLabel.remove(obir.otr.OTR.translateLabel((String)label));
		//		conceptByLabel.remove(obir.otr.OTR.translateLabel(label));
	}

	/**
	 * Removes the label associated to a given concept in a language
	 * @param concept a concept
	 * @param language a given language
	 */
	@SuppressWarnings("unchecked")
	public void removeLabel(OWLNamedClass concept,String language)
	{
		for (Object labelObj:(Collection<Object>)concept.getLabels())
		{
			if ((labelObj instanceof RDFSLiteral)&&(((RDFSLiteral)labelObj).getLanguage().equals(language)))
			{
				concept.removePropertyValue(onto.getRDFSLabelProperty(),labelObj);
				conceptByLabel.remove(obir.otr.OTR.translateLabel(((RDFSLiteral)labelObj).getString()));
				break;
			}

		}

	}

	@SuppressWarnings("unchecked")
	public static boolean labelExists(OWLNamedClass concept,String language)
	{
		for (Object labelObj:(Collection<Object>)concept.getLabels())
		{
			if ((labelObj instanceof RDFSLiteral)&&(((RDFSLiteral)labelObj).getLanguage().equals(language)))
				return true;
		}
		return false;
	}

	/**
	 * Adds a label to a concept in the OTR
	 * @param concept the concept
	 * @param label the new label
	 */
	//	@SuppressWarnings("unchecked")
	public void setLabel(OWLNamedClass concept,String label)
	{
		String currentLanguage = ObirProject.getDefaultLanguage();

		setLabel(concept, label, currentLanguage);
	}

	public void setLabel(OWLNamedClass concept,String label, String language)
	{
		removeLabel(concept, language);
		concept.addLabel(label, language);
		conceptByLabel.put(label, concept);
	}

	/**
	 * Orders all given term occurrences according to the instance (of a given list) they designate
	 * @param termOccs the given collection of term occurrences
	 * @param cptInsts the given collection of concept instances
	 * @return the term occurrences classified according to the concept instance they designate
	 */
	public static HashMap<OWLIndividual,HashSet<OWLIndividual>> getTermOccsByCptInstMap(Collection<OWLIndividual> termOccs,Collection<OWLIndividual> cptInsts)
	{
		HashMap<OWLIndividual,HashSet<OWLIndividual>> assocTermOccsByCptInst = new HashMap<OWLIndividual, HashSet<OWLIndividual>>();
		for (OWLIndividual termOcc:termOccs)
		{

			if (termOcc.hasPropertyValue(termOcc.getOWLModel().getOWLObjectProperty(designatesID)))
			{
				OWLIndividual cptInst = (OWLIndividual)termOcc.getPropertyValue(termOcc.getOWLModel().getOWLObjectProperty(designatesID));
				if (cptInsts.contains(cptInst))
				{
					HashSet<OWLIndividual> list = new HashSet<OWLIndividual>();
					if (assocTermOccsByCptInst.containsKey(cptInst))
						list = assocTermOccsByCptInst.get(cptInst);
					list.add(termOcc);
					assocTermOccsByCptInst.put(cptInst,list);
				}
			}

		}

		for (OWLIndividual cptInst:cptInsts)
		{
			if (!assocTermOccsByCptInst.containsKey(cptInst))
				assocTermOccsByCptInst.put(cptInst,new HashSet<OWLIndividual>());
		}

		return assocTermOccsByCptInst;
	}


	/**
	 * Gets all terms denoting a given concept. This method must be used with caution as it implies to browse through all terms to check them.
	 * @param concept an input concept
	 * @return all associated terms (in all different languages)
	 */
	@SuppressWarnings("unchecked")
	public static HashSet<OWLNamedClass> getAssociatedTerms(OWLNamedClass concept) 
	{
		HashSet<OWLNamedClass> terms = new  HashSet<OWLNamedClass>();
		for (OWLNamedClass potentialTerm:(Collection<OWLNamedClass>)concept.getOWLModel().getOWLNamedClass(TERM).getInstances(true))
		{
			if (((Collection<OWLNamedClass>)potentialTerm.getPropertyValues(concept.getOWLModel().getOWLObjectProperty(denotesID))).contains(concept))
				terms.add(potentialTerm);
		}
		return terms;
	}

	/**
	 * Gets all terms denoting a given concept. This method must be used with caution as it implies to browse through all terms to check them.
	 * @param concept an input concept
	 * @param language a given language
	 * @return all associated terms
	 */
	@SuppressWarnings("unchecked")
	public static HashSet<OWLNamedClass> getAssociatedTerms(final OWLNamedClass concept,String language) 
	{
		HashSet<OWLNamedClass> terms = new  HashSet<OWLNamedClass> ();
		
		OWLNamedClass topTermRoot=null;
		OWLNamedClass topTerm = concept.getOWLModel().getOWLNamedClass(OTR.TERM);
		ArrayList<OWLNamedClass> lingRoots = (ArrayList<OWLNamedClass>)topTerm.getNamedSubclasses(false);
		for (OWLNamedClass lingRoot:lingRoots)
		{
			if (language.equals((String)lingRoot.getHasValue(concept.getOWLModel().getOWLDatatypeProperty(OTR.LANGUAGE))))
			{
				topTermRoot = lingRoot;
				break;
			}
		}
		if (topTermRoot!=null)
		{		
			for (OWLNamedClass potentialTerm:(Collection<OWLNamedClass>)topTermRoot.getInstances(true))
			{
				if (((Collection<OWLNamedClass>)potentialTerm.getPropertyValues(concept.getOWLModel().getOWLObjectProperty(denotesID))).contains(concept))
					terms.add(potentialTerm);
			}
		}
		
		return terms;
	}


	/**
	 * Gets all concepts denoted by a given term
	 * @param term an input term
	 * @return all associated concepts (ie the meaning(s) of the term)
	 */
	@SuppressWarnings("unchecked")
	public static Collection<OWLNamedClass> getAssociatedConcepts(OWLNamedClass term) 
	{
		if (term!=null)
			return ((Collection<OWLNamedClass>)term.getPropertyValues(term.getOWLModel().getOWLObjectProperty(denotesID)));
		else
			return null;
	}
	
	/**
	 * Gets all instances designated by a given term occurrence
	 * @param termOcc a term occurrence
	 * @return all associated instances
	 */
	@SuppressWarnings("unchecked")
	public static Collection<OWLIndividual> getAssociatedConceptInstances(OWLIndividual termOcc)
	{
		if (termOcc!=null)
		{
			return termOcc.getPropertyValues(termOcc.getOWLModel().getOWLObjectProperty(designatesID));
		}
		return null;
	}

	/**
	 * Gets the label of a given term
	 * @param term an input term
	 * @return the words associated to the term
	 */
	public static String getTermLabel(OWLNamedClass term)
	{
		return(String)term.getPropertyValue(term.getOWLModel().getOWLDatatypeProperty(TEXTE));
	}
	/**
	 * Gets the language of the given term
	 * @param term an input term
	 * @return the language of the given term
	 */
	public static String getTermLanguage(OWLNamedClass term)
	{
		return(String)term.getPropertyValue(term.getOWLModel().getOWLDatatypeProperty(LANGUAGE));
	}

	/**
	 * Gets all term IDs for the current OTR
	 * @return all term names for the appropriate language
	 */
	@SuppressWarnings("unchecked")
	public ArrayList<String> getAllTermNames()
	{
		ArrayList<String> terms = new ArrayList<String>();

		for (OWLNamedClass term:(Collection<OWLNamedClass>)onto.getOWLNamedClass(TERM).getInstances(true))
		{
			//			if (term.getPropertyValue(onto.getOWLDatatypeProperty(LANGUAGE)).equals(currentAnalyzer.getLanguage()))
			terms.add(term.getBrowserText());
		}

		return terms;
	}

	/**
	 * Gets all term IDs for the current OTR
	 * @param language a given lang label
	 * @return all term names for the appropriate language
	 */
	@SuppressWarnings("unchecked")
	public ArrayList<String> getAllTermNames(String language)
	{
		ArrayList<String> terms = new ArrayList<String>();

		for (OWLNamedClass term:(Collection<OWLNamedClass>)getLinguisticTermRoot(language).getInstances(true))
		{
			//			if (term.getPropertyValue(onto.getOWLDatatypeProperty(LANGUAGE)).equals(currentAnalyzer.getLanguage()))
			terms.add(term.getBrowserText());
		}

		return terms;
	}


	/**
	 * Gets all terms for the current OTR
	 * @param language a given language
	 * @return all terms for the appropriate language
	 */
	@SuppressWarnings("unchecked")
	public HashSet<OWLNamedClass> getAllTerms(String language)
	{
		HashSet<OWLNamedClass> terms = new HashSet<OWLNamedClass>();
		OWLNamedClass topTermRoot=null;
		OWLNamedClass topTerm = onto.getOWLNamedClass(OTR.TERM);
		ArrayList<OWLNamedClass> lingRoots = (ArrayList<OWLNamedClass>)topTerm.getNamedSubclasses(false);
		for (OWLNamedClass lingRoot:lingRoots)
		{
			if (language.equals((String)lingRoot.getHasValue(onto.getOWLDatatypeProperty(OTR.LANGUAGE))))
			{
				topTermRoot = lingRoot;
				break;
			}
		}
		if (topTermRoot!=null)
		{		
			for (OWLNamedClass term:(Collection<OWLNamedClass>)topTermRoot.getInstances(true))
			{
				//			if (term.getPropertyValue(onto.getOWLDatatypeProperty(LANGUAGE)).equals(currentAnalyzer.getLanguage()))
				terms.add(term);
			}
		}
		return terms;
	}


	/**
	 * Gets the range concepts for a relation possibly restricted on a domain concept 
	 * @param domain a concept belonging to the domain of the relation
	 * @param relation a given relation
	 * @return all range concepts associable to the domain concept through the given relation
	 */
	public static ArrayList<OWLNamedClass> getMostSpecificRestrictedRanges(OWLNamedClass domain,OWLObjectProperty relation)
	{
		ArrayList<OWLNamedClass> result = new ArrayList<OWLNamedClass>();
		RDFResource range=domain.getAllValuesFrom(relation);
		HashSet<OWLNamedClass> authorizedDomains = new HashSet<OWLNamedClass>();
		if (relation.getDomain(false) instanceof OWLNamedClass)
			authorizedDomains.add((OWLNamedClass)relation.getDomain(false));
		else if (relation.getDomain(false) instanceof OWLUnionClass)
		{
			for (RDFSClass rge:(ArrayList<RDFSClass>)((OWLUnionClass)relation.getDomain(false)).getOperands())
			{
				if (rge instanceof OWLNamedClass)
					authorizedDomains.add((OWLNamedClass)rge);
			}
		}

		if ((range.equals(relation.getRange()))&&(!authorizedDomains.contains(domain)))//(!domain.equals(relation.getDomain(false))))
		{
			OWLNamedClass superclass = (OWLNamedClass)domain.getNamedSuperclasses().iterator().next();
			while((range.equals(relation.getRange()))&&(!authorizedDomains.contains(superclass)))//(!superclass.equals(relation.getDomain(false))))
			{

				if (superclass.getNamedSuperclasses(true).contains(relation.getDomain(false)))
				{		
					range = superclass.getAllValuesFrom(relation);
				}
				superclass = (OWLNamedClass)superclass.getNamedSuperclasses().iterator().next();
			}
		}

		if (range instanceof OWLNamedClass)
			result.add((OWLNamedClass)range);
		else if (range instanceof OWLUnionClass)
		{
			for (RDFSClass rge:(ArrayList<RDFSClass>)((OWLUnionClass)range).getOperands())
			{
				if (rge instanceof OWLNamedClass)
					result.add((OWLNamedClass)rge);
			}
		}
		return result;
	}

	/**
	 * Creates a concept instance in the given document annotation and the given field
	 * @param docAnnot the input document
	 * @param field the field
	 * @param concept the concept to be instantiated
	 * @return the concept instance created
	 */
	public OWLIndividual createConceptInstance(DocumentAnnotation docAnnot, String field, OWLNamedClass concept)
	{
		return (createConceptInstance(docAnnot, field, concept, null));
	}

	/**
	 * Creates a concept instance for a given document.
	 * @param path the filepath of a document
	 * @param concept the type of the instance to create
	 * @param indsByFileField structure storing the different concept instances found in a file, as well as the the term occurrence (and its ending position in the field) which designates it 
	 * @param isTemp records the new instance as being temporary if needed
	 * @return the newly created concept instance
	 */
	private OWLIndividual createConceptInstance(DocumentAnnotation docAnnot, String field, OWLNamedClass concept, HashMap<String,HashMap<String,HashSet<String>>> indsByFileField)//, boolean isTemp)
	{

		//		String filename = path.substring(path.lastIndexOf("\\")+1, path.length());
		String filename = docAnnot.getDocumentName();

		String conceptIndName = ObirProject.generateNextIndName();
		OWLIndividual conceptInd = concept.createOWLIndividual(conceptIndName);

		docAnnot.addConceptInstance(conceptInd, field);
		//		if (isTemp)
		//			ObirProject.getIndexingProcessor().notifyNewTemporaryInstance(conceptInd);

		if (indsByFileField!=null)
		{
			HashMap<String,HashSet<String>> cptIndsByField = new HashMap<String, HashSet<String>>();
			if (indsByFileField.containsKey(filename))
				cptIndsByField = indsByFileField.get(filename);

			HashSet<String> tempSet =new  HashSet<String>();
			tempSet =new  HashSet<String>();
			if (cptIndsByField.containsKey(field))
				tempSet = cptIndsByField.get(field);
			tempSet.add(conceptIndName);
			cptIndsByField.put(field,tempSet);
			indsByFileField.put(filename,cptIndsByField);
		}
		return conceptInd;
	}


	/**
	 * Creates a term occurrence from a given entity
	 * @param entity a given entity
	 * @param linkedConceptInd a concept instance designated by the occurrence
	 * @return the appropriate term occurrence
	 */
	public OWLIndividual createTermOccurrence(Entity entity, OWLIndividual linkedConceptInd)
	{
		String name = ObirProject.generateNextIndName();
		OWLNamedClass entityClass = onto.getOWLNamedClass(entity.type);

		//		System.out.println("term occ creation!");		
		OWLIndividual termInd = entityClass.createOWLIndividual(name);

		//ObirProject.getCorpus().getDocument(entity.docURL).addTermOccurrence(termInd);
		ObirProject.getCorpus().getDocument(entity.docURL).addTermOccurrence(termInd,entity.field);
		
		termInd.setPropertyValue(onto.getOWLDatatypeProperty(obir.otr.OTR.DOC_ID), entity.docURL.substring(entity.docURL.lastIndexOf('\\')+1,entity.docURL.length()));
		termInd.setPropertyValue(onto.getOWLDatatypeProperty(obir.otr.OTR.DOC_FIELD), entity.field);
		termInd.setPropertyValue(onto.getOWLDatatypeProperty(obir.otr.OTR.TERM_OFFSET), entity.offset.toString());
		termInd.setPropertyValue(onto.getOWLDatatypeProperty(obir.otr.OTR.DOC_WORDS), entity.text.toString());

		if (linkedConceptInd!=null)
			termInd.setPropertyValue(onto.getOWLObjectProperty(designatesID), linkedConceptInd);

		entity.ind_name = name;

		//		System.out.println("term occ finished...");
		return termInd;
	}



	/**
	 * Convenience method. See {@link #createInstances(Entity[], boolean)} for more information.
	 */
	public HashMap<String,HashMap<String,HashMap<String,String[]>>> createInstances(HashSet<Entity> entities, boolean temporary) {
		ArrayList<Entity> entList = new ArrayList<Entity>();
		entList.addAll(entities);
		return createInstances(entList,temporary);
	}


	@SuppressWarnings("unchecked")
	/**
	 * Static method to test whether a given relation is optional
	 * @param rel a relation
	 * @return true IFF the relation has no minimum cardinality restriction on its definition domain
	 */
	public static boolean isRelationOptional(OWLObjectProperty rel)
	{
		boolean result = true;

		for (OWLNamedClass domain:(Collection<OWLNamedClass>)rel.getUnionDomain(true))
		{
			if (domain.getMinCardinality(rel)== 0)
				result = true;
			else
			{
				result = false;
				break; //FIXME: NOT TAKEN INTO ACCOUNT: situation with multiple domains and different restriction states
			}
		}

		return result;
	}

	/**
	 * Creates all adequate term occurrences and corresponding concept instances from a list of entities 
	 * @param tab a list of entities
	 * @param isTemporary used to record the new instances as being temporary (if {@code true})
	 * @return a structure storing the each concept instance which was created, as well as the term occurrence (and its ending position in the field) which designates it
	 */
	public HashMap<String,HashMap<String,HashMap<String,String[]>>> createInstances(ArrayList<Entity> entities, boolean isTemporary) {

		//		long start = System.currentTimeMillis(); 
		//		System.out.print("Creating instances...");

		HashMap<String,HashMap<String,HashMap<String,String[]>>> result = new HashMap<String,HashMap<String,HashMap<String,String[]>>>();
		ArrayList<Entity> termInstances = new ArrayList<Entity>();
		HashMap<String,String[]> conceptIndToTermInfo = new HashMap<String,String[]>();
		HashMap<String,HashMap<String,HashSet<String>>> conceptIndsByFileField = new HashMap<String,HashMap<String,HashSet<String>>>();

		//		if (isTemporary)
		//			ObirProject.getCorpus().addDocument(ObirProject.xmlQueryFile);

		int cpt=0;
		for (Entity entity:entities)
		{
			cpt++;

			String conceptIndName = "";
			String servIndName = "";

			String filename = entity.docURL.substring(entity.docURL.lastIndexOf('\\')+1,entity.docURL.length());
			//			obir.ir.Document currentDocument = null;

			DocumentAnnotation currentDocument = null;
			if (ObirProject.getCorpus().containsDocument(filename))
				currentDocument = ObirProject.getCorpus().getDocument(filename);
			else
			{
				//				currentDocument = new Document (entity.docURL,semIR_HTMLFieldsByXMLTags.keySet());
				//				corpus.add(currentDocument);
				currentDocument = ObirProject.getCorpus().addDocument(entity.docURL);
			}

			//	automatic creation of concept individual IFF there is no ambiguity with the term found
			Collection<OWLNamedClass> assocConcepts = getAssociatedConcepts(onto.getOWLNamedClass(entity.type));
			OWLIndividual conceptInd = null;
			if (assocConcepts.size()==1)
			{
				OWLNamedClass concept = ((OWLNamedClass)assocConcepts.iterator().next());
				conceptInd = this.createConceptInstance(currentDocument, entity.field, concept, conceptIndsByFileField);//, isTemporary);

				conceptIndName = conceptInd.getLocalName();

				if (conceptIndName.contains("#"))
					conceptIndName=conceptIndName.substring(conceptIndName.indexOf("#")+1);
			}

			OWLIndividual termOcc = this.createTermOccurrence(entity,conceptInd);

			String endWord = entity.text.get(entity.text.size()-1);
			String [] toAdd = new String[2];
			toAdd[0] = termOcc.getLocalName();
			toAdd[1] = Integer.toString(entity.offset.get(entity.offset.size()-1)+endWord.length());
			conceptIndToTermInfo.put(conceptIndName, toAdd);

			termInstances.add(entity);
		}


		for (String file:conceptIndsByFileField.keySet())
		{
			HashMap<String,HashMap<String,String[]>> infoByField = new HashMap<String, HashMap<String,String[]>>();
			for (String field:conceptIndsByFileField.get(file).keySet())
			{
				HashMap<String,String[]> tempMap = new HashMap<String,String[]>(); 
				for (String conceptInd:conceptIndsByFileField.get(file).get(field))
				{
					String name = conceptInd;
					if (name.contains("#"))
						name=name.substring(name.indexOf("#")+1);
					tempMap.put(name, conceptIndToTermInfo.get(name));
				}
				infoByField.put(field, tempMap);
			}
			result.put(file,infoByField);
		}

		//		System.out.println("\tdone in "+(System.currentTimeMillis() -start)  + "ms");
		return(result);
	}




	/**
	 * OTR getter
	 * @return the current OTR
	 */
	public OWLModel getOntology() {
		return onto;
	}

	/**
	 * Temporary directory getter
	 * @return the directory into which several pieces of information are stored (last document index, last query...)
	 */
	public static String getTemporaryDirectory() {
		return current_dir;
	}

	/**
	 * Linguistic root getter
	 * @return the appropriate term root according to the currently used language
	 */
	@SuppressWarnings("unchecked")
	public OWLNamedClass getDefaultLinguisticTermRoot()
	{
		OWLNamedClass topTerm = onto.getOWLNamedClass(obir.otr.OTR.TERM);
		OWLNamedClass correctLingTerm = null;
		for (OWLNamedClass lingTerm:(ArrayList<OWLNamedClass>)topTerm.getNamedSubclasses(false))
		{
			if (lingTerm.getHasValue(onto.getOWLDatatypeProperty(obir.otr.OTR.LANGUAGE)).equals(ObirProject.getDefaultLanguage()))
			{
				correctLingTerm = lingTerm;
				break;
			}
		}
		return correctLingTerm;
	}

	/**
	 * Linguistic root getter
	 * @param lang a given language
	 * @return the term root corresponding to the given language
	 */
	@SuppressWarnings("unchecked")
	public OWLNamedClass getLinguisticTermRoot(String lang)
	{
		OWLNamedClass topTerm = onto.getOWLNamedClass(obir.otr.OTR.TERM);
		OWLNamedClass correctLingTerm = null;
		for (OWLNamedClass lingTerm:(ArrayList<OWLNamedClass>)topTerm.getNamedSubclasses(false))
		{
			if (lingTerm.getHasValue(onto.getOWLDatatypeProperty(obir.otr.OTR.LANGUAGE)).equals(lang))
			{
				correctLingTerm = lingTerm;
				break;
			}
		}
		return correctLingTerm;
	}


	/**
	 * Gets all term occurrences designating a given concept instance. This method can be resource-intensive, so it must be used with caution.
	 * @param ind a concept instance
	 * @return the term occurrences linked to the instance
	 */
	@SuppressWarnings("unchecked")
	public static HashSet<OWLIndividual> getAssociatedTermOccurrences(OWLIndividual ind)
	{
		HashSet<OWLIndividual> result = new HashSet<OWLIndividual>();
		if (ind!=null)
		{
			OWLModel model = ind.getOWLModel();
			OWLObjectProperty designates = model.getOWLObjectProperty(designatesID);
			for (RDFResource anyInst:(Collection<RDFResource>)model.getOWLNamedClass(TERM_OCCURRENCE).getInstances(true))//onto.getOWLIndividuals())
			{
				if ((anyInst.hasPropertyValue(designates))&&(anyInst.getPropertyValue(designates).equals(ind)))
					result.add((OWLIndividual)anyInst);
			}
		}
		return result;
	}

	/**
	 * Gets all term occurrences designating a given set of concept instances. This method can be resource-intensive, so it must be used with caution.
	 * @param inds a set of concept instances
	 * @return the term occurrences for each instance
	 */
	@SuppressWarnings("unchecked")
	public static HashMap<OWLIndividual,HashSet<OWLIndividual>> getAssociatedTermOccurrences(Collection<OWLIndividual> inds)
	{
		HashMap<OWLIndividual,HashSet<OWLIndividual>> result = new HashMap<OWLIndividual, HashSet<OWLIndividual>>();

		if ((!inds.isEmpty()) && inds.iterator().next()!=null)
		{
			OWLModel model = inds.iterator().next().getOWLModel();
			OWLObjectProperty designates = model.getOWLObjectProperty(designatesID);
			for (RDFResource anyInst:(Collection<RDFResource>)model.getOWLNamedClass(TERM_OCCURRENCE).getInstances(true))//onto.getOWLIndividuals())
			{
				if (anyInst.hasPropertyValue(designates))
				{
					for (OWLIndividual cptInd :(Collection<OWLIndividual>)anyInst.getPropertyValues(designates))
						if (inds.contains(cptInd))
						{
							HashSet<OWLIndividual> tempSet = new HashSet<OWLIndividual>();
							if (result.containsKey(cptInd))
								tempSet = result.get(cptInd);
							tempSet.add((OWLIndividual)anyInst);
							result.put(cptInd,tempSet);
						}
				}
			}
		}
		return result;
	}


	/**
	 * Checks whether a saving process is possible
	 * @return {@code true} iff no process forbids the saving
	 */
	public boolean isAutoSaveInProgress() {
		return saveInProgress;
	}

	public void setAutoSaveInProgress(boolean isLaunched) {
		saveInProgress = isLaunched;
	}

	/**
	 * Warns the system that the saving process is possible/impossible
	 * @param savePossible the value of the boolean
	 */
	public void setAutoSavePossible(boolean savePossible) {
		this.savable = savePossible;
	}

	public boolean isAutoSavePossible()
	{
		return savable;
	}

	/**
	 * OTR path getter
	 * @return the absolute path where the OTR is stored
	 */
	public String getPath() {
		return otrPath;
	}

	/**
	 * Translates a string into a close one which is acceptable for a class ID. 
	 * @param name the string to transform
	 * @return the new appropriate string
	 */
	public static String translateName(String name)
	{
		String result = name;
		result = result.replaceAll(" +", " ");
		result = result.replaceAll("(\'| |,|;|\\.|:|!|\\?)", "_");
		result = result.replaceAll("_+", "_");

		return result;
	}
	/**
	 * Returns the field of a given Term occurrence
	 * @param termOcc the term occurrence
	 * @return the name of the field where the term occurrence is found
	 */
	public static String getTermOccurrenceField(OWLIndividual termOcc)
	{
		return ((String)termOcc.getPropertyValue(termOcc.getOWLModel().getOWLDatatypeProperty(OTR.DOC_FIELD)));
	}
	/**
	 * Returns the words corresponding to the term occurrence
	 * @param termOcc an input term occurrence
	 * @return an array of strings of the words of the term occurrence
	 */
	public static ArrayList<String> getTermOccurrenceWords(OWLIndividual termOcc)
	{
		ArrayList<String> text = new ArrayList<String>();
		String alltext = (String) termOcc.getPropertyValue(termOcc.getOWLModel().getOWLDatatypeProperty(obir.otr.OTR.DOC_WORDS)); 
		alltext = alltext.substring(1, alltext.length()-1);
		Scanner scan = new Scanner(alltext);
		scan.useDelimiter(", ");
		while (scan.hasNext())
			text.add(scan.next());
		return text;
	}
	/**
	 * Returns the offset of a term occurrence
	 * @param termOcc the input term occurrence
	 * @return a list of offsets for the term occurrence
	 */
	public static ArrayList<Integer> getTermOccurrenceOffset(OWLIndividual termOcc)
	{
		ArrayList<Integer> offset = new ArrayList<Integer>();
		String allInts = (String) termOcc.getPropertyValue(termOcc.getOWLModel().getOWLDatatypeProperty(obir.otr.OTR.TERM_OFFSET));
		allInts = allInts.substring(1, allInts.length()-1);
		Scanner scan = new Scanner(allInts);
		scan.useDelimiter(", ");
		while (scan.hasNext())
			offset.add(new Integer(scan.next()));
		return (offset);
	}
	/**
	 * Checks whether in the ontology exists already an element with the same name
	 * @param id the name of the element
	 * @param otr the OTR
	 * @return true if the element is in the OTR
	 */
	@SuppressWarnings("unchecked")
	public static boolean checkPriorExistence(String id, OTR otr)
	{
		for (RDFSNamedClass c:(Collection<RDFSNamedClass>)otr.getOntology().getOWLThingClass().getNamedSubclasses(true))
		{
			if (c instanceof OWLNamedClass)
			{
				if (c.getRDFType().equals(otr.getOntology().getOWLNamedClass(CONCEPT)))
				{
					if ((otr.getExistingConceptLabels().contains(id))||c.getLocalName().equals(obir.otr.OTR.translateName(id)))
						return true;
				}
			}
		}

		return false;
	}

	/**
	 * Checks whether there are more labels in the OTR
	 * @param label the input label
	 * @param otr the OTR
	 * @return true if this label has already been used
	 */
	@SuppressWarnings("unchecked")
	public static boolean checkLabelPriorExistence(String label, OTR otr)
	{
		for (RDFSNamedClass c:(Collection<RDFSNamedClass>)otr.getOntology().getOWLThingClass().getNamedSubclasses(true))
		{
			if (c instanceof OWLNamedClass && c.getRDFType().equals(otr.getOntology().getOWLNamedClass(CONCEPT)) && otr.getExistingConceptLabels().contains(label))
				return true;
		}

		return false;
	}

	/**
	 * Method that returns from a given domain class, the list of possible pairs <relation, target> according to the OTR
	 * @param origin the domain of the relation
	 * @return a map relation -> ranges for the given domain
	 */
	@SuppressWarnings("unchecked")
	public static HashMap<OWLObjectProperty,HashSet<OWLNamedClass>> getPossibleDestinations(OWLNamedClass origin)
	{
		HashMap<OWLObjectProperty,HashSet<OWLNamedClass>> result = new HashMap<OWLObjectProperty, HashSet<OWLNamedClass>>();

		//		OWLModel model = origin.getOWLModel();
		for (RDFProperty prop : (Collection<RDFProperty>)origin.getUnionDomainProperties(true))//model.getOWLObjectPropertyClass().getInstances(true))
		{
			if (ObirProject.isOTRProperty(prop) &&
					(origin.equals(prop.getDomain(false)) || origin.getNamedSuperclasses(true).contains(prop.getDomain(false))))
			{
				HashSet<OWLNamedClass> ranges = new HashSet<OWLNamedClass>();
				for (OWLNamedClass range:OTR.getMostSpecificRestrictedRanges(origin,(OWLObjectProperty)prop))
					ranges.add(range);
				result.put((OWLObjectProperty)prop,ranges);
			}
		}
		return result;
	}
	
	@SuppressWarnings("unchecked")
	public static HashMap<OWLObjectProperty,HashSet<OWLNamedClass>> getPossibleOrigins(OWLNamedClass destination)
	{
		HashMap<OWLObjectProperty,HashSet<OWLNamedClass>> result = new HashMap<OWLObjectProperty, HashSet<OWLNamedClass>>();

		OWLModel model = destination.getOWLModel();
		for (RDFProperty prop : (Collection<RDFProperty>)model.getOWLObjectPropertyClass().getInstances(true))
		{
			if (  ObirProject.isDomainProperty(prop) )   
			{
				HashSet<OWLNamedClass> compatibleOrigins = new HashSet<OWLNamedClass>();
				HashSet<OWLNamedClass> subTreeRootOrigins = new HashSet<OWLNamedClass>();
				RDFSClass origin = prop.getDomain(false);
				if (origin instanceof OWLUnionClass)
					for (RDFSClass restriction:(Collection<RDFSClass>)((OWLUnionClass)origin).getOperands())
					{
						subTreeRootOrigins.add((OWLNamedClass)restriction);
					}
				else
					subTreeRootOrigins.add((OWLNamedClass)origin);

				for (OWLNamedClass rootOrig:subTreeRootOrigins)
				{
					HashSet<OWLNamedClass> ranges = new HashSet<OWLNamedClass>();
					ArrayList<OWLNamedClass> destinationBranch = (ArrayList<OWLNamedClass>)destination.getNamedSuperclasses(true);
					destinationBranch.add(destination);

					ArrayList<OWLNamedClass> subTreeClasses = (ArrayList<OWLNamedClass>)rootOrig.getNamedSubclasses(true);
					subTreeClasses.add(rootOrig);

					for (OWLNamedClass concept:subTreeClasses)
					{
						ranges = new HashSet<OWLNamedClass>();
						for (OWLNamedClass range:OTR.getMostSpecificRestrictedRanges(concept,(OWLObjectProperty) prop))
							ranges.add(range);
						ranges.retainAll(destinationBranch);
						if (!ranges.isEmpty())
							compatibleOrigins.add(concept);
					}
				}
				if (!compatibleOrigins.isEmpty())
					result.put((OWLObjectProperty)prop, compatibleOrigins);
			}
		}
		return result;
	}

	@SuppressWarnings("unchecked")
	public static HashMap<OWLObjectProperty,HashSet<OWLNamedClass>> getImpossibleOrigins(OWLNamedClass destination)
	{
		HashMap<OWLObjectProperty,HashSet<OWLNamedClass>> result = new HashMap<OWLObjectProperty, HashSet<OWLNamedClass>>();

		OWLModel model = destination.getOWLModel();
		for (RDFProperty prop : (Collection<RDFProperty>)model.getOWLObjectPropertyClass().getInstances(true))
		{
			if ( ObirProject.isDomainProperty(prop) )
			{
				HashSet<OWLNamedClass> incompatibleOrigins = new HashSet<OWLNamedClass>();
				HashSet<OWLNamedClass> subTreeRootOrigins = new HashSet<OWLNamedClass>();
				RDFSClass origin = prop.getDomain(false);
				if (origin instanceof OWLUnionClass)
					for (RDFSClass restriction:(Collection<RDFSClass>)((OWLUnionClass)origin).getOperands())
					{
						subTreeRootOrigins.add((OWLNamedClass)restriction);
					}
				else
					subTreeRootOrigins.add((OWLNamedClass)origin);

				for (OWLNamedClass rootOrig:subTreeRootOrigins)
				{
					ArrayList<OWLNamedClass> defRanges = OTR.getMostSpecificRestrictedRanges(rootOrig,(OWLObjectProperty)prop);
					HashSet<OWLNamedClass> ranges = new HashSet<OWLNamedClass>();
					ArrayList<OWLNamedClass> destinationBranch = (ArrayList<OWLNamedClass>)destination.getNamedSuperclasses(true);
					destinationBranch.add(destination);

					ArrayList<OWLNamedClass> subTreeClasses = (ArrayList<OWLNamedClass>)rootOrig.getNamedSubclasses(true);
					subTreeClasses.add(rootOrig);

					for (OWLNamedClass concept:subTreeClasses)
					{
						ranges = new HashSet<OWLNamedClass>();
						for (OWLNamedClass range:OTR.getMostSpecificRestrictedRanges(concept,(OWLObjectProperty) prop))
							ranges.add(range);
						ranges.retainAll(destinationBranch);

						ArrayList<OWLNamedClass> intersect = (ArrayList<OWLNamedClass>)defRanges.clone();
						intersect.retainAll(destination.getNamedSuperclasses(true));

						if (!intersect.isEmpty()&&ranges.isEmpty())
							incompatibleOrigins.add(concept);
					}
				}
				if (!incompatibleOrigins.isEmpty())
					result.put((OWLObjectProperty)prop, incompatibleOrigins);
			}
		}
		return result;
	}

	public void pauseElementsListener(boolean pause)
	{
		if (pause)
			elementListener.pause();
		else
			elementListener.resume();
	}




	/**
	 * Internal class to monitor when a term is added or deleted.
	 * @author Axel Reymonet
	 */
	class MyClassListener extends ClassAdapter
	{
		boolean paused = false;
		/**
		 * Overriding method to monitor when a term deletion happens
		 * @param cls the class concerned by the deletion of one of its instances
		 * @param instance the deleted instance
		 */
		@Override
		public void instanceRemoved(RDFSClass cls,RDFResource instance)
		{
			if (!paused && cls.getNamedSuperclasses(true).contains(ObirProject.getOWLModel().getOWLNamedClass(TERM)))
			{
				ObirProject.getIndexingProcessor().notifyTermDeletion((OWLNamedClass)instance);
			}
		}

		/**
		 * Overriding method to monitor when a term addition happens
		 * @param cls the class concerned by the addition of one of its instances
		 * @param instance the added instance
		 */
		@Override
		public void instanceAdded(RDFSClass cls, RDFResource instance) 
		{
			if (!paused)
			{
				ObirProject.getIndexingProcessor().notifyElementAddition((OWLNamedClass)instance,(OWLNamedClass)cls);
			}
		}

		public void pause()
		{
			paused = true;
		}

		public void resume()
		{
			paused = false;
		}
	}

}
