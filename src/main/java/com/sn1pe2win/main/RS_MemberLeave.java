package com.sn1pe2win.main;

import com.sn1pe2win.sql.simpledb.MappedVar;
import com.sn1pe2win.sql.simpledb.RS_Row;

public class RS_MemberLeave extends RS_Row {

	public MappedVar<Long> userID = new MappedVar<Long>("userID");
	public MappedVar<Long> serverID = new MappedVar<Long>("serverID");
	public MappedVar<Long> timeUntilDeletion = new MappedVar<Long>("time_until_deletion");
	public MappedVar<Long> timestamp = new MappedVar<Long>("timestamp");
}
