package obir.otr;

//import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;

import obir.ir.Corpus;
import obir.ir.DocumentAnnotation;

import org.xml.sax.SAXException;

import edu.stanford.smi.protegex.owl.model.OWLIndividual;
import edu.stanford.smi.protegex.owl.model.OWLModel;
import edu.stanford.smi.protegex.owl.model.OWLNamedClass;
import edu.stanford.smi.protegex.owl.model.OWLObjectProperty;

/**
 * Convenience class gathering different static methods allowing to create annotation graphs for a document from the terms and concepts found in it.
 * @author Axel Reymonet
 */
public class AnnotationGraphCreator {

	public final static boolean debug = true;

	/**
	 * Method specific to ACTIA classifying all given concept instances according to the subtaxonomy they belong to
	 * @param instNames a list of concept instance names
	 * @return all corresponding concept instances, classified by category
	 */
	@SuppressWarnings("unchecked")
	private static HashMap<String,HashSet<OWLIndividual>> orderConceptInstancesByCategory(Set<String> instNames,OWLModel onto)
	{
		HashMap<String,HashSet<OWLIndividual>> result = new HashMap<String,HashSet<OWLIndividual>>();
		result.put("service", new HashSet<OWLIndividual>());
		result.put("genericPb", new HashSet<OWLIndividual>());
		result.put("nonGenericPb", new HashSet<OWLIndividual>());
		result.put("context", new HashSet<OWLIndividual>());
		result.put("symptom", new HashSet<OWLIndividual>());
		for (String indName:instNames)
		{
			OWLIndividual conceptInd = (OWLIndividual)onto.getOWLIndividual(indName);
			if (conceptInd!=null)
			{
				ArrayList<OWLNamedClass> superclasses = (ArrayList<OWLNamedClass>)conceptInd.getRDFType().getNamedSuperclasses(true);
				superclasses.add((OWLNamedClass)conceptInd.getRDFType());
				String key = "";


				if (superclasses.contains(onto.getOWLNamedClass(OTR.serviceID)))
					key = "service";
				else if (superclasses.contains(onto.getOWLNamedClass(OTR.problemID)))
				{
					ArrayList<OWLNamedClass> ranges = OTR.getMostSpecificRestrictedRanges(((OWLNamedClass)conceptInd.getRDFType()), onto.getOWLObjectProperty(OTR.affectsServiceID));
					if ((ranges.size()==1) && ranges.iterator().next().equals(onto.getOWLNamedClass(OTR.serviceID)))
						key = "genericPb";
					else
						key = "nonGenericPb";
				}

				else if (superclasses.contains(onto.getOWLNamedClass(OTR.contextID)))
					key = "context";
				else if (superclasses.contains(onto.getOWLNamedClass(OTR.SYMPTOME)))
					key = "symptom";
				if (!key.equals(""))
				{
					HashSet<OWLIndividual> tempSet = result.get(key);
					tempSet.add(conceptInd);
					result.put(key, tempSet);
				}
			}
		}
		return result;
	}

	/**
	 * Generic method classifying all given concept instances according to the subtaxonomy they belong to
	 * @param instNames a list of concept instance names
	 * @return all corresponding concept instances, classified by category
	 */
	@SuppressWarnings("unchecked")
	private static HashMap<String,ArrayList<OWLIndividual>> orderGenericallyConceptInstancesByCategory(Set<String> instNames,OWLModel onto)
	{
		HashMap<String,ArrayList<OWLIndividual>> result = new HashMap<String,ArrayList<OWLIndividual>>();
		HashSet<OWLNamedClass> categs = new HashSet<OWLNamedClass>(); 
		for (OWLNamedClass categ:(Collection<OWLNamedClass>)onto.getOWLNamedClass(obir.otr.OTR.DOMAIN_THING).getNamedSubclasses(false))
		{
			result.put(categ.getLocalName(), new ArrayList<OWLIndividual>());
			categs.add(categ);
		}
		for (String indName:instNames)
		{
			OWLIndividual conceptInd = (OWLIndividual)onto.getOWLIndividual(indName);
			ArrayList<OWLNamedClass> superclasses = (ArrayList<OWLNamedClass>)conceptInd.getRDFType().getNamedSuperclasses(true);
			superclasses.add((OWLNamedClass)conceptInd.getRDFType());
			String key = "";

			superclasses.retainAll(categs);
			if (superclasses.size()==1)
			{
				key = superclasses.iterator().next().getLocalName();
				ArrayList<OWLIndividual> tempSet = result.get(key);
				tempSet.add(conceptInd);
				result.put(key, tempSet);
			}
		}
		return result;
	}


	/**
	 * Computes which service is the closest to a given problem
	 * @param originOffsets the position of a concept instance in a document 
	 * @param destInds a set of destination instances, linkable to the given concept instance
	 * @param instMap information relative to the positions of the destination instances
	 * @return the closest destination instance
	 */
	private static OWLIndividual computeClosestCompatibleDestination(Integer[] originOffsets,Collection<OWLIndividual> destInds,HashMap<String,String[]> instMap)
	{
		int origOffsetStart = originOffsets[0];
		int origOffsetEnd = originOffsets[1];
		String destOffsetString;
		int destOffsetStart;
		int destOffsetEnd;
		OWLIndividual closestDestInd=null;
		int closestDistance=-1;
		int distance;

		for (OWLIndividual destInd:destInds)
		{
			destOffsetString=(String)destInd.getOWLModel().getOWLIndividual(instMap.get(destInd.getBrowserText())[0]).getPropertyValue(destInd.getOWLModel().getOWLDatatypeProperty(OTR.TERM_OFFSET));
			destOffsetStart = new Integer(destOffsetString.substring(1,destOffsetString.replaceAll("]", ",").indexOf(",")));
			destOffsetEnd = Integer.parseInt(instMap.get(destInd.getBrowserText())[1]);
			distance = destOffsetStart-origOffsetEnd;
			if (distance<0)
				distance = origOffsetStart-destOffsetEnd;

			if ((closestDestInd==null)||distance<closestDistance)
			{
				closestDestInd=destInd;
				closestDistance=distance;
			}
		}
		return closestDestInd;
	}

