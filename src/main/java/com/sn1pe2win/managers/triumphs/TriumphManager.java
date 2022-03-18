package com.sn1pe2win.managers.triumphs;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.sn1pe2win.logging.SLogger;
import com.sn1pe2win.main.BotHandler;
import com.sn1pe2win.main.BotServer;
import com.sn1pe2win.managers.plugins.Plugin;
import com.sn1pe2win.managers.plugins.PluginManager;
import com.sn1pe2win.managers.triumphs.Specs.TriumphCheckpointCreationSpec;
import com.sn1pe2win.managers.triumphs.TriumphCreationSpec.TriumphType;
import com.sn1pe2win.sql.simpledb.RS_Function;
import com.sn1pe2win.sql.simpledb.Exceptions.NoSuchTableException;
import com.sn1pe2win.user.DiscordDestinyUser;

import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Role;
import discord4j.rest.util.Color;

public class TriumphManager {
	
	public static final String TABLE_TRIUMPHS = "triumphs";
	public static final String TABLE_USER_TRIUMPHS = "user_triumph";
	public static final String TABLE_TRIUMPH_OBJECTIVES = "triumph_objectives";
	public static final String TABLE_TRIUMPH_STEP = "triumph_step";
	
	private BotHandler host;
	
	public TriumphManager(BotHandler host) throws Exception {
		this.host = host;
		
		//Checke tabellen
		SLogger.INFO.broadcast("Checking triumph tables ...");
		if(!host.getDatabase().tableExists(TABLE_TRIUMPHS)) {
			SLogger.INFO.broadcast("Table " + TABLE_TRIUMPHS + " not found. Creating ...");
			host.getDatabase().addTable(TABLE_TRIUMPHS, "progressID", "name", "deactivated", "origin", "color", "category", "description", "iconURL");
		}
		if(!host.getDatabase().tableExists(TABLE_USER_TRIUMPHS)) {
			SLogger.INFO.broadcast("Table " + TABLE_USER_TRIUMPHS + " not found. Creating ...");
			host.getDatabase().addTable(TABLE_USER_TRIUMPHS, "userID", "progressID", "progress", "completed");
		}
		if(!host.getDatabase().tableExists(TABLE_TRIUMPH_STEP)) {
			SLogger.INFO.broadcast("Table " + TABLE_TRIUMPH_STEP + " not found. Creating ...");
			host.getDatabase().addTable(TABLE_TRIUMPH_STEP, "progressID", "requirement", "display", "force_role", "points", "displayName", "display_type");
		}
		if(!host.getDatabase().tableExists(TABLE_TRIUMPH_OBJECTIVES)) {
			SLogger.INFO.broadcast("Table " + TABLE_TRIUMPH_OBJECTIVES + " not found. Creating ...");
			host.getDatabase().addTable(TABLE_TRIUMPH_OBJECTIVES, "parentID", "objectiveID");
		}
	}
	
	/**Wenn ein Triumph mehrere Schritte hat wird der entsprechende hier zurückgeliefert.<br>
	 * Wenn ein Triumph nur ein Schritt besitzt wird dieser zurückgegeben.
	 * ACHTUNG: Wenn bei einem Triumph die Erwartung kleiner ist als das Minimum, dann wird null zurüggegeben!!*/
	private TriumphStepCreationSpec getTriumphStepByRequirement(String progressID, long progress) {
		//TriumphCreationSpec spec = TriumphCreationSpec.parse(progressID, this);
		RS_Function<RS_TriumphStep> fsteps = host.getDatabase().createGetFunction(RS_TriumphStep.class, "SELECT * FROM " + TABLE_TRIUMPH_STEP + " WHERE progressID = \"" + progressID + "\"");
		List<RS_TriumphStep> steps = fsteps.get(true);
		
		if(steps.size() == 1) {
			if(progress >= steps.get(0).requirement.get()) {
				return TriumphStepCreationSpec.parse(steps.get(0));				
			} else return null;
		}
		
		
		RS_TriumphStep closest = null;
		RS_TriumphStep lowest = steps.get(0);
		for(RS_TriumphStep step : steps) {
			if(closest == null && progress >= step.requirement.get()) closest = step;
			if(closest != null) {
				if(Math.abs(progress - step.requirement.get()) < Math.abs(progress - closest.requirement.get())) 
					closest = step;
			}
			if(closest == null && step.requirement.get() < lowest.requirement.get()) 
				lowest = step;
		}
		
		if(closest == null) closest = lowest;
		
		if(progress < lowest.requirement.get()) {
			return null;
		}
		return TriumphStepCreationSpec.parse(closest);
	}
	
