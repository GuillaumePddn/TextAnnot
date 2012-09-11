package obir.ir.analysis;

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

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.HashSet;
import java.util.Hashtable;

import obir.ir.analysis.fr.CustomFrenchStemFilter;
import obir.ir.analysis.it.ItalianTokenFilter;

import org.apache.lucene.analysis.ASCIIFoldingFilter;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.PorterStemFilter;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.WordlistLoader;
import org.apache.lucene.analysis.fr.FrenchAnalyzer;
import org.apache.lucene.analysis.fr.FrenchStemFilter;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.standard.StandardFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.util.Version;


/**
 * Analyzer for French language. Supports an external list of stopwords (words that
 * will not be indexed at all) and an external list of exclusions (word that will
 * not be stemmed, but indexed).
 * A default set of stopwords is used unless an alternative list is specified, the
 * exclusion list is empty by default.
 *
 * This file has been modified by Axel Reymonet to take into account the document language as a parameter, so as to call the appropriate stemmer. 
 *
 * @author Patrick Talbot (based on Gerhard Schwarz's work for German)
 * @version $Id: FrenchAnalyzer.java,v 1.2 2006/10/03 15:43:13 reymonet Exp $
 */
public class CustomAnalyzer extends Analyzer {

	/**
	 * Contains the stopwords used with the StopFilter.
	 */
	private HashSet<String> stoptable = new HashSet<String>();
	/**
	 * Contains words that should be indexed but not stemmed.
	 */
	private HashSet<String> excltable = new HashSet<String>();

	public final static String FRENCH_LANGUAGE = "fr";
	public final static String ENGLISH_LANGUAGE = "en";
	public final static String ITALIAN_LANGUAGE = "it";
	public final static String LATIN_LANGUAGE = "la";
	
	private String language;


	/**
	 * Builds an analyzer with the given stop words.
	 * @throws IOException
	 */
	@SuppressWarnings("unchecked")
	public CustomAnalyzer(String lang,Reader stopwords) throws IOException {
		language = lang;
		stoptable = new HashSet(WordlistLoader.getWordSet(stopwords));
	}
	
	
	/**
	 * Builds an exclusionlist from an array of Strings.
	 */
	@SuppressWarnings("unchecked")
	public void setStemExclusionTable(String[] exclusionlist) {
		excltable = (HashSet<String>)StopFilter.makeStopSet(exclusionlist);
	}

	/**
	 * Builds an exclusionlist from a Hashtable.
	 */
	@SuppressWarnings("unchecked")
	public void setStemExclusionTable(Hashtable exclusionlist) {
		excltable = new HashSet<String>(exclusionlist.keySet());
	}

	/**
	 * Builds an exclusionlist from the words contained in the given file.
	 * @throws IOException
	 */
	@SuppressWarnings("unchecked")
	public void setStemExclusionTable(File exclusionlist) throws IOException {
		excltable = new HashSet<String>(WordlistLoader.getWordSet(exclusionlist));
	}


	/*
	 * Removes any french accent from a string
	 * @param s any String
	 * @return same String without accent 
	 
	public static String removeAccents(String s)
	{
		s = s.replace('à','a');
		s = s.replace('â','a');
		s = s.replace('ä','a');
		s = s.replace('é','e');
		s = s.replace('è','e');
		s = s.replace('ê','e');
		s = s.replace('ë','e');
		s = s.replace('î','i');
		s = s.replace('ï','i');
		s = s.replace('ô','o');
		s = s.replace('ö','o');
		s = s.replace('û','u');
		s = s.replace('ç','c');
		s = s.replace('ñ','n');
		s = s.replace('\'',' ');
		s = s.replace('`',' ');
		s = s.replace('"',' ');
		return s;
	}
	
	public static String removeApex(String s)
	{
		s = s.replace('\'',' ');
		s = s.replace('`',' ');
		s = s.replace('"',' ');
		return s;
	}
	*/
	/**
	 * Method that returns the tokenStream for the given field and reader
	 * @param fieldName the field name
	 * @param reader a Reader that points to the field content
	 * @return the tokenized content
	 */
	public final TokenStream tokenStream(String fieldName, Reader reader) {

		if (fieldName == null) throw new IllegalArgumentException("fieldName must not be null");
		if (reader == null) throw new IllegalArgumentException("reader must not be null");

		//sépare les phrases en mots
		TokenStream result = new StandardTokenizer(Version.LUCENE_29,reader);
		 if (language.equals(ENGLISH_LANGUAGE) || language.equals(LATIN_LANGUAGE))
			 result = new StandardFilter(result);
		 else //considère les apostrophes comme des séparateurs
			 result = new CustomTokenFilter(result);
	
		result = new LowerCaseFilter(result);//passe tout en minuscule
		//result = new ASCIIFoldingFilter(result);//enléve les accents
		result = new StopFilter(true,result,stoptable);//enléve les stopwords

		//lemmatise les mots selon leur langue d'origine
		if (language.equals(FRENCH_LANGUAGE))
			result = new CustomFrenchStemFilter(result, excltable);
		else if (language.equals(ENGLISH_LANGUAGE))
			result = new PorterStemFilter(result);
		else if (language.equals(ITALIAN_LANGUAGE))
			result = new ItalianTokenFilter(result);

		return result;

	}
	/**
	 * Language getter for this analyzer
	 * @return
	 */
	public String getLanguage() {
		return language;
	}
}

