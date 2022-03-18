package com.sn1pe2win.main;

import java.io.File;

import com.devkev.devscript.raw.Process;
import com.sn1pe2win.api.OAuthHandler;
import com.sn1pe2win.commands.CommandLine;
import com.sn1pe2win.logging.SLogger;
import com.sn1pe2win.managers.Lang;
import com.sn1pe2win.managers.Version;

public class Main {

	//DONE PLugin update befehl erstellen der keine Plugin Daten löscht, bzw option geben ob man daten löschen möchte
	//DONE handle reaction events
	//DONE commands nicht case semsitive´
	//DONE FEATURE: analyse command (SQL select query)
	//TODO what happenes if an admin leaves a server
	//TODO Wenn ein Plugin geladen wurde alle Befehle aufzeigen mit dem man das Plugin Konfigurieren kann (Nicht wirklich ein erzwungenes Feature, mehr ein "best practice"
	//TODO create server removal routine (Command + Function)
	//TODO FEATURE multiple server administrators
	//TODO FEATURE: Plugin idea: Automatically pull from the postmaster
	//TODO FEATURE: notify user on expired login
	//TODO BUG: non existent plugin files are keeped in the server config
	
	public static OAuthHandler remoteAuthetification;
	
	public static final Version VERSION = Version.of("4.1.0");
	public static final Version  API_VERSION = Version.of("1.15");
	public static final CommandLine cmdArguments = new CommandLine();
	
	public static void main(String[] args) throws IllegalArgumentException, Exception {
		if(args.length == 0) throw new IllegalArgumentException("Missing config file path");
		
		Process interpreter = new Process(false);
		interpreter.includeLibrary(cmdArguments);
		String sargs = "";
		for(String s : args) sargs += " " + s;
		SLogger.INFO.log("Picked up options: " + sargs);
		interpreter.execute("export " + sargs, false);
		
		if(cmdArguments.getErrorMessage() != null) 
			throw new IllegalArgumentException(cmdArguments.getErrorMessage());
		
		SLogger.CURRENT_SCREEN = SLogger.FLAT_SCREEN;
		SLogger.APPROVE.log("[Made By]: Sn1pe2win32, V3NTER");
		
		File db = null;
		if(cmdArguments.option_database == null) 
			throw new IllegalArgumentException("Missing option -d <file> You need this option to specify a database file. A new one will be generated if the file does not exist.");
		
		db = new File(cmdArguments.option_database);
		if(!db.exists()) db.createNewFile();
		
		SLogger.APPROVE.log("[OS]: " + System.getProperty("os.name"));
		SLogger.APPROVE.log("[Host Version]: " + VERSION);
		SLogger.APPROVE.log("[API Version]: " + API_VERSION);
		SLogger.APPROVE.log("[Database]: " + db.getAbsolutePath());
		SLogger.INFO.log("Initializing OAuth2.0 ...");
		
		remoteAuthetification = new OAuthHandler(39618); //34630 alte
		remoteAuthetification.listen();
		
		SLogger.INFO.log("Loading language packs ...");
		
		Lang.init(new File("language-packs.conf"));
		
		Lang.DEBUG_CREATE_NON_EXISTENT = true;
		Lang.DEBUG_VERBOSE = true;
		Lang.DEBUG_ERROR_ON_WRONG_VARIABLE_COUNT = true;
		
		SLogger.INFO.log("Creating Server Objects ...");
		try {
			BotHandler handler = new BotHandler(db);
			handler.buildHandler();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
