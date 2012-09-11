package obir.www;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Properties;
import java.util.Vector;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import obir.ir.DocumentAnnotation;
import obir.otr.ObirProject;
import obir.www.annotation.TermOccurrence;
import obir.www.annotation.TermSequence;

/**
 * Servlet that implements the TextAnnot application and write the results page
 * as HTML
 * @author davide
 *
 */
public class TextVizServlet extends HttpServlet {
	private static final long serialVersionUID = -9124461778694958705L;

	/**
	 * Method called at Tomcat start to initialize all services
	 */
	public void init() throws ServletException {
		super.init();
		ServletContext context = getServletContext();
		TextVizWrapper.initServices(context);
	}
	
	/**
	 * This method is called when the servlet code is invoked on the JSP page (index.jsp or annotation.jsp)
	 * The overridden version creates the page with the results of the search or annotation request
	 * This method also checks whether the user is logged in or out to adjust the information displayed on the page
	 */
	public void doGet(HttpServletRequest request, HttpServletResponse response) {
		response.setContentType("text/html;charset=UTF-8");
		try {
			request.setCharacterEncoding("UTF-8");
		} catch (UnsupportedEncodingException e1) {
			e1.printStackTrace();
		}
		response.setCharacterEncoding("UTF-8");
		
		HttpSession sess = request.getSession(true);
		String sessionStatus=(String)sess.getAttribute("sessionStatus");
		String loginDisp;
		if(sessionStatus==null) loginDisp="Login";
		else if(sessionStatus.equals("active")) loginDisp="Logout";
		else loginDisp="Login";
		try {
			PrintWriter out = response.getWriter();
			String text=request.getParameter("text");
			String type=request.getParameter("button");
			
			Properties prop=null;
			if(sessionStatus!=null){
				if(sessionStatus.equals("active")) prop = (Properties)sess.getAttribute("pluginProperties");
			}
			if(prop==null){
				prop = ObirProject.getPluginProperties();
				if(sessionStatus!=null){
					if(sessionStatus.equals("active")) sess.setAttribute("pluginProperties", prop);
				}
			}
			
			out.println("<HTML><HEAD><TITLE>TextAnnot - Results</TITLE>" +
					"<link href='http://fonts.googleapis.com/css?family=Ubuntu+Condensed' rel='stylesheet' type='text/css'>" +
					"<link href='http://fonts.googleapis.com/css?family=Marvel' rel='stylesheet' type='text/css'>" +
					"<link href='http://fonts.googleapis.com/css?family=Marvel|Delius+Unicase' rel='stylesheet' type='text/css'>" +
					"<link href='http://fonts.googleapis.com/css?family=Arvo' rel='stylesheet' type='text/css'>" +
					"<link href='style.css' rel='stylesheet' type='text/css' media='screen' /></HEAD>");
			
			if(type.equals("Chercher")) {
				String rMode="";
				String classic= prop.getProperty("field.searchable.classic");
				String schema= prop.getProperty("plugin.partner");
				if(!classic.equals("")) rMode=" classique";
				else {
					if(schema.equals("arkeo")) rMode=" sémantique";
					else rMode = " sémantique + relations";
				}
				out.println("<BODY><div id='navwrap'> <div id='nav' class='floatleft'>"+
						"<a class='current' href='#'>Recherche "+rMode+"</a><a href='annotation.jsp'>Annotation</a></div>"+
						"<div id='nav' class='floatright'><a href=\""+loginDisp+"Page.jsp\">"+loginDisp+"</a><a href=\"OptionServlet?param=list\"><span class=\"optionsmenu\"> </span></a></div>" +
						"<div class='clear'></div></div><BR><BR>\n"+
						"<FORM ACTION=\"TextVizServlet\" METHOD=\"POST\">"+
						"<label for=\"text\">Requête: </label>" +
						"<INPUT TYPE=\"text\" id=\"textbox\" name=\"text\" value=\""+text+"\"size =\"80\">" +
						"<INPUT TYPE=\"submit\" name='button' VALUE=\"Chercher\"><BR></FORM>");
				
				//out.println("Requête: "+text+" <BR><BR><BR>\n");
				
				DocumentAnnotation queryAnnot=null;
				if(classic.equals("")) {
		        	
					out.println("<H3>Concepts:</H3>");
					out.println("<HR>");
					
					TermSequence seq = new TermSequence(text, ObirProject.getDefaultLanguage());
					
					AnnotationHandler ann_hdlr=new AnnotationHandler(sess.getId()); //creates an annotation handler with this session ID
					Vector<TermOccurrence> occurrences = ann_hdlr.getAnnotationVector(text);
					queryAnnot=ann_hdlr.getQueryAnnotation();
					
					seq.setAnnotations(occurrences);
					out.print(seq.getHTMLCode());
					
					out.println("<H3>Relations:</H3>");
					out.println("<HR>");
					Vector<Vector<String>> rels = ann_hdlr.getRelations();
					for(Vector<String> r : rels){
						out.print("( <span style='background-color:#"+seq.getColorFor(r.get(0))+"'>"+r.get(0)+"</span> ");
						out.print(r.get(1)+" ");
						out.print("<span style='background-color:#"+seq.getColorFor(r.get(2))+"'>"+r.get(2)+"</span> )");
					}
					out.println("<HR>");
					out.println("<H3>Résultats Recherche Sémantique:</H3>");
					out.println("<HR>");
				
		        } else {
		        	out.println("<HR>");
					out.println("<H3>Résultats Recherche Classique:</H3>");
					out.println("<HR>");
		        }
		        SearchHandler src_hdlr=new SearchHandler(text, queryAnnot, prop);
				Vector<String> sortedResults =  src_hdlr.getResults();
				
				int i=0;
				for(String s : sortedResults){
					if(i==0) {
						out.println("<TABLE cellpadding='5'>");
						out.println("<TR><TD><B>Pos</B></TD><TD><B>Fiche</B></TD><TD><B>Poids</B></TD>");
						if(sessionStatus != null){
					        if(sessionStatus.equals("active")){
					        	out.println("<TD><B>Explanation (QueryClass <-RelType-> DocumentClass)</B></TD>");
					        }
						}
						out.println("</TR>\n");
					}
					else {
						out.println("<TR>");
						String [] ans = s.split(":");
						String link=ans[0].trim();
						String weight=ans[1].trim();
						String matchingClasses="";
						if(ans.length > 2) {
							matchingClasses=ans[2].trim();
						}
						out.println("<TD>"+i+"</TD><TD><A HREF=\"CorpusVilmorin/"+link+"\">"+link+"</A></TD><TD>"+weight+"</TD>");
						if(sessionStatus != null){
				        	if(sessionStatus.equals("active")){
				        		out.println("<TD><div id='explanation'><p>"+matchingClasses+"</p></div></TD>");
				        	}
						}
						out.println("</TR>\n");
					}
					i++;
				}
				out.println("</TABLE>");
				out.println("</BODY></HTML>");
			} else {
				//Annotation code
				
				out.println("<BODY><div id='navwrap'> <div id='nav' class='floatleft'>"+
						"<a href='index.jsp'>Recherche</a><a class='current' href='#'>Annotation</a></div>"+
						"<div id='nav' class='floatright'><a href=\""+loginDisp+"Page.jsp\">"+loginDisp+"</a><a href=\"OptionServlet?param=list\"><span class=\"optionsmenu\"> </span></a></div>" +
						"<div class='clear'></div></div><BR><BR>\n"+
						"<FORM ACTION=\"TextVizServlet\" METHOD=\"POST\">"+
						"<label for=\"text\">Texte: </label><BR>" +
						"<TEXTAREA id=\"textbox\" name=\"text\" cols =\"60\" rows=\"6\">"+text+"</TEXTAREA><BR>" +
						"<INPUT TYPE=\"submit\" name='button' VALUE=\"Annoter\"><BR></FORM>");
				out.println("<H3>Concepts:</H3>");
				out.println("<HR>");
				TermSequence seq = new TermSequence(text, ObirProject.getDefaultLanguage());
				
				AnnotationHandler ann_hdlr=new AnnotationHandler(sess.getId()); //creates an annotation handler with this session ID
				Vector<TermOccurrence> occurrences = ann_hdlr.getAnnotationVector(text);
				
				seq.setAnnotations(occurrences);
				out.print(seq.getHTMLCode());
				
				out.println("<BR><H3>Relations:</H3>");
				out.println("<HR>");
				Vector<Vector<String>> rels = ann_hdlr.getRelations();
				for(Vector<String> r : rels){
					out.print("( <span style='background-color:#"+seq.getColorFor(r.get(0))+"'>"+r.get(0)+"</span> ");
					out.print(r.get(1)+" ");
					out.print("<span style='background-color:#"+seq.getColorFor(r.get(2))+"'>"+r.get(2)+"</span> )");
				}
				out.println("<HR>");
				
				out.println("</BODY></HTML>");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
	/**
	 * This method forwards the request to the doGet method
	 */
	public void doPost(HttpServletRequest request, HttpServletResponse response){
		doGet(request, response);
	}
}
