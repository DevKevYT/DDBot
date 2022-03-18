package com.sn1pe2win.managers.triumphs;

import com.sn1pe2win.sql.simpledb.MappedVar;
import com.sn1pe2win.sql.simpledb.RS_Row;

public class RS_UserTriumph extends RS_Row {

	public MappedVar<Long> discordID = new MappedVar<Long>("userID");
	public MappedVar<String> progressID = new MappedVar<String>("progressID");
	public MappedVar<Long> progress = new MappedVar<Long>("progress");
	public MappedVar<Boolean> completed = new MappedVar<Boolean>("completed");
	
}
