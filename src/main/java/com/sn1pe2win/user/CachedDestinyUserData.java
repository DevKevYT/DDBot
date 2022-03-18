package com.sn1pe2win.user;

import java.util.Date;

import com.sn1pe2win.definitions.MembershipType;

/**Hält leichte Daten die möglichst viel extra querying verhindern sollen.<br>
 * */
public class CachedDestinyUserData {
	
	private final DiscordDestinyUser user;
	
	Date cacheLastUpdated = null;
	
	boolean isOnline = false;
	
	String displayName = "";
	Date dateLastplayed = new Date(0);
	long[] characterIds = new long[] {};
	int currentSeasonRewardPowercap = 0;
	
	long totalEP = 0;
	int currentSeasonLevel = 0;
	int powerbonus = 0;
	
	MembershipType[] memberships = new MembershipType[] {};
	MembershipType crossSaveOverride = MembershipType.NONE;
	
	DestinyCharacter[] characters;
	
	/**Daten einer Pipe können hier eingespeißt werden*/
	public CachedDestinyUserData(DestinyUserDataPipeline pipe) {
		this.user = pipe.user;
	}
	
	public MembershipType getCrossSaveOverride() {
		return crossSaveOverride;
	}
	
	public String getDisplayName() {
		return displayName;
	}
	
	public Date getDateLastPlayed() {
		return dateLastplayed;
	}
	
	public long[] getCharacterIds() {
		return characterIds;
	}
	
	public int getCurrentSeasonPowercap() {
		return currentSeasonRewardPowercap;
	}
	
	public long getTotalEPEarnedThisSeason() {
		return totalEP;
	}
	
	public int getCurrentSeasonLevel() {
		return currentSeasonLevel;
	}
	
	public int getPowerbonus() {
		return powerbonus;
	}
	
	DestinyCharacter[] getCharacters() {
		return characters;
	}
	
	public DestinyCharacter getLastPlayedCharacter() {
		if(cacheLastUpdated == null) return null;
		
		long record = 0;
		
		DestinyCharacter[] c =  characters;
		DestinyCharacter chosen = c[0];
		
		for(int i = 0; i < c.length; i++) {
			try {
				if(c[i] != null) {
					if(c[i].getDateLastPlayed().getTime() > record) {
						chosen = c[i];
						record = chosen.getDateLastPlayed().getTime();
					}
				}
			}catch(Exception e) {
			}
		}
		return chosen;
	}
	
	public DestinyCharacter getHighestCharacter() {
		if(cacheLastUpdated == null) return null;
		
		int record = 0;
		DestinyCharacter[] c =  characters;
		DestinyCharacter chosen = c[0];
		for(int i = 0; i < c.length; i++) {
			if(c[i] != null) {
				if(c[i].lightlevel > record) {
					chosen = c[i];
					if(chosen == null) continue;
					record = chosen.lightlevel;
				}
			}
		}
		return chosen;
	}
	
	public DiscordDestinyUser getUser() {
		return user;
	}
}
