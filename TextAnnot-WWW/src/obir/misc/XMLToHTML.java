package obir.misc;

//Imported JAXP Transformer (TraX) classes
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Vector;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.SAXException;

/**
 * Convenience class gathering several methods to transform the content of an XML file into HTML 
 * @author Axel Reymonet
 */
public class XMLToHTML {

	/**
	 * Static method translating the whole content of an XML file into HTML with the help of a XSLT file
	 * @param xmlFile the XML file to translate
	 * @param language the language used in the corpus
	 * @return an HTML-formatted string
	 * @throws TransformerException such an exception is thrown if the system cannot read from the XSLT file
	 */
	public static String fromXMLToHTML(File xmlFile,String language) throws TransformerException
	{
		ByteArrayOutputStream bytestream = new ByteArrayOutputStream();

		TransformerFactory tFactory = TransformerFactory.newInstance();
		Transformer transformer = tFactory.newTransformer(new StreamSource("plugins/proto/stylesheet_"+language+".xslt"));
		transformer.transform(new StreamSource(xmlFile),new StreamResult(bytestream));
		String result = correctAccents(bytestream.toString());
		result = result.replaceAll("\r\n", "<br>");
		result = result.replaceAll("><br>", ">");
		result = result.replaceAll("<br><", "<");
		return (result);
	}

	/**
	 * Static method to obtain the textual content of an XML node
	 * @param node the XML node containing some text
	 * @return the corresponding text
	 */
	private static String getFieldTextualContent(Node node)
	{
		String result = "";
		if (node!=null)
		{
			NodeList childNodes = node.getChildNodes();
			Vector<Element> childrenElements = new Vector<Element>();
			String directTextValue = "";
			for (int i=0;i<childNodes.getLength();i++)
			{
				Node child = childNodes.item(i);
				if (child instanceof Element)
				{
					childrenElements.add((Element)child);
				}
				else if (child instanceof Text)
				{
					directTextValue += child.getTextContent();
				}
			}
			
			if (childrenElements.size()>0)
			{
				for (Element elt:childrenElements)
				{
					if (!result.isEmpty()&&!result.endsWith("\n"))
						result += "\n";
					result += getFieldTextualContent(elt);
				}
			}
			else
			{
				result = removeBeginningWhitespaces(directTextValue);
			}
			
			
			
//			if (childNodes.getLength()==0)
//			{
//				//			if (!node.getTextContent().equals("\n"))
//				result = node.getTextContent();
//				result = removeBeginningWhitespaces(result);
//			}
//			else
//				for (int i=0;i<childNodes.getLength();i++)
//				{
//					Node child = childNodes.item(i);
//					//				if (!child.getNodeName().equals("#text"))
//					if (child instanceof Element)
//					{
//						System.out.println();
//						if (!result.isEmpty()&&!result.endsWith("\n"))
//							result += "\n";
//						result+=getFieldTextualContent(child);
//					}
//				}
		}
		return result;
	}

	/**
	 * Static method to obtain the textual content of a given field in an XML file
	 * @param f the XML file
	 * @param field the field to get the string from
	 * @return the content of the field
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 * @throws IOException
	 */
	public static String parseXMLField(File f,String field) throws ParserConfigurationException, SAXException, IOException
	{
		DocumentBuilderFactory xmlFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder xmlBuilder = xmlFactory.newDocumentBuilder();
		org.w3c.dom.Document xmlDoc = xmlBuilder.parse(f);
//		System.out.println("language found: "+xmlDoc.getDocumentElement().getAttribute("lang"));
		String result=getFieldTextualContent(xmlDoc.getElementsByTagName(field).item(0));

		result = removeBeginningWhitespaces(result);

		return (result);
	}

	private static String removeBeginningWhitespaces(String s)
	{
		HashSet<String> whitespace = new HashSet<String>();
		whitespace.add(" ");
		whitespace.add("\t");
		whitespace.add("\n");
		whitespace.add("\r");
		whitespace.add("\f");
		while (s.length()>=1 && whitespace.contains(s.substring(0,1)))
			s = s.substring(1);
		return s;
	}


	/**
	 * Static method to correct any french accent which could be mistranslated by the parser
	 * @param s the input string
	 * @return the corresponding string, correctly accentuated
	 */
	public static String correctAccents (String s)
	{
		String result = s;
		result = result.replaceAll("&Agrave;", "À");
		result = result.replaceAll("&Acirc;", "Â");
		result = result.replaceAll("&Auml;", "Ä");
		result = result.replaceAll("&Egrave;", "È");
		result = result.replaceAll("&Eacute;", "É");
		result = result.replaceAll("&Ecirc;", "Ê");
		result = result.replaceAll("&Euml;", "Ë");
		result = result.replaceAll("&Icirc;", "Î");
		result = result.replaceAll("&Iuml;", "Ï");
		result = result.replaceAll("&Ocirc;", "Ô");
		result = result.replaceAll("&OElig;", "Œ");
		result = result.replaceAll("&Ugrave;", "Ù");
		result = result.replaceAll("&Ucirc;", "Û");
		result = result.replaceAll("&Uuml;", "Ü");
		result = result.replaceAll("&Yuml;", "Ÿ");
		result = result.replaceAll("&agrave;", "à");
		result = result.replaceAll("&acirc;", "â");
		result = result.replaceAll("&auml;", "ä");
		result = result.replaceAll("&egrave;", "è");
		result = result.replaceAll("&eacute;", "é");
		result = result.replaceAll("&ecirc;", "ê");
		result = result.replaceAll("&euml;", "ë");
		result = result.replaceAll("&icirc;", "î");
		result = result.replaceAll("&iuml;", "ï");
		result = result.replaceAll("&ocirc;", "ô");
		result = result.replaceAll("&ouml;", "ö");
		result = result.replaceAll("&Ouml;", "Ö");
		result = result.replaceAll("&oelig;", "œ");
		result = result.replaceAll("&ugrave;", "ù");
		result = result.replaceAll("&ucirc;", "û");
		result = result.replaceAll("&uuml;", "ü");
		result = result.replaceAll("&yuml;", "ÿ");
		result = result.replaceAll("&Ccedil;", "Ç");
		result = result.replaceAll("&ccedil;", "ç");
		result = result.replaceAll("&laquo;", "«");
		result = result.replaceAll("&raquo;", "»");
		result = result.replaceAll("&lsaquo;", "<");
		result = result.replaceAll("&rsaquo;", ">");
		result = result.replaceAll("&euro;", "€");
		result = result.replaceAll("&deg;", "°");
		result = result.replaceAll("&lt;", "<");
		result = result.replaceAll("&gt;", ">");
		return result;
	}
}
