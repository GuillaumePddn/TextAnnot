<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>TextAnnot - Login</title>
<link href='http://fonts.googleapis.com/css?family=Ubuntu+Condensed' rel='stylesheet' type='text/css'>
<link href='http://fonts.googleapis.com/css?family=Marvel' rel='stylesheet' type='text/css'>
<link href='http://fonts.googleapis.com/css?family=Marvel|Delius+Unicase' rel='stylesheet' type='text/css'>
<link href='http://fonts.googleapis.com/css?family=Arvo' rel='stylesheet' type='text/css'>
<link href="style.css" rel="stylesheet" type="text/css" media="screen" />

<!-- JSP XML -->
<jsp:directive.page contentType="text/html; charset=UTF-8" />
<!-- JSF/Facelets XHTML -->
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
</head>
<body>

		
<div id="navwrap">
<div id="nav" class="floatleft">
		<a href="index.jsp">Recherche</a>
		<a href="annotation.jsp">Annotation</a>
	</div>
<div id="nav" class="floatright">
		<a class="current" href="#">Login</a>
		<a href="OptionServlet?param=list"><span class="optionsmenu"> </span></a>
	</div>
<div class="clear"></div>
</div>

<div id="wrap">

	<div id="main">
		<CENTER>
<img src="images/TextAnnot.png" vspace="40px">
<BR>
<BR>
<form action="LoginServlet" METHOD="POST">
<table>
			<TR><TD>Nom d'usager:</TD><TD><input type="text" name="un"/></TD></TR>		
			<TR><TD>Mot de passe:</TD><TD><input type="password" name="pw"/></TD></TR>
			<TR><TD colspan="2" align="center"><input type="submit" name="submit" value="submit"></TD></TR>			
</table>
		</form>
</CENTER>
	</div>

</div>

<div id="footer">
<CENTER>
<TABLE cellspacing="5" cellpadding="5">
<TR>
<TD colspan="5" align="center"><H3><B>MOANO</B> - <I>Mod&egrave;les et Outils pour Applications NOmades de d&eacute;couverte de territoire</I></H3><br>
</TD>
</TR>
<TR>
<TD align="center" ><img src="images/logo_anr.png" height="30px"></TD>
<TD align="center" ><img src="images/logoT2i.png" height="50px"></TD>
<TD align="center" ><img src="images/uppa.jpg" height="50px"></TD>
<TD align="center" ><img src="images/logo_melodi3.png" height="50px" ></TD>
<TD align="center" ><img src="images/logoIRIT.gif" height="30px" ></TD>
</TR>
<TR>
<TD colspan="5" align="center">TextAnnot Powered by <I>TextViz</I><br>
<I>TextViz</I> a été élaboré dans le cadre du projet <A HREF="http://www.irit.fr/dynamo/"><IMG src="images/logo_dynamo.png" height="30px"></A></TD>
</TR>
</TABLE>
</CENTER>
</div>

</body>
</html>