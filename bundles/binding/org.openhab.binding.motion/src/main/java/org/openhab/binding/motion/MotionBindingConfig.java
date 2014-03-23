package org.openhab.binding.motion;

import org.openhab.core.binding.BindingConfig;

public class MotionBindingConfig implements BindingConfig {
	private final String itemName;
	private final String cameraId;
	
	public MotionBindingConfig(String itemName, String cameraId) {
		this.itemName = itemName;
		this.cameraId = cameraId;
	}
	
	public String getItemName() {
		return itemName;
	}
	
	public String getCameraId() {
		return cameraId;
	}
}
