package obir.ir;


import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import obir.ir.indexing.SemanticIndexing;
import obir.otr.Entity;
import obir.otr.OTR;
import obir.otr.ObirProject;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.TermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermPositions;
import org.apache.lucene.search.spans.NearSpansUnordered;
import org.apache.lucene.search.spans.SpanNearQuery;
import org.apache.lucene.search.spans.SpanOrQuery;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanTermQuery;
import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.search.spans.TermSpans;
import org.apache.lucene.store.FSDirectory;

import edu.stanford.smi.protegex.owl.model.OWLNamedClass;

/**
 * Classe qui s'occupe de la projection des termes
 * @author Axel Reymonet
 */
public class SearchFiles {

	/**
	 * Default value for term span
	 */
	public static int defaultSlop = 6;

	/**
	 * Static method to get a string with correct offsets (i.e. those really used in the document panel) from an HTML-formatted string.
	 * @param htmlContent the string potentially containing HTML tags
	 * @return the string with no tags but correct posisitions
	 */
	public static String transformIntoStringWithCorrectPositions(String htmlContent)
	{
		htmlContent = htmlContent.replaceAll("( |\t)+"," ");
		htmlContent = htmlContent.replaceAll("<br> ", "<br>");
		htmlContent = htmlContent.replaceAll(" <br>", "<br>");
		htmlContent = htmlContent.replaceAll("<br/> ", "<br/>");
		htmlContent = htmlContent.replaceAll(" <br/>", "<br/>");
		htmlContent = htmlContent.replaceAll("<title>[^<]*</title>", "__");
		htmlContent = htmlContent.replaceAll("<html>", "");
		htmlContent = htmlContent.replaceAll("</*b>", "");
		htmlContent = htmlContent.replaceAll("<[^>]*>", "_");
		//		htmlContent = htmlContent.replaceAll("( )*\n","");
		htmlContent = htmlContent.replaceAll("( )*\n"," ");

		htmlContent = htmlContent.replaceAll("\r","");
		return htmlContent;
	}

	/**
	 * Computes, from its HTML content, the field offset in the displayed document
	 * @param htmlContent the HTML content of the current document
	 * @param symptomField the field whose offset has to be computed
	 * @return the appropriate offset
	 */
	public static int computeHTMLSymptomOffset(String htmlContent,String symptomField)
	{
		int rightAfterSymptom = htmlContent.indexOf(symptomField+"_")+symptomField.length();
		int len=1;
		while ((rightAfterSymptom+len<=htmlContent.length())&&htmlContent.substring(rightAfterSymptom,rightAfterSymptom+len).replaceAll("\\s","").replaceAll("_", "").length()==0)
			len++;

		return(rightAfterSymptom+len-1);
	}

	public static ArrayList<Entity> projectOnQuery(File query, String language, String xmlField) throws IOException
	{
		ArrayList<File> wholeFiles = new ArrayList<File>();
		wholeFiles.add(query);
		ArrayList<File> allFiles = new ArrayList<File>();
		allFiles.add(query);
		HashSet<String> fields = new HashSet<String>();
		fields.add(xmlField);
		File indexDir = new File(new File(OTR.getTemporaryDirectory()+"query"),language);
		HashMap<File,HashMap<String,ArrayList<Entity>>> result = processByField(wholeFiles, allFiles, new HashSet<String>(), language, fields, -1, indexDir);
		ArrayList<Entity> entities = result.get(query).get(xmlField); 
		entities = SemanticIndexing.computeLongestEntities(entities,null);
		return entities;
	}

	public static HashMap<File,HashMap<String,ArrayList<Entity>>> projectOnCorpusByField(ArrayList<File> indexWholeFiles, ArrayList<File> allFiles, Set<String> necessaryTermsToProject, String language, Set<String> xmlFields) throws IOException
	{
		return (processByField(indexWholeFiles, allFiles, necessaryTermsToProject, language, xmlFields, -1, new File(OTR.getTemporaryDirectory()+"index/"+language)));
	}

	public static HashMap<File,HashMap<String,ArrayList<Entity>>> processByField(ArrayList<File> indexWholeFiles, ArrayList<File> allFiles, Set<String> necessaryTermsToProject, String language, Set<String> xmlFields, int forcedSlop, File luceneIndexDirectory) throws IOException
	{
		HashMap<String,HashSet<String>> allTerms = new HashMap<String, HashSet<String>>();

		for (String term:ObirProject.getOTR().getAllTermNames(language))
		{
			allTerms.put(term, SemanticIndexing.transformTermIntoQueries(ObirProject.getOWLModel().getOWLNamedClass(term)));
		}
		return processByField(indexWholeFiles, allFiles, allTerms, necessaryTermsToProject, language, xmlFields, forcedSlop, luceneIndexDirectory);
	}
	
