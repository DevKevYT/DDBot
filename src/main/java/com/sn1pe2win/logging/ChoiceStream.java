package com.sn1pe2win.logging;

import java.io.InputStream;
import java.util.Scanner;

public class ChoiceStream extends LogStream {

	public static Scanner scanner = new Scanner(System.in);
	
	public ChoiceStream(byte logLevel, String identifier) {
		super(logLevel, identifier);
	}
	
	public static void setInputStream(InputStream stream) {
		scanner = new Scanner(stream);
	}

	/**Choices are only possible inside the current screen, so they are broadcasted by standart*/
	public boolean choice(String prompt) {
		while(true) {
			console(prompt + " [Y/N]: ");
			String choice = scanner.next();
			if(choice.toLowerCase().equals("y") || choice.toLowerCase().equals("yes")) {
				broadcast(prompt + " [Y/N]: yes");
				return true;
			} else if(choice.toLowerCase().equals("n") || choice.toLowerCase().equals("no")) {
				broadcast(prompt + " [Y/N]: no");
				return false;
			}
			console("Invalid input. [Y/N]");
		}
	}
}
