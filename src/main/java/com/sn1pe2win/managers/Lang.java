package com.sn1pe2win.managers;

import java.io.File;
import java.util.ArrayList;

import com.devkev.devscript.raw.ApplicationBuilder;
import com.sn1pe2win.config.dataflow.Node;
import com.sn1pe2win.config.dataflow.Variable;
import com.sn1pe2win.logging.SLogger;

public final class Lang {

	/**Hilfreich beim erstellen von Sprachpaketen. Wenn der Text nicht gefunden wurde, wird er der Liste hinzugefügt 
	 * und der kann dann in der Text Datei übersetzt werden.*/
	public static boolean DEBUG_CREATE_NON_EXISTENT = false;
	public static boolean DEBUG_VERBOSE = false;
	public static boolean DEBUG_ERROR_ON_WRONG_VARIABLE_COUNT = false;
	
	public enum Language {
		
		German("de"), 
		English("en");
		
		public final String ISO;
		
		Language(String ISO) {
			this.ISO = ISO;
		}
		
		public static Language of(String ISOName) {
			for(Language l : Language.values()) {
				if(l.ISO.equals(ISOName)) return l;
			}
			return null;
		}
	}
	
	private static Node dbNode;
	public static Language DEFAULT;
	
	public static void init(File file) throws IllegalArgumentException, Exception {
		if(!file.exists()) file.createNewFile();
		
		dbNode = new Node(file);
		Variable def = dbNode.getCreateString("default", "en");
		DEFAULT = Language.of(def.getAsString());
		SLogger.INFO.log("Loaded " + (dbNode.getVariables().length-1) + " pack(s), default language \"" + DEFAULT.ISO + "\"");
	}
	
	public static String of(Language language, String defaultText, String ... vars) {
		Variable var = dbNode.get(defaultText);
		
		if(var.isUnknown()) {
			if(DEBUG_VERBOSE) SLogger.WARNING.console("[LANG] Language pack for '" + defaultText + "' not found!");
			if(DEBUG_CREATE_NON_EXISTENT) {
				dbNode.addNode(defaultText);
				dbNode.save(true);
				if(DEBUG_VERBOSE) SLogger.INFO.console("[LANG] Added language pack '" + defaultText + "'");
			}
			return insertVariables(defaultText, vars);
			
		} else if(var.isNode()) {
			
			Variable lang = var.getAsNode().get(language.ISO);
			if(!lang.isUnknown()) return insertVariables(lang.getAsString(), vars);
			else {
				if(DEBUG_VERBOSE) SLogger.WARNING.console("[LANG] Translation in '" + language.ISO + "' for pack " + defaultText + " not found!");
				return insertVariables(defaultText, vars);
			}
			
		} else {
			if(DEBUG_VERBOSE) SLogger.ERROR.console("[LANG] Pack '" + defaultText + "' contains illegal syntax");
			return insertVariables(defaultText, vars);
		}
	}
	
	public static String insertVariables(String text, String ... variables) {
		if(variables.length == 0) return text;
		//Generiere Variablen
		
		class Var {
			int index;
			String substring;
		}
		
		ArrayList<Var> vars = new ArrayList<>();
		char[] arr = text.toCharArray();
		
		for(int i = 0; i < arr.length; i++) {
			if(arr[i] == '$') {
				int nextVarClose = text.indexOf(']', i) + 1;
				if(nextVarClose == 0) continue;
				
				String var = text.substring(i, nextVarClose);
				//Teste ob der Index eine Nummer ist
				String nummer = var.substring(2, var.length()-1);
				if(nummer.length() == 0) continue;
				
				if(ApplicationBuilder.testForWholeNumber(nummer)) {
					int indexAsNumer = Integer.parseInt(nummer);
					if(indexAsNumer <= 0) continue;
					
					//Füge den Index mit den benötigten Informationen sortiert ein
					Var nv = new Var();
					nv.substring = text.substring(i, nextVarClose);
					nv.index = indexAsNumer;
					
					if(vars.size() > 0) {
						boolean added = false;
						inner: for(int j = 0; j < vars.size(); j++) {
							if(indexAsNumer > vars.get(j).index) {
								vars.add(j, nv);
								added = true;
								break inner;
							}
						}
						if(!added) vars.add(nv);
					} else vars.add(nv);
				}
			}	
		}
		
		if(vars.size() != variables.length && DEBUG_ERROR_ON_WRONG_VARIABLE_COUNT) {
			SLogger.ERROR.broadcast("Variable count does not match for text " + text + " " + vars.size() + " present, " + variables.length + " supplied.");
		}
		
		//Komischerweise sind die indexe umgedreht. Egal
		for(int i = 0; i < vars.size(); i++) {
			if(vars.size()-1 - i < variables.length)
				text = text.replace(vars.get(i).substring, variables[vars.size()-1 - i]);
		}
		return text;
	}
}