	public static HashMap<File,HashMap<String,ArrayList<Entity>>> processByField(ArrayList<File> indexWholeFiles, ArrayList<File> allFiles, HashMap<String,String> termsToProject, String language, Set<String> xmlFields, int forcedSlop, File luceneIndexDirectory) throws IOException
	{
		HashMap<String,HashSet<String>> toProject = new HashMap<String, HashSet<String>>();

		for (String key:termsToProject.keySet())
		{
			HashSet<String> set = new HashSet<String>();
			set.add(termsToProject.get(key));
			toProject.put(key, set);
		}
		return processByField(indexWholeFiles, allFiles, toProject, new HashSet<String>(), language, xmlFields, forcedSlop, luceneIndexDirectory);
	}
	/**
	 * Method that projects concept terms to documents, field by field
	 * @param indexWholeFiles 
	 * @param allFiles
	 * @param termsToProject
	 * @param necessaryTermsToProject
	 * @param language
	 * @param xmlFields
	 * @param forcedSlop
	 * @param luceneIndexDirectory
	 * @return a map from a File to a map from a field name to the entities found in that field
	 * @throws IOException
	 */
	private static HashMap<File,HashMap<String,ArrayList<Entity>>> processByField(ArrayList<File> indexWholeFiles, ArrayList<File> allFiles, HashMap<String,HashSet<String>> termsToProject,Set<String> necessaryTermsToProject, String language, Set<String> xmlFields, int forcedSlop, File luceneIndexDirectory) throws IOException
	{
		HashMap<String,HashMap<File,ArrayList<Entity>>> tempMap = new HashMap<String, HashMap<File,ArrayList<Entity>>>();

		

		IndexReader reader = IndexReader.open(FSDirectory.open(luceneIndexDirectory),false);

		for (String field:xmlFields)
		{
			HashMap<File,ArrayList<Entity>> entityList = new HashMap<File,ArrayList<Entity>>();

			for (String exactTerm:termsToProject.keySet())
			{
				//System.err.println("projecting term \""+exactTerm+"\"...");
				boolean termFound = false;

				int slop = defaultSlop;
				if (forcedSlop>-1)
					slop = forcedSlop;
				else
				{
					OWLNamedClass term = ObirProject.getOWLModel().getOWLNamedClass(exactTerm);
					if (term.hasPropertyValue(ObirProject.getOWLModel().getOWLDatatypeProperty(OTR.TERM_SPAN)))
						slop = (Integer)term.getPropertyValue(ObirProject.getOWLModel().getOWLDatatypeProperty(OTR.TERM_SPAN));
				}

				for (String toBeParsed:termsToProject.get(exactTerm))
				{
					TokenStream requestStream = ObirProject.getIndexingProcessor().getLinguisticAnalyzer(language).tokenStream(field,new StringReader(toBeParsed));    
					ArrayList<MySpanTermQuery> qSet = new ArrayList<MySpanTermQuery>(); 

					while (requestStream.incrementToken())
					{
						String value = ((TermAttribute)requestStream.getAttribute(TermAttribute.class)).term();
						//System.err.println("-> Spotted Words \""+value+"\"");
						qSet.add(new MySpanTermQuery(new Term(field,value)));
					}

					SpanQuery[] spanQuery = new SpanQuery [qSet.size()];
					int k = 0;
					for (MySpanTermQuery stq:qSet)
					{
						spanQuery[k]=stq;
						k++;
					}


					MySpanNearQuery snQ = new MySpanNearQuery(spanQuery,slop,true);
					Spans spans = snQ.getSpans(reader);

					TokenStream lastDocStream = null;
					File lastFile = null;
					int lastNbOfWords = 0;

					while (spans.next())
					{
						Document doc = reader.document(spans.doc());
						File assocFile = new File(doc.get("path"));

						if (allFiles.contains(assocFile) && (necessaryTermsToProject.contains(exactTerm) || indexWholeFiles.contains(assocFile)))
						{
							Entity entity = new Entity();
							entity.type 	= exactTerm; 
							entity.docURL = doc.get("path");
							entity.offset = new ArrayList<Integer>();
							entity.text = new ArrayList<String>();
							entity.field = field;

							String content = doc.get(field);

							TokenStream docStream = null;
							int nbOfWords = 0;
							if (lastFile!=null && assocFile.equals(lastFile))
							{
								docStream = lastDocStream;
								nbOfWords = lastNbOfWords;
							}
							else
								docStream = ObirProject.getIndexingProcessor().getLinguisticAnalyzer(language).tokenStream(field,new StringReader(content));

							if (spans instanceof MyTermSpans)
							{
								MyTermSpans termSpan = (MyTermSpans) spans;

								while (docStream.incrementToken())
								{
									nbOfWords += ((PositionIncrementAttribute)docStream.getAttribute(PositionIncrementAttribute.class)).getPositionIncrement()-1;
									if (nbOfWords>=termSpan.start())
									{
										nbOfWords++;
										break;
									}
									nbOfWords++;
								}
								OffsetAttribute offsetAttr = ((OffsetAttribute)docStream.getAttribute(OffsetAttribute.class));

								String noTagContent = content.substring(0,offsetAttr.startOffset());
								entity.offset.add(noTagContent.length());
								entity.text.add(content.substring(offsetAttr.startOffset(),offsetAttr.endOffset()));

								finalizeEntity(entity, entityList);
								termFound = true;
								//System.err.println("Term found - nbOfWords: "+nbOfWords+" , noTagContent: "+noTagContent);
							}
							else if (spans instanceof MyNearSpansOrdered)
							{
								String betweenTermWords = "";
								int previousEndOffset = -1;
								boolean ok = true;
	
								for (Spans tSpan:((MyNearSpansOrdered)spans).getSubSpans())
								{
									MyTermSpans termSpan = (MyTermSpans) tSpan;

									boolean valuable = false;
									while (docStream.incrementToken())
									{
										nbOfWords += ((PositionIncrementAttribute)docStream.getAttribute(PositionIncrementAttribute.class)).getPositionIncrement()-1;
										if (nbOfWords>=termSpan.start())
										{
											nbOfWords++;
											valuable = true;
											break;
										}
										nbOfWords++;
									}

									if (valuable)
									{
										OffsetAttribute offsetAttr = ((OffsetAttribute)docStream.getAttribute(OffsetAttribute.class));
										String noTagContent = content.substring(0,offsetAttr.startOffset());
										entity.offset.add(noTagContent.length());
										entity.text.add(content.substring(offsetAttr.startOffset(),offsetAttr.endOffset()));

										if (previousEndOffset!=-1)
										{
											if (previousEndOffset<=offsetAttr.startOffset())
												betweenTermWords+=content.substring(previousEndOffset,offsetAttr.startOffset());
											else
											{
												ok = false;
												break;
											}
										}
										previousEndOffset=offsetAttr.endOffset();
										
										System.err.println("Term found - nbOfWords: "+nbOfWords+" , noTagContent: "+noTagContent);
									}
								}

								if (ok)
								{
									boolean punctuationMet=false;
									for (String punct:ObirProject.getIndexingProcessor().getPunctuationMarks())
										if ((!punct.contains("-"))&&(betweenTermWords.contains(punct)))
										{
											punctuationMet=true;
											break;
										}
									if (!punctuationMet)
									{
										finalizeEntity(entity,entityList);
										termFound = true;
										
									}
								}
							}

							lastDocStream = docStream;
							lastNbOfWords = nbOfWords;
							lastFile = assocFile;

						}
					}
					if (termFound)
						break;
				}
			}
			tempMap.put(field, entityList);
		}

		reader.close();

		HashMap<File,HashMap<String,ArrayList<Entity>>> potentialEntities = new HashMap<File,HashMap<String,ArrayList<Entity>>>();

		for (File file:allFiles)
		{
			HashMap<String,ArrayList<Entity>> emptyMap = new HashMap<String, ArrayList<Entity>>();
			for (String field:xmlFields)
				emptyMap.put(field, new ArrayList<Entity>());
			potentialEntities.put(file, emptyMap);
		}

		for (String field:tempMap.keySet())
		{
			for (File file:allFiles)
			{

				HashMap<String,ArrayList<Entity>> tmp = new HashMap<String, ArrayList<Entity>>();

				if (potentialEntities.containsKey(file))
					tmp = potentialEntities.get(file);

				if (tempMap.get(field).containsKey(file))
					tmp.put(field, tempMap.get(field).get(file));

				potentialEntities.put(file, tmp);
			}
		}

		return potentialEntities;
	}


