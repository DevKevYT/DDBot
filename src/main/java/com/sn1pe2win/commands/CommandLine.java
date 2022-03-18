package com.sn1pe2win.commands;

import com.devkev.devscript.raw.Block;
import com.devkev.devscript.raw.Command;
import com.devkev.devscript.raw.Library;
import com.devkev.devscript.raw.Process;

public class CommandLine extends Library {

	public String option_token = null;
	public String option_xtoken = null;
	public String option_database = null;
	public boolean option_keeplogs = false;
	
	private String errorMessage = null;
	
	public CommandLine() {
		super("CMD Interpreter");
	}

	@Override
	public Command[] createLib() {
		return new Command[] {
			new Command("export", "string ...", "") {
				@Override
				public Object execute(Object[] arg0, Process arg1, Block arg2) throws Exception {
					errorMessage = null;
					
					for(int i = 0; i < arg0.length; i++) {
						String tag = arg0[i].toString();
						
						if(tag.equals("-d") || tag.equals("--database")) {
							if(i + 1 >= arg0.length) {
								errorMessage = "Missing option value for " + tag;
								return null;
							}
							option_database = arg0[i + 1].toString();
							i++;
						} else if(tag.equals("-t") || tag.equals("--token")) {
							if(i + 1 >= arg0.length) {
								errorMessage = "Missing option value for " + tag;
								return null;
							}
							option_token = arg0[i + 1].toString();
							i++;
						} else if(tag.equals("-x") || tag.equals("--xtoken")) {
							if(i + 1 >= arg0.length) {
								errorMessage = "Missing option value for " + tag;
								return null;
							}
							option_xtoken = arg0[i + 1].toString();
							i++;
						} else if(tag.equals("--keeplogs")) {
							option_keeplogs = true;
						} else {
							errorMessage = "Unrecognized option '" + tag + "'. Valid options are -t <token> and -x <xtoken> or -d <database-file>";
							return null;
						}
					}
					return null;
				}
			}
		};
	}
	
	public String getErrorMessage() {
		return errorMessage;
	}

	@Override
	public void scriptExit(Process arg0, int arg1, String arg2) {
		
	}

	@Override
	public void scriptImport(Process arg0) {
		
	}
}
