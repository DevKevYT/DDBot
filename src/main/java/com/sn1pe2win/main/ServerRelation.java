package com.sn1pe2win.main;

import com.sn1pe2win.sql.simpledb.MappedVar;
import com.sn1pe2win.sql.simpledb.RS_Row;

/**Beziehungstabelle*/

public class ServerRelation extends RS_Row {
	
	public final MappedVar<String> serverID = new MappedVar<String>("serverID", true);
	public final MappedVar<Long> userID = new MappedVar<Long>("userID", true);
	public final MappedVar<Integer> admin = new MappedVar<Integer>("admin", true);
	
}
