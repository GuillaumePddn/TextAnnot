package obir.misc;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Random;
import java.util.Set;
import java.util.Vector;

import obir.otr.OTR;
import obir.otr.ObirProject;
import edu.stanford.smi.protegex.owl.model.OWLNamedClass;
import edu.stanford.smi.protegex.owl.model.OWLObjectProperty;
import edu.stanford.smi.protegex.owl.model.RDFProperty;
import edu.stanford.smi.protegex.owl.model.RDFSClass;


/**
 * Handles the color mapping
 * @author Axel Reymonet
 */
public class ColorManager {


	private static final long serialVersionUID = -1609103709506264254L;

	/**
	 * The class color map
	 */
	HashMap<String,Color> classColorMap;

	/**
	 * The relation color map
	 */
	HashMap<String,Color> relationColorMap;

	/**
	 * Constructor
	 * @param proj the current project
	 */
	//	@SuppressWarnings("unchecked")
	public ColorManager()//OTR rto)
	{
		classColorMap		= new HashMap<String, Color>();
		relationColorMap = new HashMap<String, Color>();
	}

	public void overrideColor(String id,Color color,boolean isConcept)
	{
		HashMap<String,Color> map = null;

		//replaces existing color (if any) by another random one
		if (classColorMap.containsValue(color))
			map = classColorMap;
		else if (relationColorMap.containsValue(color))
			map = relationColorMap;
		if (map!=null)
		{
			for (String key:map.keySet())
			{
				if (map.get(key).equals(color))
				{
					Color newColor = color;
					while (newColor.equals(color))
						newColor = computeUnusedColor(map.equals(classColorMap));
					map.put(key, newColor);
					break;
				}
			}
		}

		//add the mapping to the appropriate map
		if (isConcept)
			map = classColorMap;
		else
			map = relationColorMap;
		map.put(id, color);
	}

