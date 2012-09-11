package obir.ir;

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Set;
import java.util.Vector;

import javax.sound.midi.SysexMessage;

import obir.ir.indexing.RelationIndexFactory;
import obir.otr.OTR;
import obir.otr.ObirProject;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

import edu.stanford.smi.protegex.owl.model.OWLIndividual;
import edu.stanford.smi.protegex.owl.model.OWLModel;
import edu.stanford.smi.protegex.owl.model.OWLNamedClass;
import edu.stanford.smi.protegex.owl.model.OWLObjectProperty;
import edu.stanford.smi.protegex.owl.model.RDFProperty;
import edu.stanford.smi.protegex.owl.model.RDFSClass;

/**
 * Convenience class gathering all methods aiming at computing a semantic similarity. All included methods are (and should be) static.
 * @author Axel Reymonet - reviewed by Davide Buscaldi
 * 
 */
public class SemanticSimilarity {

	/**
	 * Must be at least 3 to be sure that the grandparent is preferred to any brother.
	 * If equals to 1, it is Wu-Palmer measure
	 * (Used by ACTIA)
	 */
	public final static int actiaCoeff = 3;

	/**
	 * Computes Resnik probability for a concept in a given language
	 * (Used by ACTIA - only for Lin similarity)
	 * @param c a concept
	 * @param termRoot the root for all terms in a given language
	 * @return Resnik probability for c
	 */
	@SuppressWarnings("unchecked")
	public static float computeResnikProbability (OWLNamedClass c, OWLNamedClass termRoot)
	{

		//		OWLModel onto = c.getOWLModel();
		int numerator = 0;
		int denominator = 0;
		int instNb;

		for (OWLNamedClass term:(Collection<OWLNamedClass>)termRoot.getInstances(true))//.getNamedSubclasses(true))
		{
			instNb = term.getInstanceCount(false);
			denominator+=instNb;

			Collection<OWLNamedClass> concepts = OTR.getAssociatedConcepts(term);//(ArrayList<OWLNamedClass>)term.getPropertyValues(onto.getOWLObjectProperty(denotes));

			ArrayList<OWLNamedClass> cChildren = (ArrayList<OWLNamedClass>) c.getNamedSubclasses(true);
			cChildren.retainAll(concepts);
			if ((concepts.contains(c))||!cChildren.isEmpty())
				numerator+=instNb;

		}
		return(new Float(numerator)/new Float(denominator));

	}

	/**
	 * Specific method for ACTIA checking whether a concept is a component connected to a service 
	 * @param c the given concept
	 * @return the list of services connected to the concept
	 */
	private static ArrayList<OWLNamedClass> isConceptConnectedToService(OWLNamedClass c)//, OWLNamedClass categ,OTR otr)
	{
		OWLModel model = c.getOWLModel();
		ArrayList<OWLNamedClass> result = new ArrayList<OWLNamedClass>();
		if ((c.getNamedSuperclasses(true).contains(model.getOWLNamedClass(OTR.componentID))))//&&(categ.equals(model.getOWLNamedClass(OTR.serviceID))))
		{
			result = obir.otr.OTR.getMostSpecificRestrictedRanges(c, model.getOWLObjectProperty(OTR.participatesToID));
			if (c.getNamedSuperclasses(true).contains(model.getOWLNamedClass(OTR.controlComponentID)))
				result.addAll(obir.otr.OTR.getMostSpecificRestrictedRanges(c, model.getOWLObjectProperty(OTR.controlsID)));
			while (result.contains(model.getOWLNamedClass(OTR.serviceID)))
				result.remove(model.getOWLNamedClass(OTR.serviceID));
		}
		return result;
	}


	/**
	 * Computes the different probabilities for a subtaxonomy with the given root (to be used for Lin similarity)
	 * @param otr the current OTR
	 * @param categ the root of the subtaxonomy
	 * @return the probabilities for all concepts belonging to the subtaxonomy
	 */
	@SuppressWarnings("unchecked")
	public static HashMap<String,Float> computeResnikProbabilities(OTR otr,OWLNamedClass categ)//, String directory)
	{
		OWLModel onto = otr.getOntology();
		HashMap<String,Float> probas = new HashMap<String,Float>();
		for (OWLNamedClass c:(Collection<OWLNamedClass>)categ.getNamedSubclasses(true))
		{

			probas.put(c.getName(), new Float(0));
			//			probas.put(c.getName(), computeResnikProbability(c));
		}
		probas.put(categ.getName(),new Float(0));

		int instNb = 0;
		int denominator = 0;
		for (OWLNamedClass term:(Collection<OWLNamedClass>)otr.getOntology().getOWLNamedClass(OTR.TERM).getInstances(true))
		{
			Collection<OWLNamedClass> concepts = OTR.getAssociatedConcepts(term);//(ArrayList<OWLNamedClass>)term.getPropertyValues(onto.getOWLObjectProperty(OTR.denotesID));

			boolean isTermInteresting = false;
			boolean isTermUndirectlyInteresting = false;
			for (OWLNamedClass assocConcept:concepts)
				if (assocConcept.getNamedSuperclasses(true).contains(categ))
				{
					isTermInteresting = true;
					break;
				}
				else if ((categ.equals(otr.getOntology().getOWLNamedClass(OTR.serviceID)))&&(isConceptConnectedToService(assocConcept).size()!=0))
				{
					isTermUndirectlyInteresting = true;
					break;
				}

			if (isTermInteresting||isTermUndirectlyInteresting)
			{
				instNb=0;
				for (OWLIndividual termInd : (Collection<OWLIndividual>)term.getInstances(false))
					//					if (termInd instanceof OWLIndividual)
				{
					String filepath = (String)((OWLIndividual)termInd).getPropertyValue(onto.getOWLDatatypeProperty(obir.otr.OTR.DOC_ID));
					String file = filepath.substring(filepath.lastIndexOf('\\')+1,filepath.length());
					if (ObirProject.getCorpus().getValidatedDocuments().contains(ObirProject.getCorpus().getDocument(file)))//getFinalResults().containsKey(file))
					{
						instNb++;
					}
				}

				//				instNb = term.getInstanceCount(false);

				denominator+=instNb;

				for (OWLNamedClass assocConcept:concepts)
				{
					ArrayList<OWLNamedClass> assocServByCompTerm = isConceptConnectedToService(assocConcept);
					if (assocServByCompTerm.size()!=0)
					{
						for (OWLNamedClass assocServ:assocServByCompTerm)
						{
							float oldValue = probas.get(assocServ.getName());
							probas.put(assocServ.getName(), oldValue+instNb);
							for (OWLNamedClass parent:(Collection<OWLNamedClass>)assocServ.getNamedSuperclasses(true))
							{
								if (probas.containsKey(parent.getName()))
								{
									float oldParentValue = probas.get(parent.getName());
									probas.put(parent.getName(), oldParentValue+instNb);
								}
							}
						}
					}
					else
					{
						float oldValue = probas.get(assocConcept.getName());
						probas.put(assocConcept.getName(), oldValue+instNb);
						for (OWLNamedClass parent:(Collection<OWLNamedClass>)assocConcept.getNamedSuperclasses(true))
						{
							if (probas.containsKey(parent.getName()))
							{
								float oldParentValue = probas.get(parent.getName());
								probas.put(parent.getName(), oldParentValue+instNb);
							}
						}
					}
				}
			}
		}
		for (String cptName:probas.keySet())
		{
			if ((probas.get(cptName)==0)&&(denominator==0))
				probas.put(cptName, new Float(1));
			else
				probas.put(cptName, probas.get(cptName)/new Float(denominator));
		}
		return probas;

	}

