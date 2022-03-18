package com.sn1pe2win.sql.simpledb;

import java.sql.Date;


public class MappedVar<T> {
	
	final boolean immutable;
	private boolean modified = false;
	
	public final String dbName;
	T data;
	boolean ISNULL = true;
	
	public MappedVar(String databaseVariableName) {
		dbName = databaseVariableName;
		this.immutable = false;
	}
	
	public MappedVar(String databaseVariableName, boolean immutable) {
		dbName = databaseVariableName;
		this.immutable = immutable;
	}
	
	public void set(T data) {
		if(immutable) return;
		
		ISNULL = false;
		modified = true;
		this.data = data;
		
		//if(this.data != null) modified = true;
		//if(this.data == null || !immutable) this.data = data;
//		else if(immutable) Logger.warn("Tried to modify immutable variable");
	}
	
	public boolean gotModified() {
		if(immutable) return false;
		
		boolean temp = modified;
		modified = false;
		return temp;
	}
	
	/**A mapped var is empty when:<br>
	 * <ul>
	 * <li>{@link MappedVar#get()} == null</li>
	 * <li>{@link MappedVar#isUnset()} == true</li>
	 * <li>{@link MappedVar#get()}.toString() == "" (Empty string)</li>
	 * </ul>>*/
	public boolean isEmpty() {
		if(get() == null) return isUnset();
		else return get().toString().equals("") || isUnset();
	}
	
	/**@return true, if the variable (column) is not present in the table*/
	public boolean isUnset() {
		return ISNULL;
	}

	/**@return The data of the variable.*/
	public T get() {
		return data;
	}
	
	public String getAsString() {
		if(data instanceof Date) return String.valueOf(((Date) data).getTime());
		else if(data != null) return data.toString();
		else return "null";
	}
}
