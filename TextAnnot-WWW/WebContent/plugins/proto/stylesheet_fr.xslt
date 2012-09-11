<?xml version="1.0" encoding="ISO-8859-1"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

<xsl:output 
  method="html"
  encoding="ISO-8859-1"
  indent="yes" />
  
<xsl:template match="fiche">
	<html><body>
		<xsl:apply-templates select="description" />
	</body></html>
</xsl:template>
  
<xsl:template match="description">
	<br/><b>contenu</b><br/>
	<xsl:value-of select="." />
	<br/><br/>
</xsl:template>

</xsl:stylesheet>