	/**
	 * Computes the local depth of a given concept from a local root
	 * @param c a concept
	 * @param localRoot the root of a subtaxonomy
	 * @return an integer representing how specific the concept is.
	 * @author Alex Reymonet - reviewed by Davide Buscaldi to deal with multiple inheritance
	 */
	private static int computeDepth(OWLNamedClass c, OWLNamedClass localRoot)
	{
		int d=1; //distance (set to 1 to avoid 0 denom. for instance Hiver and Periode, localroot Periode)
		HashSet<OWLNamedClass> f = new HashSet<OWLNamedClass>(); //frontier set (F from Dijkstra)
		f.add(c);
		while(!f.contains(localRoot) && !f.isEmpty()){
			HashSet<OWLNamedClass> newF = new HashSet<OWLNamedClass>();
			newF.addAll(f);
			for(OWLNamedClass cl : newF){
				f.remove(cl);
				f.addAll(cl.getNamedSuperclasses(false));
			}
			d++;
			if(d>100) {
				//FIXME: this is a patch to avoid infinite loops
				d=1;
				break;
			}
		}
		//System.err.println("distance "+c.getLocalName()+" and "+localRoot.getLocalName()+" = "+d);
		return d;

	}

	/**
	 * Computes the Wu-Palmer similarity for two concepts, given a local root for the subtaxonomy including both concepts
	 * @param c1 first concept
	 * @param c2 second concept
	 * @param localRoot the local root
	 * @return Wu-Palmer similarity (see publications)
	 */
	@SuppressWarnings("unchecked")
	private static float computeWuPalmerSimilarity(OWLNamedClass c1, OWLNamedClass c2,OWLNamedClass localRoot)
	{

		ArrayList<OWLNamedClass> subsumers1 = (ArrayList<OWLNamedClass>)c1.getNamedSuperclasses(true);
		subsumers1.add(c1);
		ArrayList<OWLNamedClass> subsumers2 = (ArrayList<OWLNamedClass>)c2.getNamedSuperclasses(true);
		subsumers2.add(c2);
		subsumers1.retainAll(subsumers2);
		subsumers1.removeAll(localRoot.getNamedSuperclasses(true));
		int greatestDepth = 1;
		for (OWLNamedClass father:subsumers1)
		{
			int currentDepth = computeDepth(father,localRoot);
			if (currentDepth>greatestDepth)
				greatestDepth = currentDepth;
		}
		float result = new Float(2*greatestDepth)/new Float(computeDepth(c1,localRoot)+computeDepth(c2,localRoot));
		return result;
	}

	/**
	 * Gets all concepts which can be directly compared to a given one through the {@code lowerThan} relation
	 * @param c a given concept
	 * @param previousList recursively built list of comparable contexts
	 */
	@SuppressWarnings("unchecked")
	public static void getDirectlyComparableConcepts(OWLNamedClass c,ArrayList<OWLNamedClass> previousList)
	{
		previousList.add(c);

		Collection<OWLNamedClass> subclasses = (Collection<OWLNamedClass>) c.getNamedSubclasses();
		if (subclasses.size()>=2)
		{
			Iterator<OWLNamedClass> iter = subclasses.iterator();
			OWLNamedClass greaterChild=c;
			while (iter.hasNext())
			{
				greaterChild = iter.next();
				if (greaterChild.getPropertyValueCount(c.getOWLModel().getOWLObjectProperty(obir.otr.OTR.LOWER_THAN))==0)//(greaterChild.getAllValuesFrom(c.getOWLModel().getOWLObjectProperty(obir.otr.OTR.INFERIEUR_A)).equals(c.getOWLModel().getOWLNamedClass(obir.otr.OTR.CONTEXTE)))
					break;					
			}
			getDirectlyComparableConcepts(greaterChild,previousList);
			previousList.add(c);
		}

		OWLNamedClass superclass = c;
		for (RDFSClass potentialParent:(Collection<RDFSClass>)c.getSuperclasses(false))
		{
			if (potentialParent instanceof  OWLNamedClass)
			{
				superclass = (OWLNamedClass)potentialParent;
				break;
			}
		}
		Collection<OWLNamedClass> siblings = (Collection<OWLNamedClass>)superclass.getNamedSubclasses();
		siblings.remove(c);
		if (siblings.size()!=0)
		{
			Iterator<OWLNamedClass> iter = siblings.iterator();
			OWLNamedClass closestInferiorSibling=c;
			boolean oneInferiorFound = false;
			while (iter.hasNext())
			{
				closestInferiorSibling = iter.next();
				if ((closestInferiorSibling.getPropertyValueCount(c.getOWLModel().getOWLObjectProperty(obir.otr.OTR.LOWER_THAN))!=0)&&(closestInferiorSibling.getPropertyValue(c.getOWLModel().getOWLObjectProperty(obir.otr.OTR.LOWER_THAN)).equals(c)))//(closestInferiorSibling.getAllValuesFrom(c.getOWLModel().getOWLObjectProperty(obir.otr.OTR.INFERIEUR_A)).equals(c))
				{
					oneInferiorFound = true;
					break;
				}

			}
			if (oneInferiorFound)
				getDirectlyComparableConcepts(closestInferiorSibling,previousList);
		}
	}

