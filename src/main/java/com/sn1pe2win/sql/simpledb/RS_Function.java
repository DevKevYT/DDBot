package com.sn1pe2win.sql.simpledb;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.sn1pe2win.logging.SLogger;

public class RS_Function<T extends RS_Row> {

	final ArrayList<T> cache = new ArrayList<>();
	private final Class<? extends RS_Row> convention;
	private final PreparedStatement statement;
	private final SimpleDatabase db;
	
	private boolean onlyCache = false;
	
	RS_Function(SimpleDatabase db, Class<? extends RS_Row> convention, PreparedStatement statement) {
		this.db = db;
		this.statement = statement;
		this.convention = convention;
	}
	
	/**If set only cached rows are returned*/
	public void lockToCache() {
		if(cache.isEmpty()) SLogger.WARNING.log("Cache for function " + this + " is empty.");
		onlyCache = true;
	}
	
	public boolean isOnlyCache() {
		return onlyCache;
	}
	
	public List<T> getCached() {
		return cache;
	}
	
	/**Tries to map the fetched values to the given object instead of creating new objects.
	 * Only useful if the expected rowcount = 1*/
	public void set(T obj) {
		ResultSet rs = null;
		try {
			statement.execute();
			rs = statement.getResultSet();
		} catch (SQLException e1) {
			SLogger.ERROR.log("Failed to execute function: " + e1.getMessage());
			e1.printStackTrace();
		}
		obj.init(this, db, rs);
		cache.add(obj);
	}
	
	/**Creates new instances of the given class and parses all values into the given objects*/
	public List<T> get(boolean ignoreMissing) {
		if(isOnlyCache()) return cache;
		
		cache.clear();
		
		ResultSet rs = null;
		try {
			statement.execute();
			rs = statement.getResultSet();
		} catch (SQLException e1) {
			SLogger.ERROR.log("Failed to execute function: " + e1.getMessage());
			e1.printStackTrace();
		}
		
		if(rs == null) return new ArrayList<>(0);
		
		try {
			while(rs.next()) {
				try {
					@SuppressWarnings("unchecked")
					T object = (T) convention.getDeclaredConstructor().newInstance();
					object.ignoreMissing = ignoreMissing;
					object.init(this, db, rs);
					object.map(rs);
					object.parse(rs);
					cache.add(object);
				} catch (Exception e) {
					SLogger.ERROR.log("Error while parsing result set: " + e.getMessage());
					e.printStackTrace();
				}
			}
		} catch(SQLException e) {
			SLogger.ERROR.log("Database access error occurred while reading the result set.");
			e.printStackTrace();
		}
		return cache;
	}
	
	/***/
	public void updateAffectedRows() {
		for(T t : cache) {
			t.updateRow("WHERE rowid = " + t.getRowId());
		}
	}
}
