package obir.ir;

import java.util.HashMap;
import java.util.HashSet;

import edu.stanford.smi.protegex.owl.model.OWLIndividual;
import edu.stanford.smi.protegex.owl.model.OWLObjectProperty;
/**
 * 
 * @author Axel Reymonet
 */
public class AnnotationGraph
{
    private HashSet<OWLIndividual> nodes;
    private HashSet<OWLObjectProperty> relations;
    private HashMap<OWLIndividual, HashMap<OWLObjectProperty, HashSet<OWLIndividual>>> graphTriples;

    public AnnotationGraph()
    {
        nodes = new HashSet<OWLIndividual>();
        relations = new HashSet<OWLObjectProperty>();
        graphTriples = new HashMap<OWLIndividual, HashMap<OWLObjectProperty, HashSet<OWLIndividual>>>();
    }

    public void addNode(OWLIndividual node)
    {
        this.nodes.add(node);
    }

    public void addNodes(HashSet<OWLIndividual> nodesToAdd)
    {
        this.nodes.addAll(nodesToAdd);
    }

    public void addTriple(OWLIndividual domainInst, OWLObjectProperty relation, HashSet<OWLIndividual> codomainInst)
    {
        this.nodes.add(domainInst);
        this.nodes.addAll(codomainInst);
        this.relations.add(relation);

        if (!graphTriples.containsKey(domainInst))
        {
            graphTriples.put(domainInst, new HashMap<OWLObjectProperty, HashSet<OWLIndividual>>());
        }

        if (!graphTriples.get(domainInst).containsKey(relation))
        {
            HashMap<OWLObjectProperty, HashSet<OWLIndividual>> relationsMap = graphTriples.get(domainInst);
            relationsMap.put(relation, new HashSet<OWLIndividual>());
            graphTriples.put(domainInst, relationsMap);
        }

        HashMap<OWLObjectProperty, HashSet<OWLIndividual>> relationsMap = graphTriples.get(domainInst);
        HashSet<OWLIndividual> codomainInstMap = relationsMap.get(relation);
        codomainInstMap.addAll(codomainInst);
        relationsMap.put(relation, codomainInstMap);
        graphTriples.put(domainInst, relationsMap);
    }

    public HashMap<OWLIndividual, HashMap<OWLObjectProperty, HashSet<OWLIndividual>>> getTriplesByRelation(OWLObjectProperty aRelation)
    {
        HashMap<OWLIndividual, HashMap<OWLObjectProperty, HashSet<OWLIndividual>>> triplesForRelation = new HashMap<OWLIndividual, HashMap<OWLObjectProperty, HashSet<OWLIndividual>>>();
        for (OWLIndividual domainInst : graphTriples.keySet())
        {
            for (OWLObjectProperty relation : graphTriples.get(domainInst).keySet())
            {
                if (relation.getLocalName().equals(aRelation.getLocalName()))
                {
                    HashMap<OWLObjectProperty, HashSet<OWLIndividual>> tmpMap = graphTriples.get(domainInst);
                    tmpMap.put(relation, graphTriples.get(domainInst).get(relation));
                    triplesForRelation.put(domainInst, tmpMap);
                }
            }
        }
        return triplesForRelation;
    }

    @SuppressWarnings("unchecked")
	public void addTriplesForNodes(HashMap<OWLIndividual, HashMap<OWLObjectProperty, HashSet<OWLIndividual>>> potentialTriples, HashSet<OWLIndividual> nodesToAdd)
    {
        for (OWLIndividual domInst : potentialTriples.keySet())
        {
            for (OWLObjectProperty relation : potentialTriples.get(domInst).keySet())
            {
                HashSet<OWLIndividual> tmp = (HashSet<OWLIndividual>) potentialTriples.get(domInst).get(relation).clone();
                tmp.retainAll(nodesToAdd);
                if (nodesToAdd.contains(domInst) || !tmp.isEmpty())
                {
                    addTriple(domInst, relation, potentialTriples.get(domInst).get(relation));
                }
            }
        }
    }

    public HashSet<OWLIndividual> getNodes()
    {
        return nodes;
    }

    public HashSet<OWLObjectProperty> getRelations()
    {
        return relations;
    }

    public HashMap<OWLIndividual, HashMap<OWLObjectProperty, HashSet<OWLIndividual>>> getGraphTriples()
    {
        return graphTriples;
    }
}