	/**
	 * Computes the similarity between two concepts which are (directly or indirectly) connected through a {@code lowerThan} relation.
	 * @param cpt1 first concept
	 * @param cpt2 second concept
	 * @param directlyComparableConcepts list of all comparable concepts (including cpt1 and cpt2)
	 * @return the similarity between cpt1 and cpt2
	 */
	public static float computeDirectlyComparableConceptsSimilarity(OWLNamedClass cpt1,OWLNamedClass cpt2,ArrayList<OWLNamedClass> directlyComparableConcepts)
	{
		if (cpt1.equals(cpt2))
			return 1;
		else
		{
			int maxDistValue = 0;
			for (int i=directlyComparableConcepts.size()-1;i>0;i--)
			{
				if((directlyComparableConcepts.get(i).getPropertyValueCount(cpt1.getOWLModel().getOWLObjectProperty(obir.otr.OTR.LOWER_THAN))!=0)&&(directlyComparableConcepts.get(i).getPropertyValue(cpt1.getOWLModel().getOWLObjectProperty(obir.otr.OTR.LOWER_THAN)).equals(directlyComparableConcepts.get(i-1))))//(conceptsOrdered.get(i).getAllValuesFrom(ctxt1.getOWLModel().getOWLObjectProperty(obir.otr.OTR.INFERIEUR_A)).equals(conceptsOrdered.get(i-1)))
					maxDistValue++;
			}


			ArrayList<Integer>positions1 =new ArrayList<Integer>();
			ArrayList<Integer>positions2 =new ArrayList<Integer>();
			positions1.add(directlyComparableConcepts.indexOf(cpt1));
			if (directlyComparableConcepts.lastIndexOf(cpt1)!=(directlyComparableConcepts.indexOf(cpt1)))
				positions1.add(directlyComparableConcepts.lastIndexOf(cpt1));
			positions2.add(directlyComparableConcepts.indexOf(cpt2));
			if (directlyComparableConcepts.lastIndexOf(cpt2)!=(directlyComparableConcepts.indexOf(cpt2)))
				positions2.add(directlyComparableConcepts.lastIndexOf(cpt2));

			Integer minDistValue = maxDistValue;
			Integer intermedRelNb = 0;
			Integer currentIndex;
			if (positions1.size()>1)
			{
				if (((positions2.get(0)<positions1.get(0))&&(positions2.get(0)>positions1.get(1)))||((positions2.get(0)>positions1.get(0))&&(positions2.get(0)<positions1.get(1))))
					return 1;
			}
			if (positions2.size()>1)
			{
				if (((positions1.get(0)<positions2.get(0))&&(positions1.get(0)>positions2.get(1)))||((positions1.get(0)>positions2.get(0))&&(positions1.get(0)<positions2.get(1))))
					return 1;
			}

			for (Integer i1:positions1)
			{
				for (Integer i2:positions2)
				{
					intermedRelNb = 0;
					if (i1>i2)
					{
						currentIndex = i1;
						while (currentIndex!=i2)
						{
							currentIndex--;
							if((directlyComparableConcepts.get(currentIndex+1).getPropertyValueCount(cpt1.getOWLModel().getOWLObjectProperty(obir.otr.OTR.LOWER_THAN))!=0)&&(directlyComparableConcepts.get(currentIndex+1).getPropertyValue(cpt1.getOWLModel().getOWLObjectProperty(obir.otr.OTR.LOWER_THAN)).equals(directlyComparableConcepts.get(currentIndex))))//(comparableCtxts.get(currentIndex+1).getAllValuesFrom(ctxt1.getOWLModel().getOWLObjectProperty(obir.otr.OTR.INFERIEUR_A)).equals(comparableCtxts.get(currentIndex)))
								intermedRelNb++;
						}
					}
					else
					{
						currentIndex = i2;
						while (currentIndex!=i1)
						{
							currentIndex--;
							if((directlyComparableConcepts.get(currentIndex+1).getPropertyValueCount(cpt1.getOWLModel().getOWLObjectProperty(obir.otr.OTR.LOWER_THAN))!=0)&&(directlyComparableConcepts.get(currentIndex+1).getPropertyValue(cpt1.getOWLModel().getOWLObjectProperty(obir.otr.OTR.LOWER_THAN)).equals(directlyComparableConcepts.get(currentIndex))))//(comparableCtxts.get(currentIndex+1).getAllValuesFrom(ctxt1.getOWLModel().getOWLObjectProperty(obir.otr.OTR.INFERIEUR_A)).equals(comparableCtxts.get(currentIndex)))
								intermedRelNb++;
						}
					}
					if (intermedRelNb<minDistValue)
						minDistValue = intermedRelNb;
				}	

			}

			return 1-minDistValue/new Float(maxDistValue);
		}
	}
	/**
	 * Computes the similarity between concepts using the ACTIA scheme
	 * @param queryCpt
	 * @param docCpt
	 * @param localRoot
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private static float computeActiaConceptSimilarity(OWLNamedClass queryCpt, OWLNamedClass docCpt,OWLNamedClass localRoot)
	{
		ArrayList<OWLNamedClass> subsumers1 = (ArrayList<OWLNamedClass>)queryCpt.getNamedSuperclasses(true);
		subsumers1.add(queryCpt);
		ArrayList<OWLNamedClass> subsumers2 = (ArrayList<OWLNamedClass>)docCpt.getNamedSuperclasses(true);
		subsumers2.add(docCpt);
		subsumers1.retainAll(subsumers2);
		subsumers1.removeAll(localRoot.getNamedSuperclasses(true));
		OWLNamedClass commonParent = subsumers1.iterator().next();
		int queryDepth = computeDepth(queryCpt, localRoot);
		int docDepth = computeDepth(docCpt, localRoot);
		int commonDepth = computeDepth(commonParent, localRoot);
		return 1-(queryDepth-commonDepth+actiaCoeff*(docDepth-commonDepth))/new Float(queryDepth+actiaCoeff*docDepth);
	}


	/**
	 * Computes a semantic similarity between two concepts in a subtaxonomy
	 * @param c1 first concept
	 * @param c2 second concept
	 * @param localRoot root of the subtaxonomy including both concepts
	 * @param measure the chosen measure type (for now, only Wu-Palmer or Lin types)
	 * @param resnikCategProbas the already computed Resnik probabilities for each concept of the subtaxonomy
	 * @return the appropriate value (between 0 and 1)
	 */
	private static float computeConceptSimilarity (OWLNamedClass c1, OWLNamedClass c2, OWLNamedClass localRoot, String measure,HashMap<String,HashMap<String,Float>> resnikCategProbas)
	{
		Float value = new Float(0);
		if (measure.contains("Wu")||measure.contains("Palmer"))
			value = new Float(computeWuPalmerSimilarity(c1, c2,localRoot));
		else if (measure.contains("Lin"))
			value = new Float(computeLinSimilarity(c1,c2,localRoot,resnikCategProbas.get(localRoot.getLocalName())));
		else if (measure.contains("Actia"))
			value = new Float(computeActiaConceptSimilarity(c1, c2,localRoot));
		return value;
	}

	/**
	 * Computes an asymetric semantic similarity between two concepts in a subtaxonomy.
	 * @param queryConcept query concept
	 * @param docConcept document concept
	 * @param localRoot root of the subtaxonomy including both concepts
	 * @param measure the chosen measure type (for now, only Wu-Palmer or Lin types)
	 * @param resnikCategProbas the already computed Resnik probabilities for each concept of the subtaxonomy
	 * @return the appropriate value (between 0 and 1)
	 */
	private static float computeAsymetricConceptSimilarity(OWLNamedClass queryConcept, OWLNamedClass docConcept, OWLNamedClass localRoot, String measure,HashMap<String,HashMap<String,Float>> resnikCategProbas)
	{
		if (docConcept.equals(queryConcept)||docConcept.getNamedSuperclasses(true).contains(queryConcept))
			return 1;
		else
			return computeConceptSimilarity(queryConcept, docConcept, localRoot, measure, resnikCategProbas);
	}

