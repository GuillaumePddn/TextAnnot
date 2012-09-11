package obir.ir.analysis.fr;

/**
 * Copyright 2004-2005 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;
import java.util.HashSet;

import org.apache.lucene.analysis.Token;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;

/**
 * A filter that stems french words. It supports a table of words that should
 * not be stemmed at all. The used stemmer can be changed at runtime after the
 * filter object is created (as long as it is a FrenchStemmer).
 *
 * @author    Patrick Talbot (based on Gerhard Schwarz work for German)
 */
public final class CustomFrenchStemFilter extends TokenFilter {

	/**
	 * The actual token in the input stream.
	 */
	private Token token = null;
	//private CustomFrenchStemmer stemmer = null;
	private PaiceHuskFrenchStemmer stemmer = null;
	private HashSet<String> exclusions = null;

	public CustomFrenchStemFilter( TokenStream in ) {
    super(in);
		//stemmer = new CustomFrenchStemmer();
    	stemmer= new PaiceHuskFrenchStemmer();
	}


	public CustomFrenchStemFilter( TokenStream in, HashSet<String> exclusiontable ) {
		this( in );
		exclusions = exclusiontable;
	}

	/**
	 * @return  Returns the next token in the stream, or null at EOS
	 */
	public final Token next()
		throws IOException {
		if ( ( token = input.next() ) == null ) {
			return null;
		}
		// Check the exclusiontable
		else if ( exclusions != null && exclusions.contains( token.term() ) ) {
			return token;
		}
		else {
			String s = stemmer.stem( token.term() );
			// If not stemmed, dont waste the time creating a new token
			if ( !s.equals( token.term() ) ) {
				Token newToken = new Token( s, token.startOffset(), token.endOffset(), token.type());
				newToken.setPositionIncrement(token.getPositionIncrement());
			   return newToken;
			}
			return token;
		}
	}
	/**
	 * Set a alternative/custom FrenchStemmer for this filter.
	 */
	public void setStemmer( /*CustomFrenchStemmer*/ PaiceHuskFrenchStemmer stemmer ) {
		if ( stemmer != null ) {
			this.stemmer = stemmer;
		}
	}
//	/**
//	 * Set an alternative exclusion list for this filter.
//	 */
//	public void setExclusionTable( Hashtable exclusiontable ) {
//		exclusions = new HashSet(exclusiontable.keySet());
//	}
}


