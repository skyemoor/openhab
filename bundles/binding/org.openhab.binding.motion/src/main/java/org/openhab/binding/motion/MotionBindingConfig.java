package org.openhab.binding.motion;

import org.openhab.binding.motion.internal.CommandType;
import org.openhab.core.binding.BindingConfig;

public class MotionBindingConfig implements BindingConfig {
	private final String itemName;
	private final String cameraId;
	private final CommandType commandType;
	
	public MotionBindingConfig(String itemName, String cameraId, CommandType commandType) {
		this.itemName = itemName;
		this.cameraId = cameraId;
		this.commandType = commandType;
	}
	
	public String getItemName() {
		return itemName;
	}
	
	public String getCameraId() {
		return cameraId;
	}
	
	public CommandType getCommandType() {
		return commandType;
	}
}
