package com.sn1pe2win.managers.triumphs;

import com.sn1pe2win.sql.simpledb.MappedVar;
import com.sn1pe2win.sql.simpledb.RS_Row;

/**Beziehungstabelle. Wenn ein Triumph mehrere Untertriumphe haben soll.<br>
 * Wird hauptsächlich für Siegel gebraucht, bei dem das Siegel der Triumph ist und<br>
 * Andere untertriumphe daran gebunden sind.*/
public class RS_TriumphObjective extends RS_Row {
	
	public MappedVar<String> parentId = new MappedVar<String>("parentID");
	public MappedVar<String> objectiveId = new MappedVar<String>("objectiveID");
	
}
