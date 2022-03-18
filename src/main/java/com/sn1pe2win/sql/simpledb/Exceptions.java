package com.sn1pe2win.sql.simpledb;

public interface Exceptions {
	public class UnindexedRowError extends Exception {
		private static final long serialVersionUID = 1L;
		
		public UnindexedRowError(String details) {
			super(details);
		}
	}
	
	public class NoSuchTableException extends Exception {
		private static final long serialVersionUID = 1L;

		public NoSuchTableException(String message) {
			super(message);
		}
	}
}
