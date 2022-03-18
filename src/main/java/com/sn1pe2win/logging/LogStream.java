package com.sn1pe2win.logging;

import java.util.Date;

public class LogStream {
	
	public final byte LOG_LEVEL;
	private final String LOG_LEVEL_IDENTIFIER;
	
	public LogStream(byte logLevel, String identifier) {
		LOG_LEVEL = logLevel;
		this.LOG_LEVEL_IDENTIFIER = identifier;
	}
	
	/**Logs a new entry mathing the specified screen_id*/
	public void log(String content, long screen_id) {
		Source s = getSource();
		SLogger.addLog(LOG_LEVEL, content, screen_id, s.lineNumber, s.souceClass);
		if(screen_id == SLogger.CURRENT_SCREEN) console(content, s);
	}
	
	/**Logs a new entry into the current active screen found in {@link SLogger#CURRENT_SCREEN}
	 * Also wenn der aktive screen geloggt wird, wird automatisch die Konsolenausgabe getriggert*/
	public void log(String content) {
		log(content, SLogger.CURRENT_SCREEN);
	}
	
	/**Adds a new entry to all screens*/
	public void broadcast(String content) {
		Source s = getSource();
		SLogger.addLog(LOG_LEVEL, content, SLogger.BROADCAST_SCREEN, s.lineNumber, s.souceClass);
		console(content, s);
	}
	
	void console(String content, Date date, Source source) {
		SLogger.stream.print(LOG_LEVEL_IDENTIFIER + " " + (source != null ? source.souceClass + ":" + source.lineNumber : "")
				//+ (SLogger.dateFormat != null ? SLogger.dateFormat.format(date) : "") 
				+ " >> " + content.replace("\n", "") + "\n");
	}
	
	/**Logs directly into the console regardless what screen is currently displayed. It is also not saved in the database.
	 * Uses {@link SLogger#stream} specified in {@link SLogger#configure(java.io.PrintStream, com.sn1pe2win.sql.simpledb.SimpleDatabase)} as output stream.
	 * @param source can be null.*/
	public void console(String content, Source source) {
		console(content, new Date(System.currentTimeMillis()), source);
	}
	
	/**Logs directly into the console regardless what screen is currently displayed. It is also not saved in the database.
	 * Uses {@link SLogger#stream} specified in {@link SLogger#configure(java.io.PrintStream, com.sn1pe2win.sql.simpledb.SimpleDatabase)} as output stream.*/
	public void console(String content) {
		console(content, new Date(System.currentTimeMillis()), null);
	}
	
	private Source getSource() {
		for(StackTraceElement e : Thread.currentThread().getStackTrace()) {
			if(!e.getClassName().equals(this.getClass().getCanonicalName()) 
					&& !e.getClassName().equals(Thread.class.getCanonicalName())) {
				return new Source(e.getLineNumber(), e.getClassName().substring(e.getClassName().lastIndexOf(".")+1));
			}
		}
		return new Source(0, "unknown");
	}
}