	/**
	 * Specific method for ACTIA. Computes the global similarity between two concept instances.
	 * @param i1 first instance
	 * @param i2 second instance
	 * @param conceptSimMeasure the chosen conceptual similarity (Lin or Wu-Palmer)
	 * @param param different coefficients
	 * @param resnikSubOntoProbas the pre-computed Resnik probabilities stored by subtaxonomy
	 * @return an appropriate value (between 0 and 1)
	 */
	@SuppressWarnings("unchecked")
	public static float computeInstanceSimilarity(OWLIndividual i1, OWLIndividual i2, String conceptSimMeasure, float [] param,HashMap<String,HashMap<String,Float>> resnikSubOntoProbas)
	{
		Float result = new Float(0);

		if ((i1==null)||(i2==null))
			return result;
		else
		{
			Float alpha = new Float(param[0]); //importance relative de la sim conceptuelle p/r sim relationnelle
			Float mu = new Float(param[1]); //importance relative des relations obligatoires p/r relations facultatives 
			//		Float aPb = new Float(param[2]);
			Float aPrest = new Float(param[2]);
			//		Float affecte = new Float(param[4]);
			//		Float aCtxt = new Float(param[5]);

			OWLModel onto = i1.getOWLModel();

			//teste si les deux instances sont de type prestation...
			if ((((OWLNamedClass)i1.getRDFType()).equals(i1.getOWLModel().getOWLNamedClass(OTR.serviceID))||((OWLNamedClass)i1.getRDFType()).getNamedSuperclasses(true).contains(i1.getOWLModel().getOWLNamedClass(OTR.serviceID)))&&(((OWLNamedClass)i2.getRDFType()).equals(i2.getOWLModel().getOWLNamedClass(OTR.serviceID))||((OWLNamedClass)i2.getRDFType()).getNamedSuperclasses(true).contains(i2.getOWLModel().getOWLNamedClass(OTR.serviceID))))
			{
				Float servCptSim = new Float(0);
				//teste si les deux instances sont reliées par un chemin taxo
				if ((i1.getRDFType().equals(i2.getRDFType()))||(((OWLNamedClass)i1.getRDFType()).getNamedSuperclasses(true).contains(((OWLNamedClass)i2.getRDFType())))||(((OWLNamedClass)i2.getRDFType()).getNamedSuperclasses(true).contains(((OWLNamedClass)i1.getRDFType()))))
					servCptSim = computeAsymetricConceptSimilarity(((OWLNamedClass)i1.getRDFType()), ((OWLNamedClass)i2.getRDFType()), i1.getOWLModel().getOWLNamedClass(OTR.serviceID), conceptSimMeasure, resnikSubOntoProbas);
				result = servCptSim;//new Float(1-alpha*(1-servCptSim));
			}
			//... ou de type problème...
			else if ((((OWLNamedClass)i1.getRDFType()).equals(i1.getOWLModel().getOWLNamedClass(OTR.problemID))||((OWLNamedClass)i1.getRDFType()).getNamedSuperclasses(true).contains(i1.getOWLModel().getOWLNamedClass(OTR.problemID)))&&(((OWLNamedClass)i2.getRDFType()).equals(i2.getOWLModel().getOWLNamedClass(OTR.problemID))||((OWLNamedClass)i2.getRDFType()).getNamedSuperclasses(true).contains(i2.getOWLModel().getOWLNamedClass(OTR.problemID))))
			{
				Float servLocSimSum = new Float(0);
				Float maxServLocSim = new Float(0);
				Float servLocSim = new Float(0);
				for (OWLIndividual serv1 : (Collection<OWLIndividual>)i1.getPropertyValues(onto.getOWLObjectProperty(OTR.affectsServiceID)))
				{
					maxServLocSim = new Float(0);
					servLocSim = new Float(0);
					for (OWLIndividual serv2 : (Collection<OWLIndividual>)i2.getPropertyValues(onto.getOWLObjectProperty(OTR.affectsServiceID)))
					{
						servLocSim = new Float(computeInstanceSimilarity(serv1, serv2, conceptSimMeasure, param, resnikSubOntoProbas));
						if (servLocSim>maxServLocSim)
							maxServLocSim=servLocSim;
					}
					servLocSimSum += maxServLocSim;
				}

				Collection<OWLIndividual> contxts1 = (Collection<OWLIndividual>) i1.getPropertyValues(onto.getOWLObjectProperty(OTR.hasContextID), true);

				int ctxtNb = contxts1.size();
				int servNb = i1.getPropertyValues(onto.getOWLObjectProperty(OTR.affectsServiceID)).size();
				Float pbCptSim = new Float(computeAsymetricConceptSimilarity((OWLNamedClass)i1.getRDFType(), (OWLNamedClass)i2.getRDFType(), i1.getOWLModel().getOWLNamedClass(OTR.problemID), conceptSimMeasure, resnikSubOntoProbas ));

				if (ctxtNb!=0)
				{
					Collection<OWLIndividual> contxts2 = (Collection<OWLIndividual>) i2.getPropertyValues(onto.getOWLObjectProperty(OTR.hasContextID), true);
					Float ctxtFacultProx = new Float(0);

					for (OWLIndividual ctxt1:contxts1)
					{
						//trouver le concept comparable le + haut
						OWLNamedClass comparableTop = (OWLNamedClass)ctxt1.getRDFType();
						while(hasComparableSiblings(comparableTop))
						{
							for (RDFSClass potentialParent:(Collection<RDFSClass>)comparableTop.getSuperclasses(false))
							{
								if (potentialParent instanceof  OWLNamedClass)
								{
									comparableTop = (OWLNamedClass)potentialParent;
									break;
								}
							}

						}
						if (!comparableTop.equals((OWLNamedClass)ctxt1.getRDFType()))
						{
							ArrayList<OWLNamedClass> conceptsOrdered = new ArrayList<OWLNamedClass>();
							getDirectlyComparableConcepts(comparableTop, conceptsOrdered);

							Float closestContxtSim = new Float(-1);
							for (OWLIndividual ctxt2:contxts2)
							{
								if (conceptsOrdered.contains(ctxt2.getRDFType()))
								{
									Float currentSim = computeDirectlyComparableConceptsSimilarity((OWLNamedClass)ctxt1.getRDFType(), (OWLNamedClass)ctxt2.getRDFType(), conceptsOrdered);//, comparRelNb);
									if (currentSim>closestContxtSim)
										closestContxtSim = currentSim;
								}
							}
							if (closestContxtSim==-1)//aucun contexte comparable dans le document
							{
								closestContxtSim=new Float(0);
								int instTotNb = 0;

								HashSet<OWLNamedClass> compContexts= new HashSet<OWLNamedClass>();
								for (OWLNamedClass compContext:conceptsOrdered)
								{
									compContexts.add(compContext);
								}
								for (OWLNamedClass compContext:compContexts)
								{
									int instNb = compContext.getInstanceCount(false);
									if (instNb!=0)
										closestContxtSim+=instNb*computeDirectlyComparableConceptsSimilarity((OWLNamedClass)ctxt1.getRDFType(), compContext, conceptsOrdered);//, comparRelNb);
									instTotNb+=instNb;
								}
								closestContxtSim = new Float(closestContxtSim/instTotNb);
							}
							ctxtFacultProx+=closestContxtSim;	
						}
						else
						{
							OWLNamedClass parent = null;
							for (RDFSClass potFather : (Collection<RDFSClass>)comparableTop.getNamedSuperclasses(false))
							{
								if (potFather instanceof OWLNamedClass)
								{
									parent = (OWLNamedClass) potFather;
									break;
								}
							}

							while (!parent.equals(comparableTop.getOWLModel().getOWLThingClass()))
							{
								//							if ((parent.getPropertyValueCount(parent.getOWLModel().getOWLDatatypeProperty(obir.otr.OTR.FILS_COMPARABLES))!=0)&&(parent.getPropertyValue(parent.getOWLModel().getOWLDatatypeProperty(obir.otr.OTR.FILS_COMPARABLES)).equals(true)))
								if (parent.getRDFType().equals(parent.getOWLModel().getOWLNamedClass(OTR.CATEGORY_CONCEPT)))
								{
									comparableTop = parent;
									break;
								}
								else
								{
									for (RDFSClass potFather : (Collection<RDFSClass>)parent.getNamedSuperclasses(false))
									{
										if (potFather instanceof OWLNamedClass)
										{
											parent = (OWLNamedClass) potFather;
											break;
										}
									}
								}

							}
							if (!comparableTop.equals((OWLNamedClass)ctxt1.getRDFType()))
							{
								Float closestContxtSim = new Float(-1);
								for (OWLIndividual ctxt2:contxts2)
								{
									if (((OWLNamedClass)ctxt2.getRDFType()).getNamedSuperclasses(true).contains(comparableTop))
									{
										Float currentSim = computeAsymetricConceptSimilarity((OWLNamedClass)ctxt1.getRDFType(), (OWLNamedClass)ctxt2.getRDFType(), comparableTop, conceptSimMeasure, resnikSubOntoProbas);
										if (currentSim>closestContxtSim)
											closestContxtSim = currentSim;
									}
								}

								if (closestContxtSim==-1)//aucun contexte comparable dans le document
								{
									closestContxtSim=new Float(0);
									int instTotNb = 0;

									for (OWLNamedClass compContext:(Collection<OWLNamedClass>)comparableTop.getNamedSubclasses(true))
									{
										int instNb = compContext.getInstanceCount(false);
										if (instNb!=0)
											closestContxtSim+=instNb*computeAsymetricConceptSimilarity((OWLNamedClass)ctxt1.getRDFType(), compContext, comparableTop, conceptSimMeasure, resnikSubOntoProbas);
										instTotNb+=instNb;
									}
									closestContxtSim = new Float(closestContxtSim/instTotNb);
								}
								ctxtFacultProx+=closestContxtSim;	
							}

						}
					}

					result = new Float(alpha*pbCptSim+(1-alpha)*(mu*servLocSimSum/servNb+(1-mu)*ctxtFacultProx/ctxtNb));
				}

				else
					result = new Float(alpha*pbCptSim+(1-alpha)*(servLocSimSum/servNb));
				//result = new Float(alpha*pbCptSim+(1-alpha)*(mu*servLocSimSum/servNb+(1-mu)));
			}
			//... ou de type symptome...
			else if ((((OWLNamedClass)i1.getRDFType()).getNamedSuperclasses(true).contains(i1.getOWLModel().getOWLNamedClass(obir.otr.OTR.SYMPTOME)))&&(((OWLNamedClass)i2.getRDFType()).getNamedSuperclasses(true).contains(i2.getOWLModel().getOWLNamedClass(obir.otr.OTR.SYMPTOME))))
			{
				OWLIndividual pb1 = (OWLIndividual)i1.getPropertyValue(onto.getOWLObjectProperty(obir.otr.OTR.DEFINED_BY_PROBLEM));
				OWLIndividual pb2 = (OWLIndividual)i2.getPropertyValue(onto.getOWLObjectProperty(obir.otr.OTR.DEFINED_BY_PROBLEM));
				OWLIndividual serv1 = (OWLIndividual)i1.getPropertyValue(onto.getOWLObjectProperty(obir.otr.OTR.DEFINED_BY_SERVICE));
				OWLIndividual serv2 = (OWLIndividual)i2.getPropertyValue(onto.getOWLObjectProperty(obir.otr.OTR.DEFINED_BY_SERVICE));

				Float servLocSim=computeInstanceSimilarity(serv1, serv2, conceptSimMeasure,param,resnikSubOntoProbas);
				//Added to forbid any matching between two services not related taxonomically
				if (servLocSim==0)
					return 0;
				//end Add
				Float pbLocSim=computeInstanceSimilarity(pb1, pb2, conceptSimMeasure,param, resnikSubOntoProbas);


				//			result = new Float(new Float((aPb*pbLocSim+aPrest*servLocSim)/(aPb+aPrest)));
				result = new Float(new Float((pbLocSim+aPrest*servLocSim)/(1+aPrest)));
			}
			return result;
		}
	}