	public void deleteUserTriumphs(DiscordDestinyUser user) {
		try {
			host.getDatabase().queryUpdate("DELETE FROM " + TABLE_USER_TRIUMPHS + " WHERE userID = " + user.discordId.getAsString());
		} catch (SQLException | NoSuchTableException e) {
			e.printStackTrace();
		}
	}
	
	/**@return All steps a specific triumph has*/
//	private TriumphStepCreationSpec[] getStepsFromProgressID(String progressID) {
//		RS_Function<RS_TriumphStep> fsteps = host.getDatabase().createGetFunction(RS_TriumphStep.class, "SELECT * FROM " + TABLE_TRIUMPH_STEP + " WHERE progressID = \"" + progressID + "\"");
//		List<RS_TriumphStep> steps = fsteps.get(true);
//		TriumphStepCreationSpec[] arr = new TriumphStepCreationSpec[steps.size()];
//		for(int i = 0; i < steps.size(); i++) 
//			arr[i] = TriumphStepCreationSpec.parse(steps.get(i));
//		return arr;
//	}
	
	public UserTriumphProgress getProgressForMember(DiscordDestinyUser user, String progressID) {
		RS_Function<RS_UserTriumph> fuserTriumphs = host.getDatabase().createGetFunction(RS_UserTriumph.class, "SELECT * FROM " + TABLE_USER_TRIUMPHS + " WHERE progressID = \"" + progressID + "\"");
		fuserTriumphs.get(false);
		if(fuserTriumphs.getCached().size() == 0) {
			return new UserTriumphProgress(false, 0, progressID, user.discordId.get(), null);
		} else return new UserTriumphProgress(fuserTriumphs.getCached().get(0).completed.get(), 
				fuserTriumphs.getCached().get(0).progress.get(), 
				fuserTriumphs.getCached().get(0).progressID.get(),
				fuserTriumphs.getCached().get(0).discordID.get(), fuserTriumphs.getCached().get(0));
	}
	
//	@Deprecated
	public TriumphCreationSpec getTriumph(String progressID) {
		return TriumphCreationSpec.parse(progressID, this);
	}
	
	public void increaseProgressForMember(DiscordDestinyUser user, String progressID, int amount) {
		setProgressForMember(user, progressID, getProgressForMember(user, progressID).progress + amount, false);
	}
	
