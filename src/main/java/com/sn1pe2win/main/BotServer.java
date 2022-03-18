package com.sn1pe2win.main;

import com.sn1pe2win.api.Handshake.OAuthResponseData;
import com.sn1pe2win.commands.ClientCommands;
import com.sn1pe2win.commands.ParsedVariables;
import com.sn1pe2win.config.dataflow.Node;
import com.sn1pe2win.config.dataflow.Parser;
import com.sn1pe2win.config.dataflow.Variable;
import com.sn1pe2win.definitions.MembershipType;
import com.sn1pe2win.logging.SLogger;
import com.sn1pe2win.main.Exceptions.DestinyAccountAlreadyLinked;
import com.sn1pe2win.main.Exceptions.NoChangesToLinkedAccount;
import com.sn1pe2win.managers.Lang.Language;
import com.sn1pe2win.managers.plugins.Plugin;
import com.sn1pe2win.managers.plugins.PluginManager;
import com.sn1pe2win.sql.simpledb.Exceptions.NoSuchTableException;
import com.sn1pe2win.sql.simpledb.MappedVar;
import com.sn1pe2win.sql.simpledb.RS_Function;
import com.sn1pe2win.sql.simpledb.RS_Row;
import com.sn1pe2win.user.DiscordDestinyUser;

import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.guild.MemberJoinEvent;
import discord4j.core.event.domain.guild.MemberLeaveEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.event.domain.message.ReactionAddEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.spec.EmbedCreateSpec;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.devkev.devscript.raw.ApplicationListener;
import com.devkev.devscript.raw.Library;
import com.devkev.devscript.raw.Output;
import com.devkev.devscript.raw.Process;
import discord4j.rest.util.Color;

public class BotServer extends RS_Row implements ServerEvents {
	
	public interface ConfigModification {
		
		public void modify(Node node);
	
	}
	
	private static long screenIdCounter = 0;
	
	public final long screen_id;
	
	public MappedVar<Long> serverID = new MappedVar<Long>("serverID");
	public MappedVar<Long> mainChannelID = new MappedVar<Long>("channelID");
	public MappedVar<String> language = new MappedVar<String>("lang");
	public MappedVar<String> customConfigData = new MappedVar<String>("custom_config");
	
	/**Plugins, die aus dem global {@link PluginManager#getPlugins()} herausgenommen wurden und auch für diesen Server benutzt werden*/
	private ArrayList<Plugin> serverPlugins = new ArrayList<>();
	private Node customConfig;
	private Language LANGUAGE;
	
	private static final ClientCommands clientCommands;
	
	private BotHandler host;
	private Guild guild;
	
	private Member serverAdmin;

	private volatile boolean isDead = false;
	private volatile boolean ready;
	
	static {
		clientCommands = new ClientCommands();
	}
	
	public BotServer() {
		screen_id = screenIdCounter;
		screenIdCounter ++;
	}
	
	public boolean init(BotHandler host, Member serverAdmin) {
		this.host = host;
		this.serverAdmin = serverAdmin;
		
		try {
			guild = host.getBot().getGuildById(Snowflake.of(serverID.get())).onErrorReturn(null).block();
		} catch(Exception e) {
			SLogger.ERROR.log("Failed to initialize botserver instance. Server with ID " + serverID.get() + " not present. Got kicked?", serverID.get());
			host.removeServer(serverID.get());
			return false;
		}
		
		SLogger.INFO.log("Veryfied server " + guild.getName() + " ...", serverID.get());
		
		SLogger.INFO.log("Reading custom config data ...", serverID.get());
		customConfig = Parser.parse(customConfigData.get());	
		
		//Ready wenn kritische Prozesse angearbeitet wurden
		ready = true;
		
		SLogger.INFO.log("Loading plugins ...", serverID.get());
		
		Variable plugins = customConfig.get("plugins");
		if(!plugins.isUnknown() && plugins.isArray()) {
			for(String s : plugins.getAsArray()) {
				host.pluginManager.loadPlugin(new File(s), this);
			}
		}
		SLogger.APPROVE.log(serverPlugins.size() + " Plugin(s) loaded / pulled from pool.", serverID.get());
		
		SLogger.INFO.log("Setting server language ...", serverID.get());
		LANGUAGE = Language.of(language.getAsString());
	
		return true;
	}

	@Override
	public void onMemberJoin(MemberJoinEvent event) {
		host.pluginManager.triggerOnMemberJoinEvent(event, this);
		host.triumphManager.checkAppropriateRolesForUser(event.getMember().getId().asLong(), this);
	}

	@Override
	public void onMemberLeave(MemberLeaveEvent event) {
		host.pluginManager.triggerOnMemberLeaveEvent(event, this);
	}

