package obir.ir;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.Vector;

import obir.otr.ObirProject;
import edu.stanford.smi.protegex.owl.model.OWLIndividual;
import edu.stanford.smi.protegex.owl.model.OWLProperty;

/**
 * This class allows to store the semantic similarity's results, for each tuple (document,annotation, relation). The results are indexed by document
 * This class is also used to save the concepts associated to the tuple
 * 
 * @author Dudognon
*/
public class ResultMap extends HashMap<String, HashMap<OWLIndividual, HashMap<OWLProperty, Float>>>
{
    public static final String PLUGIN_PROPERTY_COEFFICIENTS = "plugin.coefficients";

    private static final long serialVersionUID = -2704230922906334234L;

    /**
     * Found concepts for each couple (annotation, relation) of the query
     */
    private HashMap<OWLIndividual, HashMap<OWLProperty, HashSet<OWLIndividual>>> conceptsByRelationAndAnnotation;
    private HashMap<String, Float> coefficentsByTypesOfConcept;
    private ObirProject obirProject;

    public ResultMap(ObirProject project)
    {
        this.conceptsByRelationAndAnnotation = new HashMap<OWLIndividual, HashMap<OWLProperty, HashSet<OWLIndividual>>>();
        this.coefficentsByTypesOfConcept = new HashMap<String, Float>();
        
        this.obirProject = project;
        String property = this.obirProject.getPluginProperties().getProperty(PLUGIN_PROPERTY_COEFFICIENTS);

        String[] coefficients = property.split(",");
        for (String coeff : coefficients)
        {
            String[] couple = coeff.split("=");
            coefficentsByTypesOfConcept.put(couple[0], new Float(couple[1]));
        }
    }

    /**
     * Add a new query annotation in the result map
     * @param annotation the query annotation to add
     */
    public void addAnnotation(OWLIndividual annotation)
    {
        if (!this.conceptsByRelationAndAnnotation.containsKey(annotation))
        {
            this.conceptsByRelationAndAnnotation.put(annotation, new HashMap<OWLProperty, HashSet<OWLIndividual>>());
        }
    }

    /**
     * Add a new relation in the result map
     * @param annotation the annotation linked to the relation
     * @param relation the relation to add
     */
    public void addRelation(OWLIndividual annotation, OWLProperty relation)
    {
        if (this.conceptsByRelationAndAnnotation.containsKey(annotation))
        {
            if (!this.conceptsByRelationAndAnnotation.get(annotation).containsKey(relation))
            {
                this.conceptsByRelationAndAnnotation.get(annotation).put(relation, new HashSet<OWLIndividual>());
            }
        }
        else
        {
            this.conceptsByRelationAndAnnotation.put(annotation, new HashMap<OWLProperty, HashSet<OWLIndividual>>());
            this.conceptsByRelationAndAnnotation.get(annotation).put(relation, new HashSet<OWLIndividual>());
        }
    }

    /**
     * Add a new ProxyGenea Similarity result for a tuple (document, annotation, relation)
     * @param document the concerned document
     * @param annotation the annotation linked to the relation
     * @param relation the relation
     * @param proxyGenea the similarity result
     */
    public void addProxyGeneaResult(String document, OWLIndividual annotation, OWLProperty relation, Float proxyGenea)
    {
        this.addRelation(annotation, relation);

        if (!this.containsKey(document))
        {
            HashMap<OWLIndividual, HashMap<OWLProperty, Float>> tmpMap = new HashMap<OWLIndividual, HashMap<OWLProperty, Float>>();
            tmpMap.put(annotation, new HashMap<OWLProperty, Float>());
            tmpMap.get(annotation).put(relation, proxyGenea);
            this.put(document, tmpMap);
        }
        else
        {
            if (this.get(document).containsKey(annotation))
            {
                if (this.get(document).get(annotation).containsKey(relation))
                {
                    if (this.get(document).get(annotation).get(relation).compareTo(proxyGenea) < 1)
                    {
                        this.get(document).get(annotation).put(relation, proxyGenea);
                    }
                }
                else
                {
                    this.get(document).get(annotation).put(relation, proxyGenea);
                }
            }
            else
            {
                this.put(document, new HashMap<OWLIndividual, HashMap<OWLProperty, Float>>());
                this.get(document).put(annotation, new HashMap<OWLProperty, Float>());
                this.get(document).get(annotation).put(relation, proxyGenea);
            }
        }
    }

