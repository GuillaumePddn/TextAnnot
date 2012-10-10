package obir.ir.indexing;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.SortedMap;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.Vector;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.WhitespaceAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

import obir.ir.AnnotationTriple;
import obir.ir.Corpus;
import obir.ir.DocumentAnnotation;
import obir.ir.SemanticSimilarity;
import obir.ir.analysis.fr.CustomFrenchStemmer;
import obir.ir.analysis.fr.PaiceHuskFrenchStemmer;
import obir.otr.OTR;
import obir.otr.ObirProject;

import edu.stanford.smi.protege.exception.OntologyLoadException;
import edu.stanford.smi.protegex.owl.ProtegeOWL;
import edu.stanford.smi.protegex.owl.model.OWLIndividual;
import edu.stanford.smi.protegex.owl.model.OWLModel;
import edu.stanford.smi.protegex.owl.model.OWLNamedClass;
import edu.stanford.smi.protegex.owl.model.OWLObjectProperty;
import edu.stanford.smi.protegex.owl.model.RDFProperty;
import edu.stanford.smi.protegex.owl.model.RDFSClass;
import edu.stanford.smi.protegex.owl.model.RDFSLiteral;
import fr.irit.moano.indexing.XMLVilmorinFileHandler;
/**
 * Class that implements the creation and the access methods to the auxiliary relation index
 * @author davide buscaldi
 *
 */