	/**
	 * Static method creating all possible instance pairings between two different sets of instances.
	 * @param pbNb the size of the first instance set  
	 * @param servIndices the size of the second instance set
	 * @param path the pairings already proposed
	 * @param solution the resulting matrix, built recursively
	 */
	@SuppressWarnings("unchecked")
	private static void recurseFindPath(int pbNb,HashSet<Integer> servIndices, ArrayList<Integer> path, ArrayList<ArrayList<Integer>> solution)
	{
		Iterator<Integer> it = servIndices.iterator();
		while (it.hasNext())
		{

			ArrayList<Integer> newPath = (ArrayList<Integer>) path.clone();
			int j = it.next();
			newPath.add(j);		
			if (newPath.size()==pbNb)
				solution.add(newPath);
			else
			{
				HashSet<Integer> newServIndices = (HashSet<Integer>)servIndices.clone();
				newServIndices.remove(j);
				recurseFindPath(pbNb,newServIndices,newPath,solution);
			}
		}
	}

	/**
	 * Method specific to ACTIA used to pair all specific problems with the appropriate service
	 * @param file the document in which the problem has been found
	 * @param field the field in which the problem has been found
	 * @param categorizedInds the list of all concept instances in the document
	 * @param linkedPbs a list of problems already paired, to be updated
	 * @param linkedServs a list of services already paired, to be updated
	 * @param specServs a list of services needing a concept specialization, to be updated
	 * @param instByFile information related to concept instances and their associated term occurrences
	 */
	@SuppressWarnings("unchecked")
	private static void handleSpecificProblems(String file, String field, HashMap<String,HashSet<OWLIndividual>> categorizedInds, HashSet<OWLIndividual> linkedPbs, HashSet<OWLIndividual> linkedServs,HashSet<OWLIndividual> specServs, HashMap<String,HashMap<String,HashMap<String,String[]>>> instByFile)
	{
		OWLObjectProperty affecte = ObirProject.getOWLModel().getOWLObjectProperty(OTR.affectsServiceID);

		for (OWLIndividual nonGenPbInd:categorizedInds.get("nonGenericPb"))
		{
			String pbOffsetString=(String)nonGenPbInd.getOWLModel().getOWLIndividual(instByFile.get(file).get(field).get(nonGenPbInd.getBrowserText())[0]).getPropertyValue(nonGenPbInd.getOWLModel().getOWLDatatypeProperty(OTR.TERM_OFFSET));
			Integer [] pbOffsets = new Integer[2];
			pbOffsets[0]= new Integer(pbOffsetString.substring(1,pbOffsetString.replaceAll("]", ",").indexOf(",")));
			pbOffsets[1]= Integer.parseInt(instByFile.get(file).get(field).get(nonGenPbInd.getBrowserText())[1]);

			ArrayList<OWLNamedClass> ranges = OTR.getMostSpecificRestrictedRanges((OWLNamedClass)nonGenPbInd.getRDFType(), nonGenPbInd.getOWLModel().getOWLObjectProperty(OTR.affectsServiceID));//((OWLNamedClass)nonGenPbInd.getRDFType()).getAllValuesFrom(onto.getOWLObjectProperty(AFFECTE));


			HashSet<OWLIndividual> directlyCompatibleServs = new HashSet<OWLIndividual>();
			HashMap<OWLIndividual,OWLNamedClass> neededSpecialization = new HashMap<OWLIndividual,OWLNamedClass>();
			HashSet<OWLNamedClass> intersection = new HashSet<OWLNamedClass>();		
			for (OWLIndividual servInd:categorizedInds.get("service"))
			{

				intersection = new HashSet<OWLNamedClass>();
				intersection.addAll(ranges);

				ArrayList<OWLNamedClass> classAndParents = (ArrayList<OWLNamedClass>) servInd.getRDFType().getNamedSuperclasses(true);
				classAndParents.add((OWLNamedClass)servInd.getRDFType());
				intersection.retainAll(classAndParents);
				if (!intersection.isEmpty())
					directlyCompatibleServs.add(servInd);
				else
				{
					intersection.addAll(ranges);
					intersection.retainAll(servInd.getRDFType().getNamedSubclasses(true));
					if (!intersection.isEmpty())
					{
						neededSpecialization.put(servInd, intersection.iterator().next());
					}
				}
			}

			OWLIndividual closestServInd = null;
			HashSet<OWLIndividual> eligibleServs = (HashSet<OWLIndividual>)directlyCompatibleServs.clone();
			eligibleServs.addAll(neededSpecialization.keySet());
			closestServInd = computeClosestCompatibleDestination(pbOffsets, eligibleServs, instByFile.get(file).get(field));


			//			if (!directlyCompatibleServs.isEmpty())
			if (directlyCompatibleServs.contains(closestServInd))
			{
				//				closestServInd = computeClosestCompatibleService(pbOffsets, directlyCompatibleServs, instByFile.get(file));

				nonGenPbInd.addPropertyValue(affecte, closestServInd);

				linkedPbs.add(nonGenPbInd);
				linkedServs.add(closestServInd);
			}
			else
			{
				//				closestServInd = computeClosestCompatibleService(pbOffsets, neededSpecialization.keySet(), instByFile.get(file));
				if (closestServInd!=null)
				{
					if (ranges.size()==1)
					{
						specServs.add(closestServInd);
//						String newSpecServIndName = ObirProject.generateNextIndName();

//						OWLIndividual newSpecServInd = neededSpecialization.get(closestServInd).createOWLIndividual(newSpecServIndName);
						OWLIndividual newSpecServInd = ObirProject.getOTR().createConceptInstance(ObirProject.getCorpus().getDocument(file), field, neededSpecialization.get(closestServInd));
						
						OWLIndividual termToLink = nonGenPbInd.getOWLModel().getOWLIndividual(instByFile.get(file).get(field).get(closestServInd.getBrowserText())[0]);

						termToLink.setPropertyValue(nonGenPbInd.getOWLModel().getOWLObjectProperty(OTR.designatesID), newSpecServInd);

						//						corpus.getDocument((String)termToLink.getPropertyValue(onto.getOWLDatatypeProperty(obir.otr.OTR.DOC_ID))).addConceptInstance(newSpecServInd);
						//							ObirProject.getCorpus().addConceptInstance(newSpecServInd,ObirProject.getCorpus().getDocument((String)termToLink.getPropertyValue(onto.getOWLDatatypeProperty(obir.otr.OTR.DOC_ID))));
						ObirProject.getCorpus().getDocument((String)termToLink.getPropertyValue(nonGenPbInd.getOWLModel().getOWLDatatypeProperty(obir.otr.OTR.DOC_ID))).addConceptInstance(newSpecServInd,field);

						HashMap<String,String[]> tempMap = instByFile.get(file).get(field);
						String [] tempTab = new String [2];
						tempTab[0] = termToLink.getBrowserText();
						tempTab[1] = instByFile.get(file).get(field).get(closestServInd.getBrowserText())[1];
						tempMap.put(newSpecServInd.getLocalName(), tempTab);
						if  (instByFile.containsKey(field))
							instByFile.get(field).put(file, tempMap);

						nonGenPbInd.addPropertyValue(affecte, newSpecServInd);

						linkedPbs.add(nonGenPbInd);
						linkedServs.add(newSpecServInd);	
					}
					else if (ranges.size()>1)
					{
						//TODO cas o� il y a une prestation � sp�cialiser mais que la relation d'affectation a plusieurs co-domaines possibles.
						//a priori, on ne fait rien
					}
				}
			}
		}

		HashSet<OWLIndividual> unaffectedSpecificProblems = new HashSet<OWLIndividual>();
		unaffectedSpecificProblems.addAll(categorizedInds.get("nonGenericPb"));
		unaffectedSpecificProblems.removeAll(linkedPbs);
		for (OWLIndividual specPbInst:unaffectedSpecificProblems)
		{
			ArrayList<OWLNamedClass> pbRanges = OTR.getMostSpecificRestrictedRanges((OWLNamedClass)specPbInst.getRDFType(), affecte);
			if (pbRanges.size()==1)
			{
//				String servIndName = ObirProject.generateNextIndName();
//				OWLIndividual artificialServInst = pbRanges.iterator().next().createOWLIndividual(servIndName);
				OWLIndividual artificialServInst = ObirProject.getOTR().createConceptInstance(ObirProject.getCorpus().getDocument(file), field, pbRanges.iterator().next());

//				String filename = (String) OTR.getAssociatedTermOccurrences(specPbInst).iterator().next().getPropertyValue(ObirProject.getOWLModel().getOWLDatatypeProperty(obir.otr.OTR.DOC_ID));
				//				corpus.getDocument(filename).addConceptInstance(artificialServInst);
				//					ObirProject.getCorpus().addConceptInstance(artificialServInst,ObirProject.getCorpus().getDocument(filename));
//				ObirProject.getCorpus().getDocument(filename).addConceptInstance(artificialServInst,field);

				specPbInst.addPropertyValue(affecte, artificialServInst);

				linkedPbs.add(specPbInst);
				linkedServs.add(artificialServInst);
			}
		}

	}


