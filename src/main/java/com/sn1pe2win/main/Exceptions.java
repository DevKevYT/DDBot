package com.sn1pe2win.main;

public interface Exceptions {
	public class ApiKeyNotDefinedException extends Exception {
		private static final long serialVersionUID = 1L;

		public ApiKeyNotDefinedException(String message) {
			super(message);
		}
	}
	
	/**Wenn ein anderer BEnutzer bereits das Selbe Destiny 2 Konto verlinkt haben*/
	public class DestinyAccountAlreadyLinked extends Exception {
		private static final long serialVersionUID = 1L;

		public DestinyAccountAlreadyLinked(String message) {
			super(message);
		}
	}
	
	public class NoChangesToLinkedAccount extends Exception {
		private static final long serialVersionUID = 1L;

		public NoChangesToLinkedAccount(String message) {
			super(message);
		}
	}
}
