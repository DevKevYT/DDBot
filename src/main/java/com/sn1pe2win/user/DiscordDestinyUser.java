package com.sn1pe2win.user;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;

import com.sn1pe2win.api.Handshake.OAuthResponseData;
import com.sn1pe2win.logging.SLogger;
import com.sn1pe2win.main.BotHandler;
import com.sn1pe2win.main.BotServer;
import com.sn1pe2win.main.Main;
import com.sn1pe2win.sql.simpledb.Exceptions.NoSuchTableException;
import com.sn1pe2win.sql.simpledb.MappedVar;
import com.sn1pe2win.sql.simpledb.RS_Row;

import discord4j.core.object.entity.User;

public class DiscordDestinyUser extends RS_Row {
	
	public MappedVar<Long> discordId = new MappedVar<>("userID", true);
	public MappedVar<Long> destinyId = new MappedVar<>("membershipID", true);
	public MappedVar<Integer> platform = new MappedVar<>("platform", true);
	
	public MappedVar<String> access_token = new MappedVar<>("access_token");
	public MappedVar<String> tokenType = new MappedVar<>("token_type");
	public MappedVar<Long> expires_in = new MappedVar<>("expires_in");
	public MappedVar<String> refresh_token = new MappedVar<>("refresh_token");
	public MappedVar<Long> refresh_expires_in = new MappedVar<>("refresh_expires_in");
	public MappedVar<String> membership_id = new MappedVar<>("membership_id"); //<- The bungie membership id
	
	private User discordUser;
	private boolean loginExpired = false;
	
	CachedDestinyUserData cachedData;
	
	public ArrayList<BotServer> joinedServers = new ArrayList<>();
	public BotHandler handler;
	
	public void init(User discordUser, BotHandler handler) {
		this.discordUser = discordUser;
		this.handler = handler;
	}
	
	public User getLinkedDiscordUser() {
		return discordUser;
	}
	
	public CachedDestinyUserData getCachedDestiny2Data() {	
		return cachedData;
	}
	
	public boolean loginExpired() {
		return loginExpired;
	}
	
	//TODO custom exceptions
	
	/**Returns a valid bearer token that can be used to make requests in the format "Bearer TOKEN..."
	 * Token usage is automatically updated in the database.
	 * Please keep the thrown errors silent to the client. He will be notified about an expired login on the next update
	 * @throws IllegalAccessException 
	 * @throws NoSuchTableException 
	 * @throws SQLException 
	 * @throws IOException If the token request failed.*/
	public String getAuthKey() throws IllegalAccessException, SQLException, NoSuchTableException, IOException {
		loginExpired = false;
		
		if(System.currentTimeMillis() < expires_in.get().longValue()) 
			return tokenType.get() + " " + access_token.get();
		
		else if(!refresh_expires_in.isEmpty() && !refresh_token.isEmpty()) { //Use the refresh token to regenerate the current bearer
			SLogger.INFO.log("Bearer expired for user " + discordId.getAsString() + " refreshing token ... (Refresh expires in: " + refresh_expires_in.get().longValue() + ")");
			if(System.currentTimeMillis() < refresh_expires_in.get().longValue()) {
					OAuthResponseData response = Main.remoteAuthetification.requestTokenFromRefreshToken(refresh_token.get());
					
					long expiresIn = System.currentTimeMillis() + response.expires * 1000;
					long refreshExpiresIn = System.currentTimeMillis() + response.refreshExpiresIn * 1000;
					
					handler.getDatabase().queryUpdate("UPDATE " 
							+ BotHandler.TABLE_LINKEDUSER 
							+ " SET access_token = \"" + response.accessToken + "\""
							+ ", token_type = \"" + response.tokenType + "\""
							+ ", expires_in = " + expiresIn
							+ ", refresh_token = \"" + response.refreshToken + "\""
							+ ", refresh_expires_in = " + refreshExpiresIn
					+ " WHERE userID = " + discordId.get());
					
					access_token.set(response.accessToken);
					tokenType.set(response.tokenType);
					expires_in.set(expiresIn);
					refresh_token.set(response.refreshToken);
					refresh_expires_in.set(refreshExpiresIn);
					
					SLogger.INFO.log("New bearer token issued: " + response.tokenType + " " + response.accessToken);
					return response.tokenType + " " + response.accessToken;
			} else {
				loginExpired = true;
				throw new IllegalAccessException("User needs to re- login since the refresh token expired");
			}
		} else {
			loginExpired = true;
			throw new IllegalAccessError("Bearer token expired, but client is not confidential. User needs to re- login to generate a new bearer token.");
		}
	}
}
