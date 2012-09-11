package obir.ir;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import edu.stanford.smi.protegex.owl.model.OWLIndividual;
import edu.stanford.smi.protegex.owl.model.OWLObjectProperty;

/**
 * This class allows to build the annotation graphs using the triples found in a DocumentAnnotation
 * @author Axel Reymonet
 */
public class GraphCreator
{
    private static HashMap<String, Float> coefficientsByConceptSuperType;

    /**
     * Returns the annotation graphs associated to the given triples
     * @param triples the triples present in an annotation
     * @return the annotation graphs
     */
    @SuppressWarnings("unchecked")
	public static ArrayList<AnnotationGraph> createGraph(HashMap<OWLIndividual, HashMap<OWLObjectProperty, HashSet<OWLIndividual>>> triples, HashMap<String, Float> coefficients)
    {
        ArrayList<AnnotationGraph> graphs = new ArrayList<AnnotationGraph>();
        Set<OWLIndividual> domainConcepts = new HashSet<OWLIndividual>(triples.keySet());
        Set<OWLIndividual> codomainConcepts = new HashSet<OWLIndividual>();

        coefficientsByConceptSuperType = coefficients;

        // extraire l'ensemble des concepts codomaine d'une relation
        for (OWLIndividual cpt : triples.keySet())
        {
            for (OWLObjectProperty relation : triples.get(cpt).keySet())
            {
                codomainConcepts.addAll(triples.get(cpt).get(relation));
            }
        }

        // obtenir les extremites des graphes
        domainConcepts.removeAll(codomainConcepts);

        // pour chaque extremites de graphe
        for (OWLIndividual domCpt : domainConcepts)
        {
            // r�cuperer ses descendants
            HashSet<OWLIndividual> descendants = getNodeDescendants(domCpt, triples);

            boolean graphMember = false;
            // verifier s'il appartient (ou ses fils) � un graphe existant
            for (AnnotationGraph aGraph : graphs)
            {
                HashSet<OWLIndividual> tmp = (HashSet<OWLIndividual>) aGraph.getNodes().clone();
                tmp.retainAll(descendants);
                if (!tmp.isEmpty())
                {
                    // l'ajouter avec ses descendants au graphe courant
                    aGraph.addNodes(descendants);
                    aGraph.addTriplesForNodes(triples, descendants);
                    graphMember = true;
                    break;
                }
            }

            // sinon l'ajouter avec ses descendants � un nouveau graphe
            if (!graphMember && !descendants.isEmpty())
            {
                // creer un nouveau graphe
                AnnotationGraph newGraph = new AnnotationGraph();
                newGraph.addNodes(descendants);
                newGraph.addTriplesForNodes(triples, descendants);
                graphs.add(newGraph);
            }
        }
        return graphs;
    }

    /**
     * Returns the descendants of a given node (concept)
     * @param node the parent node
     * @param triples the triples which can contain some descendants
     * @return the list of the node's descendants
     */
    private static HashSet<OWLIndividual> getNodeDescendants(OWLIndividual node, HashMap<OWLIndividual, HashMap<OWLObjectProperty, HashSet<OWLIndividual>>> triples)
    {
        HashSet<OWLIndividual> descendants = new HashSet<OWLIndividual>();
        boolean onlyIgnoredRelations = true;

        if (triples.containsKey(node))
        {
            for (OWLObjectProperty relation : triples.get(node).keySet())
            {
                // ignorer les concepts dont les coefficients n'ont pas �t� d�finis
                if (coefficientsByConceptSuperType.containsKey(relation.getDomain(false).getLocalName()) && !coefficientsByConceptSuperType.get(relation.getDomain(false).getLocalName()).equals(0f)
                        && coefficientsByConceptSuperType.containsKey(relation.getRange(false).getLocalName())
                        && !coefficientsByConceptSuperType.get(relation.getRange(false).getLocalName()).equals(0f))
                {
                    onlyIgnoredRelations = false;
                    for (OWLIndividual aDescendant : triples.get(node).get(relation))
                    {
                        descendants.addAll(getNodeDescendants(aDescendant, triples));
                    }
                }
            }
        }

        if (!onlyIgnoredRelations)
        {
            descendants.add(node);
        }
        return descendants;
    }
}
