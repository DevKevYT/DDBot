package com.sn1pe2win.main;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Scanner;

import com.sn1pe2win.DestinyEntityObjects.GlobalMessage;
import com.sn1pe2win.DestinyEntityObjects.GlobalMessage.AlertLevel;
import com.sn1pe2win.commands.ParsedVariables;
import com.sn1pe2win.commands.UnregisteredServerCommands;
import com.sn1pe2win.core.Gateway;
import com.sn1pe2win.core.Response;
import com.sn1pe2win.endpoints.GetGlobalAlerts;
import com.sn1pe2win.logging.LogEntry;
import com.sn1pe2win.logging.SLogger;
import com.sn1pe2win.main.Exceptions.ApiKeyNotDefinedException;
import com.sn1pe2win.managers.UpdateCellManager;
import com.sn1pe2win.managers.plugins.Plugin;
import com.sn1pe2win.managers.plugins.PluginManager;
import com.sn1pe2win.managers.triumphs.TriumphManager;
import com.sn1pe2win.sql.simpledb.Exceptions.NoSuchTableException;
import com.sn1pe2win.user.DiscordDestinyUser;
import com.sn1pe2win.sql.simpledb.MappedVar;
import com.sn1pe2win.sql.simpledb.RS_Function;
import com.sn1pe2win.sql.simpledb.RS_Row;
import com.sn1pe2win.sql.simpledb.RS_Set;
import com.sn1pe2win.sql.simpledb.SimpleDatabase;
import com.devkev.devscript.raw.Library;
import com.devkev.devscript.raw.Process;

import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.guild.MemberJoinEvent;
import discord4j.core.event.domain.guild.MemberLeaveEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.event.domain.message.ReactionAddEvent;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.Channel;
import discord4j.core.object.entity.channel.Channel.Type;
import discord4j.core.object.presence.Activity;
import discord4j.core.object.presence.Presence;
import discord4j.core.object.entity.channel.NewsChannel;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.rest.http.client.ClientException;
import discord4j.rest.util.Permission;
import reactor.core.publisher.Mono;

/**Alle allgemeinen Datenbankenschnittstellen werden hier gehandled.
 * Einzige Ausnahme ist die {@link BotHandler#TABLE_LINKEDUSER} tabelle,
 * welche serverspezifisch von {@link BotServer} verwaltet wird.
 * Das heißt alle Verlinkungen und "linkedUser" spezifischen datenbank verwaltungen werden dort übernommen.*/
public final class BotHandler extends RS_Row {
	
	public static final String CLIENT_CMD_PREFIX = ".";
	public static final long TIME_UNTIL_USER_DELETION = 432000000;//5 days in milliseconds 
	
	/**Only this class is allowed to operate on these tables*/
	private static final String TABLE_SERVERS = "server_config";
	private static final String TABLE_SERVER_MEMBER = "server_member";
	
	/**{@link BotServer} class is allowed to use this table*/
	public static final String TABLE_LINKEDUSER = "linked_user";
	protected static final String TABLE_MEMBER_LEAVE = "member_leave";
	
	public final MappedVar<String> API_TOKEN = new MappedVar<String>("API_TOKEN");
	public final MappedVar<String> X_API_KEY = new MappedVar<String>("X_API_KEY");
	public final MappedVar<String> VERSION = new MappedVar<String>("VERSION");
	
	private final RS_Function<BotHandler> config;
	
	
	private SimpleDatabase db;
	private File databaseFile;
	private GatewayDiscordClient botClient;
	private RS_Function<BotServer> servers;
	private RS_Function<LogEntry> screens;
	
	public BotServer adminCurrentLogged;
	private Process adminCommandHandler;
	
	/**Only true if the bot is finally initialized*/
	private volatile boolean ready = false;
	
	private final RS_Function<DiscordDestinyUser> GLOBAL_LINKED;
	
	private volatile boolean running = false;
	protected long THREAD_START_TIME;
	protected volatile boolean isUpdating = false; //The global linked list gets locked is true
	
	private UpdateCellManager updateManager;
	/**Der manager ist zwar hier um alles zentral zu verwalten. Jedoch werden plugins nur über server hinzugefügt / entfernt*/
	public final PluginManager pluginManager;
	public final TriumphManager triumphManager;
	
	private boolean maintenance = false;
	
