package com.sn1pe2win.managers.plugins;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Set;

import org.reflections.Reflections;

import com.sn1pe2win.config.dataflow.Node;
import com.sn1pe2win.config.dataflow.Parser;
import com.sn1pe2win.definitions.MembershipType;
import com.sn1pe2win.logging.SLogger;
import com.sn1pe2win.main.BotHandler;
import com.sn1pe2win.main.BotServer;
import com.sn1pe2win.main.Main;
import com.sn1pe2win.main.BotServer.ConfigModification;
import com.sn1pe2win.managers.plugins.Plugin.Scope;
import com.sn1pe2win.managers.triumphs.TriumphCreationSpec;
import com.sn1pe2win.managers.triumphs.TriumphStepCreationSpec;
import com.sn1pe2win.sql.simpledb.Exceptions.NoSuchTableException;
import com.sn1pe2win.sql.simpledb.RS_Function;
import com.sn1pe2win.user.DestinyUserDataPipeline;
import com.sn1pe2win.user.DiscordDestinyUser;
import com.devkev.devscript.raw.Process;

import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.guild.MemberJoinEvent;
import discord4j.core.event.domain.guild.MemberLeaveEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.event.domain.message.ReactionAddEvent;
import discord4j.core.object.entity.User;

public class PluginManager {
	
	private class PluginChangeRequest {
		Plugin p;
		BotServer requesting;
		boolean deleteData = true;
		boolean reload = false; //If true, the plugin is loaded as soon it has been removed
	}
	
	public class PluginSubroutine {
		
		public final BotServer server;
		public final Plugin plugin;
	
		public PluginSubroutine(BotServer server, Plugin plugin) {
			this.server = server;
			this.plugin = plugin;
		}
	}
	
	public boolean allowIncompatileVersions = false;
	
	/**Globaler Pool für alle Plugins bei denen sich Server bedienen können oder neu hochladen*/
	private ArrayList<Plugin> loadedPlugins = new ArrayList<Plugin>();
	
	private ArrayList<PluginChangeRequest> addRequests = new ArrayList<PluginChangeRequest>();
	private ArrayList<PluginChangeRequest> removeRequests = new ArrayList<PluginChangeRequest>();
	
	volatile boolean locked = false;
	private final BotHandler host;
	
	public static final String TABLE_SERVER_USERDATA = "server_userdata";
	public static final String TABLE_GLOBAL_USERDATA = "global_userdata";
	