public class RelationIndexFactory {
	/**
	 * Method that returns all the concepts in a paragraph, given the paragraph start and end offsets
	 * and a map that links concepts to their position in a document
	 * This method is based on {@link DocumentAnnotation.setFieldRelations(String field)}
	 * Any modification to one of these methods should be reflected into the other one
	 * @param para_start paragraph start offset
	 * @param para_end paragraph end offset
	 * @param concmap map from concepts to their position
	 * @return the set of concepts contained in a paragraph
	 */
	private static HashSet<OWLIndividual> getConceptsInParagraph(Integer para_start, Integer para_end, TreeMap<Integer, OWLIndividual> concmap){
		HashSet<OWLIndividual> ret = new HashSet<OWLIndividual>();
		SortedMap<Integer, OWLIndividual> subMap = concmap.subMap(para_start, para_end);
		for(Integer key : subMap.keySet()){
			ret.add(concmap.get(key));
		}
		return ret;
	}
	/**
	 * Method that finds a possible relation between an OWL individual (pivot entity) and the concepts in a paragraph
	 * @param dom the pivot entity
	 * @param ranges the concepts extracted from a paragraph
	 * @param txt the text of the paragraph
	 * @return a vector of Strings of the type "Class1 RelName Class2" to be used in the relation index
	 */
	private static Vector<String> getRelationsFor(OWLIndividual dom, HashSet<OWLIndividual> ranges, String txt)
	{
		String docText=getStemmedText(txt);
		Vector<String> ret = new Vector<String>();
		for (RDFProperty prop:(Collection<RDFProperty>)dom.getRDFType().getUnionDomainProperties(true)){
			if (ObirProject.isOTRProperty(prop) ) {
				//System.err.println("checking relation "+prop.getLocalName());
				//System.err.println("for domain: "+dom.getRDFType().getLocalName());
				
				//check domain compatibility
				OWLNamedClass cls = (OWLNamedClass) dom.getRDFType(); //instance class					
				Collection<OWLNamedClass> rngs = cls.getUnionRangeClasses(prop); //Collection of Ranges for property
				Collection<OWLNamedClass> domset = prop.getDomains(false); //Domain set for property
				
				HashSet<String> domainNames = new HashSet<String>(); //set of parents that can be domain of property
				HashSet<String> clsPSet = new HashSet<String>(); //set of parents for the instance
				
				clsPSet.add(cls.getLocalName());
				Collection<OWLNamedClass> parents = cls.getNamedSuperclasses(true);
				for(OWLNamedClass pc : parents) {	
					clsPSet.add(pc.getLocalName());
				}
				for(Object c : domset) {
					if (c instanceof OWLNamedClass) {
						domainNames.add(((OWLNamedClass)c).getLocalName());
					}
				}
				domainNames.retainAll(clsPSet); //parents of the instance class that are in the domain set for the property
				
				if(!domainNames.isEmpty()){
					//System.err.println("Domain compatibile");
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
					for(OWLIndividual rng : ranges){
					OWLNamedClass cls2 = (OWLNamedClass) rng.getRDFType();
						if(!cls2.getLocalName().equals(cls.getLocalName())){
							if(rangedCls.contains(cls2.getLocalName())){ //it is compatible with the prop range
								/*System.err.println("__possibly related instances by: "+prop.getLocalName());
								System.err.println(dom.getBrowserText()+"#"+cls.getLocalName());
								System.err.println(rng.getBrowserText()+"#"+cls2.getLocalName());
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
								boolean goodRel = false;
								for(String lbl : proplbl){
									String stemmedLabel = getStemmedText(lbl);
									if(docText.contains(stemmedLabel)){
										goodRel=true;
										break;
									}
								}
								if(goodRel) ret.add(cls.getLocalName()+" "+prop.getLocalName()+" "+cls2.getLocalName());
							}
						}
					}
				}
			}
		}
		return ret;
	}
	/**
	 * Performs the stemming of the given text
	 * @param docText
	 * @return a string containing the stemmed text, with each token separated by a space
	 */
	private static String getStemmedText(String docText)
	{
		StringBuffer buf = new StringBuffer();
		PaiceHuskFrenchStemmer stemmer = new PaiceHuskFrenchStemmer();
		StringTokenizer st = new StringTokenizer(docText, " \t\n\r\f,.:;?![]'"); //This will remove all punctuations.
		while(st.hasMoreTokens()){
			String token = st.nextToken();
			String lemmatisedToken = stemmer.stem(token);
			buf.append(lemmatisedToken);
			buf.append(" ");
		}
		
		return buf.toString().toLowerCase();
	}
	/**
	 * Creates a relation index for the given field
	 * @param field
	 */
	public static void create(String field){
		String pathEnd = "relindex";
		File index_dir = new File(obir.otr.OTR.getTemporaryDirectory()+pathEnd);
		if (index_dir.exists())
		{
			String[] files = index_dir.list();
			for (String f:files)
			{
				File temp = new File (obir.otr.OTR.getTemporaryDirectory()+pathEnd+"/"+f);
				if (temp.isDirectory())
				{
					for (String sf:temp.list())
						(new File(temp,sf)).delete();
				}
				temp.delete();
			}
			index_dir.delete();
		}
		try
		{
			Corpus annotations = ObirProject.getCorpus();
			Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_29);
			System.err.println("creating relation index to dir:"+index_dir);
			IndexWriter writer = new IndexWriter(FSDirectory.open(index_dir), analyzer,IndexWriter.MaxFieldLength.LIMITED);
			for(DocumentAnnotation ann : annotations){
				String docName=ann.getDocumentName();
				HashSet<String> relationsVec = new HashSet<String>();
				//System.err.println("Analyzing: "+docName);
					
				OWLObjectProperty hasTermOccurrenceProperty = ObirProject.getOWLModel().getOWLObjectProperty("hasTermOccurrence");
				HashSet<OWLIndividual> term_occs = ann.getTermOccurrences(field);
					
				TreeMap<Integer, OWLIndividual> concMap = new TreeMap<Integer, OWLIndividual>();
				for(OWLIndividual term : term_occs) {
					String offset = (String) term.getPropertyValue(term.getOWLModel().getOWLDatatypeProperty("term_offset"));
					try {
						OWLIndividual concept = ((Collection<OWLIndividual>)term.getPropertyValues(term.getOWLModel().getOWLObjectProperty("désigne"), true)).iterator().next();
						offset=offset.replace(']', ',');
						Integer off= new Integer(offset.substring(1, offset.indexOf(',')));
						concMap.put(off, concept);
					} catch (Exception e){
						//pass?
						//System.err.println(term.getLocalName()+" no concept designed? "+field+" "+offset);
					}
				}
				
				OWLIndividual ficheConcept = null;
				try{
					OWLIndividual first = concMap.get(concMap.keySet().iterator().next());
					if(first.getRDFType().getLocalName().matches("[EGV]_.+")) {
						ficheConcept=first;
					}
				} catch (NoSuchElementException nsee){}
					
				if(ficheConcept != null){
					File currentFile = new File(ObirProject.getCorpus().getDirectory(), docName);
					try {
						XMLVilmorinFileHandler hdlr = new XMLVilmorinFileHandler(currentFile);
						Vector<Integer> paragraphBoundaries = hdlr.getParagraphBoundaries();
						HashMap<Integer, String> paragraphs = hdlr.getParagraphContents();
							
						for(int i=0; i<(paragraphBoundaries.size()-1); i++){
							Integer start = paragraphBoundaries.get(i);
							Integer end = paragraphBoundaries.get(i+1);
							
							HashSet<OWLIndividual> domains = getConceptsInParagraph(start, end, concMap);
							Vector<String> maladies = getMaladiesRelsInParagraph(domains, paragraphs.get(start));
							Vector<String> rels = getRelationsFor(ficheConcept, domains, paragraphs.get(start));
							for(String rel : rels){
								relationsVec.add(rel);
							}
							relationsVec.addAll(maladies);
						}
						 
					} catch (Exception e) {
						e.printStackTrace();
					}
				} else {
					//System.err.println("No concept for the listed plant");
					relationsVec.add("no root concept");
				}
				
				
				StringBuffer relationsRepr = new StringBuffer();
				for(String s : relationsVec){
					relationsRepr.append(s);
					relationsRepr.append(" ");
				}
				//System.err.println("Indexing "+relationsVec.size()+" relations: "+relationsRepr.toString());
				
				Document d = new Document();
				d.add(new Field("id", docName, Field.Store.YES, Field.Index.NOT_ANALYZED));
				d.add(new Field("rels", relationsRepr.toString(), Field.Store.YES, Field.Index.ANALYZED));
				
				writer.addDocument(d);
			}
			writer.close();
		}
		catch (IOException ioe){ioe.printStackTrace();}
	}
	/**
	 * Methods that search for maladies in a paragraph and tries to connect them to the nearby
	 * concepts
	 * @param concepts the concepts in the paragraph being analyzed
	 * @param para the paragraph being analyzed
	 * @return the list of relationships having Maladie as domain 
	 */
	private static Vector<String> getMaladiesRelsInParagraph(HashSet<OWLIndividual> concepts, String para) {
		Vector<String> ret = new Vector<String>();
		HashSet<OWLIndividual> maladies = new HashSet<OWLIndividual>();
		for(OWLIndividual ind : concepts){
			HashSet<String> roots= SemanticSimilarity.getStringConceptSuperType(ind);
			for(String s : roots){
				if(s.startsWith("Malad")){
					maladies.add(ind);
					break;
				}
			}
		}
		for(OWLIndividual mal : maladies){
			Vector<String> rels = getRelationsFor(mal, concepts, para);
			ret.addAll(rels);
		}
		return ret;
	}
	/**
	 * Method that checks whether a relation is supported by its label in the text
	 * @param docAnnot document annotation
	 * @param domain domain of the relation
	 * @param range range of the relation
	 * @param relationLabel label of the relation
	 * @return true if in the document referenced by {@code docAnnot} there is a label for the relation {@code relationLabel}
	 */
	public static boolean checkRel(DocumentAnnotation docAnnot, OWLIndividual domain, OWLIndividual range, String relationLabel){
		boolean relFound=false;
		try{
			String pathEnd = "relindex";
			File index_dir = new File(OTR.getTemporaryDirectory()+pathEnd);
			Analyzer analyzer = new WhitespaceAnalyzer();
			IndexReader reader = IndexReader.open(FSDirectory.open(index_dir),true);
			QueryParser qparser = new QueryParser(Version.LUCENE_29, "id", analyzer);
			
			IndexSearcher iSearch = new IndexSearcher(reader);
			StringBuffer searchStr =  new StringBuffer();
			searchStr.append(docAnnot.getDocumentName());
			
			//System.err.println("searching Lucene for "+searchStr);
			Query qDesc = qparser.parse(searchStr.toString());
			TopDocs results = iSearch.search(qDesc, 1);
			ScoreDoc[] hits = results.scoreDocs;
			int numTotalHits = results.totalHits;
			
			HashSet<RDFSClass> domainClasses = new HashSet<RDFSClass>();
			HashSet<RDFSClass> rangeClasses = new HashSet<RDFSClass>();
			
			domainClasses.add(domain.getRDFType());
			rangeClasses.add(range.getRDFType());
			
			domainClasses.addAll(domain.getRDFType().getSubclasses(true));
			rangeClasses.addAll(range.getRDFType().getSubclasses(true));
			
			Vector<String> relPtrns= new Vector<String>();
			
			for(RDFSClass dc : domainClasses){
				for(RDFSClass rc : rangeClasses){
					String ptrn=dc.getLocalName()+" "+relationLabel+" "+rc.getLocalName();
					relPtrns.add(ptrn);
				}
			}
			
			if(numTotalHits > 0) {
				for(ScoreDoc sd : hits){
					Document doc =iSearch.doc(sd.doc);
					String rels = doc.get("rels");
					for(String relationTriplet : relPtrns){
						if(rels.contains(relationTriplet)) {
							System.err.println("Relation match in document "+docAnnot.getDocumentName()+" : "+relationTriplet);
							relFound=true;
							break;
						}
					}
				}
			}
			
			reader.close();
			iSearch.close();
			
		} catch(IOException e){
			e.printStackTrace();
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return relFound;
	}

	/**
	 * This method actuates the optimization filter for the search phase (concepts + relations).
	 * If there are not enough documents that contain the desired relations, the filtered corpus
	 * is filled with documents that contain the concepts connected by the relations
	 * @param queryAnnot the query annotation 
	 * @param originalCorpus the full corpus
	 * @param limit the size of the filter
	 * @return the filtered corpus
	 */
	public static Corpus getRelationFilteredCorpus(DocumentAnnotation queryAnnot, Corpus originalCorpus, int limit){
		Corpus retCorpus = new Corpus(true);
		try{
			String pathEnd = "relindex";
			File index_dir = new File(OTR.getTemporaryDirectory()+pathEnd);
			Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_29);
			IndexReader reader = IndexReader.open(FSDirectory.open(index_dir),true);
			
			HashSet<AnnotationTriple> triples = queryAnnot.getFieldRelations("description");
			QueryParser qparser = new QueryParser(Version.LUCENE_29, "rels", analyzer);
			IndexSearcher iSearch = new IndexSearcher(reader);
			StringBuffer searchStr =  new StringBuffer();
			
			for(AnnotationTriple t: triples){
				searchStr.append(t.getRelatingProperty().getLocalName());
				searchStr.append(" ");
			}
			
			//System.err.println("searching Lucene for "+searchStr);
			Query qDesc = qparser.parse(searchStr.toString().trim());
			
			TopDocs results = iSearch.search(qDesc, limit);
			ScoreDoc[] hits = results.scoreDocs;
			int numTotalHits = results.totalHits;
			
			if(numTotalHits > 0) {
				HashSet<String> filteredDocumentNames = new HashSet<String>(); 
				for(ScoreDoc sd : hits){
					Document doc =iSearch.doc(sd.doc);
					String doc_id = doc.get("id");
					filteredDocumentNames.add(doc_id);
				}
				
				for(DocumentAnnotation ann : originalCorpus){
					String docAnnName=ann.getDocumentName();
					if(filteredDocumentNames.contains(docAnnName)){
						retCorpus.add(ann); //use addDocument?
					}
				}
			}
			
			reader.close();
			iSearch.close();
			
		} catch(IOException e){
			e.printStackTrace();
		} catch (ParseException e) {
			e.printStackTrace();
		}
		if(retCorpus.size()==0) retCorpus=AnnotationIndexFactory.getFilteredCorpus(queryAnnot, originalCorpus, limit);
		else if(retCorpus.size()>0 && retCorpus.size() < limit){
			//integrate with concept based search
			Corpus c2=AnnotationIndexFactory.getFilteredCorpus(queryAnnot, originalCorpus, (limit-retCorpus.size()));
			for(DocumentAnnotation d : c2){
				if(!retCorpus.contains(d)) retCorpus.add(d);
			}
		}
		return retCorpus;
	}

}