	public BotHandler(File databaseFile) throws Exception {
		
		SLogger.CURRENT_SCREEN = SLogger.FLAT_SCREEN; //Just in case
		
		db = new SimpleDatabase(databaseFile);
		SLogger.configure(System.out, db);
		
		if(!db.tableExists("bot_config")) {
			SLogger.INFO.broadcast("Table bot_config not found. Creating ...");
			db.addTable("bot_config", "API_TOKEN", "X_API_KEY", "VERSION");
			db.query("INSERT INTO bot_config (VERSION) VALUES (\"" + Main.VERSION + "\")");
			SLogger.APPROVE.broadcast("Table created");
		}
		
		if(!Main.cmdArguments.option_keeplogs) {
			SLogger.INFO.broadcast("Option --keeplogs not set. Wiping previous logs");
			SLogger.deleteAll(0, SLogger.instanceStart());
		}
		
		SLogger.INFO.broadcast("Checking configurations ...");
		config = db.createGetFunction(BotHandler.class, new String[] {"*"}, new String[] {"bot_config"}, "");
		config.set(this);
		
		if(API_TOKEN.get() == null || Main.cmdArguments.option_token != null) {
			if(Main.cmdArguments.option_token == null) 
				throw new ApiKeyNotDefinedException("Incomplete config table. Unable to operate without a given API-key. Use -t or --token to specify a Discord API token");
			else {
				if(!Main.cmdArguments.option_token.equals(API_TOKEN.get())) {
					SLogger.INFO.broadcast("Adding / Changing API_KEY to " + Main.cmdArguments.option_token);
					db.query("UPDATE bot_config SET API_TOKEN = \"" + Main.cmdArguments.option_token + "\" WHERE rowid = 1");
					API_TOKEN.set(Main.cmdArguments.option_token);
				} 
			}
		}
		if(X_API_KEY.get() == null || Main.cmdArguments.option_xtoken != null) {
			if(Main.cmdArguments.option_xtoken == null)
				throw new ApiKeyNotDefinedException("Incomplete config table. Unable to operate without a given Destiny 2 API-key. Use -x or --xtoken to specify a Destiny 2 API token");
			else {
				if(!Main.cmdArguments.option_xtoken.equals(X_API_KEY.get())) {
					SLogger.INFO.broadcast("Adding / Changing X_API_KEY to " + Main.cmdArguments.option_xtoken);
					db.query("UPDATE bot_config SET X_API_KEY = \"" + Main.cmdArguments.option_xtoken + "\" WHERE rowid = 1");
					X_API_KEY.set(Main.cmdArguments.option_xtoken);
				}
			}
		}
		
		Gateway.X_API_KEY = X_API_KEY.get();
		
		if(VERSION.isUnset()) VERSION.set(Main.VERSION.toString());
		
		SLogger.INFO.log("Logging in ...");
		botClient = DiscordClientBuilder.create(API_TOKEN.get()).build().login().block();
		botClient.updatePresence(Presence.online(Activity.listening("Booting ..."))).subscribe();
		
		SLogger.INFO.log("Logged with Bot " + botClient.getSelf().block().getUsername());
		
		SLogger.INFO.log("Checking servers ...");
		
		checkTable(TABLE_SERVER_MEMBER, "userID", "serverID", "admin");
		checkTable(TABLE_SERVERS, "serverID", "channelID", "custom_config", "lang");
		checkTable(TABLE_LINKEDUSER, "userID", "membershipID", "custom_config", "platform", "access_token", "token_type", "expires_in", "refresh_token", "refresh_expires_in", "membership_id");
		checkTable(TABLE_MEMBER_LEAVE, "userID", "serverID", "time_until_deletion", "timestamp");
		
		servers = db.createGetFunction(BotServer.class, "SELECT * FROM server_config");
		servers.get(false);
		servers.lockToCache();
		
		triumphManager = new TriumphManager(this);
		
		pluginManager = new PluginManager(this);
		
		for(int i = 0; i < servers.getCached().size(); i++) {
			if(!servers.getCached().get(i).isReady()) {
				RS_Function<ServerRelation> relation = getDatabase().createGetFunction(ServerRelation.class, "SELECT * FROM " + TABLE_SERVER_MEMBER 
						+ " WHERE serverID = " + servers.getCached().get(i).serverID.get()
						+ " AND admin = 1 LIMIT 1");
				if(relation.get(true).size() == 0) {
					SLogger.ERROR.log("Broken server config. Removing server and asking to re- register", servers.getCached().get(i).serverID.get());
					removeServer(servers.getCached().get(i).serverID.get());
					i = 0;
					continue;
				} else {
					try {
						Member m = botClient.getMemberById(Snowflake.of(servers.getCached().get(i).serverID.get()), Snowflake.of(relation.getCached().get(0).userID.getAsString())).onErrorReturn(null).block();
						if(!servers.getCached().get(i).init(this, m))
							i = 0;
					} catch(Exception e) {
						e.printStackTrace();
						SLogger.ERROR.log("Broken server config: " + e.getLocalizedMessage() + " Removing server and asking to re- register!" , servers.getCached().get(i).serverID.get());
						removeServer(servers.getCached().get(i).serverID.get());
						i = 0;
						continue;
					}
				}
			}
		}
		//Lade die GLOBAL_USERS
		
		GLOBAL_LINKED = getDatabase().createGetFunction(DiscordDestinyUser.class, "SELECT * FROM " + TABLE_LINKEDUSER);
		SLogger.INFO.broadcast("Assigning global users to servers ...");
		updateLinkedUsersToServers();
		GLOBAL_LINKED.lockToCache();
		
		updateManager = new UpdateCellManager(4, this);
		
		adminCommandHandler = new Process(true);
		adminCommandHandler.addSystemOutput();
		adminCommandHandler.setInput(System.in);
		
		createUpdater();
		ready = true;
		botClient.updatePresence(Presence.online(Activity.listening(""))).subscribe();
	}
	
