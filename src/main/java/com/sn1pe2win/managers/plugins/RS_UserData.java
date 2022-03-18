package com.sn1pe2win.managers.plugins;

import com.sn1pe2win.sql.simpledb.MappedVar;
import com.sn1pe2win.sql.simpledb.RS_Row;

public class RS_UserData extends RS_Row {
	
	public MappedVar<Long> userID = new MappedVar<Long>("userID");
	public MappedVar<Long> serverID = new MappedVar<Long>("serverID");
	public MappedVar<String> origin = new MappedVar<String>("origin");
	public MappedVar<String> data = new MappedVar<String>("data");
}
