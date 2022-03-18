package com.sn1pe2win.managers.plugins;

import com.devkev.devscript.raw.Process;
import com.sn1pe2win.definitions.MembershipType;
import com.sn1pe2win.main.BotHandler;
import com.sn1pe2win.main.BotServer;
import com.sn1pe2win.managers.plugins.PluginManager.PluginSubroutine;
import com.sn1pe2win.managers.triumphs.TriumphCreationSpec;
import com.sn1pe2win.managers.triumphs.TriumphStepCreationSpec;
import com.sn1pe2win.user.DestinyUserDataPipeline;
import com.sn1pe2win.user.DiscordDestinyUser;

import discord4j.core.event.domain.guild.MemberJoinEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.event.domain.message.ReactionAddEvent;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.User;

public interface EventClasses {
	
	public abstract class Event {
		
		BotServer server;
		/**May be null*/
		DiscordDestinyUser user;
		
		public final BotServer getServer() {
			return server;
		}
		
		/**Null if the plugin scope is set to GLOBAL*/
		public final DiscordDestinyUser getUser() {
			return user;
		}
	}
	
	public class PluginLoadEvent extends Event {
	}
	
	public class PluginRemoveEvent extends Event {
		boolean deleteData;
		
		/**Returns true if it was requested to also delete generated data by this plugin.
		 * If true, please also delete custom data*/
		public boolean deleteData() {
			return deleteData;
		}
	}
	
	public class DiscordDestinyUserUpdate extends Event {
		
		DestinyUserDataPipeline userdata;
		
		User globalUser;
		Member serverUser; //Ist NULL wenn der Plugin scope auf Global gestellt ist
		PluginSubroutine subroutine;
		long updateId;
		
		/**Wenn die gecachten Daten noch nicht geladen wurden, kann es sein, dass diese Funktion etwas länger zum ausführen braucht*/
		public DestinyUserDataPipeline getDestiny2UserData() {
			return userdata;
		}
		
		public final User getGlobalUser() {
			return globalUser;
		}
		
		/**May be null depending on plugin scope*/
		public final Member getServerUser() {
			return serverUser;
		}
		
		public final PluginSubroutine getSubroutine() {
			return subroutine;
		}
		
		public final long getUpdateId() {
			return updateId;
		}
	}

	public class MessageRecievedEvent extends Event {
		
		MessageCreateEvent event;
		Process process;
		
		public MessageCreateEvent getMessageEvent() {
			return event;
		}
		
		public boolean isCommand() {
			return event.getMessage().getContent().startsWith(BotHandler.CLIENT_CMD_PREFIX);
		}
		
//		public Process getExecuter() {
//			return process;
//		}
	}
	
	public class CommandExecutedEvent extends Event {
		
		MessageCreateEvent event;
		int exitCode;
		
		public MessageCreateEvent getMessageEvent() {
			return event;
		}
		
		public int getExitCode() {
			return exitCode;
		}
	}
	
	public class MemberjoinEvent extends Event {
		
		MemberJoinEvent event;
		
		public MemberJoinEvent getDiscordEvent() {
			return event;
		}
	}
	
	public class MemberLeaveEvent extends Event {
		
		discord4j.core.event.domain.guild.MemberLeaveEvent event;
		
		public discord4j.core.event.domain.guild.MemberLeaveEvent getDiscordEvent() {
			return event;
		}
	}
	
	public class MemberLinkedEvent extends Event {
		
		public long destinyMembershipId = -1;
		public MembershipType chosen = MembershipType.NONE;
		public User requestingUser;
		

		public User getRequestingUser() {
			return requestingUser;
		}

		public MembershipType getRequestetPlatform() {
			return chosen;
		}
		
		public long getDestinyMembershipId() {
			return destinyMembershipId;
		}
	}
	
	public class ServerUpdate extends Event {
		
		//TODO Steuern auf was der Plugin ersteller zugreifen darf, da das hier globale Daten sind
		DiscordDestinyUser[] users;
		
		public DiscordDestinyUser[] getUsers() {
			return users;
		}
	}
	
	public class TriumphGivenEvent extends Event {
		
		TriumphCreationSpec data;
		TriumphStepCreationSpec currentStep;
		
		public TriumphCreationSpec getCurrentTriumph() {
			return data;
		}
		
		public TriumphStepCreationSpec getCurrentStep() {
			return currentStep;
		}
	}
	
	public class ReactionAddedEvent extends Event {
		
		ReactionAddEvent event;
		
		public ReactionAddEvent getReactionEvent() {
			return event;
		}
	}
}