    /**
     * Computes the similarity with the query, for each document and return all the pertinent documents (similarity > threshold)
     * @param threshold the minimum value allowed for the similarity
     * @return the documents and their global similarity
     */
    public Vector<Vector<String>> getGlobalSimilarityByPertinentDocuments(Float threshold)
    {
        Vector<Vector<String>> result = new Vector<Vector<String>>();
        NumberFormat nf = NumberFormat.getInstance(Locale.FRENCH);
        nf.setMaximumFractionDigits(2);

        // pour chaque document
        for (String document : this.keySet())
        {
            // similarit� globale du document
            Float maxSim = 0f;
            // pour chaque annotation extraite de la requ�te
            for (OWLIndividual annotation : this.get(document).keySet())
            {
                // similarit� globale de l'annotation
                Float annotationSim = 0f;
                Float coeffSum = 0f;
                // pour chaque relation extraite de la requ�te
                for (OWLProperty relation : this.get(document).get(annotation).keySet())
                {
                    Float coeff = 1f;
                    if (coefficentsByTypesOfConcept.containsKey(relation.getRange().getLocalName()))
                    {
                        coeff = coefficentsByTypesOfConcept.get(relation.getRange().getLocalName());
                    }
                    annotationSim += this.get(document).get(annotation).get(relation) * coeff;
                    coeffSum += coeff;
                }

                annotationSim = annotationSim / coeffSum;
                // annotationSim = annotationSim / new Float(this.get(document).get(annotation).keySet().size());

                // conserver la similarit� max obtenue pour l'ensemble des annotations du document
                if (maxSim.compareTo(annotationSim) < 0)
                {
                    maxSim = annotationSim;
                }
            }

            // Ne considerer que les documents perninents --> Similarit�>=Seuil
            if (threshold <= maxSim)
            {
                Vector<String> tempVec = new Vector<String>();
                tempVec.add(document);
                tempVec.add(String.valueOf(nf.format(maxSim)));
                result.add(tempVec);
            }
        }

        if (result.size() == 0)
        {
            Vector<String> tempVec = new Vector<String>();
            tempVec.add("No match");
            tempVec.add("0");
            result.add(tempVec);
        }
        return result;
    }

    /**
     * Computes the similarity with the query, for all documents
     * @return the documents and their global similarity
     */
    public Vector<Vector<String>> getGlobalSimilarityForAllDocuments()
    {
        return this.getGlobalSimilarityByPertinentDocuments(0f);
    }

    /**
     * Remove all results and objets in the result map
     */
    @Override
    public void clear()
    {
        super.clear();
        this.conceptsByRelationAndAnnotation.clear();
    }

    /**
     * Add some concepts for the couple (annotation, relation)
     * @param annotation the annotation linked to the relation
     * @param relation the relation
     * @param conceptList the list of concepts for the couple (annotation, relation)
     */
    public void addConcepts(OWLIndividual annotation, OWLProperty relation, HashSet<OWLIndividual> conceptList)
    {
        if (this.conceptsByRelationAndAnnotation.containsKey(annotation))
        {
            if (this.conceptsByRelationAndAnnotation.get(annotation).containsKey(relation))
            {
                this.conceptsByRelationAndAnnotation.get(annotation).get(relation).addAll(conceptList);
            }
            else
            {
                this.conceptsByRelationAndAnnotation.get(annotation).put(relation, conceptList);
            }
        }
        else
        {
            this.conceptsByRelationAndAnnotation.put(annotation, new HashMap<OWLProperty, HashSet<OWLIndividual>>());
            this.conceptsByRelationAndAnnotation.get(annotation).put(relation, conceptList);
        }
    }

    /**
     * Add a single concept for the couple (annotation, relation)
     * @param annotation the annotation linked to the relation
     * @param relation the relation
     * @param concept the concept to add
     */
    public void addConcept(OWLIndividual annotation, OWLProperty relation, OWLIndividual concept)
    {
        HashSet<OWLIndividual> conceptsList = new HashSet<OWLIndividual>();
        conceptsList.add(concept);
        this.addConcepts(annotation, relation, conceptsList);
    }

    /**
     * Returns all the annotations stored
     * @return the annotations
     */
    public Set<OWLIndividual> getAnnotations()
    {
        return this.conceptsByRelationAndAnnotation.keySet();
    }

    /**
     * Returns all the relations for a given annotation
     * @param annotation an annotation
     * @return the relations linked to this annotation
     */
    public Set<OWLProperty> getRelations(OWLIndividual annotation)
    {
        return this.conceptsByRelationAndAnnotation.get(annotation).keySet();
    }

    /**
     * Returns all the concepts for the couple (annotation, relation)
     * @param annotation an annotation
     * @param relation a relation
     * @return the concepts associated to the couple (annotation, relation)
     */
    public HashSet<OWLIndividual> getConcepts(OWLIndividual annotation, OWLProperty relation)
    {
        return this.conceptsByRelationAndAnnotation.get(annotation).get(relation);
    }
}