	public OWLObjectProperty getRelationFromColor(Color color)
	{
		HashMap<String,Color> relColors = getRelationColors();
		for (String rel:relColors.keySet())
		{
			if (relColors.get(rel).equals(color))
			{
				return(ObirProject.getOWLModel().getOWLObjectProperty(rel));
			}
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	public void initColors(OWLNamedClass termRoot)
	{
		Collection<OWLNamedClass> termsAndConcepts = (Collection<OWLNamedClass>)ObirProject.getOWLModel().getOWLNamedClass(obir.otr.OTR.CONCEPT).getInstances(true);
		OWLNamedClass correctLingTerm = termRoot;

		if (correctLingTerm!=null)
			termsAndConcepts.addAll((Collection<OWLNamedClass>)correctLingTerm.getInstances(true));

		//		sortAlphabetically(vecClasses);

		for(RDFSClass classe:termsAndConcepts){
			if ((classe instanceof OWLNamedClass)&& 
				!classColorMap.containsKey(classe.getLocalName()) && 
				( ObirProject.getOWLModel().getOWLNamedClass(obir.otr.OTR.CONCEPT).getInstances(true).contains(classe) || 
				  ObirProject.getOWLModel().getOWLNamedClass(obir.otr.OTR.TERM).getInstances(true).contains(classe)))
			{
				Color c = computeUnusedColor(true);
				classColorMap.put(classe.getLocalName(),c); //we assume each class has different name		
			}
		}

		for (RDFProperty prop:(Collection<OWLObjectProperty>)ObirProject.getOWLModel().getOWLObjectPropertyClass().getInstances(false))//.getOWLNamedClass(obir.otr.OTR.DOMAIN_THING).getAssociatedProperties())
		{
			if ((prop instanceof OWLObjectProperty)&& !relationColorMap.containsKey(prop.getLocalName()))
			{
				if ( ObirProject.isOTRProperty(prop) )
				{
					boolean hasInterestingRange = false;
					for (RDFSClass range:(Collection<RDFSClass>)prop.getUnionRangeClasses())
					{
						if (range.getRDFType().equals(ObirProject.getOWLModel().getOWLNamedClass(obir.otr.OTR.CONCEPT))||range.getRDFType().getNamedSuperclasses(true).contains((ObirProject.getOWLModel().getOWLNamedClass(obir.otr.OTR.CONCEPT))))
						{
							hasInterestingRange = true;
							break;
						}
					}

					if (hasInterestingRange)
					{
						Color c = computeUnusedColor(false);
						relationColorMap.put(prop.getLocalName(),c); //we assume each class has different name	
					}
				}
			}
		}

	}

	public Color computeUnusedColor(boolean paleColor)
	{
		Color c = null;
		Random rand = new Random();
		int offset = 0;
		if (paleColor)
			offset = 128;
		while (c==null || classColorMap.containsValue(c) || relationColorMap.containsValue(c))
		{
			int r 	= offset+rand.nextInt(128);
			int g  	= offset+rand.nextInt(128);
			int b    = offset+rand.nextInt(128);
			c = new Color(r,g,b);
		}
		return c;
	}

	/**
	 * Method to add a new color mapping in case of the creation of a new term/concept
	 * @param className the name of the new term/concept
	 */
	public void addColorMapping(String className)
	{
		String realName = className;
		if (realName.contains("#"))
			realName = realName.substring(realName.indexOf("#")+1);
		if (!classColorMap.containsKey(realName))
		{
			Random rand = new Random();
			Color c = null;	

			while (c==null || classColorMap.containsValue(c) || relationColorMap.containsValue(c))
			{
				int r 	= 127+rand.nextInt(128);
				int g  	= 127+rand.nextInt(128);
				int	b    = 127+rand.nextInt(128);
				c = new Color(r,g,b);	
			}

			classColorMap.put(realName,c);
			HashMap<String,Color> tempMap = new HashMap<String,Color>();
			Vector<String> keys = new Vector<String>();
			keys.addAll(classColorMap.keySet());
			//			sortAlphabetically(keys);
			for (String key:keys)
			{
				tempMap.put(key, (Color)classColorMap.get(key));
			}
			classColorMap = tempMap;

		}
	}

	/**
	 * Static method to sort alphabetically a set of strings
	 */
	public static ArrayList<String> sortAlphabetically(Set<String> set)
	{
		ArrayList<String> result = new ArrayList<String>();
		int indice;
		for (String s:set)
		{
			indice = 0;
			while ((indice<result.size())&&(s.toLowerCase().compareTo(result.get(indice).toLowerCase())>0))
				indice++;
			result.add(indice,s);
		}

		return result;
	}


	public Color getRelationColor(String rel)
	{
		return relationColorMap.get(rel);
	}

	public HashMap<String,Color> getRelationColors()
	{
		return relationColorMap;
	}

	/**
	 * Method to get the color corresponding to a specific term/concept
	 * @param classe the ident of the term/concept
	 * @return the paired color (the mapping changes between two different sessions)
	 */
	public Color getClassColor(String classe) {
		return classColorMap.get(classe);
	}

	/**
	 * Map size getter
	 * @return the number of colors stored in the color manager
	 */
	public int getSize()
	{
		return classColorMap.size();
	}

	/**
	 * Concept colors getter
	 * @return the mapping between a concept name and its color
	 */
	public HashMap<String,Color> getConceptColors()
	{
		HashMap<String,Color> result = new HashMap<String, Color>();
		for (String localName:classColorMap.keySet())
		{
			if (ObirProject.getOWLModel().getOWLNamedClass(localName).getRDFType().equals(ObirProject.getOWLModel().getOWLNamedClass(OTR.CONCEPT)))
				result.put(localName,classColorMap.get(localName));
		}
		return result;
	}

	/**
	 * Concept colors getter
	 * @return the mapping between a term name and its color
	 */
	public HashMap<String,Color> getTermColors()
	{
		HashMap<String,Color> result = new HashMap<String, Color>();
		for (String localName:classColorMap.keySet())
		{
			if (!ObirProject.getOWLModel().getOWLNamedClass(localName).getRDFType().equals(ObirProject.getOWLModel().getOWLNamedClass(OTR.CONCEPT)))
				result.put(localName,classColorMap.get(localName));
		}
		return result;
	}

	/**
	 * Colormap key getter
	 * @return all keys stored in the color map
	 */
	public ArrayList<String> getKeys()
	{
		return sortAlphabetically(classColorMap.keySet());
	}




}