	public void setProgressForMember(DiscordDestinyUser user, String progressID, long progress, boolean complete) {
		
		UserTriumphProgress prev = getProgressForMember(user, progressID);
		
		if(prev.databaseEntry == null) {
			//Checke of die progressID existiert
			if(host.getDatabase().createGetFunction(RS_Triumph.class, "SELECT progressID FROM " + TABLE_TRIUMPH_STEP + " WHERE progressID = \"" + progressID + "\"").get(true).size() == 1) {
				RS_UserTriumph userTriumph = new RS_UserTriumph(); 
				userTriumph.completed.set(complete);
				userTriumph.discordID.set(user.discordId.get());
				userTriumph.progress.set((long) 0);
				userTriumph.progressID.set(progressID);
				host.getDatabase().addEntry(TABLE_USER_TRIUMPHS, userTriumph); 
			} else SLogger.ERROR.console("Triumph with progressID " + progressID + " does not exist");
		}
		
		TriumphCreationSpec data = getTriumph(progressID);
		
		if(progress == prev.progress) return;
		
		boolean triggerNotification = false;
		if(data.getTriumphtype() == TriumphType.SIMPLE || data.getTriumphtype() == TriumphType.STEPS) {
			
			int prevStepsCompleted = 0;
			int currentStepsCompleted = 0;
			for(TriumphStepCreationSpec s : data.steps) {
				if(prev.progress >= s.requirement)
					prevStepsCompleted++;
				if(progress >= s.requirement)
					currentStepsCompleted++;
			}
			if(currentStepsCompleted > prevStepsCompleted) {
				triggerNotification = true;
			}
			
		} else if(data.getTriumphtype() == TriumphType.CHECKLIST) {
			for(TriumphCheckpointCreationSpec objectives : data.objectives) {
				if(objectives.progressID.equals(progressID) && progress >= objectives.requirement) {
					triggerNotification = true;
					break;
				}
			}
		}
		
		if(triggerNotification && !data.isDeactivated()) {
			//Der user hat einen Schritt abgeschlossen oder den ganzen Trumph
			try {
				host.getDatabase().queryUpdate("UPDATE " + TABLE_USER_TRIUMPHS + " SET progress = " + progress + ", completed = " + (complete ? "1" : "0") + " WHERE progressID = \"" + progressID + "\"");
			} catch (SQLException | NoSuchTableException e) {
				e.printStackTrace();
			}
			
			TriumphStepCreationSpec current = getTriumphStepByRequirement(progressID, progress);
			if(current != null)
				host.pluginManager.triggerOnMemberTriumph(data, current, user);
		}
		
		
		if(progress > prev.progress && data.isDeactivated()) return;
		try {
			host.getDatabase().queryUpdate("UPDATE " + TABLE_USER_TRIUMPHS + " SET progress = " + progress + ", completed = " + (complete ? "1" : "0") + " WHERE progressID = \"" + progressID + "\"");
		} catch (SQLException | NoSuchTableException e) {
			e.printStackTrace();
		}
		
		for(BotServer server : user.joinedServers) {
			checkAppropriateRolesForUser(user.discordId.get(), server);
		}
	}
	
