package com.sn1pe2win.commands;

import com.sn1pe2win.main.BotServer;
import com.sn1pe2win.user.DiscordDestinyUser;

import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.MessageChannel;

/**Just to work better with command variables*/
public class ParsedVariables {
	
	public Guild server = null;
	public MessageChannel channel = null;
	public User invoker = null;
	public Member invokerAsMember = null;
	public boolean isAdmin = false;
	public boolean isServerAdmin = false;
	public boolean isPrivateChannel = false;
	public BotServer botServer = null;
	public Message originMessage = null;
	public DiscordDestinyUser linkedUser = null;
	
//	public ParsedVariables parse(Process process) {
//		Object var_server = process.getVariable("server", process.getMain());
//		Object var_channel = process.getVariable("channel", process.getMain());
//		Object var_invoker = process.getVariable("invoker", process.getMain());
//		Object var_invokerAsMember = process.getVariable("invokerAsMember", process.getMain());
//		Object var_isAdmin = process.getVariable("admin", process.getMain());
//		Object var_isPrivateChannel = process.getVariable("privateChannel", process.getMain());
//		
//		server = var_server instanceof Guild ? (Guild) var_server : null;
//		channel = var_channel instanceof TextChannel ? (MessageChannel) var_channel : null;
//		invoker = var_invoker instanceof User ? (User) var_invoker : null;
//		invokerAsMember = var_invokerAsMember instanceof Member ? (Member) var_invokerAsMember : null;
//		isAdmin = var_isAdmin instanceof Boolean ? (Boolean) var_isAdmin : null;
//		isPrivateChannel = var_isPrivateChannel instanceof Boolean ? (Boolean) var_isPrivateChannel : null;
//		
//		return this;
//	}
}
