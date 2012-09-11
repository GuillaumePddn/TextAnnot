<?xml version="1.0" encoding="ISO-8859-1"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

<xsl:output 
  method="html"
  encoding="ISO-8859-1"
  indent="yes" />
  
<xsl:template match="Nom_latin">
		<br/><b>Nom Latin</b><br/>
		<xsl:value-of select="." />
		<br/><br/>
</xsl:template>

<xsl:template match="port">
		<br/><b>Port</b><br/>
		<xsl:value-of select="." />
		<br/><br/>
</xsl:template>

<xsl:template match="feuillage">
		<br/><b>Feuillage</b><br/>
		<xsl:value-of select="." />
		<br/><br/>
</xsl:template>

<xsl:template match="floraison">
		<br/><b>Floraison</b><br/>
		<xsl:value-of select="." />
		<br/><br/>
</xsl:template>

<xsl:template match="emplacement">
		<br/><b>Emplacement</b><br/>
		<xsl:value-of select="." />
		<br/><br/>
</xsl:template>

<xsl:template match="entretien">
		<br/><b>Entretien</b><br/>
		<xsl:value-of select="." />
		<br/><br/>
</xsl:template>

<xsl:template match="sol">
		<br/><b>Sol</b><br/>
		<xsl:value-of select="." />
		<br/><br/>
</xsl:template>

<xsl:template match="plantation">
		<br/><b>Plantation</b><br/>
		<xsl:value-of select="." />
		<br/><br/>
</xsl:template>

<xsl:template match="couleur">
		<br/><b>Couleur</b><br/>
		<xsl:value-of select="." />
		<br/><br/>
</xsl:template>

<xsl:template match="hauteur">
		<br/><b>Hauteur</b><br/>
		<xsl:value-of select="." />
		<br/><br/>
</xsl:template>

<xsl:template match="croissance">
		<br/><b>Croissance</b><br/>
		<xsl:value-of select="." />
		<br/><br/>
</xsl:template>

</xsl:stylesheet>