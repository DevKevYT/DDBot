package com.sn1pe2win.main;

import discord4j.core.event.domain.guild.MemberJoinEvent;
import discord4j.core.event.domain.guild.MemberLeaveEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.event.domain.message.ReactionAddEvent;

public interface ServerEvents {
	
	public void onMemberJoin(MemberJoinEvent event);
	
	public void onMemberLeave(MemberLeaveEvent event);
	
	public void onMessage(MessageCreateEvent event);
	
	public void onReactionAdded(ReactionAddEvent event);
	
}
