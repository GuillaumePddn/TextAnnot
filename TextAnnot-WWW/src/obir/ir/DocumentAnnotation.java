package obir.ir;


import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Scanner;
import java.util.Vector;

import javax.swing.SwingUtilities;
import javax.xml.transform.TransformerException;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

import obir.ir.analysis.CustomAnalyzer;
import obir.misc.XMLToHTML;
import obir.otr.OTR;
import obir.otr.ObirProject;
import obir.www.annotation.RelationAnnotation;
import edu.stanford.smi.protegex.owl.model.OWLDatatypeProperty;
import edu.stanford.smi.protegex.owl.model.OWLIndividual;
import edu.stanford.smi.protegex.owl.model.OWLModel;
import edu.stanford.smi.protegex.owl.model.OWLNamedClass;
import edu.stanford.smi.protegex.owl.model.OWLObjectProperty;
import edu.stanford.smi.protegex.owl.model.OWLRestriction;
import edu.stanford.smi.protegex.owl.model.RDFObject;
import edu.stanford.smi.protegex.owl.model.RDFProperty;
import edu.stanford.smi.protegex.owl.model.RDFResource;
import edu.stanford.smi.protegex.owl.model.RDFSLiteral;
import edu.stanford.smi.protegex.owl.model.impl.DefaultOWLNamedClass;

public class DocumentAnnotation {//extends DefaultOWLIndividual {

	private static final long serialVersionUID = -5739627783425112939L;
	
	/**
	 * The annotation as OWLIndividual
	 */
	private OWLIndividual annotation;
	/**
	 * document language
	 */
	private String language;
	/**
	 * Sub-directory of this document
	 */
	private String subDirectory;
	/**
	 * Map from field name to its concept annotation
	 */
	private HashMap<String,FieldAnnotation> documentFields;
	/**
	 * Map from field name to its relation annotation
	 */
	private HashMap<String, HashSet<AnnotationTriple>> tripleAnnotations;
	
	public DocumentAnnotation(String subDir, String docName, String defaultLanguage)
	{
		annotation = ObirProject.getOWLModel().getOWLNamedClass(OTR.DOCUMENT).createOWLIndividual(ObirProject.generateNextIndName());
		subDirectory = subDir;
		annotation.setPropertyValue(ObirProject.getOWLModel().getOWLDatatypeProperty(OTR.DOC_NAME), docName);
		//		fieldOffsets = new HashMap<String, Integer>();
		//		conceptInstancesByField = new HashMap<String, HashSet<OWLIndividual>>();
		language = Corpus.computeExplicitFileLanguage(subDirectory,docName);
		if ((language == null)||language.isEmpty())
			language = defaultLanguage;
		tripleAnnotations = new HashMap<String,HashSet<AnnotationTriple>>();
		documentFields = new HashMap<String, FieldAnnotation>();
	}

