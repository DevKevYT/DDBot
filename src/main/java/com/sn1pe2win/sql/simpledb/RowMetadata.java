package com.sn1pe2win.sql.simpledb;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;

public class RowMetadata {

	public final int row;
	public final String tableName;
	private final ResultSetMetaData data;
	
	public RowMetadata(ResultSet set, int row) throws SQLException {
		this.row = row;
		this.data = set.getMetaData();
		
		tableName = data.getTableName(row);
		//set.getMetaData().isReadOnly(row);
	}
	
	public ResultSetMetaData getData() {
		return data;
	}
	
	public int getColumnCount() {
		try {
			return data.getColumnCount();
		} catch (SQLException e) {
			return -1;
		}
	}
	
	public String[] getColumnNames() {
		ArrayList<String> names = new ArrayList<>();
			try {
				for(int i = 0; i < getColumnCount();i++) {
					names.add(data.getColumnLabel(i));
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		return names.toArray(new String[names.size()]);
	}
}