	/**Löscht einen Triumph von der Datenbank. Wird üblicherweise von {@link PluginManager#removeServerPlugin(String, BotServer)} gehandled<br>
	 * und nur aufgerufen wenn kein Server dieses Plugin mehr besitzt.
	 * @param origin - Der Dateiname des Plugins*/
	public void deleteTriumph(String origin) {
		
		ArrayList<String> affectedPIDs = new ArrayList<String>();
		
		RS_Function<RS_Triumph> t = host.getDatabase().createGetFunction(RS_Triumph.class, "SELECT progressID, origin FROM " + TABLE_TRIUMPHS + " WHERE origin = \"" + origin + "\"");
		for(RS_Triumph triumph : t.get(true)) 
			affectedPIDs.add(triumph.progressId.get()); //Man muss nicht für duplikate checken weil progressID ein primärschlüssen in der Tabelle ist
		
		if(t.getCached().isEmpty()) return;
		
		String objectiveQuery = " WHERE ";
		String stepsQuery = " WHERE ";
		for(int i = 0; i < affectedPIDs.size(); i++) {
			objectiveQuery += "parentID = \"" + affectedPIDs.get(i) + "\"" + (i < affectedPIDs.size()-1 ? " OR " : "");
			stepsQuery += "progressID = \"" + affectedPIDs.get(i) + "\"" + (i < affectedPIDs.size()-1 ? " OR " : "");
		}
		
		//TODO Error catching
		//Clear all roles from all servers
		RS_Function<RS_TriumphStep> steps = host.getDatabase().createGetFunction(RS_TriumphStep.class, "SELECT progressID FROM " + TABLE_TRIUMPH_STEP + " " + stepsQuery);
		for(BotServer servers : host.getServers()) {
			roles: for(Role fuckThis : servers.getGuild().getRoles().collectList().block()) {
				for(RS_TriumphStep s : steps.get(true)) {
					if(fuckThis.getName().equals(s.displayName.get())) {
						fuckThis.delete("Triumph step removed").block();
						break roles;
					}
				}
			}
		}
		
		RS_Function<RS_TriumphObjective> objectives = host.getDatabase().createGetFunction(RS_TriumphObjective.class, "SELECT * FROM " + TABLE_TRIUMPH_OBJECTIVES + " " + objectiveQuery);
		for(RS_TriumphObjective obj : objectives.get(false)) 
			affectedPIDs.add(obj.objectiveId.get());
		
		SLogger.INFO.broadcast("Deleting triumphs from plugin " + origin + "! " + affectedPIDs.size() + " affected Progress IDs found");
		
		String conditionQuery = " WHERE ";
		for(int i = 0; i < affectedPIDs.size(); i++) {
			conditionQuery += "progressID = \"" + affectedPIDs.get(i) + "\"" + (i < affectedPIDs.size()-1 ? " OR " : "");
		}
		try {
			host.getDatabase().queryUpdate("DELETE FROM " + TABLE_TRIUMPHS + conditionQuery + ";"
					+ "DELETE FROM " + TABLE_TRIUMPH_STEP + conditionQuery + "; DELETE FROM " + TABLE_TRIUMPH_OBJECTIVES + objectiveQuery);
		} catch (SQLException | NoSuchTableException e) {
			e.printStackTrace();
		}
	}
	
	
	/**Request to add the Triumph on the specific server.<br>*/
	public boolean addTriumph(BotServer server, Plugin origin, TriumphCreationSpec spec) {
		if(spec.steps.size() == 0) throw new IllegalArgumentException("A triumph can't have zero steps!");
		
		SLogger.INFO.log("Trying to add the triumph " + spec.progressId + " (Type: " + spec.getTriumphtype() + ") to server ...", server.serverID.get());
		
		if(!server.isReady()) {
			SLogger.ERROR.log("Requesting server dead or not ready to create triumphs.", server.serverID.get());
			return false;
		}
		
		RS_Function<RS_Triumph> test = host.getDatabase().createGetFunction(RS_Triumph.class, "SELECT progressID, origin FROM " + TABLE_TRIUMPHS + " WHERE progressID = \"" + spec.progressId + "\"");
		test.get(true);
		test.lockToCache();
		
		if(test.get(true).size() > 0) {
			if(test.get(true).get(0).origin.get().equals(origin.getFileName())) {
				SLogger.INFO.log("Triumph with the same origin already exists. Trigger Updating ...", server.serverID.get());
				return true;
			} else {
				SLogger.ERROR.log("The triumph with the progress ID " + spec.progressId + " already exists in plugin " + origin.getFileName() + " Unable to create triumph: Already exists", server.serverID.get());
			}
			return false;
		} //else createServerRole(server, spec);
		
		spec.origin = origin.getFileName();
		//Beginne den Triumph in der Datenbank zu registrieren
		host.getDatabase().addEntry(TABLE_TRIUMPHS, spec.buildTriumphData());
		//Lese die steps aus, falls es welche gibt. Ansonsten checke die andere Liste
		if(spec.getTriumphtype() == TriumphType.SIMPLE || spec.getTriumphtype() == TriumphType.STEPS) {
			RS_TriumphStep[] steps = spec.buildStepData();
			for(RS_TriumphStep s : steps) 
				host.getDatabase().addEntry(TABLE_TRIUMPH_STEP, s);
		}
		
		if(spec.getTriumphtype() == TriumphType.CHECKLIST) {
			host.getDatabase().addEntry(TABLE_TRIUMPH_STEP, spec.steps.get(0).build(spec.progressId));
			
			RS_TriumphStep[] steps = spec.buildChecklistData();
			for(RS_TriumphStep s : steps) {
				host.getDatabase().addEntry(TABLE_TRIUMPH_STEP, s);
				//Füge in die Beziehungstabelle hinzu
				RS_TriumphObjective objective = new RS_TriumphObjective();
				objective.parentId.set(spec.progressId);
				objective.objectiveId.set(s.progressId.get());
				host.getDatabase().addEntry(TABLE_TRIUMPH_OBJECTIVES, objective);
			}
		}
		
		if(spec.getTriumphtype() == TriumphType.UNKNOWN) 
			SLogger.ERROR.log("Unable to verify triumph type. Unable to add to database!", server.serverID.get());
		
		SLogger.INFO.log("Updating server relations ...", server.serverID.get());
		return false;
	}
	