	/**
	 * Checks whether a given concept is connected to other concepts with the {@code lowerThan} relation.
	 * @param c a concept
	 * @return {@code true} iff the concept is an origin or a destination for a {@code lowerThan} relation.
	 */
	@SuppressWarnings("unchecked")
	public static boolean hasComparableSiblings(OWLNamedClass c)
	{
		if (c.hasPropertyValue(c.getOWLModel().getOWLObjectProperty(obir.otr.OTR.LOWER_THAN)))//c.getPropertyValueCount(c.getOWLModel().getOWLObjectProperty(obir.otr.OTR.INFERIEUR_A))!=0)
			return true;
		else
		{
			OWLNamedClass superclass = c;
			for (RDFSClass potentialParent:(Collection<RDFSClass>)c.getSuperclasses(false))
			{
				if (potentialParent instanceof  OWLNamedClass)
				{
					superclass = (OWLNamedClass)potentialParent;
					break;
				}
			}
			Collection<OWLNamedClass> siblings = (Collection<OWLNamedClass>)superclass.getNamedSubclasses();
			for (OWLNamedClass sibling:siblings)
				if (((sibling.getPropertyValueCount(c.getOWLModel().getOWLObjectProperty(obir.otr.OTR.LOWER_THAN))!=0))&&(sibling.getPropertyValue(c.getOWLModel().getOWLObjectProperty(obir.otr.OTR.LOWER_THAN)).equals(c)))
					return true;
			return false;
		}

	}

	/**
	 * Computes the Lin similarity between 2 concepts
	 * @param c1 first concept
	 * @param c2 second concept
	 * @param localRoot root of the subtaxonomy including both concepts
	 * @param resnikProbas pre-computed Resnik probabilities for all concepts of the subtaxonomy
	 * @return an appropriate value (between 0 and 1)
	 */
	@SuppressWarnings("unchecked")
	private static float computeLinSimilarity(OWLNamedClass c1,OWLNamedClass c2,OWLNamedClass localRoot,HashMap<String,Float> resnikProbas) {
		ArrayList<OWLNamedClass> subsumers1 = (ArrayList<OWLNamedClass>)c1.getNamedSuperclasses(true);
		subsumers1.add(c1);
		ArrayList<OWLNamedClass> subsumers2 = (ArrayList<OWLNamedClass>)c2.getNamedSuperclasses(true);
		subsumers2.add(c2);
		subsumers1.retainAll(subsumers2);
		subsumers1.removeAll(localRoot.getNamedSuperclasses(true));
		float mostSpecSubsumerProba = 1;
		for (OWLNamedClass father:subsumers1)
		{
			if (resnikProbas.containsKey(father.getName()))
			{
				float fatherProba = resnikProbas.get(father.getName());
				if (fatherProba<mostSpecSubsumerProba)
					mostSpecSubsumerProba = fatherProba;
			}
		}
		float result = new Float(2*java.lang.Math.log10(new Double(mostSpecSubsumerProba)))/new Float((java.lang.Math.log10(new Double(resnikProbas.get(c1.getName())))+java.lang.Math.log10(new Double(resnikProbas.get(c2.getName())))));

		return result;
	}


	/**
	 * Global method based on word recognition ("classic" IR) to compare a given query to a set of documents
	 * @param query the query
	 * @param fieldName the field to look in in each document
	 * @param analyzer the analyzer to process the query
	 * @param corpusPath the path to where the document index is stored
	 * @param corpus the set of documents to work on
	 * @return a list of (filename,score) couples ordered by decreasing score
	 */
	public static Vector<Vector<String>> classicSearchAlgorithm(String query, String fieldName, Analyzer analyzer, String corpusPath, Corpus corpus)// float threshold)
	{
		Vector<Vector<String>> result = new Vector<Vector<String>>();

		HashSet<DocumentAnnotation> searchedDocs = corpus.getValidatedDocuments();

		try{
			IndexReader reader = IndexReader.open(FSDirectory.open(new File(corpusPath)),false);
			//			System.out.println("doc: "+reader.document(0).toString());

			QueryParser qparser = new QueryParser(Version.LUCENE_29,fieldName,analyzer);
			Query q = qparser.parse(query);

			IndexSearcher iSearch = new IndexSearcher(reader);

			boolean noSolution = true;
			NumberFormat nf = NumberFormat.getInstance(Locale.FRENCH);
			nf.setMaximumFractionDigits(2);

			HashSet<String> mentionedDocs = new HashSet<String>();

			TopDocs luceneResults = iSearch.search(q, (Filter)null, searchedDocs.size());
			for (int i=0; i<luceneResults.totalHits;i++)
			{
				String docPath = iSearch.doc(luceneResults.scoreDocs[i].doc).get("path");
				if(docPath.contains("\\")){
					docPath = docPath.substring(docPath.lastIndexOf("\\")+1,docPath.length());
				} else {
					docPath = docPath.substring(docPath.lastIndexOf("/")+1,docPath.length());
				}
				if (searchedDocs.contains(corpus.getDocument(docPath)))
				{
					mentionedDocs.add(docPath);
					noSolution = false;
					Vector<String> tempVector = new Vector<String>();
					tempVector.add(docPath);
					tempVector.add(nf.format(luceneResults.scoreDocs[i].score));
					//					if (h.score(i)>=threshold)
					result.add(tempVector);
				}
			}		

			if (noSolution)
			{
				Vector<String> tempVec = new Vector<String>();
				tempVec.add("No match");
				tempVec.add("0");
				result.add(tempVec);
			}
			iSearch.close();
			reader.close();

		}
		catch(IOException ioe){ioe.printStackTrace();}
		catch(ParseException pe){pe.printStackTrace();}

		return result;
	}