	/**
	 * Method specific to ACTIA used to pair all generic problems with the appropriate services
	 * @param categorizedInds the list of all concept instances in the document
	 * @param linkedPbs a list of problems already paired, to be updated
	 * @param linkedServs a list of services already paired, to be updated
	 * @param termInfosByCptInst information related to concept instances and their associated term occurrences
	 */
	private static void handleGenericProblems(HashMap<String,HashSet<OWLIndividual>> categorizedInds, HashSet<OWLIndividual> linkedPbs, HashSet<OWLIndividual> linkedServs,HashMap<String,String[]> termInfosByCptInst)
	{
		ArrayList<OWLIndividual> servList = new ArrayList<OWLIndividual>();
		for (OWLIndividual ind:categorizedInds.get("service"))
		{
			servList.add(ind);
		}
		ArrayList<OWLIndividual> genPbList = new ArrayList<OWLIndividual>();
		for  (OWLIndividual indiv:categorizedInds.get("genericPb"))
		{
			genPbList.add(indiv);
		}
		ArrayList<ArrayList<Integer>> pbServDist = new ArrayList<ArrayList<Integer>>();
		for (OWLIndividual pb:genPbList)
		{
			ArrayList<Integer> line = new ArrayList<Integer>();
			for (OWLIndividual serv:servList)
			{
				String pbName = pb.getBrowserText(); 
				if (pbName.contains("#"))
					pbName=pbName.substring(pbName.indexOf("#")+1);

				String pbOffsetString=(String)pb.getOWLModel().getOWLIndividual(termInfosByCptInst.get(pbName)[0]).getPropertyValue(pb.getOWLModel().getOWLDatatypeProperty(OTR.TERM_OFFSET));
				int pbOffsetStart = new Integer(pbOffsetString.substring(1,pbOffsetString.replaceAll("]", ",").indexOf(",")));
				int pbOffsetEnd = Integer.parseInt(termInfosByCptInst.get(pbName)[1]);

				String servName = serv.getBrowserText(); 
				if (servName.contains("#"))
					servName=servName.substring(servName.indexOf("#")+1);

				String servOffsetString=(String)pb.getOWLModel().getOWLIndividual(termInfosByCptInst.get(servName)[0]).getPropertyValue(pb.getOWLModel().getOWLDatatypeProperty(OTR.TERM_OFFSET));
				int servOffsetStart = new Integer(servOffsetString.substring(1,servOffsetString.replaceAll("]", ",").indexOf(",")));
				int servOffsetEnd = Integer.parseInt(termInfosByCptInst.get(servName)[1]);
				int distance = servOffsetStart-pbOffsetEnd;
				if (distance<0)
					distance = pbOffsetStart-servOffsetEnd;

				line.add(distance);
			}
			for (int i=0;i<genPbList.size()-servList.size();i++)
			{
				line.add(0);
			}
			pbServDist.add(line);
		}



		HashSet<Integer> serviceIndices = new HashSet<Integer>();
		for (int i=0;i<java.lang.Math.max(categorizedInds.get("genericPb").size(), categorizedInds.get("service").size());i++)
		{
			serviceIndices.add(i);
		}
		//		ArrayList<Integer> path = new ArrayList<Integer>();
		ArrayList<ArrayList<Integer>> soluce = new ArrayList<ArrayList<Integer>>();
		//		System.out.println("launching recursive method on: "+file);
		recurseFindPath(categorizedInds.get("genericPb").size(), serviceIndices, new ArrayList<Integer>(), soluce);

		HashSet<OWLIndividual> remainingGenPbs = new HashSet<OWLIndividual>();

		if (soluce.size()>0)
		{
			int shortestPathIndice = 0;
			int minOverallDistance=0;
			ArrayList<Integer> firstPath = soluce.get(0);
			for (int pbIndice=0;pbIndice<firstPath.size();pbIndice++)
				minOverallDistance+=pbServDist.get(pbIndice).get(firstPath.get(pbIndice));
			for (int pathInd=1;pathInd<soluce.size();pathInd++)
			{
				ArrayList<Integer> solucePath = soluce.get(pathInd);
				int distance=0;
				for (int pbIndice=0;pbIndice<solucePath.size();pbIndice++)
					distance+=pbServDist.get(pbIndice).get(solucePath.get(pbIndice));

				if (distance<minOverallDistance)
				{	
					shortestPathIndice = pathInd;
					minOverallDistance = distance;
				}
			}


			ArrayList<Integer> bestAssociation = soluce.get(shortestPathIndice); 
			for (int pb=0;pb<bestAssociation.size();pb++)
			{
				if (bestAssociation.get(pb)<servList.size())
				{
					OWLIndividual pbIndividual = genPbList.get(pb);
					OWLIndividual servIndividual = servList.get(bestAssociation.get(pb));
					pbIndividual.addPropertyValue(pbIndividual.getOWLModel().getOWLObjectProperty(OTR.affectsServiceID), servIndividual);

					//					addOWLPropertyValue(pbIndividual,onto.getOWLObjectProperty(affectsServiceID), servIndividual);


					linkedPbs.add(pbIndividual);
					linkedServs.add(servIndividual);
				}
				else
					remainingGenPbs.add(genPbList.get(pb));
			}
		}

		for (OWLIndividual genPbInd:remainingGenPbs)
		{
			String pbName = genPbInd.getBrowserText(); 
			if (pbName.contains("#"))
				pbName=pbName.substring(pbName.indexOf("#")+1);
			String pbOffsetString=(String)genPbInd.getOWLModel().getOWLIndividual(termInfosByCptInst.get(pbName)[0]).getPropertyValue(genPbInd.getOWLModel().getOWLDatatypeProperty(OTR.TERM_OFFSET));
			int pbOffsetStart = new Integer(pbOffsetString.substring(1,pbOffsetString.replaceAll("]", ",").indexOf(",")));
			int pbOffsetEnd = Integer.parseInt(termInfosByCptInst.get(pbName)[1]);
			String servOffsetString;
			int servOffsetStart;
			int servOffsetEnd;
			OWLIndividual closestServInd=null;
			int closestDistance=-1;
			int distance;

			for (OWLIndividual servInd:categorizedInds.get("service"))
			{

				servOffsetString=(String)genPbInd.getOWLModel().getOWLIndividual(termInfosByCptInst.get(servInd.getBrowserText())[0]).getPropertyValue(genPbInd.getOWLModel().getOWLDatatypeProperty(OTR.TERM_OFFSET));
				servOffsetStart = new Integer(servOffsetString.substring(1,servOffsetString.replaceAll("]", ",").indexOf(",")));
				servOffsetEnd = Integer.parseInt(termInfosByCptInst.get(servInd.getBrowserText())[1]);
				distance = servOffsetStart-pbOffsetEnd;
				if (distance<0)
					distance = pbOffsetStart-servOffsetEnd;

				if ((closestServInd==null)||distance<closestDistance)
				{
					closestServInd=servInd;
					closestDistance=distance;
				}

			}
			if (closestServInd!=null)
			{
				genPbInd.addPropertyValue(genPbInd.getOWLModel().getOWLObjectProperty(OTR.affectsServiceID), closestServInd);
				//				addOWLPropertyValue(genPbInd,onto.getOWLObjectProperty(affectsServiceID), closestServInd);

				linkedServs.add(closestServInd);
				linkedPbs.add(genPbInd);
			}
		}
	}


