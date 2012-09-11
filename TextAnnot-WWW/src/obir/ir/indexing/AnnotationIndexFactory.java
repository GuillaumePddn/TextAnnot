package obir.ir.indexing;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;

import obir.ir.Corpus;
import obir.ir.DocumentAnnotation;
import obir.otr.OTR;
import obir.otr.ObirProject;

import org.apache.lucene.analysis.Analyzer;
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

import edu.stanford.smi.protegex.owl.model.OWLIndividual;
import edu.stanford.smi.protegex.owl.model.OWLNamedClass;

/**
 * Convenience class to create and work on the auxiliary concept index
 * (concepts only - no relations)
 * @author davide buscaldi
 *
 */
public class AnnotationIndexFactory {
	/**
	 * Creates an index for the given field
	 * @param field the field to be indexed
	 */
	public static void create(String field){
		String pathEnd = "auxindex";
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
			System.err.println("writing auxiliary index to dir:"+index_dir);
			IndexWriter writer = new IndexWriter(FSDirectory.open(index_dir), analyzer,IndexWriter.MaxFieldLength.LIMITED);
			for(DocumentAnnotation ann : annotations){
				String docName=ann.getDocumentName();
				HashSet<OWLIndividual> indInstances = ann.getConceptInstances();
				HashSet<String> allConcepts = new HashSet<String>();
				for(OWLIndividual ins : indInstances){
					 //set of parents for the instance
					OWLNamedClass cls = (OWLNamedClass) ins.getRDFType(); //class
					allConcepts.add(cls.getLocalName());
					Collection<OWLNamedClass> pclasses = cls.getNamedSuperclasses(true);
					for(OWLNamedClass pc : pclasses) {
						allConcepts.add(pc.getLocalName());
					}
					
				}
				
				StringBuffer instancesRepr= new StringBuffer();
				for(String elem : allConcepts){
					instancesRepr.append(elem+" ");
				}
				
				Document d = new Document();
				d.add(new Field("id", docName, Field.Store.YES, Field.Index.NOT_ANALYZED));
				d.add(new Field("concepts", instancesRepr.toString(), Field.Store.YES, Field.Index.ANALYZED));
				
				writer.addDocument(d);
			}
			writer.close();
		}
		catch (IOException ioe){ioe.printStackTrace();}
	}
	/**
	 * This method actuates the optimization filter for the search phase (conceptual search only)
	 * @param queryAnnot the query annotation
	 * @param originalCorpus the full corpus
	 * @param limit the size of the filter
	 * @return a reduced Corpus of size {@code limit}
	 */
	public static Corpus getFilteredCorpus(DocumentAnnotation queryAnnot, Corpus originalCorpus, int limit){
		Corpus retCorpus = new Corpus(true);
		try{
			String pathEnd = "auxindex";
			File index_dir = new File(OTR.getTemporaryDirectory()+pathEnd);
			Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_29);
			IndexReader reader = IndexReader.open(FSDirectory.open(index_dir),true);
			
			QueryParser qparser = new QueryParser(Version.LUCENE_29, "concepts", analyzer);
			IndexSearcher iSearch = new IndexSearcher(reader);
			StringBuffer searchStr =  new StringBuffer();
			HashSet<OWLIndividual> annotations = queryAnnot.getConceptInstances();
			HashSet<String> searchTerms = new HashSet<String>(); //don't want repetitions
			for(OWLIndividual ann: annotations){
				//System.err.println("query annotation: "+ann.getRDFType().getLocalName());
				searchTerms.add(ann.getRDFType().getLocalName()+" ");
				//searchStr.append(ann.getRDFType().getLocalName()+" ");
				Collection<OWLNamedClass> ancestors = ann.getRDFType().getNamedSuperclasses(false); //false:only direct ancestors
				for(OWLNamedClass c : ancestors){
					String cName = c.getLocalName();
					if(cName.equals("Thing")){
						Collection<OWLNamedClass> sons = ann.getRDFType().getNamedSubclasses(false); //only direct subclasses
						for(OWLNamedClass s : sons){
							String sName = s.getLocalName();
							searchTerms.add(sName);
						}
					} else {
						searchTerms.add(cName);
					}
					//searchStr.append(c.getLocalName()+" ");
				}
				
			}
			for(String s : searchTerms){
				searchStr.append(s+" ");
			}
			
			System.err.println("searching Lucene for "+searchStr);
			Query qDesc = qparser.parse(searchStr.toString());
			
			TopDocs results = iSearch.search(qDesc, limit);
			ScoreDoc[] hits = results.scoreDocs;
			int numTotalHits = results.totalHits;
			
			if(numTotalHits > 0) {
				HashSet<String> filteredDocumentNames = new HashSet<String>(); 
				for(ScoreDoc sd : hits){
					Document doc =iSearch.doc(sd.doc);
					String doc_id = doc.get("id");
					//get documentannotation using id and add it to the filtered corpus
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
		return retCorpus;
	}
}
