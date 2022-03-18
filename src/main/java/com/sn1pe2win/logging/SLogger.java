package com.sn1pe2win.logging;

import java.io.PrintStream;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;

import com.sn1pe2win.sql.simpledb.Exceptions.NoSuchTableException;
import com.sn1pe2win.sql.simpledb.RS_Function;
import com.sn1pe2win.sql.simpledb.RS_Set;
import com.sn1pe2win.sql.simpledb.SimpleDatabase;

public class SLogger {
	
	private static final long instanceStart = System.currentTimeMillis();
	
	public static final LogStream INFO = new LogStream((byte) 0, "[   ]");
	public static final LogStream WARNING = new LogStream((byte) 1, "[ * ]");
	public static final LogStream ERROR = new LogStream((byte) 2, "[ ! ]");
	public static final LogStream FATAL = new LogStream((byte) 5, "[FATAL ERROR]");
	public static final LogStream APPROVE = new LogStream((byte) 3, "[ + ]");
	public static final ChoiceStream CHOICE = new ChoiceStream((byte) 4, "[ ? ]");
	
	/**Das Format für die Protokollierte Zeit. Default ist: dd-MM-yyyy HH:mm:ss im {@link SimpleDateFormat}*/
	public static DateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy-HH:mm:ss");
	
	/**Verhindert jegliche Datenbankeinträge*/
	public static final long FLAT_SCREEN = -2;
	/**Loggt in jeden screen*/
	public static final long BROADCAST_SCREEN = -1;
	public static long CURRENT_SCREEN = FLAT_SCREEN;
	
	private static SimpleDatabase database;
	public static PrintStream stream = System.out;
	
	public static void configure(PrintStream defaultStream, SimpleDatabase database) {
		SLogger.database = database;
		try {
			RS_Set set = database.query("SELECT name FROM sqlite_schema WHERE type ='table' AND name = 'screenlogs';");
			if(set.rows.size() == 0) {
				database.addTable("screenlogs", "type", "time", "line", "source", "content", "screen_id");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		stream = defaultStream;
	}
	
	/**Use null to ignore date output*/
	public void setDateFormat(DateFormat format) {
		SLogger.dateFormat = format;
	}
	
	static void addLog(byte type, String content, long screen_id, int line, String source) {
		
		if(screen_id == FLAT_SCREEN) return;
		if(database == null) throw new IllegalAccessError("Call ScreenLoggin.configure() before you can log things");

		try {
			database.query("INSERT INTO screenlogs (type, time, content, line, source, screen_id) VALUES (" + type + ", " + System.currentTimeMillis() + ", \"" + content + "\", " + line + ", \"" + source + "\", " + screen_id + ")");
		} catch (SQLException | NoSuchTableException e) {
			SLogger.ERROR.console("Failed to add log entry: \"" + content + "\" => " + e.getLocalizedMessage());
		}
	}
	
	public static long instanceStart() {
		return instanceStart;
	}
	
	private static RS_Function<LogEntry> getLogEntries = null;
	
	/**Also deletes all broadcasted entries
	 * @param screenId The screen id
	 * @param fromTime UNIX time (Milliseconds)
	 * @param toTime UNIX time (Milliseconds)
	 * @param loglevel The loglevel. Use -1 or any illegal value to ignore filtering*/
	public static int deleteEntries(long screenId, long fromTime, long toTime) {
		try {
			Statement statement = database.getConnection().createStatement();
			return statement.executeUpdate("DELETE FROM screenlogs WHERE time >= " + fromTime + " AND time <= " + toTime 
					+ " AND (screen_id = " + screenId + " OR screen_id = " + BROADCAST_SCREEN + ")");
		} catch (SQLException e) {
			e.printStackTrace();
			return 0;
		}
	}
	
	public static int deleteAll(long fromTime, long toTime) {
		try {
			Statement statement = database.getConnection().createStatement();
			return statement.executeUpdate("DELETE FROM screenlogs WHERE time >= " + fromTime + " AND time <= " + toTime);
		} catch (SQLException e) {
			e.printStackTrace();
			return 0;
		}
	}
	
	public static LogEntry[] getLogEntries(long screenId) {
		if(getLogEntries == null) 
			getLogEntries = database.createGetFunction(LogEntry.class, "SELECT * FROM screenlogs WHERE screen_id = " + screenId + " OR screen_id = -1 ORDER BY time ASC");
		List<LogEntry> logs = getLogEntries.get(false);
		return logs.toArray(new LogEntry[logs.size()]);
	}
}
