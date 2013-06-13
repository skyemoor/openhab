package org.openhab.binding.zwave.internal.protocol;

public class ZWaveEvent {
	public static final int SWITCH_EVENT = 1;
	public static final int DIMMER_EVENT = 2;
	public static final int NETWORK_EVENT = 3;
	private int nodeId = 0;
	private int eventType = 0;
	private String eventValue = "";

	public ZWaveEvent(int eventType, int nodeId, String eventValue) {
		this.nodeId = nodeId;
		this.eventType = eventType;
		this.eventValue = eventValue;
	}
	
	public int getNodeId() {
		return nodeId;
	}
	public void setNodeId(int nodeId) {
		this.nodeId = nodeId;
	}
	public int getEventType() {
		return eventType;
	}
	public void setEventType(int eventType) {
		this.eventType = eventType;
	}
	public String getEventValue() {
		return eventValue;
	}
	public void setEventValue(String eventValue) {
		this.eventValue = eventValue;
	}
}
