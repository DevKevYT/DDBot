package com.sn1pe2win.managers;

import java.util.ArrayList;

import com.sn1pe2win.logging.SLogger;
import com.sn1pe2win.main.BotHandler;
import com.sn1pe2win.user.DiscordDestinyUser;

public class UpdateCellManager {
	
	private BotHandler handler;
	
	/**Maximale Anzahl an threads die pro Update generiert werden dürfen.*/
	public final int updateThreadThreshold;
	
	private ArrayList<UpdateCell> cells;
	
	public UpdateCellManager(int updateThreadThreshold, BotHandler handler) {
		this.updateThreadThreshold = updateThreadThreshold;
		this.handler = handler;
		
		cells = new ArrayList<>(updateThreadThreshold + 1);
		
		//Erstelle update cells
		int pofilesPerUpdate = (handler.getGlobalLinkedList().size() / updateThreadThreshold) + 1; //Wie viele Profile in einem Thread behandelt werden
		
		if(pofilesPerUpdate == 0) { //Fewer profiles than threshold. Just create one thread
			SLogger.INFO.broadcast("Only one slice needed for " + handler.getGlobalLinkedList().size() + " at a threshold of " + updateThreadThreshold);
		} else {
			int sliceIndex = 0;
			
			for(int i = 0; i < updateThreadThreshold; i++) {
				ArrayList<DiscordDestinyUser> slice = new ArrayList<>();
				for(int j = sliceIndex; j < sliceIndex + pofilesPerUpdate; j++) {
					if(j < handler.getGlobalLinkedList().size()) slice.add(handler.getGlobalLinkedList().get(j));
					else break;
				}
				sliceIndex += slice.size();
				UpdateCell cell = new UpdateCell(handler.pluginManager);
				for(DiscordDestinyUser d : slice) 
					cell.addAssignedUser(d);
				cells.add(cell);
//				if(cell.getAssignedCount() == 0)
//					SLogger.WARNING.log("Cell " + cell.CELL_ID + " empty.");
			}
		}
	}
	
	/**Entfernt den user von der update liste. Der Name ist etwas irreführend*/
	public void unlinkUser(DiscordDestinyUser user) {
		for(UpdateCell c : cells) {
			for(int i = 0; i < c.assigned.size(); i++) {
				if(c.assigned.get(i).discordId.getAsString().equals(user.discordId.getAsString())) {
					c.removeAssignedUser(user);
					break;
				}
			}
		}
	}
	
	public void update() {
		for(UpdateCell cells : cells) 
			cells.update();
	}
	
	public BotHandler getHandler() {
		return handler;
	}
	
	/**Fügt einen user zu der update Liste hinzu. Der Name ist etwas irreführend*/
	public void linkUser(DiscordDestinyUser user) {
		UpdateCell best = null;
		for(UpdateCell cell : cells) {
			if(best == null) best = cell;
			if(!cell.equals(best)) {
				if(cell.getAssignedCount() < best.getAssignedCount())
					best = cell;
			}
		}
		if(best != null) 
			best.addAssignedUser(user);
		else SLogger.FATAL.broadcast("Failed to find good cell to add user. Failed to link");
	}
}