	private static void finalizeEntity(Entity entity,HashMap<File,ArrayList<Entity>> foundEntitiesByFile)
	{
		boolean isEntityAlreadyFound = false;
		//System.err.println("Entity text:"+entity.text+" type: "+entity.type);
		File currentFile = new File(entity.docURL);
		ArrayList<Entity> foundEntities = new ArrayList<Entity>();
		if (foundEntitiesByFile.containsKey(currentFile))
			foundEntities = foundEntitiesByFile.get(currentFile);
		for (Entity alreadyFound:foundEntities)
		{
			if ((alreadyFound.docURL.equals(entity.docURL))&&(alreadyFound.field.equals(entity.field))&&(alreadyFound.offset.equals(entity.offset))&&(alreadyFound.type.equals(entity.type)))
			{
				isEntityAlreadyFound = true;
				break;
			}
		}

		if (!isEntityAlreadyFound)
		{
			entity.ind_name = "tempInd_"+ObirProject.getIndexingProcessor().getEntityIndice();
			ObirProject.getIndexingProcessor().incrementEntityIndice();
			foundEntities.add(entity);
			foundEntitiesByFile.put(currentFile,foundEntities);
		}
	}

	static class MySpanNearQuery extends SpanNearQuery {

		private static final long serialVersionUID = 3027334498019998252L;

