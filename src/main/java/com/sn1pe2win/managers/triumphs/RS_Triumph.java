package com.sn1pe2win.managers.triumphs;

import com.sn1pe2win.sql.simpledb.MappedVar;
import com.sn1pe2win.sql.simpledb.RS_Row;

/**Diese Datenbanktabelle enthält die grundlegenden Triumph Informationen<br>
 * Punkte etc. sind in sog. "Steps" definiert.*/
public class RS_Triumph extends RS_Row {
	
	public MappedVar<String> progressId = new MappedVar<String>("progressID");
	public MappedVar<Integer> color = new MappedVar<Integer>("color"); //Color in hex
	public MappedVar<String> category = new MappedVar<String>("category");
	public MappedVar<String> description = new MappedVar<String>("description");
	public MappedVar<String> iconURL = new MappedVar<String>("iconURL");
	public MappedVar<String> origin = new MappedVar<>("origin");
	public MappedVar<String> name = new MappedVar<String>("name");
	public MappedVar<Boolean> deactivated = new MappedVar<Boolean>("deactivated");
	
}