	/**
	 * Generic method used to pair all instances of a first type with the appropriate linkable instances. 
	 * If some instances from the first list are not associated in the end, there are linked with the closest element of the second list.
	 * @param firstList a first list of instances
	 * @param secondList a list of instances from a second category
	 * @param linkedFirstCategInds a set of first category instances already paired, to be updated
	 * @param linkedSecondCategInds a set of second category instances already paired, to be updated
	 * @param instInfos information related to concept instances and their associated term occurrences
	 * @return a set of pairs
	 */
	private static HashSet<OWLIndividual[]> handleGenericPairing(ArrayList<OWLIndividual> firstList, ArrayList<OWLIndividual> secondList, HashSet<OWLIndividual> linkedFirstCategInds, HashSet<OWLIndividual> linkedSecondCategInds,HashMap<String,String[]> instInfos)
	{
		HashSet<OWLIndividual[]> result = new HashSet<OWLIndividual[]>();
		ArrayList<ArrayList<Integer>> distanceMatrix = new ArrayList<ArrayList<Integer>>();
		for (OWLIndividual firstCategInst:firstList)
		{
			ArrayList<Integer> line = new ArrayList<Integer>();
			for (OWLIndividual secondCategInst:secondList)
			{
				String firstName = firstCategInst.getBrowserText(); 
				if (firstName.contains("#"))
					firstName=firstName.substring(firstName.indexOf("#")+1);

				String firstOffsetString=(String)firstCategInst.getOWLModel().getOWLIndividual(instInfos.get(firstName)[0]).getPropertyValue(firstCategInst.getOWLModel().getOWLDatatypeProperty(OTR.TERM_OFFSET));
				int firstOffsetStart = new Integer(firstOffsetString.substring(1,firstOffsetString.replaceAll("]", ",").indexOf(",")));
				int firstOffsetEnd = Integer.parseInt(instInfos.get(firstName)[1]);

				String secondName = secondCategInst.getBrowserText(); 
				if (secondName.contains("#"))
					secondName=secondName.substring(secondName.indexOf("#")+1);

				String secondOffsetString=(String)firstCategInst.getOWLModel().getOWLIndividual(instInfos.get(secondName)[0]).getPropertyValue(firstCategInst.getOWLModel().getOWLDatatypeProperty(OTR.TERM_OFFSET));
				int secondOffsetStart = new Integer(secondOffsetString.substring(1,secondOffsetString.replaceAll("]", ",").indexOf(",")));
				int secondOffsetEnd = Integer.parseInt(instInfos.get(secondName)[1]);
				int distance = secondOffsetStart-firstOffsetEnd;
				if (distance<0)
					distance = firstOffsetStart-secondOffsetEnd;

				line.add(distance);
			}
			for (int i=0;i<firstList.size()-secondList.size();i++)
			{
				line.add(0);
			}
			distanceMatrix.add(line);
		}



		HashSet<Integer> secondCategIndices = new HashSet<Integer>();
		for (int i=0;i<java.lang.Math.max(firstList.size(), secondList.size());i++)
		{
			secondCategIndices.add(i);
		}

		ArrayList<ArrayList<Integer>> soluce = new ArrayList<ArrayList<Integer>>();

		recurseFindPath(firstList.size(), secondCategIndices, new ArrayList<Integer>(), soluce);

		HashSet<OWLIndividual> unassociatedFirstCategInds = new HashSet<OWLIndividual>();

		if (soluce.size()>0)
		{
			int shortestPathIndice = 0;
			int minOverallDistance=0;
			ArrayList<Integer> firstPath = soluce.get(0);
			for (int pbIndice=0;pbIndice<firstPath.size();pbIndice++)
				minOverallDistance+=distanceMatrix.get(pbIndice).get(firstPath.get(pbIndice));
			for (int pathInd=1;pathInd<soluce.size();pathInd++)
			{
				ArrayList<Integer> solucePath = soluce.get(pathInd);
				int distance=0;
				for (int firstCategIndice=0;firstCategIndice<solucePath.size();firstCategIndice++)
					distance+=distanceMatrix.get(firstCategIndice).get(solucePath.get(firstCategIndice));

				if (distance<minOverallDistance)
				{	
					shortestPathIndice = pathInd;
					minOverallDistance = distance;
				}
			}


			ArrayList<Integer> bestAssociation = soluce.get(shortestPathIndice); 
			for (int pb=0;pb<bestAssociation.size();pb++)
			{
				if (bestAssociation.get(pb)<secondList.size())
				{
					OWLIndividual firstCategIndividual = firstList.get(pb);
					OWLIndividual secondCategIndividual = secondList.get(bestAssociation.get(pb));
					//					firstCategIndividual.addPropertyValue(relation, secondCategIndividual);
					OWLIndividual[] pair = new OWLIndividual[2];
					pair[0] = firstCategIndividual;
					pair[1] = secondCategIndividual;
					result.add(pair);

					linkedFirstCategInds.add(firstCategIndividual);
					linkedSecondCategInds.add(secondCategIndividual);
				}
				else
					unassociatedFirstCategInds.add(firstList.get(pb));
			}
		}

		for (OWLIndividual firstTypeInd:unassociatedFirstCategInds)
		{
			String firstName = firstTypeInd.getBrowserText(); 
			if (firstName.contains("#"))
				firstName=firstName.substring(firstName.indexOf("#")+1);
			String firstOffsetString=(String)firstTypeInd.getOWLModel().getOWLIndividual(instInfos.get(firstName)[0]).getPropertyValue(firstTypeInd.getOWLModel().getOWLDatatypeProperty(OTR.TERM_OFFSET));
			Integer [] firstIndOffsets = new Integer[2];

			firstIndOffsets[0] = new Integer(firstOffsetString.substring(1,firstOffsetString.replaceAll("]", ",").indexOf(",")));
			firstIndOffsets[1] = Integer.parseInt(instInfos.get(firstName)[1]);

			OWLIndividual closestSecondTypeInd= computeClosestCompatibleDestination(firstIndOffsets, secondList, instInfos);

			if (closestSecondTypeInd!=null)
			{
				//				firstTypeInd.addPropertyValue(relation, closestSecondTypeInd);
				OWLIndividual[] pair = new OWLIndividual[2];
				pair[0] = firstTypeInd;
				pair[1] = closestSecondTypeInd;
				result.add(pair);


				linkedSecondCategInds.add(closestSecondTypeInd);
				linkedFirstCategInds.add(firstTypeInd);
			}
		}
		return result;
	}



