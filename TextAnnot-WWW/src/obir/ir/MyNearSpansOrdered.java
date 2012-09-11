package obir.ir;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;

import obir.ir.SearchFiles.MyTermSpans;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.spans.SpanNearQuery;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.Spans;

/**
 * Class that extends the default Lucene Spans used for the term projection
 * @author Axel Reymonet
 *
 */
public class MyNearSpansOrdered extends Spans {
	protected final int allowedSlop;
	protected boolean more = false;

	/** The spans in the same order as the SpanNearQuery */
	protected final Spans[] subSpans;

	/** Indicates that all subSpans have same doc() */
	protected boolean inSameDoc = false;
	protected boolean firstTime = true;
	protected int matchDoc = -1;
	protected int matchStart = -1;
	protected int matchEnd = -1;

	protected IndexReader indexReader;

	private SpanNearQuery query;


	public MyNearSpansOrdered(SpanNearQuery spanNearQuery, IndexReader reader) throws IOException {
		if (spanNearQuery.getClauses().length < 2) 
		{
			throw new IllegalArgumentException("Less than 2 clauses: "
					+ spanNearQuery);
		}

		allowedSlop = spanNearQuery.getSlop();
		SpanQuery[] clauses = spanNearQuery.getClauses();
		subSpans = new Spans[clauses.length];
		for (int i = 0; i < clauses.length; i++) 
		{
			subSpans[i] = clauses[i].getSpans(reader);
		}
		query = spanNearQuery; // kept for toString() only.
		indexReader = reader;
	}

	// inherit javadocs
	public int doc() { return matchDoc; }

	// inherit javadocs
	public int start() { return matchStart; }

	// inherit javadocs
	public int end() { return matchEnd; }

	public Spans[] getSubSpans() {
		return subSpans;
	}  

	// inherit javadocs
	public boolean next() throws IOException {
		return advanceAfterOrdered();
	}

	/**
	 * WARNING: method unchanged from NearSpansOrdered. Use at your own risk!
	 */
	public boolean skipTo(int target) throws IOException {
		if (firstTime) {
			firstTime = false;
			for (int i = 0; i < subSpans.length; i++) {
				if (! subSpans[i].skipTo(target)) {
					more = false;
					return false;
				}
			}
			more = true;
		} else if (more && (subSpans[0].doc() < target)) {
			if (subSpans[0].skipTo(target)) {
				inSameDoc = false;
			} else {
				more = false;
				return false;
			}
		}
		return advanceAfterOrdered();
	}



	protected boolean advanceAfterOrdered() throws IOException 
	{
		int docMaxPosition=-1;
		boolean inDifferentDocs = false;
		for (int i=0;i<subSpans.length;i++)
		{
			if ( ! subSpans[i].next() )
				return false;
	
			if (docMaxPosition==-1)
				docMaxPosition = i;
			else if (subSpans[i].doc()!=subSpans[docMaxPosition].doc())
			{
				inDifferentDocs = true;
				if (subSpans[i].doc()>subSpans[docMaxPosition].doc())
					docMaxPosition = i;
			}
		}

		if (inDifferentDocs && !toSameDoc(docMaxPosition))
			return false;
		
		if (!computeShortestMultiWordTerm())
			return false;
		else
		{
			matchDoc = subSpans[0].doc();
			matchStart = subSpans[0].start();
			matchEnd = subSpans[subSpans.length-1].end();
			return true;
		}

	}

	private int computeSlop()
	{
		int cpt = 0;
		for (int i=1;i<subSpans.length;i++)
		{
			int diff = subSpans[i].start()-subSpans[i-1].end();
			if (diff<0) //situation o� les mots ne sont pas dans le bon ordre
				return -1;
			else
				cpt += diff;
		}
		return cpt;
	}


	private boolean computeShortestMultiWordTerm() throws IOException
	{
		int slop = allowedSlop+1;
		while (slop > allowedSlop)
		{
			if( ! ( computeWordPositions(subSpans.length-1) || toSameDoc()))
				return false;
			
			slop = computeSlop();
			if (slop<=allowedSlop && slop!=-1)
				return true;
			else
			{
				MyTermSpans lastWord = (MyTermSpans)subSpans[subSpans.length-1]; 
				if ( ! lastWord.next())
					return false;
				slop = allowedSlop+1;
			}
		}
		return false;
	}

	private boolean computeWordPositions(int currentWord) throws IOException
	{
		int previousWord = currentWord - 1;
		MyTermSpans previous = (MyTermSpans) subSpans[previousWord];
		boolean hasNexted = false ;
		while (previous.doc()==subSpans[currentWord].doc() && previous.start()<subSpans[currentWord].start())
		{
			hasNexted = true ;
			if ( ! previous.next() )
				break;
		}
		if ( hasNexted )
			previous.notifyNoAdvance();

		if (previousWord > 0)
			computeWordPositions(previousWord);
		else
		{
			int commonDoc = subSpans[0].doc();
			for (int i=1;i<subSpans.length;i++)
			{
				if (subSpans[i].doc()!=commonDoc)
					return false;
			}
			return true;	
		}
		return false;
	}

	private boolean toSameDoc()throws IOException 
	{
		int maxPosition = -1;
		for (int i=0;i<subSpans.length;i++)
		{
			if (maxPosition==-1 || subSpans[i].doc()>subSpans[maxPosition].doc())
				maxPosition = i;
		}
		return toSameDoc(maxPosition);
	}
	
	private boolean toSameDoc(int docMaxPosition) throws IOException 
	{
		int docMax = subSpans[docMaxPosition].doc();
		for (int i=0;i<subSpans.length;i++)
		{
			if (i!=docMaxPosition)
			{
				MyTermSpans wordSpan = (MyTermSpans)subSpans[i];
				while (wordSpan.doc()<docMax)
				{
					if (!wordSpan.next())
						return false;
				}
				if (wordSpan.doc()>docMax)
				{
					if (toSameDoc(i))
					{
						inSameDoc = true;
						return true;
					}
					else
					{
						inSameDoc = false;
						return false;
					}
				}
			}
		}
		inSameDoc = true;
		return true;
	}

	public String toString() {
		return getClass().getName() + "("+query.toString()+")@"+
		(firstTime?"START":(more?(doc()+":"+start()+"-"+end()):"END"));
	}


	// TODO: Remove warning after API has been finalized
	// TODO: Would be nice to be able to lazy load payloads
	@SuppressWarnings("unchecked")
	public Collection/*<byte[]>*/ getPayload() throws IOException {
		return new LinkedList();
	}

	// TODO: Remove warning after API has been finalized
	public boolean isPayloadAvailable() {
		return false;
	}

}