	/**Überprüft, ob server userdaten gelöscht werden sollen.
	 * Wenn userdaten gelöscht wurden und der user auf keinem anderen Server ist, werden die globalen userdaten gelöscht.*/
	public void checkUserOnLeaveDeletion() {
		RS_Function<RS_MemberLeave> entries = getDatabase().createGetFunction(RS_MemberLeave.class, "SELECT * FROM member_leave");
		
		for(RS_MemberLeave leave : entries.get(true)) {
			if(leave.timestamp.get().longValue() + leave.timeUntilDeletion.get().longValue() < System.currentTimeMillis()) {
				SLogger.WARNING.log("Userdata for user " + leave.userID.get() + " expired. Notifying user and deleting data.", leave.serverID.get());
				
				//DELETE SERVER DATA HERE
				deleteServerUserData(leave.userID.get(), leave.serverID.get());
				
				try {
					removeUserFromOnLeaveList(leave.userID.get(), leave.serverID.get());
				} catch (SQLException | NoSuchTableException e) {
					e.printStackTrace();
				}
				
				if(getServerRelationsFromUser(leave.userID.get()).length == 0) {
					SLogger.WARNING.broadcast("No servers left the user " + leave.userID.get() + " is on. Deleting global data");
					
					//DELETE GLOBAL DATA HERE
					deleteUserData(leave.userID.get());
					
					try {
						db.query("DELETE FROM " + TABLE_MEMBER_LEAVE + " WHERE userId = " + leave.userID.get());
					} catch (SQLException | NoSuchTableException e) {
						e.printStackTrace();
					}
					
				}
			}
			
		}
		
	}
	
	/**Setzt den user auf die on-leave List mit vom entsprechenden Server.
	 * Jeder user darf nur einmal pro Server angegeben sein. Wenn also ein Server schon drinne ist (Warum auch immer) wird die Zeit geupdatet. Genauso bei globale Daten
	 * Wenn serverId < 0, dann wird auf die globalen Daten abgezielt. (Dies betrifft auch alle anderen Server daten)
	 * @throws NoSuchTableException 
	 * @throws SQLException */
	public void putUserOnLeaveList(long userId, long serverId, long timeUntilDeletion) throws SQLException, NoSuchTableException {	
		SLogger.INFO.log("Requesting serverdata deletion for " + userId + " because he left this server. Account expires on " + new Date(System.currentTimeMillis() + timeUntilDeletion).toString(), serverId);
		
		RS_Set set = db.query("SELECT serverID FROM " + TABLE_MEMBER_LEAVE + " WHERE serverID = " + serverId + " AND userId = " + userId);
		if(set.rows.size() > 0) 
			db.queryUpdate("UPDATE " + TABLE_MEMBER_LEAVE + " SET timestamp=" + System.currentTimeMillis() + ", time_until_deletion=" + timeUntilDeletion + " WHERE serverId = " + serverId + " AND userId = " + userId);
		else 
			db.query("INSERT INTO " + TABLE_MEMBER_LEAVE + "(userID, serverID, time_until_deletion, timestamp) VALUES (" + userId + ", " + serverId + "," + timeUntilDeletion + ", " + System.currentTimeMillis() + ")");
	}	
	
	/**Wenn der benutzer auf der "global" on-leave-list ist (serverId = -1), dann wird der eintrag unabhä#ngig von der serverid gelöscht
	 * @throws NoSuchTableException 
	 * @throws SQLException */
	public void removeUserFromOnLeaveList(long userId, long serverId) throws SQLException, NoSuchTableException {
		db.query("DELETE FROM " + TABLE_MEMBER_LEAVE + " WHERE userId = " + userId + " AND serverId = " + serverId + " OR (userId = " + userId + " AND serverId = -1)");
	}
	
	/**Diese Funktion muss immer aufgerufen werden, wenn sich etwas auf dem Server ändert. D.h. wenn jemand joint, ein Server registriert wird etc.*/
	public void updateLinkedUsersToServers() {
		for(DiscordDestinyUser user : GLOBAL_LINKED.get(true)) {
			if(user.getLinkedDiscordUser() == null)
				user.init(botClient.getUserById(Snowflake.of(user.discordId.getAsString())).block(), this);
			
			user.joinedServers.clear();
			
			for(BotServer servers : getServerRelationsFromUser(user.discordId.get())) 
				user.joinedServers.add(servers);
			
			if(user.joinedServers.size() == 0) {
				
				//FRONTEND Aber nochmal absprechen
				user.getLinkedDiscordUser()
					.getPrivateChannel().block()
					.createMessage("No ddbot server found. If you don't join any registered ddbot server within the next 5 days, your data will be deleted").block();
			}
		}
	}
	