	/**
	 * Gets text from the symptom field onward (kept only for the HTML compatibility, for ACTIA)
	 * @param f a given HTML or XML document
	 * @return the associated text
	 */
	private static String getFromSymptom(File f)
	{
		String result="";

//		String symptFieldName = ObirProject.getCorpus().getSemanticHTMLTag(ObirProject.getCorpus().getSemanticFields().iterator().next());//semIR_HTMLFieldsByXMLTags.get((String)semIR_HTMLFieldsByXMLTags.keySet().iterator().next());
//		if (f.getAbsolutePath().endsWith("html"))
//		{
//			try {
//				BufferedReader buffRead = new BufferedReader(new FileReader(f));
//				String line = buffRead.readLine();
//				boolean symptomReached = false;
//				while(line!=null)
//				{
//					line = obir.ir.SearchFiles.transformIntoStringWithCorrectPositions(line);
//					while (line.endsWith(" "))
//						line=line.substring(0, line.length()-1);
//
//					if (!symptomReached)
//					{
//						if (line.contains("_"+symptFieldName+"_"))
//						{							
//							symptomReached=true;
//							result = line.substring(line.indexOf("_"+symptFieldName+"_")+("_"+symptFieldName+"_").length());
//						}
//					}
//					else if (symptomReached)
//					{
//						result += line;
//					}				
//					line = buffRead.readLine();
//				}
//			}
//			catch (IOException ioe){ioe.printStackTrace();}
//		}
//		else if (f.getAbsolutePath().endsWith("xml"))
//		{
			try 
			{
				result = obir.ir.SearchFiles.transformIntoStringWithCorrectPositions(obir.misc.XMLToHTML.parseXMLField(f,Corpus.getSemanticFields().iterator().next()));//(String)semIR_HTMLFieldsByXMLTags.keySet().iterator().next()));
			} 
			catch (ParserConfigurationException e) {e.printStackTrace();} 
			catch (SAXException e) {e.printStackTrace();} 
			catch (IOException e) {e.printStackTrace();}
//		}
		return result;
	}

	/**
	 * Checks whether a given string includes a link word
	 * @param s a string of words
	 * @param linkPunctList a set of link words or punctuation marks
	 * @return {@code true} iff some link word or punctuation is found in the string
	 */
	private static boolean stringContainsALinkWord(String s,HashSet<String> linkPunctList)
	{
		for (String linkWord:linkPunctList)
		{
			if (!linkWord.replaceAll("[a-zA-Z]", "").equals(linkWord))
				linkWord = " "+linkWord+" ";
			if (s.contains(linkWord))
				return(true);
		//		for (String punct:ObirProject.getPunctuationMarks())
		//			if (s.contains(punct))
		//				return(true);
		}
		return false;
	}

	private static HashMap<OWLIndividual,Integer[]> computeOffsets(Collection<OWLIndividual> conceptInds,HashMap<String,String[]> instInfos)
	{
		HashMap<OWLIndividual, Integer[]> result = new HashMap<OWLIndividual, Integer[]>();
		for (OWLIndividual cptInst:conceptInds)
		{
			Integer [] values = new Integer[2];
			String offsetString = (String)cptInst.getOWLModel().getOWLIndividual(instInfos.get(cptInst.getBrowserText())[0]).getPropertyValue(cptInst.getOWLModel().getOWLDatatypeProperty(OTR.TERM_OFFSET));
			values[0] = new Integer(offsetString.substring(1,offsetString.replaceAll("]", ",").indexOf(",")));
			values[1] = Integer.parseInt(instInfos.get(cptInst.getBrowserText())[1]); 
			result.put(cptInst, values);
		}
		return result;
	}


