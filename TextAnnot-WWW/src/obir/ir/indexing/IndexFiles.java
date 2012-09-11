package obir.ir.indexing;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import obir.ir.Corpus;
import obir.ir.DocumentAnnotation;
import obir.ir.SearchFiles;
import obir.ir.analysis.CustomAnalyzer;
import obir.otr.ObirProject;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.FSDirectory;
import org.xml.sax.SAXException;

import edu.stanford.smi.protegex.owl.model.OWLIndividual;
import edu.stanford.smi.protegex.owl.model.OWLObjectProperty;

/**
 * Convenience class including methods to index one given text file or all text files under a directory. 
 * @author Axel Reymonet
 *
 */
public class IndexFiles {

	public static void scanCorpusFile(File file, boolean normalMode)
	{
		HashSet<String> xmlFields = Corpus.getSemanticFields();
		scanCorpusFile(file, xmlFields, normalMode);
	}

	/**
	 * Method to index "classically" (with Lucene) an HTML/XML file (in order to prepare for term projection). All previous index is deleted  
	 * @param filepath the absolute path to the file to index
	 */
	public static void scanCorpusFile(File toIndex, HashSet<String> xmlFields, boolean normalMode)
	{
		String pathEnd = "index";
		if (!normalMode)
			pathEnd = "amas/index";
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
			IndexWriter writer = new IndexWriter(FSDirectory.open(index_dir), ObirProject.getIndexingProcessor().getAnalyzer(toIndex),IndexWriter.MaxFieldLength.LIMITED);
			CustomAnalyzer anal = ObirProject.getIndexingProcessor().getAnalyzer(toIndex);
			addDocumentToIndex(toIndex, xmlFields, writer, anal);
			writer.close();
		}
		catch (IOException ioe){ioe.printStackTrace();}
	}
	/**
	 * This method add files from the corpus to the Lucene index 
	 * @param filesToIndex a map language -> file list
	 * @param lingIndexWriters a map language -> index writer
	 */
	public static void addCorpusFilesToIndex(HashMap<String,ArrayList<File>> filesToIndex, HashMap<String,IndexWriter> lingIndexWriters)
	{

		HashSet<String> xmlFields = Corpus.getSemanticFields();

		for (String lang:filesToIndex.keySet())
		{
			try
			{
				IndexWriter writer = lingIndexWriters.get(lang);
				for (File toIndex:filesToIndex.get(lang))
				{
					addDocumentToIndex(toIndex, xmlFields, writer,ObirProject.getIndexingProcessor().getAnalyzer(lang));
				}
				writer.commit();
			}
			catch (IOException ioe){ioe.printStackTrace();}
		}

	}
	/**
	 * Adds a document to a previously created index
	 * @param toIndex file to be indexed
	 * @param xmlFields indexing fields
	 * @param writer the lucene writer
	 * @param analyzer the lucene analyzer
	 * @throws IOException
	 */
	public static void addDocumentToIndex(File toIndex, HashSet<String> xmlFields, IndexWriter writer, CustomAnalyzer analyzer) throws IOException
	{
		Document d = createDocumentFromFile(toIndex, xmlFields);
		if (analyzer!=null)
			writer.addDocument(d,analyzer);
		else
			writer.addDocument(d);
	}
	/**
	 * Returns a document (Lucene) representation for the file passed as input
	 * @param toIndex
	 * @param xmlFields
	 * @return
	 * @throws IOException
	 */
	private static Document createDocumentFromFile(File toIndex,HashSet<String> xmlFields) throws IOException
	{
		Document d=new Document();
		try
		{
			d.add(new Field("path",toIndex.getAbsolutePath(),Field.Store.YES,Field.Index.NOT_ANALYZED));

			for (String field:xmlFields)
			{
				String symptContent = obir.misc.XMLToHTML.parseXMLField(toIndex,field);
				symptContent = obir.ir.SearchFiles.transformIntoStringWithCorrectPositions(symptContent);
				d.add(new Field(field,symptContent,Field.Store.YES,Field.Index.ANALYZED,Field.TermVector.WITH_POSITIONS_OFFSETS));
			}

			if (xmlFields.size()==0) //method called by AMAS
			{
				String content="";
				BufferedReader buffRead = new BufferedReader(new FileReader(toIndex));
				String line = buffRead.readLine();
				while (line!=null)
				{
					content+=line+"\n";
					line = buffRead.readLine();
				}
				d.add(new Field("text",content,Field.Store.YES,Field.Index.ANALYZED,Field.TermVector.WITH_POSITIONS_OFFSETS));
			}
			
		}
		catch (ParserConfigurationException pce){pce.printStackTrace();}
		catch (SAXException se){se.printStackTrace();}
		return d;
	}
	/**
	 * Removes a document from the (Lucene) index
	 * @param toIndex
	 * @param writer
	 * @throws IOException
	 */
	public static void removeDocumentFromIndex(File toIndex, IndexWriter writer) throws IOException
	{
		Term documentPath = new Term("path", toIndex.getAbsolutePath());
		writer.deleteDocuments(documentPath);
		writer.commit();
	}
	/**
	 * Updates a document in the (Lucene) index
	 * @param toIndex
	 * @param xmlFields
	 * @param writer
	 * @param analyzer
	 * @throws IOException
	 */
	public static void updateDocumentInIndex(File toIndex, HashSet<String> xmlFields, IndexWriter writer, CustomAnalyzer analyzer) throws IOException
	{
		Term documentPath = new Term("path", toIndex.getAbsolutePath());
		Document newDoc = createDocumentFromFile(toIndex, xmlFields);
		writer.updateDocument(documentPath, newDoc);
		writer.commit();
	}
	
	/**
	 * Method to index "classically" a set of XML files (for non semantic IR). All previous indexes are deleted  
	 * @param corpus the current corpus
	 * @param projectPath path where the indexes are going to be kept
	 * @param analByLang required analyzers for all available languages
	 * @param onlySemanticFields boolean to choose whether to index only the "semantic" fields or also the classic ones
	 */
	@SuppressWarnings("unchecked")
	public static void scanWholeCorpus(Corpus corpus, String projectPath, HashMap<String,CustomAnalyzer> analByLang, HashMap<String, HashMap<String,String>> correspondingFilesByLang)//, Map<String,String> htmlFieldNamesByXMLFields)
	{
		HashSet<String> availableLanguages = new HashSet<String>();
		for (String l:analByLang.keySet())
			availableLanguages.add(l);

		projectPath = projectPath.replaceAll("\\\\", "/"); 
		if (!projectPath.endsWith("/"))
			projectPath+= "/";

		HashSet<String> fieldsToIndex = Corpus.getSemanticFields();
		if (correspondingFilesByLang!=null && !correspondingFilesByLang.isEmpty())
			fieldsToIndex.addAll(Corpus.getClassicFields());

		for (String xmlField:fieldsToIndex)//.keySet())
		{
			for (String lang:availableLanguages)
			{
				File index_dir = new File(projectPath+"index_"+xmlField+"/"+lang);
				if (index_dir.exists())
				{
					String[] files = index_dir.list();
					for (String f:files)
					{
						File file = new File (index_dir.getAbsolutePath(),f);
						file.delete();
					}
					index_dir.delete();
				}
			}
		}

		try 
		{
			HashMap<String,HashMap<String,IndexWriter>> writers = new HashMap<String, HashMap<String,IndexWriter>>();
			for (String xmlField:fieldsToIndex)
			{
				HashMap<String,IndexWriter> writersByLang = new HashMap<String, IndexWriter>();
				for (String lang:analByLang.keySet())
				{
					writersByLang.put(lang,new IndexWriter(FSDirectory.open(new File(projectPath+"index_"+xmlField+"/"+lang)), analByLang.get(lang), true,IndexWriter.MaxFieldLength.LIMITED));
				}
				writers.put(xmlField, writersByLang);
			}
			DocumentBuilderFactory xmlFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder xmlBuilder = xmlFactory.newDocumentBuilder();

			for (DocumentAnnotation docAnnot: corpus.getValidatedDocuments())
			{
				String currentLang = docAnnot.getLanguage();
				HashSet<String> secondLangs = (HashSet<String>)availableLanguages.clone();
				secondLangs.remove(currentLang);

				File fichier = new File(corpus.getDirectory(),docAnnot.getDocumentName());
				org.w3c.dom.Document xmlDoc = xmlBuilder.parse(fichier);
				
				
				for (String xmlField:fieldsToIndex)
				{
					Document d = new Document();
						if (xmlDoc.getElementsByTagName(xmlField).getLength()>0)
					{
						String content = xmlDoc.getElementsByTagName(xmlField).item(0).getTextContent();
						d.add(new Field(xmlField,content,Field.Store.YES,Field.Index.ANALYZED,Field.TermVector.WITH_POSITIONS_OFFSETS));
						d.add(new Field("path",fichier.getAbsolutePath(),Field.Store.YES,Field.Index.NO));
						writers.get(xmlField).get(docAnnot.getLanguage()).addDocument(d);
					}
				}

				if (correspondingFilesByLang!=null && !correspondingFilesByLang.isEmpty())
				{
					for (String secondLang:secondLangs)
					{
						String secondFilename = correspondingFilesByLang.get(secondLang).get(docAnnot.getDocumentName());
						if (secondFilename!=null)
						{
							File secondFile = new File(new File(corpus.getDirectory(),secondLang),secondFilename); 

							org.w3c.dom.Document secondXMLDoc = xmlBuilder.parse(secondFile);
							for (String xmlField:Corpus.getClassicFields())
							{
								Document d = new Document();
								d.add(new Field("path",fichier.getAbsolutePath(),Field.Store.YES,Field.Index.NO));

								if (secondXMLDoc.getElementsByTagName(xmlField).getLength()>0)
								{
									String content = secondXMLDoc.getElementsByTagName(xmlField).item(0).getTextContent();
									d.add(new Field(xmlField,content,Field.Store.YES,Field.Index.ANALYZED,Field.TermVector.WITH_POSITIONS_OFFSETS));
									writers.get(xmlField).get(secondLang).addDocument(d);
								}
							}
						}
					}
				}

			}

			for (String key:writers.keySet())
				for (String lang:writers.get(key).keySet())
					writers.get(key).get(lang).close();
			//now create auxiliary indexes
			for (String xmlField:fieldsToIndex)
			{
				AnnotationIndexFactory.create(xmlField); //TODO: gestire field differenti
				RelationIndexFactory.create(xmlField);
			}
			
		}
		catch (ParserConfigurationException fnf){fnf.printStackTrace();}
		catch (SAXException fnf){fnf.printStackTrace();}
		catch (FileNotFoundException fnf){fnf.printStackTrace();}
		catch (IOException ioe){ioe.printStackTrace();}
	}

}
