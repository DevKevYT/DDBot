package com.sn1pe2win.sql.simpledb;

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;

import com.sn1pe2win.logging.SLogger;
import com.sn1pe2win.sql.simpledb.Exceptions.UnindexedRowError;

public abstract class RS_Row {

	public static final String RS_ID_NAME = ".rsid";
	
	protected ArrayList<MappedVar<?>> mapped = new ArrayList<>();
	protected ArrayList<MappedVar<?>> fetched = new ArrayList<>();
	
	private long RS_ID;
	//private int RS_ID;
	
	private RowMetadata metadata;
	private ResultSet set;
	
	/**Database the row is from*/
	private SimpleDatabase con;
	/**Function the row was pulled from. May be null*/
	private RS_Function<? extends RS_Row> function = null;
	
	public boolean ignoreMissing = false;
	private boolean init = false;
	
	public RS_Row() {
	}
	
	void init(RS_Function<? extends RS_Row> function, SimpleDatabase con, ResultSet set) {
		this.con = con;
		this.function = function;
		
		try {
			metadata = new RowMetadata(set, set.getRow());
		} catch (SQLException e) {
			//Logger.err("Failed to fetch metadata.");
		}
		
		this.set = set;
		map(set);
		init = true;
	}
	
	@SuppressWarnings("unchecked")
	protected void map(ResultSet row) {
		mapped.clear();
		
		try {
			if(row.isClosed()) {
				SLogger.WARNING.log("Unable to map variables: ResultSet closed");
				return;
			}
		} catch (SQLException e1) {
			e1.printStackTrace();
		}
		
		for(Field f : this.getClass().getDeclaredFields()) {
			if(f.getType().getTypeName().equals(MappedVar.class.getTypeName())) {		
				String n = f.getGenericType().getTypeName();
				String typeName = n.substring(n.indexOf("MappedVar<")+10, f.getGenericType().getTypeName().length()-1);
				try { 
					if(typeName.equals(String.class.getTypeName())) {
						MappedVar<String> mv = ((MappedVar<String>) f.get(this));
						if(mv.dbName.equals("rowid")) continue;
						mv.data = row.getString(mv.dbName);
						mv.ISNULL = false;
						mapped.add(mv);
					} else if(typeName.equals(Boolean.class.getTypeName())) {
						MappedVar<Boolean> mv = ((MappedVar<Boolean>) f.get(this));
						if(mv.dbName.equals("rowid")) continue;
						mv.data = row.getBoolean(mv.dbName);
						mv.ISNULL = false;
						mapped.add(mv);
					} else if(typeName.equals(Integer.class.getTypeName())) {
						MappedVar<Integer> mv = ((MappedVar<Integer>) f.get(this));
						if(mv.dbName.equals("rowid")) continue;
						mv.data = row.getInt(mv.dbName);
						mv.ISNULL = false;
						mapped.add(mv);
					} else if(typeName.equals(Long.class.getTypeName())) {
						MappedVar<Long> mv = ((MappedVar<Long>) f.get(this));
						if(mv.dbName.equals("rowid")) continue;
						mv.data = row.getLong(mv.dbName);
						mv.ISNULL = false;
						mapped.add(mv);
					} else if(typeName.equals(Float.class.getTypeName())) {
						MappedVar<Float> mv = ((MappedVar<Float>) f.get(this));
						if(mv.dbName.equals("rowid")) continue;
						mv.data = row.getFloat(mv.dbName);
						mv.ISNULL = false;
						mapped.add(mv);
					} else if(typeName.equals(Date.class.getTypeName())) {
						MappedVar<Date> mv = ((MappedVar<Date>) f.get(this));
						if(mv.dbName.equals("rowid")) continue;
						mv.data = row.getDate(mv.dbName);
						mv.ISNULL = false;
						mapped.add(mv);
					} else throw new IllegalAccessError("Illegal generic type: " + typeName);
				} catch (Exception e) {
					if(!e.toString().contains("no such column") && !ignoreMissing) 
						SLogger.ERROR.log("Failed to map variable: " + e.toString());
				}
			}
		}
	}
	
	/**Overwrite this function to parse custom values*/
	public void parse(ResultSet set) {
		
	};
	
	public ResultSet getResultSet() {
		return set;
	}
	
	public long getRowId() {
		return RS_ID;
	}
	
	public RowMetadata getMetadata() {
		return metadata;
	}
	
	public boolean isIndexed() {
		return metadata != null && con != null && RS_ID != -1;
	}
	
	/**Adds the row to the database where it matches the table
	 * @throws UnindexedRowError */
	public void addRow(RS_Row row) throws UnindexedRowError {
		if(!isIndexed()) throw new UnindexedRowError("Row not indexed i.e. pulled from the database. Unable to add new row to unknown table.");
		con.addEntry(getMetadata().tableName, row);
	}
	
	public void deleteRow() throws UnindexedRowError {
		if(!isIndexed()) throw new UnindexedRowError("Row not indexed i.e. pulled from the database.");
		con.deleteEntry(getMetadata().tableName, "WHERE rowid == " + getRowId());
	}
	
	public RS_Function<? extends RS_Row> getFunction() {
		return function;
	}
	
	public void updateRow(String conditionStatement) {
		if(!init) throw new IllegalAccessError("This row instance is not initialized. Call RS_Row.manualInit() to manually initialize this row.");
		
		String fullStatement = "UPDATE " + metadata.tableName + " SET ";
		int modified = 0;
		for(MappedVar<?> v : mapped) {
			if(v.gotModified()) {
				modified ++;
				if(v.get() instanceof String) fullStatement += v.dbName + " = \"" + v.getAsString() + "\", ";
				else fullStatement += v.dbName + " = " + v.getAsString() + ", ";
			}
		}
		if(modified >= 1) fullStatement = fullStatement.substring(0, fullStatement.length() - 2);
		if(modified == 0) return; //Es gibt nichts zu updaten
		
		fullStatement += " " + (conditionStatement.toLowerCase().trim().startsWith("where") ? conditionStatement : " WHERE " + conditionStatement);
		try {
			Statement statement = con.getConnection().createStatement();
			statement.executeUpdate(fullStatement);
			//Logger.log("Successfully updated " + rows + " row(s)");
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
}