	@Override
	public void onMessage(MessageCreateEvent event) {
		try {
			Message message = event.getMessage();
			
			//members = event.getClient().getGuildMembers(Snowflake.of(node.getData().serverID)).buffer().blockLast();
			host.pluginManager.triggerOnServerMessageRecieved(event, this);
			
			if(!message.getAuthor().get().equals(host.getBot().getSelf().block()) && message.getContent().startsWith(BotHandler.CLIENT_CMD_PREFIX)) {
				MessageChannel channel = message.getChannel().block();
				
				if(!isReady()) {
					//FRONTEND 
					channel.createEmbed(spec -> {
						spec.setTitle("Nicht so schnell!");
						spec.setDescription("Ich wurde gerade erst neu gestartet.\nBitte gedulde dich einen Moment.");
						spec.setColor(Color.RED);
					}).block();
					return;
				}
				
				String content = message.getContent().substring(BotHandler.CLIENT_CMD_PREFIX.length());
				SLogger.INFO.log("Recieved script to execute from " + message.getAuthor().get().getId().asString() + ": " + content, serverID.get());
				
				Process commandHandler = new Process(true);
				commandHandler.clearLibraries();
				commandHandler.maxRuntime = 3000;
				commandHandler.includeLibrary(clientCommands);
				for(Plugin p : serverPlugins) {
					Library lib = p.addLibrary();
					if(lib != null) commandHandler.includeLibrary(lib);
				}
				
				bindProcessToChannel(commandHandler, channel);
				
				commandHandler.getVariables().clear();
				
				
				final ParsedVariables variables = new ParsedVariables();
				variables.server = guild;
				variables.channel = message.getChannel().block();
				variables.invoker = message.getAuthor().get();
				variables.invokerAsMember = message.getAuthorAsMember().block();
				variables.isAdmin = variables.invoker.getId().asString().equals(serverAdmin.getId().asString());
				variables.isPrivateChannel = false;
				variables.botServer = this;
				variables.originMessage = event.getMessage();
				variables.isServerAdmin = variables.invokerAsMember.getId().asString().equals(serverAdmin.getId().asString()) ? true : false;
				
				for(DiscordDestinyUser u : host.getGlobalLinkedList()) {
					if(u.discordId.getAsString().equals(variables.invoker.getId().asString())) {
						variables.linkedUser = u;
						break;
					}
				}
				
				commandHandler.setVariable("options", variables, true, true);
				
				commandHandler.setCaseSensitive(false);
				commandHandler.execute(content, true);
				
				commandHandler.setApplicationListener(new ApplicationListener() {
					public void done(int arg0) {
						System.gc();
						//pluginmgr.triggerOnCommandExecuted(event, p, arg0);
					}
				});
				//pluginmgr.triggerOnCommandRecieved(event, p);
			}
		} catch(Exception e) {
			e.printStackTrace();
			SLogger.ERROR.log("Failed to handle the message event " + e.getMessage(), serverID.get());
		}
	}
	
	public Message postMessageInMainChannel(EmbedData data) {
		return host.sendEmbedMessage(data, serverID.get(), mainChannelID.get());
	}
	
	public Message postMessageInMainChannel(String plainMessage) {
		return host.sendPlainMessage(plainMessage, serverID.get(), mainChannelID.get());
	}
	
	public void bindProcessToChannel(Process process, MessageChannel channel) {
		process.getOutput().clear();
		process.addOutput(new Output() {
			@Override
			public void warning(String arg0) {}
			@Override
			public void log(String arg0, boolean arg1) {
				Message botmessage;
				
				if(arg0.isEmpty()) {
					Object embed = process.getVariable("embed", process.getMain());
					
					if(embed == null) {
						SLogger.WARNING.log("A embed post was requested (empty message), but no embed was specified as process Variable \"embed\"", serverID.get());
						return;
					}
					
					if(!(embed instanceof EmbedData)) {
						SLogger.WARNING.log("Unable to convert " + embed.getClass().getTypeName() + " to " + EmbedCreateSpec.class.getTypeName(), serverID.get());
						return;
					}
					
					botmessage = channel.createEmbed(spec -> {
						EmbedData data = (EmbedData) embed;
						if(data.getUrl() != null) spec.setUrl(data.getUrl());
						if(data.getAuthor() != null) spec.setAuthor(data.getAuthor(), data.getAuthorURL(), data.getAuthorIconURL());
						if(data.getColor() != null) spec.setColor(data.getColor());
						if(data.getDescription() != null) spec.setDescription(data.getDescription());
						if(data.getImageURL() != null) spec.setImage(data.getImageURL());
						if(data.getThumbnailURL() != null) spec.setThumbnail(data.getThumbnailURL());
						if(data.getTitle() != null) spec.setTitle(data.getTitle());
						if(data.getFooter() != null) spec.setFooter(data.getFooter(), data.getFooterURL());
						for(EmbedData.Field f : data.getFields()) 
							spec.addField(f.name, f.text, f.inline);
					}).block();
					
				} else botmessage = channel.createMessage(arg0).block();
				
				process.setVariable("last-bot-message", botmessage, false, false);
				process.removeVariable("embed");
			}
			
			@Override
			public void error(String arg0) {
				if(arg0.isEmpty()) return;
				SLogger.WARNING.log("ERROR while executing command: " + arg0, serverID.get());
				
				if(!arg0.contains("No such command")) {
					channel.createEmbed(spec -> {
						spec.setTitle("Huch :(");
						spec.setDescription("Ein Fehler ist aufgetreten");
						spec.setFooter("Mehr Details:\n" + arg0, "http://bohrmaschinengang.de/errorcode.png");
						spec.setColor(Color.RED);
					}).block();
				} else {
					channel.createEmbed(spec -> {
						spec.setTitle("Diesen Befehl kenne ich nicht");
						spec.setDescription("Für eine Liste aller Befehle, gib //help ein.");
						spec.setFooter("Weitere Details:\n" + arg0, "http://bohrmaschinengang.de/errorcode.png");
						spec.setColor(Color.RED);
					}).block();
				}
			}
		});
	}
	
