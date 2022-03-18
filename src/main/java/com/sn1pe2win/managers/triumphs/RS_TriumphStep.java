package com.sn1pe2win.managers.triumphs;

import com.sn1pe2win.sql.simpledb.MappedVar;
import com.sn1pe2win.sql.simpledb.RS_Row;

public class RS_TriumphStep extends RS_Row {
	
	public MappedVar<String> progressId = new MappedVar<String>("progressID");
	public MappedVar<Boolean> forceRole = new MappedVar<Boolean>("force_role");
	public MappedVar<Long> requirement = new MappedVar<Long>("requirement");
	public MappedVar<Boolean> display = new MappedVar<Boolean>("display");
	public MappedVar<Integer> points = new MappedVar<Integer>("points");
	public MappedVar<String> displayName = new MappedVar<String>("displayName");
	public MappedVar<String> origin = new MappedVar<String>("origin");
	public MappedVar<String> displayType = new MappedVar<String>("display_type");
	
}