	/**Überprüft, ob alle Triumphrollen  dem Benutzerfortschritt entsprechen. Schwere Operation*/
	public void checkAppropriateRolesForUser(long userId, BotServer server) {
		Member member = server.getGuild().getMemberById(Snowflake.of(userId)).block();
		List<Role> serverRoles = server.getGuild().getRoles().collectList().block();
		
		RS_Function<RS_TriumphStep> all = host.getDatabase().createGetFunction(RS_TriumphStep.class, "SELECT displayName, force_role FROM " + TABLE_TRIUMPH_STEP + " WHERE force_role = 1");
		for(RS_TriumphStep step : all.get(true)) {
			List<Role> roles = member.getRoles().collectList().block();
			for(Role r : roles) {
				if(r.getName().equals(step.displayName.get())) {
					member.removeRole(r.getId(), "Triumph Role Refresh - Removeall").block();
				}
			}
		}
		
		for(UserTriumphProgress progress : getProgressFromMember(userId)) {
			TriumphStepCreationSpec spec = getTriumphStepByRequirement(progress.progressID, progress.progress);
			
			if(spec != null) {
				if(spec.forceRole) {
					//SLogger.INFO.log("Granting role " + current.displayName + " for user " + user.discordId.get(), server.serverID.get());
					if(!checkForRolePresent(spec.displayName, server)) {
						SLogger.INFO.log("Role not present. Creating ...", server.serverID.get());
						Role role = createServerRole(server, spec.displayName, getTriumph(progress.progressID).color);
						member.addRole(role.getId(), "Triumph Role Refresh - Add needed").block();
						return;
					}
					for(Role r : serverRoles) {
						if(r.getName().equals(spec.displayName)) {
							member.addRole(r.getId(), "Triumph Role Refresh - Add needed").block();
						}
					}
				}
			}
		}
	}
	
	/**Gibt alle Triumphe vom Benutzer zurück*/
	public UserTriumphProgress[] getProgressFromMember(long userId) {
		RS_Function<RS_UserTriumph> function = host.getDatabase().createGetFunction(RS_UserTriumph.class, "SELECT * FROM " + TABLE_USER_TRIUMPHS + " WHERE userID = " + userId);
		ArrayList<UserTriumphProgress> progress = new ArrayList<>();
		
		for(RS_UserTriumph t : function.get(true)) {
			progress.add(new UserTriumphProgress(t.completed.get(), 
					t.progress.get(), 
					t.progressID.get(),
					t.discordID.get(), t));
		}
		return progress.toArray(new UserTriumphProgress[progress.size()]);
	}
	
	private Role createServerRole(BotServer server, String name, Color color) {
		SLogger.INFO.log("Creating server role " + name + " DO NOT RENAME!", server.serverID.get());
		return server.getGuild().createRole(spec -> {
			spec.setColor(color);
			spec.setName(name);
			spec.setReason("Generated by ddbot.top for triumph step " + name);
		}).block();
	}
	
	private boolean checkForRolePresent(String rolename, BotServer server) {
		for(Role r : server.getGuild().getRoles().collectList().block()) {
			if(r.getName().equals(rolename)) {
				return true;
			}
		}
		return false;
	}
	
	public BotHandler getHandler() {
		return host;
	}
	
}