	@SuppressWarnings("unchecked")
	public DocumentAnnotation(OWLIndividual ind,String defaultLanguage)
	{
		annotation = ind;
		documentFields = new HashMap<String, FieldAnnotation>();
		//fieldOffsets = new HashMap<String, Integer>();
		//conceptInstancesByField = new HashMap<String, HashSet<OWLIndividual>>();
		HashSet<String> semFields = Corpus.getSemanticFields();
		
		for (OWLIndividual fieldAnnot:(Collection<OWLIndividual>)annotation.getPropertyValues(ObirProject.getOWLModel().getOWLObjectProperty(OTR.hasFieldAnnotation)))
		{
			FieldAnnotation fieldAnnotObj = new FieldAnnotation(this,fieldAnnot);
			documentFields.put(fieldAnnotObj.getFieldName(), fieldAnnotObj);
		}
		
		if (documentFields.size()==0) //cas où la rto n'a pas encore  été transformée ds nv méta-modéle
		{
			HashMap<String,HashSet<OWLIndividual>> occsByField = new HashMap<String, HashSet<OWLIndividual>>();
			for (OWLIndividual termOcc:(Collection<OWLIndividual>)annotation.getPropertyValues(annotation.getOWLModel().getOWLObjectProperty(OTR.DOC_HAS_TERM_OCCS)))
			{
				HashSet<OWLIndividual> tmpSet = new HashSet<OWLIndividual>();
				String field = (String)termOcc.getPropertyValue(termOcc.getOWLModel().getOWLDatatypeProperty(OTR.DOC_FIELD));
				if (occsByField.containsKey(field))
					tmpSet.addAll(occsByField.get(field));
				tmpSet.add(termOcc);
				occsByField.put(field,tmpSet);
			}

			HashMap<String,HashSet<OWLIndividual>> instsByField = new HashMap<String, HashSet<OWLIndividual>>();
			if (semFields.size()==1) //cas Artal et Actia
			{
				instsByField.put(semFields.iterator().next(), new HashSet<OWLIndividual>(annotation.getPropertyValues(ind.getOWLModel().getOWLObjectProperty(OTR.DOC_HAS_CONCEPT_INSTS))));
			}
			else //cas Arkéo
			{
				for (OWLIndividual termOcc:(Collection<OWLIndividual>)annotation.getPropertyValues(ind.getOWLModel().getOWLObjectProperty(OTR.DOC_HAS_TERM_OCCS)))
				{
					String field = (String) termOcc.getPropertyValue(termOcc.getOWLModel().getOWLDatatypeProperty(OTR.DOC_FIELD));
					HashSet<OWLIndividual> tmpSet = new HashSet<OWLIndividual>();
					if (instsByField.containsKey(field))
						tmpSet.addAll(instsByField.get(field));
					tmpSet.addAll(termOcc.getPropertyValues(ind.getOWLModel().getOWLObjectProperty(OTR.designatesID)));
					instsByField.put(field, tmpSet);
				}
			}
			
			for (String field:Corpus.getSemanticFields())
			{
				float coverage = getTemporaryIndexingScore(field);
				FieldAnnotation fieldAnnot = new FieldAnnotation(this, field,occsByField.get(field),instsByField.get(field),coverage);
				annotation.addPropertyValue(ObirProject.getOWLModel().getOWLObjectProperty(OTR.hasFieldAnnotation),fieldAnnot.getOWLEquivalent());
				documentFields.put(field, fieldAnnot);
			}
			OWLDatatypeProperty doc_cover = ObirProject.getOWLModel().getOWLDatatypeProperty(OTR.DOC_HAS_COVERAGE);
			annotation.removePropertyValue(doc_cover,annotation.getPropertyValue(doc_cover));
		}
		
		
		HashSet<OWLIndividual> explicitConceptInstances = new HashSet<OWLIndividual>();
		for (OWLIndividual termOcc:(Collection<OWLIndividual>)annotation.getPropertyValues(ind.getOWLModel().getOWLObjectProperty(OTR.DOC_HAS_TERM_OCCS)))
		{
			//				String field = (String) termOcc.getPropertyValue(termOcc.getOWLModel().getOWLDatatypeProperty(OTR.DOC_FIELD));
			for (OWLIndividual cptInst:(Collection<OWLIndividual>)termOcc.getPropertyValues(ind.getOWLModel().getOWLObjectProperty(OTR.designatesID)))
			{
				//					addConceptInstance(cptInst,field);
				explicitConceptInstances.add(cptInst);
			}
		}

		if(explicitConceptInstances.size()!=annotation.getPropertyValues(ind.getOWLModel().getOWLObjectProperty(OTR.DOC_HAS_CONCEPT_INSTS)).size())
			unvalidate();

		language = Corpus.computeExplicitFileLanguage((String)ind.getPropertyValue(ind.getOWLModel().getOWLDatatypeProperty(OTR.DOC_NAME)));
		if ((language == null)||language.isEmpty())
			language = defaultLanguage;
	}


	/**
	 * Adds a term occurrence to the document
	 * @param termOcc the occurrence to add
	 */
	public void addTermOccurrence(OWLIndividual termOcc, String field)
	{
		getFieldAnnotation(field).addTermOccurrence(termOcc);
	}


	/**
	 * Adds a concept instance to the document
	 * @param cptInst the instance to add
	 * @param field the field containing the given instance
	 */
	public void addConceptInstance(OWLIndividual cptInst,String field)
	{
		getFieldAnnotation(field).addConceptInstance(cptInst);
	}

	/**
	 * Checks whether the document contains any children of the given concept
	 * @param cpt the given concept
	 * @param field the field into which looking for children
	 * @return {@code true} iff at least one concept instance found in the document field has a type which is a subclass of the concept.
	 */
	public boolean containsHyponymsOf(OWLNamedClass cpt,String field)
	{
		return getFieldAnnotation(field).containsHyponymsOf(cpt);
	}

	/**
	 * Gets all terms found in the document
	 * @return all the types of the term occurrences found
	 */
	public HashSet<OWLNamedClass> getTerms()
	{
		HashSet<OWLNamedClass> result = new HashSet<OWLNamedClass>();
		for (OWLIndividual termOcc:getTermOccurrences())
		{
			result.add((OWLNamedClass)termOcc.getRDFType());
		}
		return result;
	}


	/**
	 * Gets all terms found in the document
	 * @return all the types of the term occurrences found
	 */
	public HashSet<OWLNamedClass> getTerms(String field)
	{
		return getFieldAnnotation(field).getTerms();
	}

	/**
	 * Gets all concepts found in the document
	 * @return all the types of the concept instances found
	 */
	@SuppressWarnings("unchecked")
	public HashSet<OWLNamedClass> getConcepts()
	{
		HashSet<OWLNamedClass> result = new HashSet<OWLNamedClass>();
		for (String field:Corpus.getSemanticFields())
			result.addAll(getFieldAnnotation(field).getConcepts());

		return result;
	}

