package obir.www;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.util.HashSet;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import obir.ir.DocumentAnnotation;
import obir.otr.ObirProject;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import edu.stanford.smi.protegex.owl.jena.JenaOWLModel;

/**
 * Servlet that loads a new file into the corpus and annotates it, updating the RTO
 */
//FIXME: it does not update the auxiliary indexes
public class LoadFileServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
       
    /**
     * @see HttpServlet#HttpServlet()
     */
    public LoadFileServlet() {
        super();
    }
    
    /**
     * Checks whether the uploaded document is compatible with the model
     * @param xmlFile the uploaded document
     * @return true if the uploaded document is compatible
     */
    protected boolean checkDocumentFormat(File xmlFile){
    	try {
	    	DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
	    	DocumentBuilder db = factory.newDocumentBuilder();
	
	    	//parse file into DOM
	    	Document doc = db.parse(xmlFile);
	    	DOMSource source = new DOMSource(doc);
	
	    	//now use a transformer to add the DTD element
	    	TransformerFactory tf = TransformerFactory.newInstance();
	    	Transformer transformer = tf.newTransformer();
	    	transformer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, getServletContext().getRealPath("plugins")+"/proto/document_model.dtd");
	    	System.err.println(getServletContext().getRealPath("plugins")+"/proto/document_model.dtd");
	    	StringWriter writer = new StringWriter();
	    	StreamResult result = new StreamResult(writer);
	    	transformer.transform(source, result);

	    	//finally parse the result. 
	    	//this will throw an exception if the doc is invalid
    
			db.parse(new InputSource(new StringReader(writer.toString())));
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
    	return true;
    }
	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("text/html;charset=UTF-8");
		// Check that we have a file upload request
		boolean isMultipart = ServletFileUpload.isMultipartContent(request);
		// Create a factory for disk-based file items
		FileItemFactory factory = new DiskFileItemFactory();

		// Create a new file upload handler
		ServletFileUpload upload = new ServletFileUpload(factory);
		
		HttpSession sess = request.getSession(true);
		String sessionStatus=(String)sess.getAttribute("sessionStatus");
		String loginDisp;
		if(sessionStatus==null) loginDisp="Login";
		else if(sessionStatus.equals("active")) loginDisp="Logout";
		else loginDisp="Login";
		
		PrintWriter out=response.getWriter();
		out.println("<HTML><HEAD><TITLE>TextAnnot - Options</TITLE>" +
				"<link href='http://fonts.googleapis.com/css?family=Ubuntu+Condensed' rel='stylesheet' type='text/css'>" +
				"<link href='http://fonts.googleapis.com/css?family=Marvel' rel='stylesheet' type='text/css'>" +
				"<link href='http://fonts.googleapis.com/css?family=Marvel|Delius+Unicase' rel='stylesheet' type='text/css'>" +
				"<link href='http://fonts.googleapis.com/css?family=Arvo' rel='stylesheet' type='text/css'>" +
				"<link href='style.css' rel='stylesheet' type='text/css' media='screen' /></HEAD>");
		out.println("<!-- JSP XML --><jsp:directive.page contentType=\"text/html; charset=UTF-8\" />" +
				"<!-- JSF/Facelets XHTML --><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />" +
				"</head><body>" +
				"<div id=\"navwrap\"><div id=\"nav\" class=\"floatleft\"><a href=\"index.jsp\">Recherche</a><a href=\"annotation.jsp\">Annotation</a></div>" +
				"<div id='nav' class='floatright'><a href=\""+loginDisp+"Page.jsp\">"+loginDisp+"</a><a href=\"OptionServlet?param=list\"><span class=\"optionsmenu\"> </span></a></div>" +
				"</div><div class=\"clear\"></div></div><div id=\"wrap\">");
		
		// Parse the request
		try {
			 if(!isMultipart){
				out.write("<center>error - Request was not multipart!</center>"); 
			 	return; 
			 } 
			 
			List<FileItem> items = upload.parseRequest(request);
			for(FileItem item : items) {
			    if (item.isFormField()) {
			        //do something for a formfield
			    } else {
			    	//do something for a file
			    	//String fieldName = item.getFieldName();
			        String fileName = item.getName();
			        if(fileName.endsWith(".xml")){
				        System.err.println("Loading file: "+fileName);
				        /*String contentType = item.getContentType();
				        boolean isInMemory = item.isInMemory();
				        long sizeInBytes = item.getSize();
				        */
				        
				        //TODO: read file and add it to disk, annotate the file
				        String corpusDir=getServletContext().getRealPath("/CorpusVilmorin");
				        
				        File savedFile = new File(getServletContext().getRealPath("/CorpusVilmorin")+"/"+item.getName());
				        System.err.println("writing file to: "+getServletContext().getRealPath("/CorpusVilmorin")+"/"+item.getName());
				        
				        //out.write("<center>writing file to: "+getServletContext().getRealPath("/CorpusVilmorin")+"/"+item.getName()+"...</center>");
				        item.write(savedFile);
				        //out.flush();
				        
				        boolean goAhead=checkDocumentFormat(savedFile);
				        if(goAhead){
					        out.write("<BR><BR><center>updating index...</center>");
					        out.flush();
							ObirProject.getIndexingProcessor().doIndexing();
							
							out.write("<BR><BR><center>validating annotations...</center>");
					        out.flush();
							File corpusDirFile = ObirProject.getCorpus().getDirectory();
							String[] filesInCorpusDir = corpusDirFile.list();
							int length = filesInCorpusDir.length;
							for (int i = 0; i < length; i++) 
							{				
								File currentFile = new File(corpusDir, filesInCorpusDir[i]);
								if (currentFile.isFile() && filesInCorpusDir[i].endsWith(".xml")) 
								{
									DocumentAnnotation current = ObirProject.getCorpus().getDocument(filesInCorpusDir[i]); //QUI CONTROLLA SE IL DOCUMENTO E' GIA' STATO ANNOTATO
									if (current == null) 
									{
										ObirProject.getIndexingProcessor().removeDocumentFromIndex(current);
										ObirProject.getCorpus().validateDocument(current);
									}
								}
							}
							out.write("<BR><BR><center>updating OWL file...</center>");
					        out.flush();
							ObirProject.doSaving(true);
							
							out.write("<BR><BR><center>Done</center>");
					        out.flush();
				        } else {
				        	out.write("<BR><BR><center>XML format error (document not in UTF-8 or wrong DTD)</center>");
					        out.flush();
					        savedFile.delete();
				        }
			        } else {
			        	 out.write("<BR><BR><center>the file is not an xml document</center>");
					     out.flush();
			        }
			    }
			}
			
		} catch (FileUploadException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		String type=request.getParameter("datafile");
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doGet(request, response);
	}

}