	/**Unlinks the current linked destiny 2 account from an user from this server. 
	 * Only if the user got unlinked from all servers he is removed from te GLOBAL_LINKED list*/
	public void unlinkUser(Member discordMember) {
		try {
			host.deleteUserServerRelation(serverID.get(), discordMember.getId().asLong());
		} catch (SQLException | NoSuchTableException e) {
			SLogger.ERROR.log("Failed to remove user server relation with user " + discordMember.getId().asLong() + " to this server. Retrying soon.", serverID.get());
			e.printStackTrace();
		}
	}
	
	//Eigentlich gehört diese Methode in BotHandler kLasse. Aber der übersicht wegen behalte ich sie erst einmal hier
	public DiscordDestinyUser linkUser(User discordMember, long destinyId, MembershipType platform, OAuthResponseData credentials) 
			throws SQLException, NoSuchTableException, DestinyAccountAlreadyLinked, NoChangesToLinkedAccount {
		
		for(DiscordDestinyUser user : host.getGlobalLinkedList()) {
			if(!user.discordId.getAsString().equals(discordMember.getId().asString()) && user.destinyId.getAsString().equals(String.valueOf(destinyId))) 
				throw new DestinyAccountAlreadyLinked("Dieser Destiny 2 Account ist schon mit jemanden anderen verlinkt.");
		}
		
		for(DiscordDestinyUser user : host.getGlobalLinkedList()) {
			if(user.discordId.getAsString().equals(discordMember.getId().asString()) && !user.destinyId.getAsString().equals(String.valueOf(destinyId))) {
				SLogger.INFO.log("User requests a account change to destiny 2 account: " + destinyId, serverID.get());
				host.getDatabase().queryUpdate("UPDATE " 
						+ BotHandler.TABLE_LINKEDUSER 
						+ " SET membershipID = " + destinyId 
						+ ", access_token = \"" + credentials.accessToken + "\""
						+ ", token_type = \"" + credentials.tokenType + "\""
						+ ", expires_in = " + (System.currentTimeMillis() + credentials.expires * 1000)
						+ ", refresh_token = " + credentials.refreshToken
						+ ", refresh_expires_in = " + (System.currentTimeMillis() + credentials.refreshExpiresIn * 1000)
						+ ", membership_id = " + credentials.bungieMembership
				+ "\" WHERE userID = " + discordMember.getId().asString());
				//TODO create a "hard" update where triumphs etc. get affected. Also wipe the custom data column
				return user;
			} else if(user.discordId.getAsString().equals(discordMember.getId().asString()) && user.destinyId.getAsString().equals(String.valueOf(destinyId))) 
				throw new NoChangesToLinkedAccount("Keine Änderungen an der bestehenden Verlinkung erkannt. Keine Änderungen wirksam");
		}
		
		//This user should be present in the "server_member" table. If not add him
		host.addUserServerRelation(discordMember.getId().asLong(), serverID.get(), false);
		
		host.getDatabase().query("INSERT INTO " + BotHandler.TABLE_LINKEDUSER + "(userID, membershipID, platform, access_token, token_type, refresh_token, expires_in, refresh_expires_in, membership_id)" 
				+ "VALUES (" 
				+ discordMember.getId().asString() + ", " 
				+ destinyId + ", " 
				+ platform.id + ", " 
				+ "\"" + credentials.accessToken + "\", "
				+ "\"" + credentials.tokenType + "\", "
				+ "\"" + credentials.refreshToken + "\", "
				+ (System.currentTimeMillis() + credentials.expires * 1000) + ", "
				+ (System.currentTimeMillis() + credentials.refreshExpiresIn * 1000) + ","
				+ credentials.bungieMembership + ")");
		
		SLogger.INFO.log("User registered in database with primary key " + discordMember.getId().asString(), serverID.get());
		
		//Lade den Benutzer aus der Datenbank, da die mappedVars immutable sind
		RS_Function<DiscordDestinyUser> func = host.getDatabase().createGetFunction(DiscordDestinyUser.class, "SELECT * FROM " + BotHandler.TABLE_LINKEDUSER 
				+ " WHERE userID = " + discordMember.getId().asLong());
		
		List<DiscordDestinyUser> list = func.get(false);
		if(list.size() == 1) {
			list.get(0).init(discordMember, getHost()); //The init function will take care of additional tables like user_triumphs
			SLogger.INFO.log("Adding user to global list ...");
			host.addGlobalLinkedUser(list.get(0));
			getHost().pluginManager.triggerOnMemberLinked(platform, destinyId, discordMember, list.get(0));
			return list.get(0);
		}
		return null;
	}
	
