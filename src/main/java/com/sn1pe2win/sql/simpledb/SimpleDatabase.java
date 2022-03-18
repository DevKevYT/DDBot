package com.sn1pe2win.sql.simpledb;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

import com.sn1pe2win.logging.SLogger;
import com.sn1pe2win.sql.simpledb.Exceptions.NoSuchTableException;

public class SimpleDatabase {

	private Connection connection;
	private File file;
	
	public SimpleDatabase(File file) throws Exception {
		this.file = file;
		if(!file.exists()) throw new FileNotFoundException("Local Database file not found: " + file.getAbsolutePath());
		SLogger.APPROVE.log("Database connection established ...");
		
		Class.forName("org.sqlite.JDBC");
		
		connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
	}
	
	public File getFile() {
		return file;
	}
	
	public boolean tableExists(String tableName) {
		try {
			return query("SELECT name FROM sqlite_schema WHERE type ='table' AND name = \"" + tableName + "\"").rows.size() == 1;
		} catch (SQLException | NoSuchTableException e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public RS_Set deleteTable(String tableName) throws SQLException, NoSuchTableException {
		return query("DROP TABLE " + tableName + ";");
	}
	
	public RS_Set addTable(String tableName, String ... columns) throws Exception {
		StringBuilder statement = new StringBuilder("CREATE TABLE " + tableName + " (");
		for(String s : columns) {
			statement.append(s + ", ");
		}
		statement.deleteCharAt(statement.length()-2);
		statement.append(");");
		return query(statement.toString());
	}
	
	/**Similar to a prepared statement. Only to pull data from a database
	 * @param <T>*/
	public <T extends RS_Row> RS_Function<T> createGetFunction(Class<? extends RS_Row> type, String[] select, String[] from, String condition) {
		StringBuilder sql = new StringBuilder("SELECT ");
		for(String s : select) {
			if(!s.equals("rowid")) sql.append(s.trim() + ", ");
		}
		
		if(select.length != 0) sql.append("rowid FROM ");
		
		for(int i = 0; i < from.length; i++) sql.append(from[i].trim() + (i < from.length-1 ? ", " : ""));
		
		if(condition != null) {
			if(!condition.isEmpty()) {
				if(!condition.startsWith("WHERE")) sql.append(" WHERE");
				sql.append(" " + condition);
			}
		}
		try {
			PreparedStatement statement = connection.prepareStatement(sql.toString());
			RS_Function<T> function = new RS_Function<>(this, type, statement);
			return function;
		} catch (SQLException e) {
			SLogger.ERROR.log("Failed to create function statement: " + e.getLocalizedMessage());
			return null;
		}
	}
	
	/**Similar to a prepared statement. Only to pull data from a database
	 * @param <T>*/
	public <T extends RS_Row> RS_Function<T> createGetFunction(Class<? extends RS_Row> type, String query) {
		try {
			PreparedStatement statement = connection.prepareStatement(query);
			RS_Function<T> function = new RS_Function<>(this, type, statement);
			return function;
		} catch (SQLException e) {
			SLogger.ERROR.log("Failed to create function statement: " + e.getLocalizedMessage());
			return null;
		}
	}
	
	public void deleteEntry(String tableName, String condition) {
		String statement = "DELETE FROM " + tableName;
		if(!condition.startsWith("WHERE")) statement += " WHERE";
		else statement += " ";
		statement += condition;
		
		try {
			Statement stmt = connection.createStatement();
			stmt.execute(statement);
		} catch (SQLException e) {
			SLogger.ERROR.log("Failed to delete row(s) from table " + tableName + ": " + e.getLocalizedMessage());
			e.printStackTrace();
		}
	}
	
	public RS_Row addEntry(String tableName, RS_Row data) {
		StringBuilder statement = new StringBuilder("INSERT INTO " + tableName + " ");
		StringBuilder columns = new StringBuilder("(");
		StringBuilder values = new StringBuilder("(");
		
		int count = 0;
		for(Field f : data.getClass().getDeclaredFields()) {
			if(f.getType().getTypeName().equals(MappedVar.class.getTypeName())) {		
				try {
					MappedVar<?> field = ((MappedVar<?>) f.get(data));
					if(field.get() != null) {
						columns.append(field.dbName + ", ");
						values.append((field.get().getClass().getTypeName().equals("java.lang.String") ? "\"" + field.get() + "\"" : field.get()) + ", ");
						count ++;
					}
				} catch (IllegalArgumentException | IllegalAccessException e) {
					SLogger.ERROR.log("Failed to read java field " + f.getName() + " from class " + f.getClass());
					e.printStackTrace();
				}
			}
		}
		if(count > 0) {
			columns.setLength(columns.length()-2);
			values.setLength(values.length()-2);
		}
		columns.append(")");
		values.append(")");
		statement.append(columns + " VALUES " + values);
		
		try {
			Statement stmt = connection.createStatement();
			stmt.execute(statement.toString());
		} catch (SQLException e) {
			SLogger.ERROR.log("Failed to add row into table " + tableName + ": " + e.getLocalizedMessage());
			e.printStackTrace();
		}
		return data;
	}
	
	public int queryUpdate(String sql) throws SQLException, NoSuchTableException {
		Statement statement = connection.createStatement();
		try {
			return statement.executeUpdate(sql);
		} catch(Exception e) {
			if(e.getLocalizedMessage().contains("no such table")) 
				throw new NoSuchTableException(e.getLocalizedMessage());
			throw e;
		}
	}
	
	public ResultSet plainQuery(String sql) throws SQLException, NoSuchTableException {
		Statement statement = connection.createStatement();
		try {
			statement.execute(sql);
		} catch(Exception e) {
			if(e.getLocalizedMessage().contains("no such table")) 
				throw new NoSuchTableException(e.getLocalizedMessage());
			throw e;
		}
		
		return statement.getResultSet();
	}
	
	public RS_Set query(String sql) throws SQLException, NoSuchTableException {
		Statement statement = connection.createStatement();
		try {
			statement.execute(sql);
		} catch(Exception e) {
			if(e.getLocalizedMessage().contains("no such table")) 
				throw new NoSuchTableException(e.getLocalizedMessage());
			throw e;
		}
		
		ResultSet rs = statement.getResultSet();
		if(rs == null) return new RS_Set(rs, new RS_Row[] {});
		
		ArrayList<RS_Row> rows = new ArrayList<>();
		
		try {
			while(rs.next()) {
				try {
					RS_Row r = new RS_Row() {
						public void parse(ResultSet set) {}
					};
					r.map(rs);
					r.init(null, this, rs);
					rows.add(r);
				} catch(Exception e) {
					SLogger.ERROR.log("Error while parsing result set: " + e.getMessage());
					e.printStackTrace();
				}
			}
		} catch(SQLException e) {
			SLogger.ERROR.log("Database access error occurred while reading the result set.");
			e.printStackTrace();
		}
		return new RS_Set(rs, rows.toArray(new RS_Row[rows.size()]));
	}
	
	public Connection getConnection() {
		return connection;
	}
}
