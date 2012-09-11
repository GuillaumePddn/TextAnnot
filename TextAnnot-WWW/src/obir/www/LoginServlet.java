package obir.www;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import obir.otr.ObirProject;

/**
 * Servlet for the Login/Logout procedure
 */
public class LoginServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
       
    /**
     * @see HttpServlet#HttpServlet()
     */
    public LoginServlet() {
        super();
    }

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		if(request.getParameter("submit").equals("submit")){
			String username = request.getParameter("un");
			String password = request.getParameter("pw");
			String [] users = {"admin", "MNB", "royer", "sallaberry"};
			String [] passwords = {"t2iadmin", "moano", "moano", "moano"};
			//DBUser user = fetchFromDatabaseIfValid(username, password);
			HttpSession sess = request.getSession(true);
			/*sess.setAttribute("USER", user);
			if (user == null) {
				  // send 'no user/password match' message
			} else {
				  // send 'successful login' screen
			}*/
			boolean validUser=false;
			for(int i=0; i< users.length; i++){
				if(username.equals(users[i]) && password.equals(passwords[i])) validUser=true;
			}
			if(validUser) {
				sess.setAttribute("sessionStatus", "active");
				response.sendRedirect("userLogged.jsp");
				sess.setAttribute("username", username);
			} else {
				response.sendRedirect("invalidLogin.jsp");
			}
		} else if(request.getParameter("submit").equals("logout")){
			HttpSession sess = request.getSession(true);
			sess.setAttribute("sessionStatus", "closed");
			sess.setAttribute("pluginProperties", ObirProject.getPluginProperties()); //set properties back to default values
			response.sendRedirect("logoutSuccess.jsp");
		}
		
		
		
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doGet(request, response);
	}

}