	private void checkTable(String tableName, String ... attributes) {
		if(!db.tableExists(tableName)) {
			SLogger.INFO.broadcast("Creating nonexistent '" + tableName + "' table.");
			try {
				db.addTable(tableName, attributes);
			} catch (Exception e) {
				e.printStackTrace();
				SLogger.FATAL.broadcast("Failed to create '" + tableName + "' table!");
			}
		}
	}
	
	public List<BotServer> getServers() {
		return servers.getCached();
	}
	
	public void buildHandler() {
		
		botClient.on(MessageCreateEvent.class).subscribe(event -> {
			if(event.getGuildId().isPresent() && event.getMember().isPresent()) {
				boolean serverPresent = false;
				for(BotServer servers : getServers()) {
					if(event.getGuildId().get().asLong() == servers.serverID.get()) {
						serverPresent = true;
						servers.onMessage(event);
						break;
					}
				}
				if(!serverPresent) {
					if(event.getMessage().getContent().startsWith(CLIENT_CMD_PREFIX)) {
						SLogger.INFO.broadcast("Executing script on non registered server " + event.getGuildId().get().asLong() + "(" + event.getGuild().block().getName() + ")");
						
						Process unregistered = new Process(true);
						ParsedVariables variables = new ParsedVariables();
						variables.server = event.getGuild().block();
						variables.invokerAsMember = event.getMember().get();
						variables.isServerAdmin = true;
						variables.channel = event.getMessage().getChannel().block();
						unregistered.includeLibrary(new UnregisteredServerCommands(this));
						unregistered.setVariable("options", variables, true, true);
						unregistered.setCaseSensitive(false);
						unregistered.execute(event.getMessage().getContent().substring(CLIENT_CMD_PREFIX.length()), false);
					}
				}
			} else {
				if(event.getMessage().getContent().startsWith(CLIENT_CMD_PREFIX)) {
					//TODO private bot message
					if(event.getMessage().getAuthor().isPresent()) {
						event.getMessage().getAuthor().get().getPrivateChannel().block().createMessage("Coming Soon ...\nEs ist noch nicht möglich mir Befehle über eine Privatnachricht zu senden.\n\nBitte sende einen Befehl für mich über einen Server auf dem ich auch bin.").block();
					}
				}
			}
		});
		
		botClient.on(MemberLeaveEvent.class).subscribe(event -> {
			for(BotServer server : getServers()) {
				if(event.getGuildId().asString().equals(server.serverID.getAsString())) server.onMemberLeave(event);
			}
			
			try {
				putUserOnLeaveList(event.getUser().getId().asLong(), event.getGuildId().asLong(), TIME_UNTIL_USER_DELETION);
			} catch (SQLException | NoSuchTableException e) {
				SLogger.FATAL.log("Failed to put member " + event.getUser().getId().asLong() + " on on-leave-list (" + e.getLocalizedMessage() + ")", event.getGuildId().asLong());
			}
			//FRONTEND notify user
			updateLinkedUsersToServers();
		});
		
		botClient.on(MemberJoinEvent.class).subscribe(event -> {
			for(BotServer server : getServers()) {
				if(event.getGuildId().asString().equals(server.serverID.getAsString())) server.onMemberJoin(event);
			}
			
			try {
				removeUserFromOnLeaveList(event.getMember().getId().asLong(), event.getGuildId().asLong());
			} catch (SQLException | NoSuchTableException e) {
				SLogger.FATAL.log("Failed to remove member " + event.getMember().getId().asLong() + " from on-leave-list (" + e.getLocalizedMessage() + ")", event.getGuildId().asLong());
			}
			
			updateLinkedUsersToServers();
		});
		
		botClient.on(ReactionAddEvent.class)
		.onErrorResume(e -> {
             return Mono.just(null);
         }).subscribe(event -> {
        	if(event == null) 
        		 return;
        	
			if(event.getGuildId().isPresent()) {
				for(BotServer server : getServers()) {
					if(event.getGuildId().get().asString().equals(server.serverID.getAsString())) server.onReactionAdded(event);
				}
			} else {
				if(!event.getUserId().asString().equals(botClient.getSelfId().asString())) {
					for(DiscordDestinyUser u : getGlobalLinkedList()) {
						if(u.discordId.getAsString().equals(event.getUserId().asString())) {
							pluginManager.triggerOnPrivateMessageReactionAdded(event, u);
							return;
						}
					}
					SLogger.WARNING.log("User added a reaction in a private channel, but user is not linked yet");
				}
			}
		});
		
		new Thread(new Runnable() {
			@Override
			public void run() {
				@SuppressWarnings("resource")
				Scanner s = new Scanner(System.in);
				SLogger.INFO.console("[Benutze '.commands' für eine Liste von Befehlen]");
				
				while(true) {
					try {
						if(SLogger.CURRENT_SCREEN <= 0) 
							SLogger.WARNING.console("No screen selected. Type [screen:list] for a list of screens to choose of");

						if(SLogger.CURRENT_SCREEN <= 0) System.out.print("#> ");
						else System.out.print(SLogger.CURRENT_SCREEN + "> ");
						String script = s.nextLine();
						adminCommandHandler.clearLibraries();
						adminCommandHandler.includeLibrary(new com.sn1pe2win.commands.NativeLibrary(BotHandler.this));
						
						adminCommandHandler.getVariables().clear();
						
						ParsedVariables variables = new ParsedVariables();
						if(SLogger.CURRENT_SCREEN > 0) {
							variables.server = botClient.getGuildById(Snowflake.of(SLogger.CURRENT_SCREEN)).block();
							variables.botServer = getBotServerById(SLogger.CURRENT_SCREEN);
							
							if(variables.botServer != null) {
								for(Plugin p : variables.botServer.getServerPlugins()) {
									try {
										Library lib = p.addLibrary();
										if(lib != null) adminCommandHandler.includeLibrary(lib);
									}  catch(NoClassDefFoundError e) {
										SLogger.ERROR.log("An error occurred while importing commands for server plugin: " + p + " Please reload this plugin if you need the commands. " + e.getLocalizedMessage());
									}
								}
							}
							
						}
						variables.invoker = botClient.getSelf().block();
						variables.isAdmin = true;
						variables.isPrivateChannel = true;
						variables.isServerAdmin = true;
						adminCommandHandler.setVariable("options", variables, true, true);
						
						adminCommandHandler.setVariable("admin", "true", true, true);
						adminCommandHandler.setCaseSensitive(false);
						adminCommandHandler.execute(script, false);
					} catch(Exception e) {
						adminCommandHandler.kill(null, "Java Exception");
						s = new Scanner(System.in);
					}
				}
			}
		}, "Admin-Console").start();
	}
	
