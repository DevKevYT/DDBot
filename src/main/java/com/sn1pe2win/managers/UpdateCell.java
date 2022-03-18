package com.sn1pe2win.managers;

import java.util.ArrayList;

import com.sn1pe2win.logging.SLogger;
import com.sn1pe2win.managers.plugins.PluginManager;
import com.sn1pe2win.user.DestinyUserDataPipeline;
import com.sn1pe2win.user.DiscordDestinyUser;

public class UpdateCell {

	private class Request {
		DiscordDestinyUser user;
		boolean add; //Wenn true, der Benutzer soll hinzugefügt werden. Wenn false der Benutzer soll entfernt werden
	}
	
	private static int cellIds = 0;
	
	private long updateId = 0;
	public final int CELL_ID;
	
	private final Thread thread;
	
	ArrayList<DiscordDestinyUser> assigned = new ArrayList<>(); 
	ArrayList<Request> waitingQueue = new ArrayList<>();
	
	private volatile boolean locked = false;
	private volatile boolean killed = false;
	private volatile long iteration = 0;
	
	@SuppressWarnings("unused")
	private PluginManager manager;
	
	//TODO Versuche die Recourcen immer gleichmäßig auf alle Zellen aufzuteilen
	public UpdateCell(PluginManager manager) {
		CELL_ID = cellIds;
		cellIds ++;
		this.manager = manager;
		
		thread = new Thread(new Runnable() {
			@Override
			public void run() {
				while(!killed) {
					synchronized (thread) {
						try {
							thread.wait();
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
					
					try {
						iteration ++;
						
						locked = true;
						
						for(DiscordDestinyUser user : assigned) {
							manager.triggerOnDestinyMemberUpdate(new DestinyUserDataPipeline(user, UpdateCell.this), user, iteration);
						}
						
						updateId ++;
						locked = false;
						
						for(Request req : waitingQueue) {
							SLogger.APPROVE.broadcast("User " + req.user.discordId.getAsString() + (req.add ? " added to update list." : " removed from update list"));
							if(req.add) addAssignedUser(req.user);
							else removeAssignedUser(req.user);
						}
						waitingQueue.clear();
						
						locked = false;
						
					} catch(Exception e) {
						SLogger.ERROR.broadcast("An unexpected error happened in cell " + CELL_ID + ": " + e.getLocalizedMessage());
						e.printStackTrace();
						locked = false;
					}
				}
			}
		});
		
		thread.setName("Update Cell " + CELL_ID);
		thread.start();
	}
	
	/**Assigns the user once the cell is not locked*/
	public boolean addAssignedUser(DiscordDestinyUser user) {
		if(!isLocked()) {
			SLogger.INFO.log("Assigned user " + user.discordId.getAsString() + " to cell " + CELL_ID);
			assigned.add(user);
			return true;
		} else {
			SLogger.INFO.broadcast("Added user " + user.discordId.getAsString() + " to waiting queue ...");
			Request r = new Request();
			r.add = true;
			r.user = user;
			waitingQueue.add(r);
			return false;
		}
	}
	
	public boolean removeAssignedUser(DiscordDestinyUser user) {
		if(!isLocked()) {
			assigned.remove(user);
			SLogger.INFO.log("Deleted user from update list");
			return true;
		} else {
			SLogger.INFO.broadcast("Added user " + user.discordId.getAsString() + " to remove waiting queue ...");
			Request r = new Request();
			r.add = false;
			r.user = user;
			waitingQueue.add(r);
			return false;
		}
	}
	
	public synchronized boolean isLocked() {
		return locked;
	}
	
	public long getUpdateId() {
		return updateId;
	}
	
	public synchronized void kill() {
		killed = true;
	}
	
	public int getAssignedCount() {
		return assigned.size() + waitingQueue.size();
	}
	
	public void update() {
		if(locked) {
			SLogger.WARNING.broadcast("Cell [" + toString() + "] is still locked");
			return;
		}
		
		updateId ++;
		if(assigned.size() == 0) return;
		
		synchronized (thread) {
			thread.notify();
		}
	}
	
	public String toString() {
		return "ID: " + CELL_ID + " Iter: " + iteration + " Assigned: " + getAssignedCount();
	}
}