	/**
	 * Gets all concepts found in the document field
	 * @param field a given field in the current document
	 * @return all the types of the concept instances found
	 */
	public HashSet<OWLNamedClass> getConcepts(String field)
	{
		return getFieldAnnotation(field).getConcepts();
	}


	/**
	 * Term occurrences getter
	 * @return all term occurrences associated to the document
	 */
	@SuppressWarnings("unchecked")
	public HashSet<OWLIndividual> getTermOccurrences() {
		HashSet<OWLIndividual> result = new HashSet<OWLIndividual>();
		for (String field:Corpus.getSemanticFields())
			result.addAll(getFieldAnnotation(field).getTermOccurrences());

		return result;
	}

	/**
	 * Term occurrences getter
	 * @param field a document field
	 * @return all term occurrences associated to the given document field
	 */
	@SuppressWarnings("unchecked")
	public HashSet<OWLIndividual> getTermOccurrences(String field) {
		return getFieldAnnotation(field).getTermOccurrences();
	}

	/**
	 * Concept instances getter
	 * @return all concept instances associated to the document
	 */
	@SuppressWarnings("unchecked")
	public HashSet<OWLIndividual> getConceptInstances() {
		HashSet<OWLIndividual> result = new HashSet<OWLIndividual>();
		for (String field:Corpus.getSemanticFields())
			result.addAll(getFieldAnnotation(field).getConceptInstances());

		return result;
	}

	public HashSet<OWLIndividual> getConceptInstances(String field) {
		return getFieldAnnotation(field).getConceptInstances();
	}

	/**
	 * Gets all the instances of the given concept which have been found in the given document field
	 * @param concept the given concept
	 * @param field the field
	 * @return all concept instances found in the document field with the appropriate type
	 */
	public HashSet<OWLIndividual> getAllInstancesOf(OWLNamedClass concept, String field)
	{
		return getFieldAnnotation(field).getAllInstancesOf(concept);
	}


	/**
	 * Indexing score getter
	 * @param field the indexed field
	 * @return the ratio of found words in the indexed field of the given document
	 */
	@SuppressWarnings("unchecked")
	public Float getIndexingScore(String field) {
		return getFieldAnnotation(field).getIndexingScore();
	}

	/**
	 * Indexing score setter
	 * @param field the indexed field
	 * @param indexingScore the score to set
	 */
	public void setIndexingScore(String field,Float indexingScore) {
		getFieldAnnotation(field).setIndexingScore(indexingScore);
	}

	/**
	 * Indexing score remover
	 * @param field the indexed field
	 */
	@SuppressWarnings("unchecked")
	public void removeIndexingScore(String field) {
		for (String entry:(Collection<String>)annotation.getPropertyValues(annotation.getOWLModel().getOWLDatatypeProperty(OTR.DOC_HAS_COVERAGE)))
		{
			String currentField = entry.substring(0, entry.indexOf("="));
			if (field.equals(currentField))
				annotation.removePropertyValue(annotation.getOWLModel().getOWLDatatypeProperty(OTR.DOC_HAS_COVERAGE), entry);
		}
	}


	/**
	 * Checks whether the document is validated
	 * @return {@code true} iff the corresponding attribute value for the document is set to {@code true}
	 */
	public boolean isValidated()
	{
		return ((Boolean)annotation.getPropertyValue(annotation.getOWLModel().getOWLDatatypeProperty(OTR.DOC_VALIDATION)));
	}

	/**
	 * Removes a term occurrence from the given document
	 * @param termOcc the occurrence to add
	 */
	public void removeTermOccurrence(OWLIndividual termOcc)
	{
		//annotation.removePropertyValue(annotation.getOWLModel().getOWLObjectProperty(OTR.DOC_HAS_TERM_OCCS), termOcc);
		for (String field:Corpus.getSemanticFields())
		{
			FieldAnnotation fieldAnnot = getFieldAnnotation(field);
			if (fieldAnnot.getTermOccurrences().contains(termOcc))
			{
				fieldAnnot.removeTermOccurrence(termOcc);
				break;
			}
		}
	}