		public MySpanNearQuery(SpanQuery[] clauses, int slop, boolean inOrder) {
			super(clauses, slop, inOrder);
		}

		@Override
		public Spans getSpans(final IndexReader reader) throws IOException {
			if (clauses.size() == 0)                     
				return new SpanOrQuery(getClauses()).getSpans(reader);

			if (clauses.size() == 1)                    
				return ((SpanQuery)clauses.get(0)).getSpans(reader);

			return inOrder
			? (Spans) new MyNearSpansOrdered(this,reader)
			: (Spans) new NearSpansUnordered(this, reader);
		}
	}

	static class MySpanTermQuery extends SpanTermQuery {

		private static final long serialVersionUID = -6508502074342446270L;

		public MySpanTermQuery(Term term)
		{
			super(term);
		}

		@Override
		public Spans getSpans(IndexReader reader) throws IOException {
			return new MyTermSpans(reader,term);
		}	
	}

	/**
	 * WARNING: CALLING UNOVERRIDEN METHODS MAY RESULT IN HAZARDOUS BEHAVIOURS. USE THEM AT YOUR OWN RISK!!! 
	 * @author areymone
	 */
	static class MyTermSpans extends TermSpans {

		protected boolean advanceForNext;
		protected int previousDoc = -1;
		protected int previousStart = -1;


		public MyTermSpans(IndexReader reader, Term term) throws IOException {
			super(reader.termPositions(term),term);
			advanceForNext = true;
		}

		public MyTermSpans(TermPositions positions, Term term, int _doc, int _freq,int _count,int _position) throws IOException
		{
			super(positions,term);
			doc = _doc;
			freq = _freq;
			count = _count;
			position = _position;
		}

		protected boolean reallyNext() throws IOException
		{
			previousDoc = doc;
			previousStart = position;
			if (count == freq) {
				if (!positions.next()) {
					doc = Integer.MAX_VALUE;
					return false;
				}
				doc = positions.doc();
				freq = positions.freq();
				count = 0;
			}
			position = positions.nextPosition();
			count++;
			return true;  
		}

		@Override
		public int doc()
		{
			if (advanceForNext)
				return super.doc();
			else
				return previousDoc;
		}

		@Override
		public int start()
		{
			if (advanceForNext)
				return super.start();
			else
				return previousStart;
		}

		@Override
		public int end()
		{
			return start()+1;
		}

		public boolean next() throws IOException 
		{
			if (advanceForNext)
				return reallyNext();
			else
			{
				advanceForNext = true;
				return (doc != Integer.MAX_VALUE);
			}
		}

		public void notifyNoAdvance()
		{
			advanceForNext = false;
		}

		public String toString() {
			return "spans(" + term.toString() + ")@" +
			(doc() == -1 ? "START" : (doc() == Integer.MAX_VALUE) ? "END" : doc() + "-" + start());
		}
	}	


}