	private static boolean atLeastOneInWindow(HashMap<OWLIndividual, Integer[]> elementOffsets,int startPoint, int endPoint)
	{
		for (OWLIndividual inst:elementOffsets.keySet())
		{
			int eltStart = elementOffsets.get(inst)[0];
			int eltEnd = elementOffsets.get(inst)[1];
			if ((eltStart>=startPoint && eltStart<endPoint)||(eltEnd>=startPoint && eltEnd<=endPoint))
				return true;
		}
		return false;
	}


	private static HashSet<OWLIndividual[]> handlePivotPairing(ArrayList<OWLIndividual> mainList,ArrayList<OWLIndividual> pivots, ArrayList<OWLIndividual> secondList, HashMap<String,String[]> instInfos)
	{
		HashSet<OWLIndividual[]> result = new HashSet<OWLIndividual[]>();

		HashMap<OWLIndividual, Integer[]> pivotOffsets = computeOffsets(pivots, instInfos);
		HashMap<OWLIndividual, Integer[]> firstOffsets = computeOffsets(mainList, instInfos);
		HashMap<OWLIndividual, Integer[]> secondOffsets = computeOffsets(secondList, instInfos);

		for (OWLIndividual pivot:pivots)
		{

			int pivotOffsetEnd = pivotOffsets.get(pivot)[1];

			int secondOffsetStart;
			OWLIndividual closestSecondInd=null;
			int closestDistance=-1;
			int distance;

			for (OWLIndividual destInd:secondList)
			{
				secondOffsetStart = secondOffsets.get(destInd)[0];
				distance = secondOffsetStart-pivotOffsetEnd;

				if (distance>=0 && ((closestSecondInd==null)||distance<closestDistance) && !atLeastOneInWindow(firstOffsets, pivotOffsetEnd, secondOffsetStart))
				{
					closestSecondInd=destInd;
					closestDistance=distance;
				}
			}

			if (closestSecondInd != null)
			{
				//				OWLIndividual closestMainInd = computeClosestCompatibleDestination(pivotOffsets.get(pivot), mainList, instInfos);
				int pivotOffsetStart = pivotOffsets.get(pivot)[0];
				int mainOffsetStart;
				int mainOffsetEnd;
				OWLIndividual closestMainInd=null;
				closestDistance=-1;

				for (OWLIndividual mainInd:mainList)
				{
					mainOffsetStart = firstOffsets.get(mainInd)[0];
					mainOffsetEnd = firstOffsets.get(mainInd)[1];
					distance = mainOffsetStart-pivotOffsetEnd;
					if (distance<0)
						distance = pivotOffsetStart-mainOffsetEnd;

					if ((!mainInd.equals(closestSecondInd)) && ((closestMainInd==null)||distance<closestDistance))
					{
						closestMainInd=mainInd;
						closestDistance=distance;
					}
				}

				if (closestMainInd != null)
				{

					OWLIndividual[] triple = new OWLIndividual[3];
					triple[0] = closestMainInd;
					triple[1] = pivot;
					triple[2] = closestSecondInd;
					result.add(triple);

				}
			}
		}
		return result;
	}


	/**
	 * Heuristic function to create a symptom
	 * @param indByFile structure storing the different concept instances found in a file, as well as the term occurrence (and its ending position in the field) which designates it
	 * @param minCover the minimum cover under which the algorithm is not applied
	 * @return the list of symptoms created within each file
	 */
	public static void automaticGraphCreation(HashMap<String,HashMap<String,HashMap<String,String[]>>> indByFile, float minCover)
	{
		HashMap<String,HashSet<OWLIndividual>> result = new HashMap<String,HashSet<OWLIndividual>>();



		int processSize = indByFile.keySet().size();
		int cpt = 0;

		//		HashSet<DocumentAnnotation> indexedDocs = new HashSet<DocumentAnnotation>();

		for (String file:indByFile.keySet())
		{
			cpt++;
			//			System.out.print("Processing file "+file+"...");

			for (String field:indByFile.get(file).keySet())
			{

				//			float coverage = new Float(0);
				//			if (minCover==0)
				//			coverage = new Float(1);
				//			else
				//			{
				//			String cover = this.advices.get(file)[3];
				//			cover = cover.replaceAll(",", ".");
				//			coverage = Float.parseFloat(cover);
				//			}

				//			if(coverage>=minCover)
				//			{

				if (ObirProject.getDomainPartner().equals(ObirProject.ARTAL)) //for ARTAL
				{
					HashSet<OWLIndividual> affectedComponents=new HashSet<OWLIndividual>();
					HashSet<OWLIndividual> affectedDefaults=new HashSet<OWLIndividual>();
					HashMap<String,ArrayList<OWLIndividual>> conceptIndTypes = orderGenericallyConceptInstancesByCategory(indByFile.get(file).get(field).keySet(),ObirProject.getOWLModel());
					ArrayList<OWLIndividual> firstList = new ArrayList<OWLIndividual>();
					firstList.addAll(conceptIndTypes.get(OTR.ARTAL_DEFAULT));
					firstList.addAll(conceptIndTypes.get(OTR.ARTAL_FUNCTION));

					HashSet<OWLIndividual[]> pairs = handleGenericPairing(firstList, conceptIndTypes.get(OTR.ARTAL_COMPONENT), affectedDefaults, affectedComponents, indByFile.get(file).get(field));
					for (OWLIndividual[] pair:pairs)
					{
						OWLIndividual first = pair[0];
						OWLIndividual second = pair[1];
						OWLObjectProperty relation = null;
						if (first.getRDFType().equals(first.getOWLModel().getOWLNamedClass(OTR.ARTAL_DEFAULT))||first.getRDFType().getNamedSuperclasses(true).contains(first.getOWLModel().getOWLNamedClass(OTR.ARTAL_DEFAULT)))
							relation = ObirProject.getOWLModel().getOWLObjectProperty(OTR.ARTAL_AFFECTS_COMPONENT);
						else
							relation = ObirProject.getOWLModel().getOWLObjectProperty(OTR.ARTAL_CONCERNS_COMPONENT);
						first.setPropertyValue(relation, second);
					}

					HashSet<OWLIndividual[]> triples = handlePivotPairing(conceptIndTypes.get(OTR.ARTAL_DEFAULT), conceptIndTypes.get(OTR.ARTAL_TRIGGER_EVENT), firstList, indByFile.get(file).get(field));
					for (OWLIndividual[]triple:triples)
					{
						OWLIndividual main = triple[0];
						OWLIndividual pivot = triple[1];
						OWLIndividual sub = triple[2];
						OWLObjectProperty pivotToMain = pivot.getOWLModel().getOWLObjectProperty(OTR.ARTAL_CAUSES);
						OWLObjectProperty pivotToSub = null;
						if (sub.getRDFType().equals(sub.getOWLModel().getOWLNamedClass(OTR.ARTAL_DEFAULT))||sub.getRDFType().getNamedSuperclasses(true).contains(sub.getOWLModel().getOWLNamedClass(OTR.ARTAL_DEFAULT)))
							pivotToSub = ObirProject.getOWLModel().getOWLObjectProperty(OTR.ARTAL_CONCERNS_DEFAULT);
						else
							pivotToSub = ObirProject.getOWLModel().getOWLObjectProperty(OTR.ARTAL_CONCERNS_FUNCTION);

						pivot.setPropertyValue(pivotToMain, main);
						pivot.setPropertyValue(pivotToSub, sub);
					}
				}
			}
			//			System.out.println("\t done");

		}
		//		if (!project.isPrototypeGeneric())
		//			separateSymptomsIntoDisjointGraphs(indexedDocs,project);

//		return result;
	}

