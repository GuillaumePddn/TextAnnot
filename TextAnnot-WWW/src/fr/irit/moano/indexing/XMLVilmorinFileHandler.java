package fr.irit.moano.indexing;

import java.util.Vector;
import java.util.HashMap;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import java.io.File;
import java.io.IOException;
import java.util.Stack;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
/**
 * This class provides the handler for XML files of the type "Vilmorin". To be used by Lucene indexers
 * @author Davide Buscaldi
 *
 */
public class XMLVilmorinFileHandler extends DefaultHandler {
  /* A buffer for each XML element */
  protected StringBuffer contentBuffer = new StringBuffer(); //all content
  protected Vector<Integer> paragraphLimits = new Vector<Integer>(); //paragraph start points
  protected HashMap<Integer, String> paragraphs = new HashMap<Integer, String>(); //maps paragraph start point to paragraph content
  protected StringBuffer textBuffer = new StringBuffer();
  protected StringBuffer titleBuffer = new StringBuffer();
  protected StringBuffer parentBuffer = new StringBuffer();
  protected StringBuffer paragraphBuffer = new StringBuffer();
  protected String docID = new String();
  
  protected Stack<String> elemStack;
  protected Document parsedDocument;
  
  public XMLVilmorinFileHandler(File xmlFile) 
  	throws ParserConfigurationException, SAXException, IOException {
    
	// Now let's move to the parsing stuff
    SAXParserFactory spf = SAXParserFactory.newInstance();
    
    // use validating parser?
    //spf.setValidating(false);
    // make parser name space aware?
    //spf.setNamespaceAware(true);

    SAXParser parser = spf.newSAXParser();
    this.docID=xmlFile.getName();
    //System.out.println("parser is validating: " + parser.isValidating());
    try {
      parser.parse(xmlFile, this);
    } catch (org.xml.sax.SAXParseException spe) {
      System.out.println("SAXParser caught SAXParseException at line: " +
        spe.getLineNumber() + " column " +
        spe.getColumnNumber() + " details: " +
		spe.getMessage());
    }
  }

  // call at document start
  public void startDocument() throws SAXException {
	  parsedDocument=new Document();
	  elemStack=new Stack<String>();
	  paragraphLimits = new Vector<Integer>();
	  paragraphLimits.add(new Integer(0)); //this includes the taxon and parent "paragraphs"
  }

  // call at element start
  public void startElement(String namespaceURI, String localName,
    String qualifiedName, Attributes attrs) throws SAXException {

    String eName = localName;
     if ("".equals(eName)) {
       eName = qualifiedName; // namespaceAware = false
     }
     
     elemStack.addElement(eName);
     if(eName=="fiche") {
     	textBuffer.setLength(0);
     	titleBuffer.setLength(0);
     	parentBuffer.setLength(0);
     	contentBuffer.setLength(0);
     }
     if(eName=="paragraphe") {
    	paragraphLimits.add(new Integer(contentBuffer.length()));
    	paragraphBuffer.setLength(0);
     }
     
     // list the attribute(s)
     if (attrs != null) {
       for (int i = 0; i < attrs.getLength(); i++) {
         String aName = attrs.getLocalName(i); // Attr name
         if ("".equals(aName)) { aName = attrs.getQName(i); }
         // perform application specific action on attribute(s)
         // for now just dump out attribute name and value
         //System.out.println("attr " + aName+"="+attrs.getValue(i));
       }
     }
  }
  
  public void characters(char[] text, int start, int length)
    throws SAXException {
  	if(elemStack.peek().equalsIgnoreCase("taxon")){
  		titleBuffer.append(text, start, length);
  		contentBuffer.append(text, start, length);
  		paragraphBuffer.append(text, start, length);
  	} else if (elemStack.peek().equalsIgnoreCase("parent")) {
  		parentBuffer.append(text, start, length);
  		contentBuffer.append(text, start, length);
  		paragraphBuffer.append(text, start, length);
  	} else if (elemStack.peek().equalsIgnoreCase("titre") || elemStack.peek().equalsIgnoreCase("alinea") || elemStack.peek().equalsIgnoreCase("enonce") || elemStack.peek().equalsIgnoreCase("nom_maladie")) {
  		textBuffer.append(text, start, length);
  		contentBuffer.append(text, start, length);
  		paragraphBuffer.append(text, start, length);
  	}
  	
  }

  public void endElement(String namespaceURI, String simpleName,
    String qualifiedName)  throws SAXException {

    String eName = simpleName;
    if ("".equals(eName)) {
      eName = qualifiedName; // namespaceAware = false
    }
    elemStack.pop();
    if (eName.equals("fiche")){
    	paragraphLimits.add(new Integer(contentBuffer.length())); //dummy paragraph indicating the end of paragraphs
    	paragraphs.put(paragraphLimits.lastElement(), "");
    	
    	String fullText=titleBuffer.toString()+" "+parentBuffer.toString()+" "+textBuffer.toString();
    	parsedDocument.add(new Field("titre", titleBuffer.toString(), Field.Store.YES, Field.Index.NOT_ANALYZED));
    	parsedDocument.add(new Field("parent", parentBuffer.toString(), Field.Store.YES, Field.Index.NOT_ANALYZED));
    	parsedDocument.add(new Field("contenu", fullText, Field.Store.YES, Field.Index.ANALYZED));
    	parsedDocument.add(new Field("name", this.docID, Field.Store.YES, Field.Index.NOT_ANALYZED));

    }
    if (eName.equals("paragraphe") || eName.equals("parent") || eName.equals("maladie")){
    	paragraphs.put(paragraphLimits.lastElement(), paragraphBuffer.toString());
    	
    }
  }
  
  public Document getParsedDocument() {
	  return this.parsedDocument;
  }

  public Vector<Integer> getParagraphBoundaries() {
	return this.paragraphLimits;
  }

  public HashMap<Integer, String> getParagraphContents() {
	return this.paragraphs;
  }

	
}