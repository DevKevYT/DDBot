package com.sn1pe2win.user;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;

import com.sn1pe2win.DestinyEntityObjects.PlayerActivity;
import com.sn1pe2win.DestinyEntityObjects.Profile.CharacterComponent;
import com.sn1pe2win.DestinyEntityObjects.Profile.DestinyProfileComponent;
import com.sn1pe2win.DestinyEntityObjects.Profile.ProfileProgressionComponent;
import com.sn1pe2win.core.Gateway;
import com.sn1pe2win.core.Response;
import com.sn1pe2win.definitions.MembershipType;
import com.sn1pe2win.endpoints.GetProfile;
import com.sn1pe2win.managers.UpdateCell;

/**Temporär geladene Destiny 2 User Informationen als Pipeline*/
public class DestinyUserDataPipeline {
	
	/**Um maintenance zu handlen oder sonstiges.
	 * Wird global angewandt*/
	public static int BUNGIE_SERVER_STATUS = Gateway.STATUS_OK;
	
	public final GetProfile profile;
	public final DiscordDestinyUser user;
	public final UpdateCell cell;
	
	//Ist nur temporär geladen. May be null
	public DestinyProfileComponent profileData;
	public CharacterComponent characterData;
	public ProfileProgressionComponent progression;
	
	public DestinyUserDataPipeline(DiscordDestinyUser user, UpdateCell cell) {
		this.user = user;
		this.cell = cell;
		profile = new GetProfile(MembershipType.of(((short) (int) user.platform.get())), user.destinyId.get());
		
		boolean fullUpdate = true;
		
		//Cache schonmal Daten und stelle in der Pipeline gleichzeitig schonmal frische Daten bereit
		Response<DestinyProfileComponent> profileData = profile.getDestinyProfileComponent();
		if(profileData.success()) {
			this.profileData = profileData.getResponseData();
			
			//Präpariere gecachte Daten
			user.cachedData = new CachedDestinyUserData(this);
			user.cachedData.displayName = this.profileData.getUserInfo().getDisplayName();
			try {
				user.cachedData.dateLastplayed = this.profileData.getDateLastPlayed();
			} catch (ParseException e) {}
			user.cachedData.characterIds = this.profileData.getCharacterIds();
			user.cachedData.currentSeasonRewardPowercap = this.profileData.getCurrentSeasonRewardPowerCap();
			user.cachedData.memberships = this.profileData.getUserInfo().getApplicableMembershipTypes();
			user.cachedData.crossSaveOverride = this.profileData.getUserInfo().getCrossSaveOverride();
			
		} else fullUpdate = false;
		
		
		Response<CharacterComponent> characters = profile.getCharacterComponent();
		if(characters.success()) {
			this.characterData = characters.getResponseData();
			user.cachedData.characters = new DestinyCharacter[this.characterData.getCharacters().length];
			for(int i = 0; i < user.cachedData.characters.length; i++) {
				user.cachedData.characters[i] = new DestinyCharacter();
				user.cachedData.characters[i].characterId = this.characterData.getCharacters()[i].getCharacterId();
				try {
					user.cachedData.characters[i].dateLastPlayed = this.characterData.getCharacters()[i].getDateLastPlayed();
				} catch (ParseException e) {}
				user.cachedData.characters[i].emblemPath = this.characterData.getCharacters()[i].getEmblemPath();
				user.cachedData.characters[i].lightlevel = this.characterData.getCharacters()[i].getLightLevel();
				user.cachedData.characters[i].minutesPlayedLastSession = this.characterData.getCharacters()[i].getMinutesPlayedLastSession();
				user.cachedData.characters[i].minutesPlayedTotal = this.characterData.getCharacters()[i].getMinutesPlayedTotal();
			}
			
		} else fullUpdate = false;
		
		Response<ProfileProgressionComponent> progression = profile.getProfileProgression();
		if(progression.success()) {
			this.progression = progression.getResponseData();
			user.cachedData.powerbonus = this.progression.getSeasonalArtifact().getPowerbonus();
			user.cachedData.totalEP = this.progression.getSeasonalArtifact().getPointProgression().getCurrentProgress();
		} else fullUpdate = false;
		
		if(fullUpdate) user.cachedData.cacheLastUpdated = new Date(System.currentTimeMillis());
		
		BUNGIE_SERVER_STATUS = Gateway.getStatus();
	}
	
	public Response<PlayerActivity[]> loadRaidHistory(boolean onlySuccessfull) {
		Response<PlayerActivity[]> activity = profileData.getActivityHistory(4, 0);
		if(activity.success()) {
			if(!onlySuccessfull) return activity;
			else {
				ArrayList<PlayerActivity> build = new ArrayList<PlayerActivity>();
				for(PlayerActivity a : activity.getResponseData()) {
					if(a.getActivityStats().completed()) build.add(a);
				}
				return new Response<PlayerActivity[]>(build.toArray(new PlayerActivity[build.size()]));
			}
		} else return activity;
	}
	
	/**Kann benutzt werden um andere Profil Komponenten zu laden*/
	public GetProfile getProfileLoader() {
		return profile;
	}
	
	public CachedDestinyUserData getCachedUserData() {
		return user.cachedData;
	}
}