	/**
	 * Gets the highest float number from a matrix
	 * @param matrix the query/doc symptoms matrix
	 * @return the highest value contained in the matrix
	 */
	private static float getMaxSimilarity(HashMap<String,HashMap<String,Float>> matrix)
	{
		float maxSim = 0;
		float currentSim;
		String maxQueryPb = "";
		String maxFilePb = "";

		for(String queryPbInst:matrix.keySet())
		{
			for (String filePbInst:matrix.get(queryPbInst).keySet())
			{
				currentSim = matrix.get(queryPbInst).get(filePbInst);
				if (java.lang.Math.abs(currentSim)>java.lang.Math.abs(maxSim))
				{
					maxSim=currentSim;
					maxQueryPb = queryPbInst;
					maxFilePb = filePbInst;
				}
			}
		}
		if (!maxQueryPb.equals(""))
		{
			matrix.remove(maxQueryPb);
			for (String queryPb:matrix.keySet())
			{
				HashMap<String,Float> tempMap = matrix.get(queryPb);
				tempMap.remove(maxFilePb);
				matrix.put(queryPb, tempMap);
			}
		}
		return(maxSim);
	}

	/**
	 * Computes the ProxiGenea similarity for two concepts, given a local root for the subtaxonomy including both concepts
	 * @param c1 first concept
	 * @param c2 second concept
	 * @param localRoot the local root
	 * @return ProxiGenea similarity (see Thesis)
	 * @author Bachelin
	 */
	@SuppressWarnings("unchecked")
	public static float computeProxiGenea(OWLNamedClass c1, OWLNamedClass c2,OWLNamedClass localRoot)
	{
		ArrayList<OWLNamedClass> subsumers1 = (ArrayList<OWLNamedClass>)c1.getNamedSuperclasses(true);
		subsumers1.add(c1);
		ArrayList<OWLNamedClass> subsumers2 = (ArrayList<OWLNamedClass>)c2.getNamedSuperclasses(true);
		subsumers2.add(c2);
		subsumers1.retainAll(subsumers2);
		subsumers1.removeAll(localRoot.getNamedSuperclasses(true));
		int greatestDepth = 1;
		for (OWLNamedClass father:subsumers1)
		{
			int currentDepth = computeDepth(father,localRoot);
			if (currentDepth>greatestDepth)
				greatestDepth = currentDepth;
		}
		float result = new Float(greatestDepth*greatestDepth)/new Float(computeDepth(c1,localRoot)*computeDepth(c2,localRoot));
		return result;
	}

	/**
	 * Computes the ProxiGenea2 similarity for two concepts, given a local root for the subtaxonomy including both concepts
	 * @param c1 first concept
	 * @param c2 second concept
	 * @param localRoot the local root
	 * @return ProxiGenea2 similarity (see Thesis)
	 * @author Bachelin
	 */
	@SuppressWarnings("unchecked")
	public static float computeProxiGenea2(OWLNamedClass c1, OWLNamedClass c2,OWLNamedClass localRoot)
	{
		ArrayList<OWLNamedClass> subsumers1 = (ArrayList<OWLNamedClass>)c1.getNamedSuperclasses(true);
		subsumers1.add(c1);
		ArrayList<OWLNamedClass> subsumers2 = (ArrayList<OWLNamedClass>)c2.getNamedSuperclasses(true);
		subsumers2.add(c2);
		subsumers1.retainAll(subsumers2);
		subsumers1.removeAll(localRoot.getNamedSuperclasses(true));
		int greatestDepth = 1;
		for (OWLNamedClass father:subsumers1)
		{
			int currentDepth = computeDepth(father,localRoot);
			if (currentDepth>greatestDepth)
				greatestDepth = currentDepth;
		}
		float result = new Float(greatestDepth)/new Float(computeDepth(c1,localRoot)+computeDepth(c2,localRoot)-greatestDepth);
		return result;
	}

	/**
	 * Computes the ProxiGenea3 similarity for two concepts, given a local root for the subtaxonomy including both concepts
	 * @param c1 first concept
	 * @param c2 second concept
	 * @param localRoot the local root
	 * @return ProxiGenea3 similarity (see Thesis)
	 * @author Bachelin
	 */
	@SuppressWarnings("unchecked")
	public static float computeProxiGenea3(OWLNamedClass c1, OWLNamedClass c2,OWLNamedClass localRoot)
	{
		ArrayList<OWLNamedClass> subsumers1 = (ArrayList<OWLNamedClass>)c1.getNamedSuperclasses(true);
		subsumers1.add(c1);
		ArrayList<OWLNamedClass> subsumers2 = (ArrayList<OWLNamedClass>)c2.getNamedSuperclasses(true);
		subsumers2.add(c2);
		subsumers1.retainAll(subsumers2);
		subsumers1.removeAll(localRoot.getNamedSuperclasses(true));
		int greatestDepth = 1;
		for (OWLNamedClass father:subsumers1)
		{
			int currentDepth = computeDepth(father,localRoot);
			if (currentDepth>greatestDepth)
				greatestDepth = currentDepth;
		}
		float result = 1/new Float(1+computeDepth(c1,localRoot)+computeDepth(c2,localRoot)-2*greatestDepth);
		return result;
	}

