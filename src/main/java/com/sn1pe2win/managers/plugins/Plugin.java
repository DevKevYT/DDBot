package com.sn1pe2win.managers.plugins;

import java.io.File;
import java.util.ArrayList;

import com.devkev.devscript.raw.Library;
import com.sn1pe2win.api.Handshake;
import com.sn1pe2win.config.dataflow.Node;
import com.sn1pe2win.main.BotServer;
import com.sn1pe2win.main.BotServer.ConfigModification;
import com.sn1pe2win.managers.Version;
import com.sn1pe2win.managers.triumphs.TriumphManager;
import com.sn1pe2win.sql.simpledb.RS_Function;
import com.sn1pe2win.sql.simpledb.SimpleDatabase;
import com.sn1pe2win.user.DiscordDestinyUser;

/**Um ein Plugin zu implementieren, sollte eine beliebige Klasse der jar datei von dieser Klasse erben*/
public abstract class Plugin {
	
	/**Der score definiert, ob pro user ein Plugin nur einmal global ausgeführt wird oder ob das Plugin für jeden Server einzeln ausgeführt wird.*/
	public enum Scope {
		GLOBAL, //Das Plugin wird für jeden user einmal ausgeführt pro update durchlauf ohne server spezifikation
		SERVER //Das Plugin wird für jedem Server für jeden user einmal ausgeführt
	}
	
	final Version currentVersion = Version.of("4.1.0");
	
	public Scope scope = Scope.SERVER;
	
	File file;
	Class<?> classData;
	
	/**Wenn eine .jar mehrere Klassen hat die {@link Plugin} erben. Diese Liste beinhaltet auch die eigene Klasse*/
	Plugin[] neighbors;
	/**True, if multiple plugin classes are in the same file. Neighbors can be found in {@link Plugin#neighbors}*/
	boolean fileAmbigous = false;
	
	/**Für welche user der space schon generiert wurde*/
	ArrayList<Long> globalUserDataSpaceGenerated = new ArrayList<Long>();
	ArrayList<ServerUserDataSpace> serverUserDataSpaceGenerated = new ArrayList<>();
	
	/**Nur geladen wenn das Plugin geladen wurde*/
	TriumphManager triumphManager;
	PluginManager pluginManager;
	
	/**This can be used to distinguish between different plugin versions and to check, if the most recent plugin version is running!*/
	public Version VERSION = Version.of("1.0");
	
	//Ser Globale space bezieht sich auf Daten eines Users die Serverübergreifend genutzt werden können
	//Der Server space bezieht sich auf user data welche von jedem Server selber nur benutzt werden.
	//Bei Triumphen bietet isch also ein globaler scope an, da ein Inventar Item nicht auf mehreren Servern gespeichert werden muss
	//sondern in einem gemeinsamen space.
	
	/**Never called if the scope relies on server. Called everytime this plugin gets loaded on a server*/
	void checkEsistingUserSpaces(SimpleDatabase database) {
		if(scope == Scope.GLOBAL) {
			globalUserDataSpaceGenerated.clear();
			RS_Function<RS_UserData> data = database.createGetFunction(RS_UserData.class, "SELECT DISTINCT userID from " + PluginManager.TABLE_GLOBAL_USERDATA + " WHERE origin = \"" + getFileName() + "\"");
			for(RS_UserData d : data.get(true)) 
				globalUserDataSpaceGenerated.add(d.userID.get());
		} else if(scope == Scope.SERVER) {
			serverUserDataSpaceGenerated.clear();
			RS_Function<RS_UserData> data = database.createGetFunction(RS_UserData.class, "SELECT DISTINCT userID, serverID from " + PluginManager.TABLE_SERVER_USERDATA + " WHERE origin = \"" + getFileName() + "\"");
			for(RS_UserData d : data.get(true)) {
				ServerUserDataSpace s = new ServerUserDataSpace();
				s.serverId = d.serverID.get();
				s.userId = d.userID.get();
				serverUserDataSpaceGenerated.add(s);
			}
		}
	}
	
	public Plugin() {
		scope = Scope.SERVER;
	}
	
	public final File getFile() {
		return file;
	}
	
	public final String getFileName() {
		return file.getName();
	}
	
	public String toString() {
		return file.getName() + " $" + classData.getName() + ".class" + " (Ver: " + VERSION + ")";
	}
	
	public final TriumphManager getTriumphManager() {
		return triumphManager;
	}
	
	/**server can be null of the plugin scope is global*/
	public void modifyUserConfig(DiscordDestinyUser user, BotServer server, ConfigModification modification) {
		if(scope == Scope.GLOBAL) {
			Node ref = pluginManager.getGlobalUserConfig(this, user);
			modification.modify(ref);
			pluginManager.modifyGlobalConfig(this, user, ref);
			
		} else if(scope == Scope.SERVER && server != null) {
			Node ref = pluginManager.getServerUserConfig(this, user, server);
			modification.modify(ref);
			pluginManager.modifyServerUserConfig(this, user, ref, server);
		}
	}
	
	
	//TODO Dokumentation
	public abstract Scope getScope();
	
	public abstract Library addLibrary();
	
	/**Server specific. Not affected by scope*/
	public abstract void onPluginLoad(EventClasses.PluginLoadEvent event);
	
	/**Server specific. Not affected by scope*/
	public abstract void onPluginRemove(EventClasses.PluginRemoveEvent event);
	
	/**User specific. Affected by scope. If {@link Scope} == GLOBAL, {@link EventClasses.DiscordDestinyUserUpdate#getServer()} = null!*/
	public abstract void onDestinyMemberUpdate(EventClasses.DiscordDestinyUserUpdate userUpdate);
	
	/**Server specific. Not affected by scope. Updates occur every ~5 minutes. You may want to create extra threads for better usage*/
	public abstract void onServerUpdate(EventClasses.ServerUpdate update);
	
	/**Server specific. Not affected by scope*/
	public abstract void onMessageRecieved(EventClasses.MessageRecievedEvent event);
	
	/**Server specific. Not affected by scope*/
	public abstract void onCommandExecuted(EventClasses.CommandExecutedEvent event);
	
	/**Server specific. Not affected by scope*/
	public abstract void onMemberJoin(EventClasses.MemberjoinEvent event);
	
	/**Server specific. Not affected by scope*/
	public abstract void onMemberLeave(EventClasses.MemberLeaveEvent event);
	
	/**Member specific. Affected by scope*/
	public abstract void onTriumphAquired(EventClasses.TriumphGivenEvent event);
	
	/**only triggered on server messages.
	 * If this plugin has a global scope this function is also fired on private messages.
	 * Please keep in mind that the server fiel may be null if the plugin scope is global*/
	public abstract void onReactionAdded(EventClasses.ReactionAddedEvent event);
	
	/**User specific. Affected by scope
	 * Pretty unique function. Executed, when the {@link Handshake#success(String, discord4j.core.object.entity.Message)} or {@link Handshake#error(String)}
	 * is called in the //link command in {@link DefaultCommands}*/
	public abstract void onMemberLinked(EventClasses.MemberLinkedEvent event);
}