	public static void separateSymptomsIntoDisjointGraphs(DocumentAnnotation doc, String field)
	{
		HashSet<DocumentAnnotation> docs = new HashSet<DocumentAnnotation>();
		docs.add(doc);
		separateSymptomsIntoDisjointGraphs(docs, field);
	}

	/**
	 * Method specific to ACTIA. Used to have each annotation graph separated from a different symptom, even if they theoretically share a common node.  
	 * @param docs the different document annotations to check and modify if needed
	 */
	@SuppressWarnings("unchecked")
	public static void separateSymptomsIntoDisjointGraphs(Set<DocumentAnnotation> docs, String field)
	{
		for (DocumentAnnotation doc:docs)
		{

			HashMap<OWLIndividual,HashSet<OWLIndividual>> termOccsByCptInst = new HashMap<OWLIndividual, HashSet<OWLIndividual>>();
			//				for (OWLIndividual occ:ObirProject.getCorpus().getTermOccurrences(doc))
			for (OWLIndividual occ:doc.getTermOccurrences(field))
			{
				for (OWLIndividual cptInst:(Collection<OWLIndividual>)occ.getPropertyValues(occ.getOWLModel().getOWLObjectProperty(OTR.designatesID)))
				{
					HashSet<OWLIndividual> termOccs = new HashSet<OWLIndividual>();
					if (termOccsByCptInst.containsKey(cptInst))
						termOccs = termOccsByCptInst.get(cptInst);
					termOccs.add(occ);
					termOccsByCptInst.put(cptInst,termOccs);
				}
			}


			HashSet<OWLIndividual> symptoms = new HashSet<OWLIndividual>();
			HashMap<OWLIndividual,HashSet<OWLIndividual>> compByControlledServ = new HashMap<OWLIndividual,HashSet<OWLIndividual>>();
			HashMap<OWLIndividual,HashSet<OWLIndividual>> compByRealizedServ = new HashMap<OWLIndividual,HashSet<OWLIndividual>>();
			//				for (OWLIndividual cptInst:ObirProject.getCorpus().getConceptInstances(doc))
			for (OWLIndividual cptInst:doc.getConceptInstances(field))
			{
				if (((OWLNamedClass)cptInst.getRDFType()).getNamedSuperclasses(true).contains(cptInst.getOWLModel().getOWLNamedClass(OTR.SYMPTOME)))
					symptoms.add(cptInst);
				else if (((OWLNamedClass)cptInst.getRDFType()).getNamedSuperclasses(true).contains(cptInst.getOWLModel().getOWLNamedClass(OTR.componentID)))
				{
					HashSet<OWLIndividual> tempSet = new HashSet<OWLIndividual>();
					OWLIndividual linkedServ = (OWLIndividual)cptInst.getPropertyValue(cptInst.getOWLModel().getOWLObjectProperty(OTR.controlsID));
					if (((OWLNamedClass)cptInst.getRDFType()).getNamedSuperclasses(true).contains(cptInst.getOWLModel().getOWLNamedClass(OTR.controlComponentID)))
					{
						if (compByControlledServ.containsKey(linkedServ))
							tempSet = compByControlledServ.get(linkedServ);
						tempSet.add(cptInst);
						compByControlledServ.put(linkedServ,tempSet);
					}

					linkedServ = (OWLIndividual)cptInst.getPropertyValue(cptInst.getOWLModel().getOWLObjectProperty(OTR.participatesToID));
					tempSet = new HashSet<OWLIndividual>();
					if (compByRealizedServ.containsKey(linkedServ))
						tempSet = compByRealizedServ.get(linkedServ);
					tempSet.add(cptInst);
					compByRealizedServ.put(linkedServ,tempSet);
				}
			}

			HashSet<OWLIndividual> alreadyAffectedServices = new HashSet<OWLIndividual>();
			HashSet<OWLIndividual> alreadyAffectedContexts = new HashSet<OWLIndividual>();

			for (OWLIndividual sympt:symptoms)
			{
				OWLIndividual pb = (OWLIndividual)sympt.getPropertyValue(sympt.getOWLModel().getOWLObjectProperty(OTR.DEFINED_BY_PROBLEM));
				OWLIndividual serv = (OWLIndividual)sympt.getPropertyValue(sympt.getOWLModel().getOWLObjectProperty(OTR.DEFINED_BY_SERVICE));
				if (pb!=null&&serv!=null)
				{
					Collection<OWLIndividual> ctxts = (Collection<OWLIndividual>)pb.getPropertyValues(sympt.getOWLModel().getOWLObjectProperty(OTR.hasContextID));

					//verif que pb non reli� � plusieurs prest
					//TODO a verifier
					if(pb.hasPropertyValue(sympt.getOWLModel().getOWLObjectProperty(OTR.affectsServiceID))&&pb.getPropertyValues(sympt.getOWLModel().getOWLObjectProperty(OTR.affectsServiceID)).size()>1)
					{
						//creation d'un nouveau pb "clon�"
//						OWLIndividual newPb = ((OWLNamedClass)pb.getRDFType()).createOWLIndividual(ObirProject.generateNextIndName());
						OWLIndividual newPb = ObirProject.getOTR().createConceptInstance(doc, field,(OWLNamedClass)pb.getRDFType());
						
						
						//							ObirProject.getCorpus().addConceptInstance(newPb,doc);
//						doc.addConceptInstance(newPb,field);
						if (termOccsByCptInst.containsKey(pb))
							for (OWLIndividual termOcc:termOccsByCptInst.get(pb))
								termOcc.addPropertyValue(sympt.getOWLModel().getOWLObjectProperty(OTR.designatesID), newPb);

						if (!sympt.hasPropertyValue(sympt.getOWLModel().getOWLObjectProperty(OTR.DEFINED_BY_PROBLEM), newPb))
							sympt.setPropertyValue(sympt.getOWLModel().getOWLObjectProperty(OTR.DEFINED_BY_PROBLEM), newPb);
						if (!newPb.hasPropertyValue(sympt.getOWLModel().getOWLObjectProperty(OTR.affectsServiceID), serv))
							newPb.setPropertyValue(sympt.getOWLModel().getOWLObjectProperty(OTR.affectsServiceID), serv);
						pb.removePropertyValue(sympt.getOWLModel().getOWLObjectProperty(OTR.affectsServiceID), serv);

						HashSet<OWLIndividual> newCtxts = new HashSet<OWLIndividual>();
						for (OWLIndividual ctxt:ctxts)
						{
//							OWLIndividual newCtxt = ((OWLNamedClass)ctxt.getRDFType()).createOWLIndividual(ObirProject.generateNextIndName()); 
							OWLIndividual newCtxt = ObirProject.getOTR().createConceptInstance(doc, field,(OWLNamedClass)ctxt.getRDFType());
							
							if (termOccsByCptInst.containsKey(ctxt))
								for (OWLIndividual termOcc:termOccsByCptInst.get(ctxt))
									termOcc.addPropertyValue(sympt.getOWLModel().getOWLObjectProperty(OTR.designatesID), newCtxt);

							newCtxts.add(newCtxt);
							//							doc.addConceptInstance(newCtxt);
							//								ObirProject.getCorpus().addConceptInstance(newCtxt,doc);
//							doc.addConceptInstance(newCtxt,field);
						}

						if (!newCtxts.isEmpty())
							newPb.setPropertyValues(sympt.getOWLModel().getOWLObjectProperty(OTR.hasContextID), newCtxts);


						pb = newPb;
					}

					if (alreadyAffectedServices.contains(serv))
					{
//						OWLIndividual newServ = ((OWLNamedClass)serv.getRDFType()).createOWLIndividual(ObirProject.generateNextIndName());
						OWLIndividual newServ = ObirProject.getOTR().createConceptInstance(doc, field,(OWLNamedClass)serv.getRDFType());
						//						doc.addConceptInstance(newServ);
						//							ObirProject.getCorpus().addConceptInstance(newServ,doc);
//						doc.addConceptInstance(newServ,field);

						if (termOccsByCptInst.containsKey(serv))
							for (OWLIndividual termOcc:termOccsByCptInst.get(serv))
								termOcc.addPropertyValue(sympt.getOWLModel().getOWLObjectProperty(OTR.designatesID), newServ);

						if (compByControlledServ.containsKey(serv))
							for (OWLIndividual ctlComp:compByControlledServ.get(serv))
							{
//								OWLIndividual newCtlComp = ((OWLNamedClass)ctlComp.getRDFType()).createOWLIndividual(ObirProject.generateNextIndName());
								OWLIndividual newCtlComp = ObirProject.getOTR().createConceptInstance(doc, field,(OWLNamedClass)ctlComp.getRDFType());
								//								doc.addConceptInstance(newCtlComp);
								//									ObirProject.getCorpus().addConceptInstance(newCtlComp,doc);
//								doc.addConceptInstance(newCtlComp,field);

								newCtlComp.setPropertyValue(sympt.getOWLModel().getOWLObjectProperty(OTR.controlsID),newServ);
								if (termOccsByCptInst.containsKey(ctlComp))
									for (OWLIndividual termOcc:termOccsByCptInst.get(ctlComp))
										termOcc.addPropertyValue(sympt.getOWLModel().getOWLObjectProperty(OTR.designatesID), newCtlComp);
							}
						if (compByRealizedServ.containsKey(serv))
							for (OWLIndividual comp:compByRealizedServ.get(serv))
							{
//								OWLIndividual newComp = ((OWLNamedClass)comp.getRDFType()).createOWLIndividual(ObirProject.generateNextIndName()); 
								OWLIndividual newComp = ObirProject.getOTR().createConceptInstance(doc, field,(OWLNamedClass)comp.getRDFType());
								//								doc.addConceptInstance(newComp);
								//									ObirProject.getCorpus().addConceptInstance(newComp,doc);
//								doc.addConceptInstance(newComp,field);

								if (termOccsByCptInst.containsKey(comp))
									for (OWLIndividual termOcc:termOccsByCptInst.get(comp))
										termOcc.addPropertyValue(sympt.getOWLModel().getOWLObjectProperty(OTR.designatesID), newComp);

								newComp.setPropertyValue(sympt.getOWLModel().getOWLObjectProperty(OTR.participatesToID),newServ);
							}
						sympt.setPropertyValue(sympt.getOWLModel().getOWLObjectProperty(OTR.DEFINED_BY_SERVICE), newServ);
						pb.setPropertyValue(sympt.getOWLModel().getOWLObjectProperty(OTR.affectsServiceID), newServ);
					}
					else
						alreadyAffectedServices.add(serv);

					for (OWLIndividual ctxt:ctxts)
					{
						if (alreadyAffectedContexts.contains(ctxt))
						{
//							OWLIndividual newCtxt = ((OWLNamedClass)ctxt.getRDFType()).createOWLIndividual(ObirProject.generateNextIndName());
							OWLIndividual newCtxt = ObirProject.getOTR().createConceptInstance(doc, field,(OWLNamedClass)ctxt.getRDFType());
							//							doc.addConceptInstance(newCtxt);
							//								ObirProject.getCorpus().addConceptInstance(newCtxt,doc);
							doc.addConceptInstance(newCtxt,field);

							if (termOccsByCptInst.containsKey(ctxt))
								for (OWLIndividual termOcc:termOccsByCptInst.get(ctxt))
									termOcc.addPropertyValue(sympt.getOWLModel().getOWLObjectProperty(OTR.designatesID), newCtxt);

							pb.removePropertyValue(sympt.getOWLModel().getOWLObjectProperty(OTR.hasContextID), ctxt);
							pb.addPropertyValue(sympt.getOWLModel().getOWLObjectProperty(OTR.hasContextID), newCtxt);
						}
						else
							alreadyAffectedContexts.add(ctxt);
					}
				}
			}
		}
	}

}
