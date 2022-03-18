package com.sn1pe2win.api;

import com.sn1pe2win.core.Response;

import discord4j.core.object.entity.Message;

public interface Handshake {
	
	public class OAuthResponseData {
		
		public final String accessToken;
		public final String refreshToken;
		public final String tokenType;
		public final String bungieMembership;
		//Time given from oauth in seconds
		public final long expires;
		public final long refreshExpiresIn;
		public final Message requestMessage;
		
		OAuthResponseData(String accessToken, String refreshToken, String bungieMembership, long expires, Message requestMessage, long refreshExpiresIn, String tokenType) {
			this.accessToken = accessToken;
			this.refreshToken = refreshToken;
			this.bungieMembership = bungieMembership;
			this.requestMessage = requestMessage;
			this.expires = expires;
			this.refreshExpiresIn = refreshExpiresIn;
			this.tokenType = tokenType;
		}
	}
	
	/**This function is fired, when the destiny member could be found*/
	public Response<?> success(OAuthResponseData data);
	
	public Response<?> error(String message);
}