	private Thread createUpdater() throws IllegalAccessError {
		if(running) throw new IllegalAccessError("The updater is still alive and it is not allowed to create multiple updaters");
		Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				THREAD_START_TIME = System.currentTimeMillis();
				SLogger.INFO.broadcast("Updater Thread started");
				
				while(true) {
					try {
						synchronized (this) {
							long leftFromDefaultUpdateDelay = 1 * 60000;
							long start = System.currentTimeMillis();
							try {
								Response<GlobalMessage[]> alerts = GetGlobalAlerts.getGlobalAlerts();
								
								boolean mn = false;
								if(alerts.success()) {
									for(GlobalMessage m : alerts.getResponseData()) {
										if(m.getAlertLevel() == AlertLevel.RED) {
											mn = true;
										}
									}
									
									if(mn) {
										if(!maintenance)
											SLogger.WARNING.broadcast("Bungie API under maintenance / not working - AlertLevel = RED");
										maintenance = true;
									} else {
										if(maintenance) 
											SLogger.APPROVE.broadcast("Ready to handle requests again");
										maintenance = false;
									}
								} else {
									maintenance = true;
								}
								
								if(!maintenance) {
									checkUserOnLeaveDeletion();
									
									for(BotServer s : getServers()) 
										pluginManager.triggerOnUpdate(s.getServerUser(), s);
									
									updateManager.update();
								}
								
							} catch(Exception e) {
								isUpdating = false;
								SLogger.ERROR.broadcast("Error in thread Profile Scheduler observed:" + e.getLocalizedMessage());
								e.printStackTrace();
							}
							leftFromDefaultUpdateDelay = leftFromDefaultUpdateDelay - (System.currentTimeMillis() - start);
							try {
								wait(leftFromDefaultUpdateDelay < 0 ? 1 : leftFromDefaultUpdateDelay);
							} catch (InterruptedException e) {
								isUpdating = false;
								SLogger.FATAL.broadcast("Scheduler failed to pause the thread. Attempting to restart...");
								running = false;
								createUpdater();
							}
						}
					} catch(Throwable e) {
						isUpdating = false;
						SLogger.FATAL.broadcast("Critical error in updater: " + e.getMessage() + " Attempting to restart...");
						e.printStackTrace();
						running = false;
						createUpdater();
					}
				}
			}

		}, "Profile Updater");
		t.start();
		running = true;
		return t;
	}
	
	//TODO custom exceptions
	/**Registriere den Server*/
	public boolean registerServer(long adminId, long serverId, long channelId) {
		SLogger.INFO.log("Searching for server members ...", serverId);
		//botClient.getGuild
		List<Member> members = botClient.getGuildMembers(Snowflake.of(serverId)).collectList().onErrorReturn(null).block();
		if(members == null) {
			SLogger.ERROR.log("Failed to get server members. Unable to resume", serverId);
			return false;
		}
		
		Member admin = botClient.getMemberById(Snowflake.of(serverId), Snowflake.of(adminId)).onErrorReturn(null).block();
		if(admin == null) {
			SLogger.ERROR.log("Failed to retrieve member permissions. Unable to verify administrator rights.");
			//FRONTEND "Es ist ein Fehler aufgetreten, bei dem der Benutzer nicht gefunden werden konnte"
			sendPlainMessage("Ich konnte deine Berechtigungen nicht überprüfen :(", serverId, channelId);
			return false;
		}
		
		if(!admin.getBasePermissions().block().contains(Permission.ADMINISTRATOR)) {
			SLogger.ERROR.log("Member " + adminId + " needs to be admin in order to register the server");
			//FRONTEND "Der Benutzer der den Server registrieren möchte besitzt keine Administrator Rechte"
			sendEmbedMessage(new EmbedData()
					.setTitle("Keine Berechtigung")
					.setDescription("Du musst ein Admin dieses Servers sein, um diesen Server zu registrieren"), serverId, channelId);
			return false;
		}
		
		SLogger.INFO.log("Requesting adming found on server", serverId);
		try {
			addUserServerRelation(adminId, serverId, true);
			db.query("INSERT INTO "+TABLE_SERVERS+" (serverID, channelID, lang) VALUES (" + serverId + "," + channelId + ", \"de\")");
			
			//Jetzt lade den Server aus der Datenbank
			RS_Function<BotServer> tempFunction = getDatabase().createGetFunction(BotServer.class, "SELECT * FROM " + TABLE_SERVERS + " WHERE serverID = " + serverId);
			for(BotServer s : tempFunction.get(true)) {
				SLogger.APPROVE.broadcast("Loaded new server " + s.serverID.get() + " from database and added to active list.");
				s.init(this, admin);
				servers.getCached().add(s);
			}
			SLogger.APPROVE.log("Server sucessfully registered!", serverId);
			updateLinkedUsersToServers();
			return true;
		} catch (SQLException | NoSuchTableException e) {
			SLogger.ERROR.log("Failed to add server. Unable to resume " + e.getLocalizedMessage(), serverId);
			return false;
		}
	}
	
	public Message sendEmbedMessage(EmbedData data, long serverId, long channelId) {
		SLogger.INFO.log("Sending embed message to channel " + channelId, serverId);
		Channel channel = botClient.getChannelById(Snowflake.of(channelId)).onErrorReturn(null).block();
		
		if(channel == null) {
			SLogger.ERROR.log("Channel with id " + channelId + " not found", serverId);
			return null;
		}
		
		if(data.getPlainMessageText() != null) {
			if(data.getPlainMessageText().length() > 2000) {
				SLogger.ERROR.log("Message longer than 2000 chars. Cropping");
				data.setPlainTextMessage(data.getPlainMessageText().substring(0, 2000));
			}
		}
		
		if(channel.getType() == Type.GUILD_TEXT) {
			TextChannel tchannel = botClient.getChannelById(Snowflake.of(channelId)).onErrorReturn(null).cast(TextChannel.class).block();
			return tchannel.createMessage(spec -> {
				if(data.getPlainMessageText() != null) spec.setContent(data.getPlainMessageText());
				spec.setEmbed(embed -> {
					if(data.getUrl() != null) embed.setUrl(data.getUrl());
					if(data.getAuthor() != null) embed.setAuthor(data.getAuthor(), data.getAuthorURL(), data.getAuthorIconURL());
					if(data.getColor() != null) embed.setColor(data.getColor());
					if(data.getDescription() != null) embed.setDescription(data.getDescription());
					if(data.getImageURL() != null) embed.setImage(data.getImageURL());
					if(data.getThumbnailURL() != null) embed.setThumbnail(data.getThumbnailURL());
					if(data.getTitle() != null) embed.setTitle(data.getTitle());
					if(data.getFooter() != null) embed.setFooter(data.getFooter(), data.getFooterURL());
					for(EmbedData.Field f : data.getFields()) embed.addField(f.name, f.text, f.inline);
				});
			}).block();
		
		} else if(channel.getType() == Type.GUILD_NEWS) {
			NewsChannel tchannel = botClient.getChannelById(Snowflake.of(channelId)).onErrorReturn(null).cast(NewsChannel.class).block();
			return tchannel.createMessage(spec -> {
				if(data.getPlainMessageText() != null) spec.setContent(data.getPlainMessageText());
				spec.setEmbed(embed -> {
					if(data.getUrl() != null) embed.setUrl(data.getUrl());
					if(data.getAuthor() != null) embed.setAuthor(data.getAuthor(), data.getAuthorURL(), data.getAuthorIconURL());
					if(data.getColor() != null) embed.setColor(data.getColor());
					if(data.getDescription() != null) embed.setDescription(data.getDescription());
					if(data.getImageURL() != null) embed.setImage(data.getImageURL());
					if(data.getThumbnailURL() != null) embed.setThumbnail(data.getThumbnailURL());
					if(data.getTitle() != null) embed.setTitle(data.getTitle());
					if(data.getFooter() != null) embed.setFooter(data.getFooter(), data.getFooterURL());
					for(EmbedData.Field f : data.getFields()) embed.addField(f.name, f.text, f.inline);
				});
			}).block();
		} else SLogger.WARNING.log("Unsupported channel type for message " + channel.getType(), serverId);
		return null;
	}

	/**Types are {@link TextChannel}, {@link NewsChannel}
	 * May return null*/
	public Message sendPlainMessage(String message, long serverId, long channelId) {
		SLogger.INFO.log("Sending message to channel " + channelId + ": " + message, serverId);
		
		Channel channel = botClient.getChannelById(Snowflake.of(channelId)).onErrorReturn(null).block();
		if(channel == null) {
			SLogger.ERROR.log("Channel with id " + channelId + " not found", serverId);
			return null;
		}
		if(channel.getType() == Type.GUILD_TEXT) {
			TextChannel tchannel = botClient.getChannelById(Snowflake.of(channelId)).onErrorReturn(null).cast(TextChannel.class).block();
			return tchannel.createMessage(message).block();
		} else if(channel.getType() == Type.GUILD_NEWS) {
			NewsChannel tchannel = botClient.getChannelById(Snowflake.of(channelId)).onErrorReturn(null).cast(NewsChannel.class).block();
			return tchannel.createMessage(message).block();
		} else SLogger.WARNING.log("Unsupported channel type for messaging " + channel.getType(), serverId);
		return null;
	}
	
	/**Removes a server from the server_config table.*/
	public void removeServer(long serverId) {
		try {
			for(BotServer s : servers.getCached()) {
				if(s.serverID.getAsString().equals(String.valueOf(serverId))) {
					servers.getCached().remove(s);
					break;
				}
			}
			
			SLogger.INFO.log("Server found and deleted from active list. Trying to delete database entries ...", serverId);
			
			int rows = getDatabase().queryUpdate("DELETE FROM " + TABLE_SERVERS + " WHERE serverID = " + serverId);
			if(rows > 0) SLogger.APPROVE.log("Server deleted from " + TABLE_SERVERS + " table", serverId);
			else SLogger.WARNING.broadcast("Failed to delete server " + serverId + ": Not present");
		
		} catch (SQLException | NoSuchTableException e) {
			SLogger.ERROR.broadcast("Failed to delete server (SQL fault): " + e.getLocalizedMessage());
		}
	}
	
	/**@return Returns an active and ready BotServer instance. May return null if the id is not found*/
	public BotServer getBotServerById(long id) {
		for(BotServer server : servers.getCached()) {
			if(server.isReady()) {
				if(server.serverID.get() == id) return server;
			}
		}
		return null;
	}
	
	void deleteUserServerRelation(long serverID, long userID) throws SQLException, NoSuchTableException {
		db.query("DELETE FROM " + TABLE_SERVER_MEMBER + " WHERE serverID = " + serverID + " AND userID = " + userID);
	}
	
	/**Löscht alle Daten eines Users von einem Server.
	 * Aktuell betrifft das nur server plugin Daten*/
	public void deleteServerUserData(long userId, long serverId) {
		try {
			pluginManager.deleteServerUserPluginData(userId, serverId);
		} catch (SQLException | NoSuchTableException e) {
			SLogger.FATAL.broadcast("Failed to delete server plugin data. Please delete manually");
		}
	}
	
	/**Löscht alle globalen Daten eines Users. Globale Plugindaten und Triumphe, sowie alle verlinkungen.
	 * Server Plugindaten werden ebenfalls gelöscht. Server userdaten werden ebenfalls gelöscht*/
	public void deleteUserData(long userId) {
		
		SLogger.INFO.broadcast("Deleting global userdata for user " + userId + " ...");
		for(DiscordDestinyUser user : GLOBAL_LINKED.getCached()) {
			if(user.discordId.get() == userId) {
				SLogger.INFO.broadcast("Requested user removal from update list ...");
				updateManager.unlinkUser(user);
				SLogger.INFO.broadcast("Removed from global linked list ...");
				GLOBAL_LINKED.getCached().remove(user);	
				SLogger.INFO.broadcast("Unlinked user ...");
				updateManager.unlinkUser(user);
				SLogger.INFO.broadcast("Deleted Triumphs ...");
				triumphManager.deleteUserTriumphs(user);
				break;
			}
		}
		try {
			pluginManager.deleteGlobalUserPluginData(userId);
			SLogger.INFO.broadcast("Deleted global plugin data");
		} catch (SQLException | NoSuchTableException e1) {
			SLogger.FATAL.broadcast("Failed to delete global plugin data. Please delete manually");
		}
		
		//Datenbankeinträge endgültig entfernen
		try {
			getDatabase().queryUpdate("DELETE FROM " + TABLE_LINKEDUSER + " WHERE userID = " + userId);
		} catch (SQLException | NoSuchTableException e) {
			e.printStackTrace();
		}
	}
	
	
	@Deprecated
	/**Löscht einen User inklusive aller seiner Triumphe. GEFÄHRLICH!*/
	public void deleteUser(DiscordDestinyUser user) {
		
		GLOBAL_LINKED.getCached().remove(user);
		
		//TODO fertig machen plugin user data löschen
		updateManager.unlinkUser(user);
		
		//Datenbankeinträge endgültig entfernen
		try {
			getDatabase().queryUpdate("DELETE FROM " + TABLE_LINKEDUSER + " WHERE userID = " + user.discordId.getAsString());
		} catch (SQLException | NoSuchTableException e) {
			e.printStackTrace();
		}
		
		triumphManager.deleteUserTriumphs(user);
	}
	
	/**Heavy operation. Only use this when really nessecary
	 * @return All the servers the user is linked on. Automatically cleans up invalid entries or duplicate entries*/
	BotServer[] getServerRelationsFromUser(long userID) {
		
		class BotServerRelation {
			boolean isAdmin = false;
			BotServer server;
		}
		
		ArrayList<BotServerRelation> serversFromUser = new ArrayList<>();
		for(BotServer servers : this.servers.getCached()) {
			Member member = null;
			
			try {
				member = botClient.getMemberById(Snowflake.of(servers.serverID.get()), Snowflake.of(userID))
				.onErrorReturn(ClientException.isStatusCode(404), null).block();
			} catch(Exception e) {}
			
			if(member != null) {
				BotServerRelation r = new BotServerRelation();
				r.server = servers;
				r.isAdmin = servers.getAdmin().getId().asString().equals(member.getId().asString());
				serversFromUser.add(r);
			}
		}
		
		try {
			getDatabase().query("DELETE FROM " + TABLE_SERVER_MEMBER + " WHERE userID = " + userID);
		} catch (SQLException | NoSuchTableException e) {
			e.printStackTrace();
		}
		if(serversFromUser.size() == 0) return new BotServer[] {};
		
		String values = "";
		for(BotServerRelation r : serversFromUser) 
			values += "(" + userID + ", " + r.server.serverID.get() + ", " + (r.isAdmin ? "1" : "0") + "), ";
		if(serversFromUser.size() > 0) values = values.substring(0, values.length()-2);
		try {
			getDatabase().query("INSERT INTO " + TABLE_SERVER_MEMBER + " (userID, serverID, admin) VALUES " + values);
		} catch (SQLException | NoSuchTableException e) {
			e.printStackTrace();
		}
		ArrayList<BotServer> server = new ArrayList<>();
		for(BotServerRelation r : serversFromUser) 
			server.add(r.server);
		return server.toArray(new BotServer[server.size()]);
	}
	
	void addUserServerRelation(long userID, long serverID, boolean admin) throws SQLException, NoSuchTableException {
		db.query("INSERT INTO "+ TABLE_SERVER_MEMBER + " (userID, serverID, admin) VALUES (" + userID + ", " + serverID + ", " + (admin ? "1" : "0") + ")");
	}
	
	public void addGlobalLinkedUser(DiscordDestinyUser user) {
		for(DiscordDestinyUser u : GLOBAL_LINKED.getCached()) {
			if(u.destinyId.getAsString().equals(user.destinyId.getAsString())) {
				SLogger.ERROR.broadcast("Failed to add user to global list: Already present");
				return;
			}
		}
		GLOBAL_LINKED.getCached().add(user);
		updateLinkedUsersToServers();
		updateManager.linkUser(user);
	}
	
	/**DIE LISTE NICHT MODIFIZIEREN!!!*/
	public List<DiscordDestinyUser> getGlobalLinkedList() {
		return GLOBAL_LINKED.getCached();
	}
	
	public File getDatabaseFile() {
		return databaseFile;
	}
	
	public SimpleDatabase getDatabase() {
		return db;
	}
	
	public GatewayDiscordClient getBot() {
		return botClient;
	}
	
	public boolean isReady() {
		return ready;
	}
	
	public List<LogEntry> getScreens() {
		if(screens == null) screens = db.createGetFunction(LogEntry.class, "SELECT DISTINCT screen_id FROM screenlogs");
		return screens.get(true);
	}
	
	public boolean isAPIMaintenance() {
		return maintenance;
	}
}
