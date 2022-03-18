package com.sn1pe2win.managers.triumphs;

public class UserTriumphProgress {
	
	final boolean completed;
	final long progress;
	final String progressID;
	final long userID;
	final RS_UserTriumph databaseEntry;
	
	public UserTriumphProgress(boolean completed, long progress, String progressID, long userID, RS_UserTriumph databaseEntry) {
		this.completed = completed;
		this.progress = progress;
		this.progressID = progressID;
		this.userID = userID;
		this.databaseEntry = databaseEntry; //null wenn nicht in Datenbank
	}
}