	public void updateDatabase() {
		customConfigData.set(customConfig.print(false));
		
		super.updateRow("WHERE serverID = " + serverID.get());
	}
	
	/**DO NOT EDIT THE "plugins" VARIABLE!*/
	public void modifyConfig(ConfigModification spec) {
		spec.modify(customConfig);
		updateDatabase();
	}
	
	public Guild getGuild() {
		return guild;	
	}
	
	public Member getAdmin() {
		return serverAdmin;
	}
	
	public boolean isReady() {
		if(host != null)
			return this.ready && !isDead();
		else return this.ready && !isDead();
	}
	
	public boolean isDead() {
		return isDead;
	}
	
	public Language getLang() {
		return LANGUAGE;
	}
	
	public long getMainChannelId() {
		return mainChannelID.get();
	}
	
	public void setLanguage(Language lang) {
		this.LANGUAGE = lang;
		language.set(lang.ISO);
	}
	
	public String toString() {
		return serverID.getAsString();
	}
	
	/**@deprecated
	 * Bitte {@link PluginManager#loadPlugin(File, BotServer)} benutzern.*/
	public void loadServerPlugins(File ... plugin) {
		ArrayList<String> newValue = new ArrayList<String>();
		for(Plugin current : serverPlugins) newValue.add(current.getFile().getAbsolutePath());
		
		main: for(File f : plugin) {
			for(Plugin p : serverPlugins) {
				if(p.getFileName().equals(f.getName())) continue main;
			}
			
			ArrayList<Plugin> newPlugins = host.pluginManager.loadPlugin(f, this);
			for(Plugin newPluign : newPlugins) {
				serverPlugins.add(newPluign);
				SLogger.APPROVE.log("Adding plugin " + newPluign + " to server.", serverID.get());
			}
			
			if(newPlugins.size() > 0) //Nur das erste in die Pfadliste hinzufügen, da der Pfad eh gleich ist
				newValue.add(newPlugins.get(0).getFile().getAbsolutePath());
		}
		
		customConfig.addArray("plugins", newValue.toArray(new String[newValue.size()]));
		updateDatabase();
	}
	
	/**@deprecated
	 * Bitte {@link PluginManager#removeServerPlugin(String, BotServer)} benutzen.<br>*/
	public void removeServerPluginsFromConfig(Plugin ... plugins) {
		for(Plugin p : plugins) {
			boolean found = false;
			for(Plugin server : serverPlugins) {
				if(server.getFileName().equals(p.getFileName())) {
					found = true;
					break;
				}
			}
			if(found) {
				SLogger.APPROVE.log("Plugin removed from server " + serverID.get());
				customConfig.get("plugins").removeArrayEntry(p.getFile().getAbsolutePath());
				serverPlugins.remove(p);
			}
		}
	}
	
	public ArrayList<DiscordDestinyUser> getServerUser() {
		ArrayList<DiscordDestinyUser> joined = new ArrayList<>();
		for(DiscordDestinyUser global : host.getGlobalLinkedList()) {
			for(BotServer s : global.joinedServers) {
				if(s.serverID.getAsString().equals(this.serverID.getAsString())) joined.add(global);
			}
		}
		return joined;
	}
	
	/**NICHT MODIFIZIEREN!!! ICH BEISSE!*/
	public ArrayList<Plugin> getServerPlugins() {
		return serverPlugins;
	}

	@Override
	public void onReactionAdded(ReactionAddEvent event) {
		getHost().pluginManager.triggerOnReactionAdded(event, this);
	}
	
	public BotHandler getHost() {
		return host;
	}
	
	public Language getLanguage() {
		return LANGUAGE;
	}
}
