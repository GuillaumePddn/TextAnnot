package obir.ir.analysis;

import java.io.IOException;
import java.util.ArrayList;

import org.apache.lucene.analysis.Token;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;

/**
 * Class built to consider the "'" and "-" signs as a separator (which was not the case in {@code TokenFilter})
 * @author Axel Reymonet
 */
public class CustomTokenFilter extends TokenFilter {

	private ArrayList<Token> intermediateTokens;
	public CustomTokenFilter(TokenStream ts)
	{
		super(ts);
		this.intermediateTokens = new ArrayList<Token>();
	}
	
	public Token next() throws IOException
	{

		if (!this.intermediateTokens.isEmpty())
		{
			return (this.intermediateTokens.remove(0));
		}
		else
		{
		Token nextToken = this.input.next();
		if (nextToken!=null)
		{
			String tempTerm=nextToken.term();
			String endingTerms = tempTerm;
			int incr=0;
			while (endingTerms.contains("'")||endingTerms.contains("-"))
			{
				int charIndex;
				int index1 = endingTerms.indexOf("'");
				int index2 = endingTerms.indexOf("-");
				
				if (index1==-1)
					charIndex = index2;
				else if (index2==-1)
					charIndex = index1;
				else if (index1<index2)
					charIndex = index1;
				else
					charIndex = index2;
				
				tempTerm = endingTerms.substring(0, charIndex);
//				System.out.println("ending: "+endingTerms+"\nhead:"+tempTerm);
				if (tempTerm != "")
					this.intermediateTokens.add(new Token(tempTerm,nextToken.startOffset()+incr,nextToken.startOffset()+incr+tempTerm.length()));
				incr += tempTerm.length()+1;
				endingTerms = endingTerms.substring(charIndex+1,endingTerms.length());
			}
			if (endingTerms!="")
			this.intermediateTokens.add(new Token(endingTerms,nextToken.startOffset()+incr,nextToken.startOffset()+incr+endingTerms.length()));
			return (this.intermediateTokens.remove(0));
		}
		return null;
		}
	}
}