	/**
	 * Removes a concept instance from the given document
	 * @param cptInst the instance to add
	 */
	@SuppressWarnings("unchecked")
	public void removeConceptInstance(OWLIndividual cptInst)
	{
		/*annotation.removePropertyValue(annotation.getOWLModel().getOWLObjectProperty(OTR.DOC_HAS_CONCEPT_INSTS), cptInst);
		for (String field:conceptInstancesByField.keySet())
		{
			if (conceptInstancesByField.get(field).contains(cptInst))
			{
				HashSet<OWLIndividual> newInsts = (HashSet<OWLIndividual>) conceptInstancesByField.get(field).clone();
				newInsts.remove(cptInst);
				conceptInstancesByField.put(field, newInsts);
				break;
			}
		}*/
		for (String field:Corpus.getSemanticFields())
		{
			FieldAnnotation fieldAnnot = getFieldAnnotation(field);
			if (fieldAnnot.getConceptInstances().contains(cptInst))
			{
				fieldAnnot.removeConceptInstance(cptInst);
				break;
			}
		}
	}
	/**
	 * Method intented to be used only by the Web Service ThemaStream
	 * @param o1
	 * @param o2
	 * @param relLabels
	 * @param field
	 * @return a string array spotted label, spotted word(s)
	 */
	private Vector<String> getRelSpottedWords(OWLIndividual o1, OWLIndividual o2, HashSet<String> relLabels, String field){
		Vector<String> ret = new Vector<String>();
		try{
			String fs = System.getProperty("file.separator");
			String path=Corpus.getDirectoryPath();//getDocumentRelativePath();
			String docName=getDocumentName();
			String luceneBaseDir=OTR.getTemporaryDirectory();//ObirProject.getParameterDir()+"default";
			if(getDocumentName().endsWith("query.xml")) {
				luceneBaseDir+="query"+fs+language+fs;
			} else {
				luceneBaseDir+="search"+fs+"index_"+field+fs+language+fs;
			}
			IndexReader reader = IndexReader.open(FSDirectory.open(new File(luceneBaseDir)),false);
			
			CustomAnalyzer analyzer = ObirProject.getIndexingProcessor().getAnalyzer(language);
			QueryParser qparser = new QueryParser(Version.LUCENE_29, field, analyzer);
			IndexSearcher iSearch = new IndexSearcher(reader);
			for(String s : relLabels){
				StringBuffer searchStr =  new StringBuffer();
				searchStr.append("path:\""+path+fs+docName+"\" "+s);
				//System.err.println("searching Lucene for "+searchStr);
				Query qDesc = qparser.parse(searchStr.toString());
				Term searchTerm = new Term(field, (qparser.parse(s)).toString());
				TopDocs luceneResults = iSearch.search(qDesc, 10);
				if(luceneResults.totalHits > 0) {
					ret.add(s); //spotted label
					ret.add(searchTerm.text().replaceAll((field+":"), "")); //spotted words
					break;
					//System.err.println("result found ");
				}
			}
			
			reader.close();
			iSearch.close();

		} catch(IOException e){
			e.printStackTrace();
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return ret;
	}
	
	/**
	 * Method intended to be used by ThemaStream only
	 * @param field
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public Vector<RelationAnnotation> getRelationAnnotations(String field)
	{
		Vector<RelationAnnotation> relAnnot = new Vector<RelationAnnotation>();
		for (OWLIndividual inst:getConceptInstances(field))
		{
			for (RDFProperty prop:(Collection<RDFProperty>)inst.getRDFType().getUnionDomainProperties(true))//.getRDFProperties())
			{
				if ( ObirProject.isOTRProperty(prop) )
				{
					//compatibilité des domaines
					OWLNamedClass cls = (OWLNamedClass) inst.getRDFType();
					Collection<OWLNamedClass> coll2 = cls.getUnionRangeClasses(prop); //si ottengono gli stessi risultati che con il metodo su prop? sembrerebbe di si
					Collection<OWLNamedClass> dom = prop.getDomains(false);//Non dovrebbe essere necessario ma sembra di si
					HashSet<String> domainNames = new HashSet<String>();
					HashSet<String> clsPSet = new HashSet<String>();
					clsPSet.add(cls.getLocalName());
					Collection<OWLNamedClass> parents = cls.getNamedSuperclasses(true);
					for(OWLNamedClass pc : parents) {
						
						clsPSet.add(pc.getLocalName());
					}
					for(Object c : dom) {
						if (c instanceof OWLNamedClass)
							domainNames.add(((OWLNamedClass)c).getLocalName());
					}
					domainNames.retainAll(clsPSet);
					
					if(!domainNames.isEmpty()){
						HashSet<String> rangedCls = new HashSet<String>();
						for(OWLNamedClass cl : coll2) {
							rangedCls.add(cl.getLocalName());
							
							Collection<OWLNamedClass> sons = cl.getNamedSubclasses(true);
							for(OWLNamedClass scl : sons) {
								rangedCls.add(scl.getLocalName());
							}
						}
						
						for (OWLIndividual inst2:getConceptInstances(field)){
							OWLNamedClass cls2 = (OWLNamedClass) inst2.getRDFType();
							if(!cls2.getLocalName().equals(cls.getLocalName())){
								if(rangedCls.contains(cls2.getLocalName())){ //compatibilité du range
									Collection<Object> labels = prop.getLabels();
									HashSet<String> proplbl= new HashSet<String>();
									
									for(Object label : labels) {
										String lbl=null;
										if (label instanceof RDFSLiteral)
											lbl = OTR.translateLabel(((RDFSLiteral)label).getString());
										else if (label instanceof String)
											lbl = OTR.translateLabel((String)label);
										if (lbl!=null)
											//System.err.println(lbl);
											proplbl.add(lbl);
									}
									
									Vector<String> spottedItems = getRelSpottedWords(inst, inst2, proplbl, field);
									if(spottedItems.size() > 0){
										RelationAnnotation ann = new RelationAnnotation(inst, (OWLObjectProperty)prop, inst2, spottedItems);
										relAnnot.add(ann);
									}
								}
							}
						}
					}
					
				}
			}
		}
		return relAnnot;
	}
	
	/**
	 * Checks if two concepts are potentially related by a relation designed by a set of labels
	 * @param o1
	 * @param o2
	 * @param relLabels
	 * @param field
	 * @return
	 */
	private boolean checkRelationBetweenConcepts(OWLIndividual o1, OWLIndividual o2, HashSet<String> relLabels, String field){
		//TODO: passare dal controllo semplice al verificare gli spans (per controllare le posizioni della label)
		boolean found=false;
		try{
			String fs = System.getProperty("file.separator");
			String path=Corpus.getDirectoryPath();//getDocumentRelativePath();
			String docName=getDocumentName();
			String luceneBaseDir=OTR.getTemporaryDirectory();//ObirProject.getParameterDir()+"default";
			if(getDocumentName().endsWith("query.xml")) {
				luceneBaseDir+="query"+fs+language+fs;
			} else {
				luceneBaseDir+="search"+fs+"index_"+field+fs+language+fs;
			}
			IndexReader reader = IndexReader.open(FSDirectory.open(new File(luceneBaseDir)),false);
			//			System.out.println("doc: "+reader.document(0).toString());
			//System.err.println("lucene base dir :"+luceneBaseDir);
			/* versione che cerca il documento e verifica che contenga le labels */
			CustomAnalyzer analyzer = ObirProject.getIndexingProcessor().getAnalyzer(language);
			QueryParser qparser = new QueryParser(Version.LUCENE_29, field, analyzer);
			IndexSearcher iSearch = new IndexSearcher(reader);
			StringBuffer searchStr =  new StringBuffer();
			searchStr.append("path:\""+path+fs+docName+"\"");
			for(String s : relLabels){
				searchStr.append(" "+s);
			}
			System.err.println("searching Lucene for "+searchStr);
			Query qDesc = qparser.parse(searchStr.toString());
			TopDocs luceneResults = iSearch.search(qDesc, 10);
			if(luceneResults.totalHits > 0) {
				found=true;
				//System.err.println("result found ");
			}
			reader.close();
			iSearch.close();
			
		} catch(IOException e){
			e.printStackTrace();
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return found;
	}
	
	/**
	 * Gets all the triples (subject instance - relation - object instance) spotted in a given field NEW VERSION WITH AnnotationTriple
	 * @param field a field label
	 * @return associated triples
	 */
	@SuppressWarnings("unchecked")
	public void setFieldRelations(String field)
	{
		HashSet<AnnotationTriple> tripleGraph = new HashSet<AnnotationTriple>();
		for (OWLIndividual inst:getConceptInstances(field))
		{
			for (RDFProperty prop:(Collection<RDFProperty>)inst.getRDFType().getUnionDomainProperties(true))//.getRDFProperties())
			{
				//System.err.println("checking relation "+prop.getLocalName());
				if ( ObirProject.isOTRProperty(prop) )
				{
					//check domain compatibility
					OWLNamedClass cls = (OWLNamedClass) inst.getRDFType(); //instance class
					
					Collection<OWLNamedClass> rngs = cls.getUnionRangeClasses(prop); //Collection of Ranges for property
					
					Collection<OWLNamedClass> dom = prop.getDomains(false); //Domain set for property
					
					
					HashSet<String> domainNames = new HashSet<String>(); //set of parents that can be domain of property
					HashSet<String> clsPSet = new HashSet<String>(); //set of parents for the instance
					//System.err.println("current class:"+cls.getLocalName());
					clsPSet.add(cls.getLocalName());
					Collection<OWLNamedClass> parents = cls.getNamedSuperclasses(true);
					for(OWLNamedClass pc : parents) {
						
						clsPSet.add(pc.getLocalName());
						//System.err.println("parent class:"+pc.getLocalName());
					}
					for(Object c : dom) {
						if (c instanceof OWLNamedClass) {
							domainNames.add(((OWLNamedClass)c).getLocalName());
							//System.err.println("domain class:"+((OWLNamedClass)c).getLocalName());
						}
					}
					domainNames.retainAll(clsPSet); //parents of the instance class that are in the domain set for the property
					
					HashSet<OWLIndividual> destinations = new HashSet<OWLIndividual>();
					if(!domainNames.isEmpty()){
						HashSet<String> rangedCls = new HashSet<String>();
						for(OWLNamedClass cl : rngs) {
							rangedCls.add(cl.getLocalName());
							//System.err.println("Adding class in range: "+cl.getLocalName());
							Collection<OWLNamedClass> sons = cl.getNamedSubclasses(true);
							for(OWLNamedClass scl : sons) {
								rangedCls.add(scl.getLocalName());
								//System.err.println("Adding subclass in range: "+scl.getLocalName());
							}
						}
							
						for (OWLIndividual inst2:getConceptInstances(field)){ //search another instance that can be connected to inst
							OWLNamedClass cls2 = (OWLNamedClass) inst2.getRDFType();
							if(!cls2.getLocalName().equals(cls.getLocalName())){
								if(rangedCls.contains(cls2.getLocalName())){ //it is compatible with the prop range
									/*
									System.err.println("__possibly related instances by: "+prop.getLocalName());
									System.err.println(inst.getBrowserText()+"#"+cls.getLocalName());
									System.err.println(inst2.getBrowserText()+"#"+cls2.getLocalName());
									System.err.println("_________________________________");
									*/
									Collection<Object> labels = prop.getLabels();
									HashSet<String> proplbl= new HashSet<String>();
									
									for(Object label : labels) {
										String lbl=null;
										if (label instanceof RDFSLiteral)
											lbl = OTR.translateLabel(((RDFSLiteral)label).getString());
										else if (label instanceof String)
											lbl = OTR.translateLabel((String)label);
										if (lbl!=null)
											//System.err.println(lbl);
											proplbl.add(lbl);
									}
									
									boolean goodRel = checkRelationBetweenConcepts(inst, inst2, proplbl, field); //algorithm that search the property labels in the context
									if(goodRel){
										destinations.add(inst2);
									}
								}
							}
						}
					}
					/* versione originale che prende solo quello che è nell'ontologia
					Collection<OWLIndividual> dests = (Collection<OWLIndividual>)inst.getPropertyValues(prop); //qui le entita' che sono in relazione con inst secondo l'ontologia
					for (OWLIndividual dest: dests)
					{
						destinations.add(dest);
					}*/
					if (destinations.size()>0)
					{
						for(OWLIndividual dest: destinations){
							tripleGraph.add(new AnnotationTriple(inst, prop, dest));
						}
					}
				}
			}
		}
		tripleAnnotations.put(field, tripleGraph);
	}
	
	/**
	 * Gets all the triples (subject instance - relation - object instance) spotted in a given field NEW VERSION WITH AnnotationTriple
	 * @param field a field label
	 * @return associated triples
	 */
	@SuppressWarnings("unchecked")
	public HashSet<AnnotationTriple> getFieldRelations(String field)
	{
		return tripleAnnotations.get(field);
	}
	
	/**
	 * Gets all the triples (subject instance - relation - object instance) spotted in a given field
	 * @param field a field label
	 * @return associated triples
	 */
	@SuppressWarnings("unchecked")
	public HashMap<OWLIndividual,HashMap<OWLObjectProperty,HashSet<OWLIndividual>>> getFieldTriple(String field)
	{
		HashMap<OWLIndividual,HashMap<OWLObjectProperty,HashSet<OWLIndividual>>> tripleGraph = new HashMap<OWLIndividual, HashMap<OWLObjectProperty,HashSet<OWLIndividual>>>();
		for (OWLIndividual inst:getConceptInstances(field))
		{
			for (RDFProperty prop:(Collection<RDFProperty>)inst.getRDFType().getUnionDomainProperties(true))//.getRDFProperties())
			{
				//System.err.println("checking relation "+prop.getLocalName());
				if ( ObirProject.isOTRProperty(prop) )
				{
					//System.err.println(prop.getLocalName()+" is supported by OTR");
					HashMap<OWLObjectProperty,HashSet<OWLIndividual>> related = new HashMap<OWLObjectProperty, HashSet<OWLIndividual>>();
					if (tripleGraph.containsKey(inst))
						related = tripleGraph.get(inst);
					//check domain compatibility
					OWLNamedClass cls = (OWLNamedClass) inst.getRDFType(); //instance class
					
					Collection<OWLNamedClass> rngs = cls.getUnionRangeClasses(prop); //Collection of Ranges for property
					
					Collection<OWLNamedClass> dom = prop.getDomains(false); //Domain set for property
					
					
					HashSet<String> domainNames = new HashSet<String>(); //set of parents that can be domain of property
					HashSet<String> clsPSet = new HashSet<String>(); //set of parents for the instance
					//System.err.println("current class:"+cls.getLocalName());
					clsPSet.add(cls.getLocalName());
					Collection<OWLNamedClass> parents = cls.getNamedSuperclasses(true);
					for(OWLNamedClass pc : parents) {
						
						clsPSet.add(pc.getLocalName());
						//System.err.println("parent class:"+pc.getLocalName());
					}
					for(Object c : dom) {
						if (c instanceof OWLNamedClass) {
							domainNames.add(((OWLNamedClass)c).getLocalName());
							//System.err.println("domain class:"+((OWLNamedClass)c).getLocalName());
						}
					}
					domainNames.retainAll(clsPSet); //parents of the instance class that are in the domain set for the property
					
					HashSet<OWLIndividual> destinations = new HashSet<OWLIndividual>();
					if(!domainNames.isEmpty()){
						HashSet<String> rangedCls = new HashSet<String>();
						for(OWLNamedClass cl : rngs) {
							rangedCls.add(cl.getLocalName());
							//System.err.println("Adding class in range: "+cl.getLocalName());
							Collection<OWLNamedClass> sons = cl.getNamedSubclasses(true);
							for(OWLNamedClass scl : sons) {
								rangedCls.add(scl.getLocalName());
								//System.err.println("Adding subclass in range: "+scl.getLocalName());
							}
						}
							
						for (OWLIndividual inst2:getConceptInstances(field)){ //search another instance that can be connected to inst
							OWLNamedClass cls2 = (OWLNamedClass) inst2.getRDFType();
							if(!cls2.getLocalName().equals(cls.getLocalName())){
								if(rangedCls.contains(cls2.getLocalName())){ //it is compatible with the prop range
									/*
									System.err.println("__possibly related instances by: "+prop.getLocalName());
									System.err.println(inst.getBrowserText()+"#"+cls.getLocalName());
									System.err.println(inst2.getBrowserText()+"#"+cls2.getLocalName());
									System.err.println("_________________________________");
									*/
									Collection<Object> labels = prop.getLabels();
									HashSet<String> proplbl= new HashSet<String>();
									
									for(Object label : labels) {
										String lbl=null;
										if (label instanceof RDFSLiteral)
											lbl = OTR.translateLabel(((RDFSLiteral)label).getString());
										else if (label instanceof String)
											lbl = OTR.translateLabel((String)label);
										if (lbl!=null)
											//System.err.println(lbl);
											proplbl.add(lbl);
									}
									
									boolean goodRel = checkRelationBetweenConcepts(inst, inst2, proplbl, field); //algorithm that search the property labels in the context
									if(goodRel){
										destinations.add(inst2);
									}
								}
							}
						}
					}
					/* versione originale che prende solo quello che è nell'ontologia
					Collection<OWLIndividual> dests = (Collection<OWLIndividual>)inst.getPropertyValues(prop); //qui le entita' che sono in relazione con inst secondo l'ontologia
					for (OWLIndividual dest: dests)
					{
						destinations.add(dest);
					}*/
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
	 * @param field a field label
	 * @return the set of isolated instances
	 */
	@SuppressWarnings("unchecked")
	public HashSet<OWLIndividual> getIsolatedInstances(String field)
	{
		return getFieldAnnotation(field).getIsolatedInstances();
	}

	/**
	 * Resets all concept instances and term occurrences in the document
	 */
	@SuppressWarnings("unchecked")
	public void resetAllOWLIndividuals()
	{
		for (String field:Corpus.getSemanticFields())
			getFieldAnnotation(field).resetAllOWLIndividuals();
	}

	public void deleteAllAnnotations()
	{
		HashSet<OWLIndividual> toDel = new HashSet<OWLIndividual>();
		toDel.addAll(getTermOccurrences());
		toDel.addAll(getConceptInstances());
		if (toDel.size()>0)
		{

			boolean needed = ObirProject.blockDispatchEvents();

			for (OWLIndividual inst:toDel)
				inst.delete();
			resetAllOWLIndividuals();

			ObirProject.unblockDispatchEvents(needed);

		}
	}

	
	public void deleteAllAnnotations(String field)
	{
		HashSet<OWLIndividual> toDel = new HashSet<OWLIndividual>();
		toDel.addAll(getTermOccurrences(field));
		toDel.addAll(getConceptInstances(field));

		boolean needed = ObirProject.blockDispatchEvents();
		
		getFieldAnnotation(field).resetAllOWLIndividuals();

		if (!toDel.isEmpty())
		{
			for (OWLIndividual inst:toDel)
				inst.delete();
		}

		ObirProject.unblockDispatchEvents(needed);
	}

	/**
	 * Sets the document as validated. ONLY CALL FROM CORPUS OBJECT
	 */
	public void validate()
	{
		annotation.setPropertyValue(annotation.getOWLModel().getOWLDatatypeProperty(OTR.DOC_VALIDATION),true);
	}

	/**
	 * Sets the document as non-validated. ONLY CALL FROM CORPUS OBJECT
	 */
	public void unvalidate()
	{
		annotation.setPropertyValue(annotation.getOWLModel().getOWLDatatypeProperty(OTR.DOC_VALIDATION),false);
	}

	public OWLIndividual getAnnotation()
	{
		return annotation;
	}

	/**
	 * Document filename getter
	 * @return the relevant name
	 */
	public String getDocumentName()
	{
		String result = (String)annotation.getPropertyValue(annotation.getOWLModel().getOWLDatatypeProperty(OTR.DOC_NAME));
		return result;//.substring(result.lastIndexOf("/")+1);
	}

	public String getDocumentRelativePath()
	{
		if (subDirectory!=null && !subDirectory.isEmpty())
			return subDirectory+"\\"+getDocumentName();
		else
			return getDocumentName();
	}

	public OWLModel getOWLModel()
	{
		return annotation.getOWLModel();
	}

	public void addFieldOffset(String field,Integer offset)
	{
		//fieldOffsets.put(field, offset);
		getFieldAnnotation(field).setFieldOffset(offset);
	}

	public Integer getFieldOffset(String field)
	{
		/*Integer offset = fieldOffsets.get(field);
		if (offset==null)
			offset = computeFieldOffset(field);
		return offset;*/
		return getFieldAnnotation(field).getFieldOffset();
	}

	public Integer computeFieldOffset(String field)
	{
		Corpus corpus = ObirProject.getCorpus();
		File associatedFile = new File(corpus.getDirectoryPath()+"\\"+getDocumentRelativePath());
		Integer offset = null;
		try 
		{
			String transformedContent = SearchFiles.transformIntoStringWithCorrectPositions(XMLToHTML.fromXMLToHTML(associatedFile,getLanguage()));
			offset = SearchFiles.computeHTMLSymptomOffset(transformedContent,corpus.getSemanticHTMLTag(field,getLanguage()));
			addFieldOffset(field,offset);
		} 
		catch (TransformerException e) {e.printStackTrace();}

		return offset;
	}

	public String getLanguage()
	{
		return language;
	}

	public String toString()
	{
		return "DocAnnot(\""+getDocumentRelativePath()+"\")";
	}
	
	public OWLIndividual getOWLEquivalent()
	{
		return annotation;
	}
	
	public Float getTemporaryIndexingScore(String field)
	{
		String entry = (String)annotation.getPropertyValue(annotation.getOWLModel().getOWLDatatypeProperty(OTR.DOC_HAS_COVERAGE));
		Scanner scan = new Scanner(entry);
		scan.useDelimiter("(=|,)");
		boolean rightCover = false;
		while (scan.hasNext())
		{
			String piece = scan.next();
			if (rightCover)
				return new Float(piece);
			else 
			{
				if (piece.contains(field))
					rightCover = true;
			}
		}
		return Float.NaN;
	}
	
	public FieldAnnotation getFieldAnnotation(String field)
	{
		if (documentFields.get(field)==null)
		{
			FieldAnnotation fieldAnnot = new FieldAnnotation(this, field);
			annotation.addPropertyValue(ObirProject.getOWLModel().getOWLObjectProperty(OTR.hasFieldAnnotation),fieldAnnot.getOWLEquivalent());
			documentFields.put(field, fieldAnnot);
		}
		return documentFields.get(field);
	}
	
	public void moveFromFieldToField(String oldField,String newField)
	{
		HashSet<OWLIndividual> toRemoveFromOldMap = new HashSet<OWLIndividual>();
		for (OWLIndividual inst:getConceptInstances(oldField))
		{
			addConceptInstance(inst, newField);
			toRemoveFromOldMap.add(inst);
		}

		//		HashSet<OWLIndividual> newInsts = (HashSet<OWLIndividual>) conceptInstancesByField.get(oldField);
		//		for (OWLIndividual inst:toRemoveFromOldMap)
		//			newInsts.remove(inst);
	}
	
	public boolean containsOntologyLeaf()
	{
		for (String field:Corpus.getSemanticFields())
		{
			if (containsOntologyLeaf(field))
				return true;
		}
		return false;
		
	}

	public HashSet<OWLNamedClass> getOntologyLeaves(String field)
	{
		HashSet<OWLNamedClass> result = new HashSet<OWLNamedClass>();
		for (OWLNamedClass cpt:getConcepts(field))
		{
			if (cpt.getNamedSubclasses(true).size()==0)
				result.add(cpt);
		}
		return result;
	}
	
	public boolean containsOntologyLeaf(String field)
	{
		for (OWLNamedClass cpt:getConcepts(field))
		{
			if (cpt.getNamedSubclasses(true).size()==0)
				return true;
		}
		return false;
	}
}
