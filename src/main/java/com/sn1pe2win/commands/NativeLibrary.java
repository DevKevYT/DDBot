package com.sn1pe2win.commands;

import java.io.File;
import java.sql.ResultSet;

import com.devkev.devscript.raw.ApplicationBuilder;
import com.devkev.devscript.raw.Block;
import com.devkev.devscript.raw.Command;
import com.devkev.devscript.raw.DataType;
import com.devkev.devscript.raw.Library;
import com.devkev.devscript.raw.Process;
import com.devkev.devscript.raw.Process.HookedLibrary;
import com.sn1pe2win.logging.LogEntry;
import com.sn1pe2win.logging.SLogger;
import com.sn1pe2win.main.BotHandler;
import com.sn1pe2win.main.BotServer;
import com.sn1pe2win.managers.plugins.Plugin;
import com.sn1pe2win.user.DiscordDestinyUser;

public class NativeLibrary extends Library {

	BotHandler handler;
	
	/**Command outputs werden standartmäßig in die Console geloggt da es nur eine "Admin" Konsole ist.*/
	public NativeLibrary(BotHandler handler) {
		super("Admin Library");
		this.handler = handler;
	}
	
	@Override
	public Command[] createLib() {
		
		return new Command[] {
				
				new Command(".commands", "", "Gibt eine Liste von allen derzeit geladenen Commands zurück. Kann verschieden sein, jenachdem in welchem Screen man sich befindet.") {

					@Override
					public Object execute(Object[] arg0, Process arg1, Block arg2) throws Exception {
						String message = "";
						
						for(HookedLibrary libs : arg1.getLibraries()) {
							message += "\n[" + libs.name + "]\n";
							for(Command c : libs.commands) {
								message += "  " + c.name + "";
								for(DataType t : c.arguments) {
									message += " <" + t.type.typeName + ">";
								}
								message += "\n";
							}
						}
						
						arg1.log(message, true);
						return null;
					}
				},
				
				new Command(".screen:list", "", "Gibt eine Liste von Aktiven Screens bzw. Servern aus auf der der Bot eingeloggt ist") {
					@Override
					public Object execute(Object[] arg0, Process arg1, Block arg2) throws Exception {
						SLogger.INFO.console("The following screens are available:");
						for(BotServer s : handler.getServers())  //Active screens
							SLogger.APPROVE.console("SID:\t"  + s.serverID.get() + " [" + s.screen_id + ", active]");
						
						main: for(LogEntry screen : handler.getScreens()) {
							for(BotServer s : handler.getServers()) {
								if(s.serverID.getAsString().equals(screen.screen.getAsString())) 
									continue main;
							}
							SLogger.APPROVE.console("SID:\t" + (screen.screen.get() != -1 ? String.valueOf(screen.screen.get()) : "-1 (broadcast)") + " [dead]");
						}
						
						SLogger.INFO.console("Type screen:set <SID> to change the screen");
						return null;
					}
				},
				
				new Command(".screen:wipe", "", "Wipes the current screen") {
					@Override
					public Object execute(Object[] arg0, Process arg1, Block arg2) throws Exception {
						if(handler.adminCurrentLogged == null) {
							SLogger.WARNING.console("No screen active");
							return null;
						}
						if(SLogger.CHOICE.choice("Do you really want to wipe all screen logs from ID " 
								+ handler.adminCurrentLogged.screen_id + "(" + handler.adminCurrentLogged.serverID.get() + ")")) {
						
							int amount = SLogger.deleteEntries(handler.adminCurrentLogged.serverID.get(), 0, System.currentTimeMillis());
							SLogger.INFO.console(amount + " log entrie(s) deleted");
						}
						return null;
					}
				},
				
				new Command(".screen:wipedead", "", "") {
					@Override
					public Object execute(Object[] arg0, Process arg1, Block arg2) throws Exception {
						if(SLogger.CHOICE.choice("Do you really want to wipe all dead screen logs from ID?")) {
							int count = 0;
							for(LogEntry screen : handler.getScreens()) {
								BotServer found = null;
								for(BotServer s : handler.getServers()) {
									if(s.serverID.getAsString().equals(screen.screen.getAsString())) {
										found = s;
										break;
									}
								}
								if(found == null) {
									count ++;
									SLogger.INFO.console("Deleting dead screen " + screen.screen.get());
									SLogger.deleteEntries(screen.screen.get(), 0, System.currentTimeMillis());
								}
							}
							SLogger.APPROVE.console("Wiped " + count + " screen(s).");
						}
						return null;
					}
				},
				
				new Command(".screen:set", "string", "Wechselt den aktuellen Screen") {
					@Override
					public Object execute(Object[] arg0, Process arg1, Block arg2) throws Exception {
						if(ApplicationBuilder.testForWholeNumber(arg0[0].toString())) {
							
							long screen_id = Long.valueOf(arg0[0].toString());
							long tempScreen = SLogger.CURRENT_SCREEN;
							
							LogEntry[] entries = new LogEntry[] {};
							boolean active = false;
							long index = -1;
							
							for(BotServer servers : handler.getServers()) {
								index++;
								
								if(servers.serverID.get() == screen_id) {
									active = true;
									handler.adminCurrentLogged = servers;
									SLogger.CURRENT_SCREEN = handler.adminCurrentLogged.serverID.get();
									entries = SLogger.getLogEntries(screen_id);
									break;
									
								}
								if(screen_id == index) {
									screen_id = servers.serverID.get();
									active = true;
									handler.adminCurrentLogged = servers;
									SLogger.CURRENT_SCREEN = handler.adminCurrentLogged.serverID.get();
									entries = SLogger.getLogEntries(screen_id);
									break;
								}
							}
							
							if(entries.length == 0) 
								SLogger.WARNING.console("No lines found for screen " + screen_id);
							else {
								try {
									if(System.console() != null) {
										final String os = System.getProperty("os.name");  
										if (os.contains("Windows"))  
											new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
										else {
											System.out.print("\033[H\033[2J");  
											System.out.flush();  
										}
									}
								} catch(Exception e) {
									SLogger.WARNING.console("Failed to clear screen");
								}
								
								if(active)
									SLogger.APPROVE.console("[SET SCREEN TO " + SLogger.CURRENT_SCREEN + "]");
								else {
									SLogger.APPROVE.console("[SHOWING " + screen_id + "] (DEAD, RETURNING TO " + tempScreen + ")");
								}
								if(entries.length <= 20) {
									for(LogEntry e : entries) 
										e.console();
								} else {
									for(int i = 0; i < 20; i++) 
										entries[i].console();
								}
							}
						} else SLogger.ERROR.console("Unsupported screen id (Only numbers)");
						return null;
					}
				},
				
				new Command(".whoami", "", "Gibt den Servernamen zurück") {
					@Override
					public Object execute(Object[] arg0, Process arg1, Block arg2) throws Exception {
						ParsedVariables var = (ParsedVariables) arg1.getVariable("options", arg2);
						if(var.server != null)
							SLogger.INFO.log("SID: " + var.server.getId().asString() + " | Name: " + var.server.getName(), var.server.getId().asLong());
						else SLogger.WARNING.log("You need to be inside a valid screen. Use 'screen:list'");
						return null;
					}
				},
				
				new Command(".plugin", "string ...", "load <path> | listserver | listpool | removeserver <filename> | reload <filename> | info <filename>") {
					@Override
					public Object execute(Object[] arg0, Process arg1, Block arg2) throws Exception {
						ParsedVariables var = (ParsedVariables) arg1.getVariable("options", arg2);
						
						if(arg0.length == 0) {
							arg1.log("Unknown option. Options are: " + description, true);
							return null;
						}
						
						if(arg0[0].toString().equals("load")) {
							if(arg0.length > 1) {
								
								if(var.botServer == null) {
									arg1.log("You need to be inside a server screen! Use screen:list for a list of available screens!", true);
									return null;
								}
								
								String message = "Loaded new plugin classes:\n";
								for(Plugin p : handler.pluginManager.loadPlugin(new File(arg0[1].toString()), var.botServer)) 
									message += p.toString() + " Scope: " + p.scope + "\n";
								arg1.log(message, true);
							} else arg1.log("Missing option. Option is: 'load <path-to-file>'", true);
						} else if(arg0[0].toString().equals("listserver")) {
							
							if(var.botServer == null) {
								arg1.log("You need to be inside a server screen! Use screen:list for a list of available screens!", true);
								return null;
							}
							
							String message = "Loaded plugins for current server:\n";
							
							for(Plugin p : var.botServer.getServerPlugins()) 
								message += p.toString() + " Scope: " + p.scope + "\n";
							
							arg1.log(message, true);
						} else if(arg0[0].toString().equals("listpool")) {
							String message = "Loaded plugins in global pool:\n";
							
							for(Plugin p : handler.pluginManager.getPlugins()) 
								message += p.toString() + " Scope: " + p.scope + "\n";
							
							arg1.log(message, true);
						} else if(arg0[0].toString().equals("removeserver")) {
							if(arg0.length > 1) {
								if(var.botServer == null) {
									arg1.log("You need to be inside a server screen! Use screen:list for a list of available screens!", true);
									return null;
								}
								
								boolean choice = SLogger.CHOICE.choice("Also delete plugin data? (Keeping can cause junkdata)");
								handler.pluginManager.removeServerPlugin(arg0[1].toString(), var.botServer, choice, false);
								
							} else arg1.log("Missing option. Option is 'removeserver <filename>'", true);
						} else if(arg0[0].toString().equals("reload")) {
							if(arg0.length > 1) {
								if(var.botServer == null) {
									arg1.log("You need to be inside a server screen! Use screen:list for a list of available screens!", true);
									return null;
								}
								//ATTENTION: Updating a plugin affects the global pool!
								handler.pluginManager.removeServerPlugin(arg0[1].toString(), var.botServer, false, true);
								
							} else arg1.log("Missing option. Option is 'reload <filename>'", true);
						} else if(arg0[0].toString().equals("info")) {
							
							if(arg0.length > 1) {
								
								Plugin info = handler.pluginManager.getLoadedPlugin(arg0[1].toString());
								if(info != null) {
									
									arg1.log("Source File: " + info.getFile().getAbsolutePath().replace("\\", "/")
											+ "\nPool Version: " + info.VERSION + "\nLoaded by server:", true);
									
									for(BotServer s : handler.getServers()) {
										for(Plugin p : s.getServerPlugins()) {
											if(p.getFileName().equals(info.getFileName())) {
												arg1.log("\t" + s.serverID.get(), true);
											}
										}
									}
								} else SLogger.WARNING.log("Plugin " + arg0[0].toString() + " not found in global pool");
								
							} else arg1.log("Missing option. Option is 'reload <filename>'", true);
							
						} else arg1.log("Unknown option. Options are: " + description, true);
						return null;
					}
				},
				
				new Command(".listusers", "", "Listet alle registrierten User des Servers auf") {
					@Override
					public Object execute(Object[] arg0, Process arg1, Block arg2) throws Exception {
						String message = "\n";
						for(DiscordDestinyUser user : handler.getGlobalLinkedList()) {
							for(BotServer server : user.joinedServers) {
								if(server.serverID.getAsString().equals(String.valueOf(SLogger.CURRENT_SCREEN))) {
									message += user.getLinkedDiscordUser().getUsername() + " (ID: " + user.discordId.get() + ", Linked: " + user.destinyId.getAsString() + ")\n";
								}
							}
						}
						arg1.log(message, true);
						return null;
					}
				},
				
				new Command(".deleteuser", "string", "Löscht einen Benutzer. GEFÄHRLICH!") {
					@Override
					public Object execute(Object[] arg0, Process arg1, Block arg2) throws Exception {
						for(DiscordDestinyUser user : handler.getGlobalLinkedList()) {
							if(user.discordId.getAsString().equals(arg0[0].toString())) {
								handler.deleteUserData(user.discordId.get());
								arg1.log("User deleted", true);
								return null;
							}
						}
						arg1.log("User not found. User 'listusers' inside a screen to list all users linked on the server", true);
						return null;
					}
				},
				
				new Command(".setprogress", "string string string", "Gibt einem benutzer Fortschritt") {

					@Override
					public Object execute(Object[] arg0, Process arg1, Block arg2) throws Exception {
						
						if(!ApplicationBuilder.testForWholeNumber(arg0[2].toString())) {
							arg1.log("Unsupported progress value: " + arg0[2].toString(), true);
							return null;
						}
						
						for(DiscordDestinyUser user : handler.getGlobalLinkedList()) {
							if(user.discordId.getAsString().equals(arg0[0].toString())) {
								handler.triumphManager.setProgressForMember(user, arg0[1].toString(), Long.parseLong(arg0[2].toString()), false);
								return null;
							}
						}
						arg1.log("User not found", true);
						return null;
					}
					
				},
				
				new Command(".query", "string", "Executes a SQLite query") {
					@Override
					public Object execute(Object[] arg0, Process arg1, Block arg2) throws Exception {
						ResultSet set = handler.getDatabase().plainQuery(arg0[0].toString());
						String columns = "|\t";
						for(int i = 1; i < set.getMetaData().getColumnCount()+1; i++) {
							columns += set.getMetaData().getColumnName(i) + "\t |\t";
							
						}
						arg1.log(columns, true);
						int columnsNumber = set.getMetaData().getColumnCount();
						while (set.next()) {
							String line = "| ";
						    for (int i = 1; i <= columnsNumber; i++) {
						        String columnValue = set.getString(i);
						        line += columnValue + " | ";
						    }
						    arg1.log(line, true);
						}
						return null;
					}
				}
		};
	}

	@Override
	public void scriptExit(Process arg0, int arg1, String arg2) {
		
	}

	@Override
	public void scriptImport(Process arg0) {
		
	}
}