	public PluginManager(BotHandler host) {
		this.host = host;
		
		if(!host.getDatabase().tableExists(TABLE_GLOBAL_USERDATA)) { //Bezieht sich auf Zeug was von Plugins mit global scope gespeichert wird
			try {
				host.getDatabase().addTable(TABLE_GLOBAL_USERDATA, "userID", "origin", "data");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		if(!host.getDatabase().tableExists(TABLE_SERVER_USERDATA)) { //Bezieht sich auf Zeug was von Plugins mit server scope gespeichert wird
			try {
				host.getDatabase().addTable(TABLE_SERVER_USERDATA, "userID", "serverID", "origin", "data");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	public BotHandler getBotHandler() {
		return host;
	}
	
	/**Es wird empfohlen die Node als referenz zu speichern und als Referenz in {@link PluginManager#modifyGlobalConfig(Plugin, DiscordDestinyUser, BotServer, Node, ConfigModification)} zu übergeben wenn änderungen vorgenommen wurden.*/
	public Node getGlobalUserConfig(Plugin plugin, DiscordDestinyUser user) {
		for(Long l : plugin.globalUserDataSpaceGenerated) {
			if(user.discordId.getAsString().equals(String.valueOf(l))) {
				RS_Function<RS_UserData> fdata = host.getDatabase().createGetFunction(RS_UserData.class, "SELECT data FROM " + TABLE_GLOBAL_USERDATA + " WHERE origin = \"" + plugin.getFileName() + "\" AND userID = " + user.discordId.getAsString());				
				return Parser.parse(fdata.get(true).get(0).data.getAsString());
			}
		}
		return new Node();
	}
	
	public Node getServerUserConfig(Plugin plugin, DiscordDestinyUser user, BotServer server) {
		for(ServerUserDataSpace l : plugin.serverUserDataSpaceGenerated) {
			if(user.discordId.getAsString().equals(String.valueOf(l.userId)) && server.serverID.getAsString().equals(String.valueOf(l.serverId))) {
				RS_Function<RS_UserData> fdata = host.getDatabase().createGetFunction(RS_UserData.class, "SELECT data FROM " + TABLE_SERVER_USERDATA + " WHERE origin = \"" + plugin.getFileName() + "\" AND serverID = " + server.serverID.getAsString() + " AND userID = " + user.discordId.getAsString());				
				return Parser.parse(fdata.get(false).get(0).data.getAsString());
			}
		}
		return new Node();
	}
	
	public void modifyServerUserConfig(Plugin plugin, DiscordDestinyUser user, Node node, BotServer server) {
		if(plugin.scope != Scope.SERVER) {
			SLogger.ERROR.log("Plugin scope is not global! Try saving userdata in server scope space!", server.serverID.get());
			return;
		}
		boolean spaceExists = false;
		for(ServerUserDataSpace l : plugin.serverUserDataSpaceGenerated) {
			if(user.discordId.getAsString().equals(String.valueOf(l.userId)) && server.serverID.getAsString().equals(String.valueOf(l.serverId))) {
				spaceExists = true;
				break;
			}
		}
		if(!spaceExists) {
			try {
				SLogger.INFO.log("Server user space for user " + user.discordId.getAsString() + " not found. Creating one for plugin " + plugin.getFileName() + " on server " + server.serverID.get());
				host.getDatabase().query("INSERT INTO " + TABLE_SERVER_USERDATA + " (userID, serverID, origin) VALUES (" + user.discordId.get() + ", " + server.serverID.get() + ", \"" + plugin.getFileName() + "\")");
				ServerUserDataSpace s = new ServerUserDataSpace();
				s.serverId = server.serverID.get();
				s.userId = user.discordId.get();
				plugin.serverUserDataSpaceGenerated.add(s);
			} catch (SQLException | NoSuchTableException e) {
				e.printStackTrace();
			}
		}
		
		try {
			host.getDatabase().queryUpdate("UPDATE " + TABLE_SERVER_USERDATA + " SET data = \"" + node.print(false) + "\" WHERE userID = " + user.discordId.getAsString());
		} catch (SQLException | NoSuchTableException e) {
			e.printStackTrace();
		}
	}
	
	public void modifyGlobalConfig(Plugin plugin, DiscordDestinyUser user, Node node) {	
		if(plugin.scope != Scope.GLOBAL) {
			SLogger.ERROR.log("Plugin scope is not global! Try saving userdata in server scope space!");
			return;
		}
		boolean spaceExists = false;
		for(Long l : plugin.globalUserDataSpaceGenerated) {
			if(user.discordId.getAsString().equals(String.valueOf(l))) {
				spaceExists = true;
				break;
			}
		}
		
		if(!spaceExists) {
			try {
				SLogger.INFO.log("Global user space for user " + user.discordId.getAsString() + " not found. Creating one for plugin " + plugin.getFileName() + " ...");
				host.getDatabase().query("INSERT INTO " + TABLE_GLOBAL_USERDATA + " (userID, origin) VALUES (" + user.discordId.getAsString() + ", \"" + plugin.getFileName() + "\")");
				plugin.globalUserDataSpaceGenerated.add(user.discordId.get());
			} catch (SQLException | NoSuchTableException e) {
				e.printStackTrace();
			}
		} 
		
		try {
			host.getDatabase().queryUpdate("UPDATE " + TABLE_GLOBAL_USERDATA + " SET data = \"" + node.print(false) + "\" WHERE userID = " + user.discordId.getAsString() + " AND origin = \"" + plugin.getFileName() + "\"");
		} catch (SQLException | NoSuchTableException e) {
			e.printStackTrace();
		}
		
	}
	
	/**
	 * Lädt ein Plugin in den globalen Pool rein. Wenn das Plugin schon exisitert, wird es zurückgegeben.<br>
	 * Es wird ein Array von geladenen Plugins zurückgegeben, da es sein kann, dass eine Plugin Datei mehrere Klassen besitzt, die {@link Plugin} erben.
	 * Diese werden in der Liste separat behandelt, besitzten jedoch den gleichen Dateinamen und können nicht direkt unterschieden werden.<br>
	 * Dadurch ist es möglich mehrere Plugins in einer Datei zusammenzufassen und immer zusammen zu laden.*/
	public ArrayList<Plugin> loadPlugin(File file, BotServer server) {
		SLogger.INFO.log("Attempting to load plugin file: " + file.getAbsolutePath(), server.serverID.get());
		
		if(!file.exists()) {
			SLogger.ERROR.log("Plugin file: " + file.getAbsolutePath() + " not found or does not exist", server.serverID.get());
			return new ArrayList<>(0);
		}
		
		Plugin preloaded = getLoadedPlugin(file.getName());
		if(preloaded != null) {
			SLogger.INFO.log("Plugin " + preloaded.getFileName() + " already added to pool. Adding to server config ...", server.serverID.get());
			
			ArrayList<Plugin> set = new ArrayList<>(preloaded.neighbors.length);
			for(Plugin p : preloaded.neighbors) {
				set.add(p);
				
				PluginChangeRequest request = new PluginChangeRequest();
				request.p = p;
				request.requesting = server;
				requestPluginLoad(request);
			
			}
			server.modifyConfig(spec -> {
				spec.getCreateArray("plugins").addArrayEntry(file.getAbsolutePath(), true);
			});
			return set;
		}
		
		URI uri = file.getAbsoluteFile().toURI();
		URL url = null;
		try {
			url = uri.toURL();
		} catch (MalformedURLException e1) {
			SLogger.ERROR.log("Failed to load plugin classes: " + e1.getLocalizedMessage(), server.serverID.get());
			return new ArrayList<Plugin>(0);
		}
	
		ArrayList<Plugin> loadedFromSingleJar = new ArrayList<Plugin>();
		Reflections reflections = null;
		URLClassLoader classLoader = null;
		try {
			classLoader = new URLClassLoader(new URL[] {url});
			reflections = new Reflections(classLoader);
		} catch(Exception e) {
			if(classLoader != null) {
				try {
					classLoader.close();
				} catch (IOException e1) {
					SLogger.ERROR.log("Failed to close classloader: " + e, server.serverID.get());
				}
			}
			SLogger.ERROR.log("Failed to load plugin classes: " + e, server.serverID.get());
			return new ArrayList<Plugin>(0);
		}
		
		SLogger.INFO.log("Loading plugin file: " + file.getName(), server.serverID.get());
		Set<Class<? extends Plugin>> subtypes = reflections.getSubTypesOf(Plugin.class);
		
		if(subtypes.size() == 0) 
			SLogger.WARNING.log("No instance found extending " + Plugin.class.getCanonicalName(), server.serverID.get());
		
		//Es ist egal wie viele Klassen es sind, dasich die Triumph Herkunft immer auf eine ganze Datei bezieht
		if(loadedFromSingleJar.size() > 0) {
			//refresh triumphs
			SLogger.INFO.broadcast("Refreshing triumphs from Plugin " + loadedFromSingleJar.get(0).getFileName());
			host.triumphManager.deleteTriumph(file.getName());			
		}
				
		for(Class<? extends Plugin> objects : subtypes) {
			try {
				Plugin pluginObject = objects.getConstructor().newInstance();
				
				if(pluginObject.currentVersion.MINOR < Main.VERSION.MINOR) {
					SLogger.ERROR.log("Incompatible versions detected. Plugin Target Version: " + pluginObject.currentVersion + " Host Version: " 
							+ Main.VERSION + ". Unable to load plugin!", server.serverID.get());
					continue;
				}
				
				pluginObject.file = file;
				pluginObject.classData = objects;
				PluginChangeRequest request = new PluginChangeRequest();
				request.p = pluginObject;
				request.requesting = server;
				
				//Config einträge sind nicht asynchron
				server.modifyConfig(spec -> {
					spec.getCreateArray("plugins").addArrayEntry(file.getAbsolutePath(), true);
				});
				request.p.triumphManager = host.triumphManager;
				request.p.pluginManager = this;
				
				requestPluginLoad(request);
				
				loadedFromSingleJar.add(pluginObject);
				
			} catch(Exception e) {
				SLogger.ERROR.log("Failed to load class '" + objects.getCanonicalName() + "' from plugin file " + file.getAbsolutePath() + ": " + e.getMessage(), server.serverID.get());
				e.printStackTrace();
			}
		}
		
//		try {
//			classLoader.close();
//		} catch (IOException e) {
//			SLogger.ERROR.log("Failed to close classloader: " + e, server.serverID.get());
//		}
		
		if(loadedFromSingleJar.size() > 1) {
			for(Plugin l : loadedFromSingleJar) {
				l.neighbors = loadedFromSingleJar.toArray(new Plugin[loadedFromSingleJar.size()]);
				l.fileAmbigous = true;
				l.checkEsistingUserSpaces(host.getDatabase());
			}
		} else if(loadedFromSingleJar.size() > 0) {
			loadedFromSingleJar.get(0).neighbors = new Plugin[] {loadedFromSingleJar.get(0)};
			loadedFromSingleJar.get(0).checkEsistingUserSpaces(host.getDatabase());
		}
		
		return loadedFromSingleJar; 
	}
	
	
	private void requestPluginLoad(PluginChangeRequest request) {
		if(!locked) {
			
			boolean poolAdded = false;
			
			for(Plugin p : loadedPlugins) {
				if(p.getFileName().equals(request.p.getFileName())
						&& p.classData.getCanonicalName().equals(request.p.classData.getCanonicalName())) {
					poolAdded = true;
					break;
				}
			}
			
			for(Plugin p : request.requesting.getServerPlugins()) {
				if(p.getFileName().equals(request.p.getFileName()) 
						&& p.classData.getCanonicalName().equals(request.p.classData.getCanonicalName())) {
					SLogger.WARNING.log("Plugin already added to server", request.requesting.serverID.get());
					addRequests.remove(request);
					return;
				}
			}
			
			if(!poolAdded) {
				loadedPlugins.add(request.p);
				
			}
			request.requesting.getServerPlugins().add(request.p);
			
			if(request.p.getScope() == null) {
				request.p.scope = Scope.SERVER;
				SLogger.WARNING.log("Plugin Scope not defined. Defaulting to Scope.SERVER", request.requesting.serverID.get());
			} else request.p.scope = request.p.getScope();
			
			SLogger.APPROVE.broadcast("Plugin class loaded and removed from queue: " + request.p.getClass().getCanonicalName() + ".class, Version: " + request.p.VERSION);
			
			try {
				EventClasses.PluginLoadEvent event = new EventClasses.PluginLoadEvent();
				event.server = request.requesting;
				
				request.p.onPluginLoad(event);
	
			} catch(Exception e) {
				SLogger.ERROR.log("An error occurred for plugin " + request.p
						+ " (line " + getExceptionLineNumber(e, request.p.getClass()) + "): " + e.getLocalizedMessage(), request.requesting.serverID.get());
			}
			
			addRequests.remove(request);
		} else {
			SLogger.INFO.broadcast("Current Loaded plugins are locked. Adding plugin " + request.p + " to queue.");
			if(addRequests.contains(request)) addRequests.add(request);
		}
	}
	
	private void requestPluginRemoval(PluginChangeRequest request) {
		if(!locked) {
			
			for(Plugin p : request.p.neighbors) {
				
				request.requesting.getServerPlugins().remove(p);
			
				try {
					EventClasses.PluginRemoveEvent event = new EventClasses.PluginRemoveEvent();
					event.server = request.requesting;
					event.deleteData = request.deleteData;
					p.onPluginRemove(event);
				} catch(Exception e) {
					SLogger.ERROR.log("Error in plugin function onPluginRemove() " + e.getMessage() + " for plugin " + p, request.requesting.serverID.get());
					e.printStackTrace();
				}
				
				if(!request.deleteData) 
					SLogger.INFO.log("No userdata cleanup requested.");
				
				if(p.scope == Scope.SERVER && request.deleteData) {
					SLogger.INFO.log("Deleting server userdata for server", request.requesting.serverID.get());
					try {
						int count = host.getDatabase().queryUpdate("DELETE FROM " + TABLE_SERVER_USERDATA + " WHERE origin = \"" + p.getFileName() + "\" AND serverID = " + request.requesting.serverID.getAsString());
						SLogger.APPROVE.console("Cleared " + count + " user entries ...");
					} catch (SQLException | NoSuchTableException e) {
						e.printStackTrace();
					}
					
					for(int i = 0; i < p.serverUserDataSpaceGenerated.size(); i++) {
						if(request.requesting.serverID.getAsString().equals(String.valueOf(p.serverUserDataSpaceGenerated.get(i).serverId))) {
							p.serverUserDataSpaceGenerated.remove(i);
							i--;
						}
					}
				}
				
				SLogger.INFO.log("Plugin removed " + p.getFile().getAbsolutePath());
				removeRequests.remove(request);
			}
			
			boolean presentOnAnyServer = false;
			//Checke ob das Plugin noch auf irgendeinem Server geladen ist
			outer: for(BotServer s : host.getServers()) {
				for(Plugin p : s.getServerPlugins()) {
					if(request.p.equals(p)) {
						presentOnAnyServer = true;
						break outer;
					}
				}
			}
			
			if(!presentOnAnyServer && request.deleteData) {
				if(!request.deleteData) {
					SLogger.INFO.log("Plugin on no server present, but no data cleanup requested. Leaving global data it as it is ...");
					return;
				}
				
				SLogger.INFO.log("Plugin on no server present. Removing from pool and deleting userdata ...");
				
				boolean globalRemoved = false;
				boolean serverRemoved = false;
				
				for(Plugin p : request.p.neighbors) {
					if(p.scope == Scope.GLOBAL && !globalRemoved) {
						SLogger.INFO.console("Clearing userdata: " + p.scope);
						
						try {
							int count = host.getDatabase().queryUpdate("DELETE FROM " + TABLE_GLOBAL_USERDATA + " WHERE origin = \"" + p.getFileName() + "\"");
							SLogger.APPROVE.console("Cleared " + count + " user entries ...");
							
						} catch (SQLException | NoSuchTableException e) {
							e.printStackTrace();
						}
						
						globalRemoved = true;
					}
					if(p.scope == Scope.SERVER && !serverRemoved) {
						
						SLogger.INFO.console("Clearing userdata: " + p.scope);
						
						try {
							int count = host.getDatabase().queryUpdate("DELETE FROM " + TABLE_SERVER_USERDATA + " WHERE origin = \"" + p.getFileName() + "\"");
							SLogger.APPROVE.console("Cleared " + count + " user entries ...");
							
						} catch (SQLException | NoSuchTableException e) {
							e.printStackTrace();
						}
						
						serverRemoved = true;
					}
					host.triumphManager.deleteTriumph(p.getFileName());
					loadedPlugins.remove(p);	
				}
				
			} 
			
			if(request.reload) {
				//Removing from global pool to add it later
				loadedPlugins.remove(request.p);
				SLogger.INFO.log("Reloading plugin ...");
				loadPlugin(request.p.getFile(), request.requesting);
			}
		} else {
			SLogger.INFO.broadcast("Current Loaded plugins are locked. Adding request " + request.p + " to remove queue.");
			if(removeRequests.contains(request)) removeRequests.add(request);
		}
	}
	
	private void handleQueue() {
		for(PluginChangeRequest p : addRequests) 
			requestPluginLoad(p);
		for(PluginChangeRequest p : removeRequests)
			requestPluginRemoval(p);
	}
	
	/**Requests a plugin removal from a server<br>
	 * If the plugin is not present on any server anymore, it is removed from the global pool.<br>
	 * @param fileName - The unique file name not the path.<br>
	 * @param deleteData - If this function should also delete the user data
	 * @param reload - If the plugin should get loaded again as soon as it has been removed*/
	public void removeServerPlugin(String fileName, BotServer server, boolean deleteData, boolean reload) {
		boolean found = false;
		ArrayList<PluginChangeRequest> requests = new ArrayList<>();
		main: for(Plugin p : loadedPlugins) {
			if(p.getFileName().equals(fileName)) {
				found = true;
				
				PluginChangeRequest request = new PluginChangeRequest();
				request.requesting = server;
				request.p = p;
				request.reload = reload;
				request.deleteData = deleteData;
				
				SLogger.INFO.log("Adding plugin " + p + " to request! [reload=" + reload + ", cleanup="+deleteData+"]", server.serverID.get());
				
				requests.add(request);
					
				server.modifyConfig(spec -> {
					spec.getCreateArray("plugins").removeArrayEntry(p.getFile().getAbsolutePath());
				});
				break main;
			}
		}
		
		for(int i = 0; i < requests.size(); i++) {
			requestPluginRemoval(requests.get(i));
			requests.remove(i);
			i = 0;
		}
		
		if(!found)
			SLogger.WARNING.log("No plugin with filename " + fileName + " found. Missed the '.jar?'", server.serverID.get());
	}
	
	public void deleteGlobalUserPluginData(long userId) throws SQLException, NoSuchTableException {
		host.getDatabase().queryUpdate("DELETE FROM " + TABLE_GLOBAL_USERDATA + " WHERE userID = " + userId);
		for(Plugin p : getPlugins()) {
			p.globalUserDataSpaceGenerated.remove(userId);
		}
	}
	
	public void deleteServerUserPluginData(long userId, long serverId) throws SQLException, NoSuchTableException {
		host.getDatabase().queryUpdate("DELETE FROM " + TABLE_SERVER_USERDATA + " WHERE userID = " + userId);
		
		for(int i = 0; i < getPlugins().size(); i++) {
			for(int j = 0; j < getPlugins().get(i).serverUserDataSpaceGenerated.size(); j++) {
				if(getPlugins().get(i).serverUserDataSpaceGenerated.get(j).serverId == serverId
						&& getPlugins().get(i).serverUserDataSpaceGenerated.get(j).userId == userId) {
					getPlugins().get(i).serverUserDataSpaceGenerated.remove(j);
					j = 0;
				}
			}
		}
	}
	
	private ArrayList<PluginSubroutine> createPluginExecutionSubroutines(ArrayList<BotServer> affectedServers) {
		locked = true;
		
		ArrayList<PluginSubroutine> subroutines = new ArrayList<>();
		
		//Subroutinen erstellen
		for(BotServer server : affectedServers) {
			//Füge alle plugins die Scope.GLOBAL haben nur einmal hinzu. Bei allen anderen ist es egal
			for(Plugin p : server.getServerPlugins()) {
				if(p.scope == Scope.GLOBAL) {
					boolean added = false;
					for(int i = 0; i < subroutines.size(); i++) {
						if(subroutines.get(i).plugin.getFileName().equals(p.getFileName())
								&& subroutines.get(i).plugin.getFileName().equals(p.getFileName())) {
							added = true;
							break;
						}
					}
					if(!added) subroutines.add(new PluginSubroutine(null, p));
					
				} else {
					//Füge die subroutine hinzu wenn das Plugin auf diesem Server geladen ist
					for(Plugin sp : server.getServerPlugins()) {
						if(sp.getFileName().equals(p.getFileName())) {
							subroutines.add(new PluginSubroutine(server, p));
							break;
						}
					}
				}
			}
		}
		
		return subroutines;
	}
	
	/**Führt alle Methoden der Plugins aus, die mit diesem Server assoziiert werden.*/
	public void triggerOnDestinyMemberUpdate(DestinyUserDataPipeline pipeline, DiscordDestinyUser user, long updateId) {
		for(PluginSubroutine routine : createPluginExecutionSubroutines(user.joinedServers)) {
			try {
				EventClasses.DiscordDestinyUserUpdate u = new EventClasses.DiscordDestinyUserUpdate();
				u.globalUser = user.getLinkedDiscordUser();
				if(routine.server != null) u.serverUser = host.getBot().getMemberById(Snowflake.of(routine.server.serverID.get()), user.getLinkedDiscordUser().getId()).block();
				u.subroutine = routine;
				u.userdata = pipeline;
				u.user = user;
				u.updateId = updateId;
				u.server = routine.server;
				routine.plugin.onDestinyMemberUpdate(u);
			} catch(Exception e) {
				if(routine.server != null) 
					SLogger.ERROR.log("Error in plugin execution subroutine occurred for plugin " + routine.plugin 
						+ " (line " + getExceptionLineNumber(e, routine.plugin.getClass()) + "): " + e.getLocalizedMessage(), routine.server.serverID.get());
				else SLogger.ERROR.broadcast("Error in global scope plugin " + routine.plugin 
						+ " (line " + getExceptionLineNumber(e, routine.plugin.getClass()) + ") occurred for user " + user.getLinkedDiscordUser().getId().asString() + ": " + e.getLocalizedMessage());
			}
		}
		
		locked = false;
		handleQueue();
	}
	
	//TODO methoden ausführzeit und recourcenverbrauch überwachen
	public void triggerOnUpdate(ArrayList<DiscordDestinyUser> loadedMembers, BotServer server) {
		handleQueue();
		locked = true;
		for(Plugin p : server.getServerPlugins()) {
			try {
				EventClasses.ServerUpdate e = new EventClasses.ServerUpdate();
				e.server = server;
				e.users = loadedMembers.toArray(new DiscordDestinyUser[loadedMembers.size()]);
				p.onServerUpdate(e);
			}catch(Exception e) {
				SLogger.ERROR.log(printErrorInformation("triggerOnUpdate()", e, p, null), server.serverID.get());
			}
		}
		locked = false;
	}
	
	public void triggerOnServerMessageRecieved(MessageCreateEvent message, BotServer server) {
		handleQueue();
		locked = true;
		for(Plugin p : server.getServerPlugins()) {
			try {
				EventClasses.MessageRecievedEvent event = new EventClasses.MessageRecievedEvent();
				event.event = message;
				event.server = server;
				p.onMessageRecieved(event);
			}catch(Exception e) {
				SLogger.ERROR.log(printErrorInformation("triggerOnCommandRecieved()", e, p, message.getMessage().getContent()), server.serverID.get());
				e.printStackTrace();
			}
		}
		locked = false;
	}
	
	public void triggerOnCommandExecuted(MessageCreateEvent message, Process process, int exitCode, BotServer server) {
		handleQueue();
		locked = true;
		for(Plugin p : server.getServerPlugins()) {
			try {
				EventClasses.CommandExecutedEvent event = new EventClasses.CommandExecutedEvent();
				event.event = message;
				event.server = server;
				event.exitCode = exitCode;
				p.onCommandExecuted(event);
			}catch(Exception e) {
				SLogger.ERROR.log(printErrorInformation("triggerOnCommandExecuted()", e, p, message.getMessage().getContent() + " Exitcode: " + exitCode), server.serverID.get());
				e.printStackTrace();
			}
		}
		locked = false;
	}
	
	//TODO Vielleich nóch das DiscordDestinyUser mit beifügen, falls der user schon auf einem anderen Server verlinkt ist
	public void triggerOnMemberJoinEvent(MemberJoinEvent event, BotServer server) {
		handleQueue();
		locked = true;
		for(Plugin p : server.getServerPlugins()) {
			try {
				EventClasses.MemberjoinEvent e = new EventClasses.MemberjoinEvent();
				e.event = event;
				e.server = server;
				p.onMemberJoin(e);
			}catch(Exception e) {
				SLogger.ERROR.log(printErrorInformation("triggerOnMemberJoinEvent()", e, p, event.getMember().getUsername()), server.serverID.get());
				e.printStackTrace();
			}
		}
		locked = false;
	}
	
	public void triggerOnMemberLeaveEvent(MemberLeaveEvent event, BotServer server) {
		handleQueue();
		locked = true;
		for(Plugin p : server.getServerPlugins()) {
			try {
				EventClasses.MemberLeaveEvent e = new EventClasses.MemberLeaveEvent();
				e.event = event;
				e.server = server;
				p.onMemberLeave(e);
			}catch(Exception e) {
				SLogger.ERROR.log(printErrorInformation("triggerOnMemberJoinEvent()", e, p, event), server.serverID.get());
				e.printStackTrace();
			}
		}
		locked = false;
	}
	
	/**Monster Methode o_o*/
	public void triggerOnMemberLinked(MembershipType chosen, long destinyId, User requestingUser, DiscordDestinyUser successFullUser) {
		handleQueue();
		locked = true;
		for(PluginSubroutine routine : createPluginExecutionSubroutines(successFullUser.joinedServers)) {
			try {
				EventClasses.MemberLinkedEvent e = new EventClasses.MemberLinkedEvent();
				e.chosen = chosen;
				e.destinyMembershipId = destinyId;
				e.requestingUser = requestingUser;
				e.server = routine.server;
				routine.plugin.onMemberLinked(e);
			} catch(Exception e) {
				SLogger.ERROR.log(printErrorInformation("triggerOnMemberLinked()", e, routine.plugin, requestingUser.getUsername() + " tried to link " + destinyId), routine.server.serverID.get());
				e.printStackTrace();
			}
		}
		
		locked = false;
	}
	
	/**Only linked users can send private messages*/
	public void triggerOnPrivateMessageReactionAdded(ReactionAddEvent event, DiscordDestinyUser user) {
		handleQueue();
		locked = true;
		for(PluginSubroutine p : createPluginExecutionSubroutines(user.joinedServers)) {
			try {
				EventClasses.ReactionAddedEvent e = new EventClasses.ReactionAddedEvent();
				e.event = event;
				e.server = p.server;
				e.user = user;
				p.plugin.onReactionAdded(e);
			} catch(Exception e) {
				SLogger.ERROR.log(printErrorInformation("triggerOnReactionAdded()", e, p.plugin, event), p.server.serverID.get());
				e.printStackTrace();
			}
		}
		
		locked = false;
	}
	
	/**only triggered on server messages
	 * If a plugin has a global scope this */
	public void triggerOnReactionAdded(ReactionAddEvent event, BotServer server) {
		handleQueue();
		locked = true;
		for(Plugin p : server.getServerPlugins()) {
			try {
				EventClasses.ReactionAddedEvent e = new EventClasses.ReactionAddedEvent();
				e.event = event;
				e.server = server;
				p.onReactionAdded(e);
			}catch(Exception e) {
				SLogger.ERROR.log(printErrorInformation("triggerOnReactionAdded()", e, p, event), server.serverID.get());
				e.printStackTrace();
			}
		}
		
		locked = false;
	}
	
	public void triggerOnMemberTriumph(TriumphCreationSpec data, TriumphStepCreationSpec currentStep, DiscordDestinyUser user) {
		locked = true;
		
		for(PluginSubroutine routine : createPluginExecutionSubroutines(user.joinedServers)) {
			try {
				EventClasses.TriumphGivenEvent u = new EventClasses.TriumphGivenEvent();
				u.currentStep = currentStep;
				u.data = data;
				u.user = user;
				u.server = routine.server;
				routine.plugin.onTriumphAquired(u);
			} catch(Exception e) {
				if(routine.server != null) 
					SLogger.ERROR.log("Error in plugin execution subroutine occurred for plugin " + routine.plugin 
						+ " (line " + getExceptionLineNumber(e, routine.plugin.getClass()) + "): " + e.getLocalizedMessage(), routine.server.serverID.get());
				else SLogger.ERROR.broadcast("Error in global scope plugin " + routine.plugin 
						+ " (line " + getExceptionLineNumber(e, routine.plugin.getClass()) + ") occurred for user " + user.getLinkedDiscordUser().getId().asString() + ": " + e.getLocalizedMessage());
			}
		}
		
		locked = false;
		handleQueue();
	}
	
	@SuppressWarnings("unchecked")
	public <T> Object pipe(String pluginClassName, String fieldName, Class<?> type) {
		for(Plugin p : getPlugins()) {
			if(p.getClass().getName().equals(pluginClassName)) {
				for(Field field : p.getClass().getFields()) {
					if(field.getName().equals(fieldName)) {
						if((T) field.getType() == type) {
							try {
								return field.get(p);
							} catch (IllegalArgumentException | IllegalAccessException e) {
								e.printStackTrace();
								return null;
							}
						}
					}
				}
			}
		}
		return null;
	}
	
//	/**Gibt plugins zurück, die nur für den speziellen Server gedacht sind*/
//	public ArrayList<Plugin> getServerPlugins(BotServer server) {
//		ArrayList<Plugin> list = new ArrayList<>();
//		for(Plugin p : getPlugins()) {
//			if(p.server.serverID.getAsString().equals(server.serverID.getAsString()))
//				list.add(p);
//		}
//		return list;
//	}
	
	public ArrayList<Plugin> getPlugins() {
		return loadedPlugins;
	}
	
	private String printErrorInformation(String method, Exception e, Plugin plugin, Object other) {
		StackTraceElement target = null;
		for(StackTraceElement element : e.getStackTrace()) {
			if(element.getClassName().equals(plugin.getClass().getName())) {
				target = element;
				break;
			}
		}
		if(target == null) {
			outer: for(Throwable sup : e.getSuppressed()) {
				for(StackTraceElement element : sup.getStackTrace()) {
					if(element.getClassName().equals(plugin.getClass().getName())) {
						target = element;
						break outer;
					}
				}
			}
		}
		
		if(target != null) 
			return "Plugin error in file " + target.getFileName() + " in method " + target.getMethodName() + " at line " + target.getLineNumber() + ": " + e.toString() + " More info: " + (other == null ? "-" : other.toString());
		else return "Plugin Error for method " + method + " at " + plugin.file.getName() + ": " + e.toString() + " More info: " + (other == null ? "-" : other.toString());
	}
	
	public Plugin getLoadedPlugin(String fileName) {
		for(Plugin p : loadedPlugins) {
			if(p.getFileName().equals(fileName)) return p;
		}
		return null;
	}
	
	/**Ob ein Plugin aus einer Datei schon geladen ist*/
	public boolean pluginLoaded(File file) {
		return getLoadedPlugin(file.getName()) != null;
	}
	
	private int getExceptionLineNumber(Exception e, Class<?> pluginClass) {
		for(StackTraceElement l : e.getStackTrace()) 
			return l.getLineNumber();
		return -1;
	}
}
