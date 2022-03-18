package com.sn1pe2win.sql.simpledb;

import java.sql.ResultSet;
import java.util.ArrayList;

public class RS_Set {
	
	public ResultSet set;
	public ArrayList<? super RS_Row> rows = new ArrayList<>();
	
	public RS_Set(ResultSet set, RS_Row[] rows) {
		for(RS_Row r : rows) this.rows.add(r);
		this.set = set;
	}
}