	/**
	 * Global method based on the OTR (semantic IR) to compare a formalized query to a set of documents
	 * @param queryAnnotationIsolatedInstances - all found isolated instances in the query
	 * (scheme ARKEOTEK)
	 * @param corpus - a whole set of documents. WARNING: only validated documents must be compared with the query (corpus.getValidatedDocuments())
	 * @param threshold - a given value between 0 and 1, under which a document similarity with the query is considered irrelevant (too low)
	 * @param searchDocumentField - the semantic field used
	 * @return a list of (filename,score) couples ordered by decreasing score
	 */
	public static Vector<Vector<String>> genericSimilarityAlgorithm(HashSet<OWLIndividual> queryAnnotationIsolatedInstances, Corpus corpus, float threshold, String searchDocumentField, Properties prop)
	{
		NumberFormat nf = NumberFormat.getInstance(Locale.FRENCH);
		nf.setMaximumFractionDigits(2);
		HashMap<String, Float> coefficentsByTypesOfConcept = getCoefficientsByConceptSuperType(prop);
		Vector<Vector<String>> results = new Vector<Vector<String>>();
		String cptSim = prop.getProperty("plugin.conceptsim");
		
		//System.err.println("Instances in query: "+queryAnnotationIsolatedInstances.size());
		if (queryAnnotationIsolatedInstances.size() > 0)
		{
			HashSet<DocumentAnnotation> validatedDocuments = corpus.getValidatedDocuments();
			for (DocumentAnnotation aDocumentAnnotation : validatedDocuments)
			{
				Float documentSimilarity = 0f;
				Float coeffSum = 0f;
				Vector<String> matchingClasses = new Vector<String>();
				
				HashSet<OWLIndividual> documentInstances = aDocumentAnnotation.getIsolatedInstances(searchDocumentField);

				for (OWLIndividual queryInstance : queryAnnotationIsolatedInstances)
				{
					Collection<OWLNamedClass> queryInstanceSuperTypes=getConceptSuperType(queryInstance);
					Float coeff = new Float(1.0);
					Float maxInstanceSimilarity = 0f;
					String matchingPair="";
					for (OWLIndividual docInstance : documentInstances)
					{
						// si les concepts sont comparables
						Collection<OWLNamedClass> documentInstanceSuperTypes=getConceptSuperType(docInstance);
						//String s_docConceptSuperType=getConceptSuperType(docInstance).getLocalName();
						documentInstanceSuperTypes.retainAll(queryInstanceSuperTypes);
						if (documentInstanceSuperTypes.size()>0)
						{
							OWLNamedClass cs_class = documentInstanceSuperTypes.iterator().next();
							String cs_typeName=cs_class.getLocalName();
							coeff = coefficentsByTypesOfConcept.get(cs_typeName);
							if(coeff==null) coeff = new Float(1.0); 

							Float tmpInstanceSimilarity = coeff * computeConceptSimilarity(cptSim, (OWLNamedClass) queryInstance.getRDFType(), (OWLNamedClass) docInstance.getRDFType(), cs_class);
							if (tmpInstanceSimilarity > maxInstanceSimilarity)
							{
								maxInstanceSimilarity = tmpInstanceSimilarity;
								matchingPair = "("+queryInstance.getRDFType().getLocalName()+"<->"+docInstance.getRDFType().getLocalName()+")";
							}
						}
						//System.err.println("instance similarity: "+docInstance.getLocalName()+" <-> "+queryInstance.getLocalName()+"="+maxInstanceSimilarity);
					}
					
					if(!matchingPair.equals("")) matchingClasses.add(matchingPair);
					documentSimilarity += maxInstanceSimilarity;
					coeffSum += coeff;
				}

				documentSimilarity = documentSimilarity / Math.max(coeffSum, 1f);
				//System.err.println("similarity for document '"+aDocumentAnnotation.getDocumentName()+"' : "+nf.format(documentSimilarity));
				if (documentSimilarity >= threshold)
				{
					Vector<String> tempVec = new Vector<String>();
					tempVec.add(aDocumentAnnotation.getDocumentName());
					tempVec.add(String.valueOf(nf.format(documentSimilarity)));
					tempVec.add(matchingClasses.toString());
					results.add(tempVec);
				}
			}
		}

		if (results.size() == 0)
		{
			Vector<String> tempVec = new Vector<String>();
			tempVec.add("No match");
			tempVec.add("0");
			results.add(tempVec);
		}
		return results;
	}

   
    /**
     * Method based on the OTR (semantic IR) to compare a formalized query to a set of documents
     * calculates concept similarity then check whether the relations found in the query are present, if so a bonus is given to the score
     * (scheme MOANO)
     * @param queryAnnotationTriples - all found triples in the query
     * @param corpus - a whole set of documents. WARNING: only validated documents must be compared with the query (corpus.getValidatedDocuments())
     * @param threshold - a given value between 0 and 1, under which a document similarity with the query is considered irrelevant (too low)
      * @param searchDocumentField - the semantic field used
     * @return a list of (filename,score) couples ordered by decreasing score
     * 
     * @author Davide Buscaldi
     */
    public static Vector<Vector<String>> newRelationBasedSimilarityAlgorithm(HashSet<OWLIndividual> queryAnnotationIsolatedInstances, /*HashMap<OWLIndividual, HashMap<OWLObjectProperty, HashSet<OWLIndividual>>>*/ HashSet<AnnotationTriple> queryAnnotationTriples, Corpus corpus, float threshold,
            String searchDocumentField, Properties prop)
    {
        NumberFormat nf = NumberFormat.getInstance(Locale.FRENCH);
        nf.setMaximumFractionDigits(2);
        HashMap<String, Float> coefficentsByTypesOfConcept = getCoefficientsByConceptSuperType(prop);
        HashMap<String, Float> maxCoefficientsByQueryInstance = new HashMap<String, Float>();
        
        String cptSim = prop.getProperty("plugin.conceptsim");
        
        Vector<Vector<String>> results = new Vector<Vector<String>>();
        
		//System.err.println("Instances in query: "+queryAnnotationIsolatedInstances.size());
		if (queryAnnotationIsolatedInstances.size() > 0)
		{
			HashSet<DocumentAnnotation> validatedDocuments = corpus.getValidatedDocuments();
			for (DocumentAnnotation aDocumentAnnotation : validatedDocuments)
			{
				//System.err.println("Checking document: "+aDocumentAnnotation.getDocumentName());
				Float documentSimilarity = 0f;
				Float coeffSum = 0f;
				Vector<String> matchingClasses = new Vector<String>();
				
				HashSet<OWLIndividual> documentInstances = aDocumentAnnotation.getIsolatedInstances(searchDocumentField);
				HashMap<String, String> bestMatchingClass = new HashMap<String, String>();
								
				for (OWLIndividual queryInstance : queryAnnotationIsolatedInstances)
				{
					
					Collection<OWLNamedClass> queryInstanceSuperTypes=getConceptSuperType(queryInstance);
					Float coeff = new Float(1.0);
					Float maxInstanceSimilarity = 0f;
					String matchingPair="";
					bestMatchingClass.put(queryInstance.getRDFType().getLocalName(), queryInstance.getRDFType().getLocalName()); //if we don't find a corresponding class, we keep the original one
					
					for (OWLIndividual docInstance : documentInstances)
					{
						// si les concepts sont comparables
						Collection<OWLNamedClass> documentInstanceSuperTypes=getConceptSuperType(docInstance);
						documentInstanceSuperTypes.retainAll(queryInstanceSuperTypes);
						if (documentInstanceSuperTypes.size()>0)
						{
							OWLNamedClass cs_class = documentInstanceSuperTypes.iterator().next();
							String cs_typeName=cs_class.getLocalName();
							coeff = coefficentsByTypesOfConcept.get(cs_typeName);
							if(coeff==null) coeff = new Float(1.0); 
							Float tmpInstanceSimilarity = coeff * computeConceptSimilarity(cptSim, (OWLNamedClass) queryInstance.getRDFType(), (OWLNamedClass) docInstance.getRDFType(), cs_class);
							if (tmpInstanceSimilarity > maxInstanceSimilarity)
							{
								maxInstanceSimilarity = tmpInstanceSimilarity;
								matchingPair = "("+queryInstance.getRDFType().getLocalName()+"<-Concept->"+docInstance.getRDFType().getLocalName()+")";
								bestMatchingClass.put(queryInstance.getRDFType().getLocalName(), docInstance.getRDFType().getLocalName());
							}
						}
					}
					
					if(!matchingPair.equals("")) matchingClasses.add(matchingPair);
					maxCoefficientsByQueryInstance.put(queryInstance.getRDFType().getLocalName(), maxInstanceSimilarity);
					
					documentSimilarity += maxInstanceSimilarity;
					coeffSum += coeff;
				}

				documentSimilarity = documentSimilarity / Math.max(coeffSum, 1f);
				//System.err.println("similarity for document '"+aDocumentAnnotation.getDocumentName()+"' : "+nf.format(documentSimilarity));
				
				if (queryAnnotationTriples.size() > 0){
					
					for (AnnotationTriple queryAnnotationTriple : queryAnnotationTriples)
	                {
	                    OWLIndividual queryDomainNode = queryAnnotationTriple.getDomainInd();
	                    RDFProperty queryRelation = queryAnnotationTriple.getRelatingProperty();
	                    OWLIndividual queryRangeNode = queryAnnotationTriple.getRangeInd();
                    	
                		boolean relationExists = RelationIndexFactory.checkRel(aDocumentAnnotation, queryDomainNode, queryRangeNode, queryRelation.getLocalName());
                		
            			if(relationExists){
            				String s_domConceptSuperType=getConceptSuperType(queryDomainNode).iterator().next().getLocalName();
    						String s_rangeConceptSuperType=getConceptSuperType(queryRangeNode).iterator().next().getLocalName();
    						Float domCoeff = coefficentsByTypesOfConcept.get(s_domConceptSuperType);
    						if(domCoeff==null) domCoeff= 1f;
    						Float rangeCoeff = coefficentsByTypesOfConcept.get(s_rangeConceptSuperType);
    						if(rangeCoeff==null) rangeCoeff= 1f;
    						
    						/* CALCUL DU POIDS DE LA RELATION */
            				Float domainWeight = maxCoefficientsByQueryInstance.get(queryDomainNode.getRDFType().getLocalName());
                			Float rangeWeight = maxCoefficientsByQueryInstance.get(queryRangeNode.getRDFType().getLocalName());
                			Float relationWeight = (domainWeight+rangeWeight)/(domCoeff+rangeCoeff);
                			documentSimilarity+=relationWeight;
                			
                			String matchingTriple = "("+bestMatchingClass.get(queryDomainNode.getRDFType().getLocalName())+"<-"+queryRelation.getLocalName()+"->"+bestMatchingClass.get(queryRangeNode.getRDFType().getLocalName())+")";
                			matchingClasses.add(matchingTriple);
        				}	                    			
                		
	                }
				}
					
				if (documentSimilarity >= threshold)
				{
					Vector<String> tempVec = new Vector<String>();
					tempVec.add(aDocumentAnnotation.getDocumentName());
					tempVec.add(String.valueOf(nf.format(documentSimilarity)));
					tempVec.add(matchingClasses.toString());
					results.add(tempVec);
				}
			}
		}
		
		if (results.size() == 0)
		{
			Vector<String> tempVec = new Vector<String>();
			tempVec.add("No match");
			tempVec.add("0");
			results.add(tempVec);
		}
		return results;
    }
    
