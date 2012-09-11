/*
 * Created on Jul 14, 2004
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package obir.otr;

import java.util.ArrayList;

/**
 * The base class to represent an entity (i.e. a term occurrence)
 * @author Axel Reymonet
 */
public class Entity {

	/**
	 * The URL of the document in which the entity appears
	 */
	public String docURL;
	
	/**
	 * The name of the entity
	 */
	public String ind_name;
	
	/**
	 * The set of words corresponding to the term which have been retrieved in the document
	 */
	public ArrayList<String> text;
	
	/**
	 * The offsets for each found words
	 */
	public ArrayList<Integer> offset;		
	
	/**
	 * The type of the entity (ie the found term)
	 */
	public String type;
	
	/**
	 * The document field in which the entity was found (important for offsets)
	 */
	public String field;

	
	public Entity(){
		text = new ArrayList<String>();
		offset = new ArrayList<Integer>();
	}
	public Entity(Entity entity){
		this.docURL = entity.docURL;
		this.ind_name = entity.ind_name; 
		this.text	= entity.text;
		this.offset	= entity.offset;
		this.type	= entity.type;
		this.field = entity.field;
	}
	
	@Override
	public String toString()
	{
		String result = "Entity ["+ind_name+",type="+type+",doc="+docURL+",field="+field+",text="+text.toString()+",offset="+offset.toString()+"]";
		return result;
	}
}
