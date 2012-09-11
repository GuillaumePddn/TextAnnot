package obir.ir.analysis.it;

import java.io.IOException;

import org.apache.lucene.analysis.Token;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.tartarus.snowball.ext.ItalianStemmer;

public class ItalianTokenFilter extends TokenFilter {

	private Token token = null;
	ItalianStemmer stemmer;
	
	public ItalianTokenFilter(TokenStream in ) 
	{
		super(in);
		stemmer = new ItalianStemmer();
	}
	
	@Override
	public Token next() throws IOException {
		if ( ( token = input.next() ) == null ) {
			return null;
		}
		else
		{
			stemmer.setCurrent(token.term());
			stemmer.stem();
			String s = stemmer.getCurrent();
//		String s = stemmer.stem( token.termText() );
		// If not stemmed, dont waste the time creating a new token
		if ( !s.equals( token.term() ) ) {
			Token newToken = new Token( s, token.startOffset(), token.endOffset(), token.type());
			newToken.setPositionIncrement(token.getPositionIncrement());
		   return newToken;
		}
		return token;
		}
	}

}
