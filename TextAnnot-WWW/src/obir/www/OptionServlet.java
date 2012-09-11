package obir.www;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import obir.otr.ObirProject;

/**
 * Implementation of the servlet that shows and updates the search options and the list of coefficients
 */
public class OptionServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
    private PrintWriter out;
    /**
     * Status of the session (if the user is logged in or not)
     */
    private String sessionStatus;
    /**
     * Status of the session (as a boolean)
     */
    private boolean httpSessionActive;
    //Properties prop=null;
    /**
     * @see HttpServlet#HttpServlet()
     */
    public OptionServlet() {
        super();
    }
    /**
     * Method that sets the appropriate session status
     * @param sess the HTTP session
     */
    private void setSessionStatus(HttpSession sess){
    	this.sessionStatus=(String)sess.getAttribute("sessionStatus");
    	if(sessionStatus==null) httpSessionActive=false;
    	else {
    		if(sessionStatus.equals("active")) httpSessionActive=true;
    		else httpSessionActive=false;
    	}
    }
    /**
     * Method that writes (in HTML) the option page
     * @param prop the plugin.properties
     */
    protected void listProps(Properties prop){
    	out.println("<div id=\"main\"><H3>Coefficients</H3>");
		if(httpSessionActive) out.println("<BR><BR><FORM ACTION=\"OptionServlet\" METHOD=\"GET\">");
		
		out.println("<TABLE cellspacing=\"2\" cellpadding=\"2\">");
		String[] coeffs=(prop.getProperty("plugin.coefficients")).split(",");
        for (String coeff : coeffs)
        {
            String[] couple = coeff.split("=");
            out.print("<TR><TD>");
            out.print("<label for=\""+couple[0]+"\">"+couple[0]+": </label></TD><TD>");
            out.print("<INPUT TYPE=\"text\" id=\""+couple[0]+"\" name=\""+couple[0]+"\" size =\"1\" value=\""+couple[1]+"\"");
            if(!httpSessionActive){
            	out.print(" disabled=\"disabled\" ");
            }
            out.println("></TD></TR>");
        }
        out.println("</TABLE>");
        if(httpSessionActive){
    		out.println("<INPUT TYPE=\"submit\" name=\"param\" VALUE=\"Modifier Coefficients\"><BR></FORM>" +
    				"</CENTER>	</div>");
    
        	out.print("<div id=\"fileload\"><H3>Charger un fichier XML:</H3>");
        	out.print("<form action=\"LoadFileServlet\" enctype=\"multipart/form-data\" method=\"post\">"+
        	"Fichier:" +
        	"<input type=\"file\" name=\"datafile\" size=\"40\"></BR>"+
        	"<input type=\"submit\" name=\"action\" value=\"Envoyer\"></form></div>");
        }
        out.println("<div id=\"parambox\"><H3>Paramètres de RI:</H3>");
        if(httpSessionActive) out.println("<FORM ACTION=\"OptionServlet\" METHOD=\"GET\">");
        
        String classic= prop.getProperty("field.searchable.classic");
        String semSearch = prop.getProperty("field.searchable.semantic");
        if (!classic.equals("") && semSearch.equals("")){
        	out.print("Recherche sémantique <input type=\"radio\" name=\"RI\" value='semantic'");
        	if(!httpSessionActive) out.print(" disabled=\"disabled\" ");
        	out.println(" /><BR>");
        	out.print("Recherche classique <input type=\"radio\" name=\"RI\" value='classic' ");
        	if(!httpSessionActive) out.print(" disabled=\"disabled\" ");
        	out.println(" checked='yes' /><BR>");
        } else {
        	out.print("Recherche sémantique <input type=\"radio\" name=\"RI\" value='semantic'");
        	if(!httpSessionActive) out.print(" disabled=\"disabled\" ");
        	out.println(" checked='yes' /><BR>");
        	out.print("Recherche classique <input type=\"radio\" name=\"RI\" value='classic' ");
        	if(!httpSessionActive) out.print(" disabled=\"disabled\" ");
        	out.println(" /><BR>");
        }
        
        String schema= prop.getProperty("plugin.partner");
        out.println("<BR>Schéma de Ponderation:<BR>");
        out.print("Arkeo (concepts) <input type=\"radio\" name=\"SP\" value='arkeo' ");
        if(!httpSessionActive) out.print(" disabled=\"disabled\" ");
        if (schema.equals(ObirProject.ARKEO)) out.println(" checked='yes' /><BR>");
        else out.println(" /><BR>");
        out.print("Moano (concepts + relations) <input type=\"radio\" name=\"SP\" value='artal' ");
        if(!httpSessionActive) out.print(" disabled=\"disabled\" ");
        if (schema.equals(ObirProject.ARTAL)) out.println(" checked='yes' /><BR>");
        else out.println(" /><BR>");
        
        out.println("<BR>");
        out.println("<BR>Algorithme de Similarité Conceptuelle:<BR>");
        
        String cptSim= prop.getProperty("plugin.conceptsim");
        out.print("Proxigénéa 1 <input type=\"radio\" name=\"SC\" value='pg1' ");
        if(!httpSessionActive) out.print(" disabled=\"disabled\" ");
        if(cptSim.equals("pg1")) out.println(" checked='yes' /><BR>");
        else out.println(" /><BR>");
        out.print("Proxigénéa 2 <input type=\"radio\" name=\"SC\" value='pg2' ");
        if(!httpSessionActive) out.print(" disabled=\"disabled\" ");
        if(cptSim.equals("pg2")) out.println(" checked='yes' /><BR>");
        else out.println(" /><BR>");
        out.print("Proxigénéa 3 <input type=\"radio\" name=\"SC\" value='pg3' ");
        if(!httpSessionActive) out.print(" disabled=\"disabled\" ");
        if(cptSim.equals("pg3")) out.println(" checked='yes' /><BR>");
        else out.println(" /><BR>");
        out.print("Wu-Palmer <input type=\"radio\" name=\"SC\" value='wp' ");
        if(!httpSessionActive) out.print(" disabled=\"disabled\" ");
        if(cptSim.equals("wp")) out.println(" checked='yes' /><BR>"); 
        else out.println(" /><BR>");
        out.println("<BR>");
        
        String luceneFilterLimit = prop.getProperty("lucene.filtersize");
        out.println("<BR>Taille du filtre Lucene:<BR>");
        out.print("<INPUT TYPE=\"text\" id=\"luclim\" name=\"luclim\" size =\"1\" value=\""+luceneFilterLimit+"\"");
        if(!httpSessionActive){
        	out.print(" disabled=\"disabled\" ");
        }
        out.println("></BR>");
        
        if(httpSessionActive){
    		out.println("<input type=\"submit\" name=\"param\" value=\"Modifier Paramètres\">");
            out.println("</FORM>");
    	}
        
        out.println("</div>");
    }

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("text/html;charset=UTF-8");
		Properties prop;
		try {
			request.setCharacterEncoding("UTF-8");
		} catch (UnsupportedEncodingException e1) {
			e1.printStackTrace();
		}
		response.setCharacterEncoding("UTF-8");

		try {
			this.out = response.getWriter();
			String type=request.getParameter("param");
			HttpSession sess = request.getSession(true);
			setSessionStatus(sess);
			
			String loginDisp;
			if(!httpSessionActive) loginDisp="Login";
			else loginDisp="Logout";
			
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
			
			/* Set properties depending on session */
			
			if(!httpSessionActive){
				prop = ObirProject.getPluginProperties();
			} else {
				prop = (Properties)sess.getAttribute("pluginProperties");
				if(prop==null) prop = ObirProject.getPluginProperties();
				sess.setAttribute("pluginProperties", prop);
			}

			/* done setting properties */
			
			if(type.equals("list")){	
				
				listProps(prop);
		        
			} else if(type.startsWith("Modifier")){
				//change parameters and show them
				Properties newProps = (Properties)prop.clone();
				if(type.endsWith("Coefficients")){
					String[] coeffs=(prop.getProperty("plugin.coefficients")).split(",");
					StringBuffer ncStr = new StringBuffer();
			        int i=0;
					for (String coeff : coeffs)
			        {
			        	String[] couple = coeff.split("=");
			        	String newVal=request.getParameter(couple[0]);
			        	ncStr.append(couple[0]+"="+newVal);
			        	i++;
			        	if(i<coeffs.length) ncStr.append(",");
			        }
			        newProps.setProperty("plugin.coefficients", ncStr.toString());
			        System.err.println("Setting coefficients : "+ncStr.toString());
				} else {
					String sType = request.getParameter("RI");
			        String classic= prop.getProperty("field.searchable.classic");
			        String semSearch = prop.getProperty("field.searchable.semantic");
			        if(sType.equals("classic") && classic.equals("")) {
			        	newProps.setProperty("field.searchable.classic", semSearch);
			        	newProps.setProperty("field.searchable.semantic", "");
			        } 
			        if(sType.equals("semantic") && semSearch.equals("")){
			        	newProps.setProperty("field.searchable.classic", "");
			        	newProps.setProperty("field.searchable.semantic", classic);
			        }
			        
			        String pp = request.getParameter("SP");
			        newProps.setProperty("plugin.partner", pp);
			        
			        String cptSim= request.getParameter("SC");
			        newProps.setProperty("plugin.conceptsim", cptSim);
			        
			        String lucLim= request.getParameter("luclim");
			        newProps.setProperty("lucene.filtersize", lucLim);
			        
			        System.err.println("Setting parameters : ");
			        System.err.println("Semantic RI: "+semSearch);
			        System.err.println("Classic RI: "+classic);
			        System.err.println("partner: "+pp);
			        System.err.println("conceptSim: "+cptSim);
				} 
		       
		        sess.setAttribute("pluginProperties", newProps);
		        
		        listProps(newProps);
			}
			out.println("</div></BODY></HTML>");
		} catch (IOException e){
			e.printStackTrace();
		}
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doGet(request, response);
	}

}
