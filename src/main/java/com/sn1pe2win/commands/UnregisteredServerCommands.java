package com.sn1pe2win.commands;

import com.devkev.devscript.raw.Block;
import com.devkev.devscript.raw.Command;
import com.devkev.devscript.raw.Library;
import com.devkev.devscript.raw.Process;
import com.sn1pe2win.main.BotHandler;
import com.sn1pe2win.main.EmbedData;

/**Basic commands für unregistrierte Server und Clients ohne listener*/
public class UnregisteredServerCommands extends Library {

	public BotHandler botHandler;
	
	public UnregisteredServerCommands(BotHandler botHandler) {
		super("Unregistered Server Commands");
		this.botHandler = botHandler;
	}

	@Override
	public Command[] createLib() {
		return new Command[] {
				new Command("register", "", "") {

					@Override
					public Object execute(Object[] arg0, Process arg1, Block arg2) throws Exception {
						ParsedVariables var = (ParsedVariables) arg1.getVariable("options", arg2);
						
						if(botHandler.registerServer(var.invokerAsMember.getId().asLong(), var.server.getId().asLong(), var.channel.getId().asLong())) {
							botHandler.sendEmbedMessage(new EmbedData()
									.setTitle("Thank you")
									.setDescription("Your server is registered and members can now link their profile. You can change the language setting later"), var.server.getId().asLong(), var.channel.getId().asLong());
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
