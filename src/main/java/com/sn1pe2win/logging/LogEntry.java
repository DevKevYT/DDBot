package com.sn1pe2win.logging;

import java.util.Date;

import com.sn1pe2win.sql.simpledb.MappedVar;
import com.sn1pe2win.sql.simpledb.RS_Row;

public class LogEntry extends RS_Row {
	
	public MappedVar<Integer> loglevel = new MappedVar<>("type");
	public MappedVar<Long> time = new MappedVar<>("time");
	public MappedVar<String> content = new MappedVar<>("content");
	public MappedVar<Long> screen = new MappedVar<>("screen_id");
	public MappedVar<Integer> sLine = new MappedVar<>("line");
	public MappedVar<String> source = new MappedVar<>("source");
	
	public void console() {
		if(!loglevel.isEmpty() && !time.isEmpty() && !screen.isEmpty()) {
			
			String c = (screen.get() == SLogger.BROADCAST_SCREEN ? " " : "") + content.get();
			Source s = new Source(0, "unknown");
			
			if(!sLine.isEmpty()) s.lineNumber = sLine.get();
			if(!source.isEmpty()) s.souceClass = source.get();
			
			if(loglevel.get() == SLogger.INFO.LOG_LEVEL) 
				SLogger.INFO.console(c, new Date(time.get()), s);
			else if(loglevel.get() == SLogger.WARNING.LOG_LEVEL) 
				SLogger.WARNING.console(c, new Date(time.get()), s);
			else if(loglevel.get() == SLogger.ERROR.LOG_LEVEL) 
				SLogger.ERROR.console(c, new Date(time.get()), s);
			else if(loglevel.get() == SLogger.APPROVE.LOG_LEVEL) 
				SLogger.APPROVE.console(c, new Date(time.get()), s);
			else if(loglevel.get() == SLogger.CHOICE.LOG_LEVEL) 
				SLogger.CHOICE.console(c, new Date(time.get()), s);
			
		} else SLogger.stream.println("[" + loglevel.get() + "] " + new Date(time.get()) + " > " + content.get());
	}
}
