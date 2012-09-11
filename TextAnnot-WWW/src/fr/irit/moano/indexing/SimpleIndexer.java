package fr.irit.moano.indexing;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.fr.FrenchAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriter.MaxFieldLength;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

import java.io.File;
import java.io.IOException;
import java.util.Date;
/**
 * This class creates a standard Lucene index (not to be used with TextAnnot)
 * @author Davide Buscaldi
 *
 */
public class SimpleIndexer {
	
	private SimpleIndexer() {}
	
		/** Index all text files under a directory, using the standard Lucene Analyzer */
	  public static void main(String[] args) {
	    String usage = "java fr.irit.moano.indexing.SimpleIndexer"
	                 + " [-index INDEX_PATH] -docs DOCS_PATH \n\n"
	                 + "This indexes the documents in DOCS_PATH, creating a Lucene index"
	                 + "in INDEX_PATH that can be searched with SearchFiles";
	    String indexPath = "index";
	    String docsPath = null;
	    
	    for(String arg : args){
	    	System.err.println(arg);
	    }
	    
	    for(int i=0;i<args.length;i++) {
	      if ("-index".equals(args[i])) {
	        indexPath = args[i+1];
	        i++;
	      } else if ("-docs".equals(args[i])) {
	        docsPath = args[i+1];
	        i++;
	      } 
	    }

	    if (docsPath == null) {
	      System.err.println("Usage: " + usage);
	      System.exit(1);
	    }

	    final File docDir = new File(docsPath);
	    if (!docDir.exists() || !docDir.canRead()) {
	      System.out.println("Document directory '" +docDir.getAbsolutePath()+ "' does not exist or is not readable, please check the path");
	      System.exit(1);
	    }
	    
	    Date start = new Date();
	    try {
	      System.out.println("Indexing to directory '" + indexPath + "'...");

	      Directory dir = FSDirectory.open(new File(indexPath));
	      Analyzer analyzer = new FrenchAnalyzer(Version.LUCENE_29);

	      IndexWriter writer = new IndexWriter(dir, analyzer, MaxFieldLength.UNLIMITED);
	      indexDocs(writer, docDir);

	      writer.close();

	      Date end = new Date();
	      System.out.println(end.getTime() - start.getTime() + " total milliseconds");

	    } catch (IOException e) {
	      System.out.println(" caught a " + e.getClass() +
	       "\n with message: " + e.getMessage());
	    }
	  }

	  /**
	   * Indexes the given file using the given writer, or if a directory is given,
	   * recurses over files and directories found under the given directory.
	   * 
	   * NOTE: This method indexes one document per input file.  This is slow.  For good
	   * throughput, put multiple documents into your input file(s).  An example of this is
	   * in the benchmark module, which can create "line doc" files, one document per line,
	   * using the
	   * <a href="../../../../../contrib-benchmark/org/apache/lucene/benchmark/byTask/tasks/WriteLineDocTask.html"
	   * >WriteLineDocTask</a>.
	   *  
	   * @param writer Writer to the index where the given file/dir info will be stored
	   * @param file The file to index, or the directory to recurse into to find files to index
	   * @throws IOException
	   */
	  static void indexDocs(IndexWriter writer, File file)
	    throws IOException {
	    // do not try to index files that cannot be read
	    if (file.canRead()) {
	      if (file.isDirectory()) {
	        String[] files = file.list();
	        // an IO error could occur
	        if (files != null) {
	          for (String f : files) {
	            indexDocs(writer, new File(file, f));
	          }
	        }
	      } else {
	    	if(file.getName().endsWith(".xml")) {
	    		System.out.println("indexing " + file);
	            try {
	            	XMLVilmorinFileHandler hdlr = new XMLVilmorinFileHandler(file);
	            	Document doc=hdlr.getParsedDocument();
	            	writer.addDocument(doc);
	            } catch (Exception e) {
	            	e.printStackTrace();
	            } 
		      }
		    }
	    }
	  }
	
}
