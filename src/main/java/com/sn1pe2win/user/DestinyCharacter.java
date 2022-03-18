package com.sn1pe2win.user;

import java.util.Date;

public class DestinyCharacter {
	
	int lightlevel = 0;
	String emblemPath = "";
	long characterId = 0;
	Date dateLastPlayed = new Date(0);
	int minutesPlayedLastSession = 0;
	int minutesPlayedTotal = 0;
	
	public int getLightlevel() {
		return lightlevel;
	}
	
	public String getEmblemPath() {
		return emblemPath;
	}
	
	public long getCharacterId() {
		return characterId;
	}
	
	public Date getDateLastPlayed() {
		return dateLastPlayed;
	}
	
	public int getMinutesPlayedLastSession() {
		return minutesPlayedLastSession;
	}
	
	public int getMinutesPlayedTotal() {
		return minutesPlayedTotal;
	}
}