	/**
	 * Extracts graphs using the triples found in an annotation
	 * @param queryTriples the triples found in an annotation
	 * @return a list of AnnotationGraph
	 */
	public static ArrayList<AnnotationGraph> extractGraphsFromTriples(HashMap<OWLIndividual, HashMap<OWLObjectProperty, HashSet<OWLIndividual>>> queryTriples)
	{
		ArrayList<AnnotationGraph> annotGraphs = new ArrayList<AnnotationGraph>();

		for (OWLIndividual domainInst : queryTriples.keySet())
		{
			for (OWLObjectProperty relation : queryTriples.get(domainInst).keySet())
			{
				for (OWLIndividual codomainInst : queryTriples.get(domainInst).get(relation))
				{
					boolean associated = false;

					for (AnnotationGraph graph : annotGraphs)
					{
						if (graph.getNodes().contains(codomainInst) || graph.getNodes().contains(domainInst))
						{
							graph.addTriple(domainInst, relation, queryTriples.get(domainInst).get(relation));
							associated = true;
							break;
						}
					}

					if (!associated)
					{
						AnnotationGraph graph = new AnnotationGraph();
						graph.addTriple(domainInst, relation, queryTriples.get(domainInst).get(relation));
						annotGraphs.add(graph);
					}
				}
			}
		}
		return annotGraphs;
	}

	/**
	 * Retrieves the supertype of a given concept.
	 * @param concept a concept
	 * @return its supertype
	 */
	@SuppressWarnings("unchecked")
	public static Collection<OWLNamedClass> getConceptSuperType(OWLIndividual concept)
	{
		//System.err.println("Concept: "+concept.getRDFType().getLocalName());
		Collection<OWLNamedClass> conceptSuperclasses = concept.getRDFType().getNamedSuperclasses(true);
		conceptSuperclasses.add((OWLNamedClass) concept.getRDFType());
		conceptSuperclasses.retainAll(concept.getOWLModel().getOWLNamedClass(OTR.DOMAIN_THING).getNamedSubclasses(false));
		/*System.err.println("superclasses: ");
		for(OWLNamedClass c : conceptSuperclasses){
			System.err.println(c.getBrowserText());
		}
		System.err.println();*/
		return conceptSuperclasses;//.iterator().next(); version sans heritage multiple
	}
	
	
	/**
	 * Retrieves the supertype of a given concept as string
	 * @param concept a concept
	 * @return its supertype
	 */
	@SuppressWarnings("unchecked")
	public static HashSet<String> getStringConceptSuperType(OWLIndividual concept)
	{
		HashSet<String> superSet= new HashSet<String>();
		HashSet<String> topSet= new HashSet<String>();
		Collection<OWLNamedClass> conceptSuperclasses = concept.getRDFType().getNamedSuperclasses(true);
		for(OWLNamedClass c : conceptSuperclasses) superSet.add(c.getLocalName());
		superSet.add(concept.getRDFType().getLocalName());
		Collection<OWLNamedClass> topClasses = concept.getOWLModel().getOWLNamedClass(OTR.DOMAIN_THING).getNamedSubclasses(false);
		for(OWLNamedClass c : topClasses) topSet.add(c.getLocalName());
		superSet.retainAll(topSet);
		return superSet;//.iterator().next(); version sans heritage multiple
	}

	/**
	 * Retrieves from the configuration file, the coefficients associated to each supertype
	 * @param obirProject
	 * @return a list of couples (supertype, coefficient)
	 */
	public static HashMap<String, Float> getCoefficientsByConceptSuperType(Properties prop)
	{
		HashMap<String, Float> coefficientByConceptSuperType = new HashMap<String, Float>();
		String property = prop.getProperty("plugin.coefficients"); //ObirProject.getPluginProperties().

		String[] coefficients = property.split(",");
		for (String coeff : coefficients)
		{
			String[] couple = coeff.split("=");
			coefficientByConceptSuperType.put(couple[0], new Float(couple[1]));
		}
		return coefficientByConceptSuperType;
	}
	
	/**
	 * Convenience method that picks the correct concept similarity method according to the input type
	 * @param type the concept similarity method (one among pg1, pg2, pg3 and wp)
	 * @param c1 the first concept to be compared
	 * @param c2 the second concept
	 * @param localRoot the most general common parent
	 * @return the similarity value according to the given method
	 * @author Davide Buscaldi
	 */
	private static float computeConceptSimilarity(String type, OWLNamedClass c1, OWLNamedClass c2,OWLNamedClass localRoot){
		if(type.equals("pg2")) return computeProxiGenea2(c1, c2, localRoot);
		else if(type.equals("pg3")) return computeProxiGenea3(c1, c2, localRoot);
		else if(type.equals("wp")) return computeWuPalmerSimilarity(c1, c2, localRoot);
		else return computeProxiGenea(c1, c2, localRoot);
	}
}