package org.openhab.binding.motion.internal;

import org.apache.commons.lang.StringUtils;

/**
 * Represents all valid commands which could be processed by this binding
 * 
 * @author Ben Jones
 * @since 1.5.0
 */
public enum CommandType {
	
	MOTION("motion"),
	ARMED("armed"),
	MASKED("masked");
	
	/** Represents the camera command as it will be used in *.items configuration */
	String command;
	
	private CommandType(String command) {
		this.command = command;
	}
	
	public String getCommand() {
		return command;
	}
	
	public static CommandType fromString(String command) {
		if (!StringUtils.isEmpty(command)) {
			for (CommandType commandType : CommandType.values()) {
				if (commandType.getCommand().equals(command)) {
					return commandType;
				}
			}
		}
		
		throw new IllegalArgumentException("Invalid command: " + command);
	}
}
