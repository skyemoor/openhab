/**
 * openHAB, the open Home Automation Bus.
 * Copyright (C) 2010-2012, openHAB.org <admin@openhab.org>
 *
 * See the contributors.txt file in the distribution for a
 * full listing of individual contributors.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 * Additional permission under GNU GPL version 3 section 7
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with Eclipse (or a modified version of that library),
 * containing parts covered by the terms of the Eclipse Public License
 * (EPL), the licensors of this Program grant you additional permission
 * to convey the resulting work.
 */
package org.openhab.binding.zwave.internal.commandclass;

import org.openhab.binding.zwave.internal.protocol.SerialMessage;
import org.openhab.binding.zwave.internal.protocol.SerialMessage.SerialMessageClass;
import org.openhab.binding.zwave.internal.protocol.SerialMessage.SerialMessageType;
import org.openhab.binding.zwave.internal.protocol.ZWaveController;
import org.openhab.binding.zwave.internal.protocol.ZWaveEvent;
import org.openhab.binding.zwave.internal.protocol.ZWaveEvent.ZWaveEventType;
import org.openhab.binding.zwave.internal.protocol.ZWaveNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles the Multi Level Switch command class. Multi level switches accept
 * on / off 
 * on or off and report their status as on (0xFF) or off (0x00).
 * The commands include the possibility to set a given level, get a given
 * level and report a level.
 * @author Jan-Willem Spuij
 * @since 1.3.0
 */
public class ZWaveMultiLevelSwitchCommandClass extends ZWaveCommandClass implements ZWaveBasicCommands {

	private static final Logger logger = LoggerFactory.getLogger(ZWaveMultiLevelSwitchCommandClass.class);
	
	private static final int SWITCH_MULTILEVEL_SET = 0x01;
	private static final int SWITCH_MULTILEVEL_GET = 0x02;
	private static final int SWITCH_MULTILEVEL_REPORT = 0x03;
	private static final int SWITCH_MULTILEVEL_START_LEVEL_CHANGE = 0x04;
	private static final int SWITCH_MULTILEVEL_STOP_LEVEL_CHANGE = 0x05;
	private static final int SWITCH_MULTILEVEL_SUPPORTED_GET = 0x06;
	private static final int SWITCH_MULTILEVEL_SUPPORTED_REPORT = 0x07;
	
	private static final int DIMMER_STEP_SIZE = 5;
	
	/**
	 * Maintain our own level to enable increase / decrease steps
	 */
	private int level;
	
	/**
	 * Creates a new instance of the ZWaveMultiLevelSwitchCommandClass class.
	 * @param node the node this command class belongs to
	 * @param controller the controller to use
	 */
	public ZWaveMultiLevelSwitchCommandClass(ZWaveNode node,
			ZWaveController controller) {
		super(node, controller);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public CommandClass getCommandClass() {
		return CommandClass.SWITCH_MULTILEVEL;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getMaxVersion() {
		return 3;
	};

	/**
	 * Returns the level for the multi-level switch.
	 */
	public int getLevel() {
		return level;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void handleApplicationCommandRequest(SerialMessage serialMessage,
			int offset, int endpoint) {
		logger.debug("Handle Message Switch Multi Level Request");
		logger.debug(String.format("Received Switch Multi Level Request for Node ID = %d", this.getNode().getNodeId()));
		int command = serialMessage.getMessagePayload()[offset] & 0xFF;
		switch (command) {
			case SWITCH_MULTILEVEL_SET:
			case SWITCH_MULTILEVEL_GET:
			case SWITCH_MULTILEVEL_SUPPORTED_GET:
			case SWITCH_MULTILEVEL_SUPPORTED_REPORT:
				logger.warn(String.format("Command 0x%02X not implemented.", command));
			case SWITCH_MULTILEVEL_START_LEVEL_CHANGE:
				return;
			case SWITCH_MULTILEVEL_STOP_LEVEL_CHANGE:
				logger.debug("Process Switch Multi Level Stop Level Change");
				// request level after dimming
				logger.debug("Requesting level from node {}, endpoint {}", this.getNode().getNodeId(), endpoint);
				this.getController().requestLevel(this.getNode().getNodeId(), endpoint);
				break;
			case SWITCH_MULTILEVEL_REPORT:
				logger.debug("Process Switch Multi Level Report");
				
				int value = serialMessage.getMessagePayload()[offset + 1] & 0xFF; 
				logger.debug(String.format("Switch Multi Level report from nodeId = %d, value = 0x%02X", this.getNode().getNodeId(), value));
				Object eventValue;
				if (value == 0) {
					eventValue = "OFF";
				} else if (value < 99) {
					eventValue = value;
				} else {
					eventValue = "ON";
				}
				this.level = value;
				ZWaveEvent zEvent = new ZWaveEvent(ZWaveEventType.DIMMER_EVENT, this.getNode().getNodeId(), endpoint, eventValue);
				this.getController().notifyEventListeners(zEvent);
				break;
			default:
			logger.warn(String.format("Unsupported Command 0x%02X for command class %s (0x%02X).", 
					command, 
					this.getCommandClass().getLabel(),
					this.getCommandClass().getKey()));
		}
	}

	/**
	 * Gets a SerialMessage with the SWITCH_MULTILEVEL_GET command 
	 * @return the serial message
	 */
	public SerialMessage getLevelMessage() {
		logger.debug("Creating new message for application command SWITCH_MULTILEVEL_GET for node {}", this.getNode().getNodeId());
		SerialMessage result = new SerialMessage(SerialMessageClass.SendData, SerialMessageType.Request);
    	byte[] newPayload = { 	(byte) this.getNode().getNodeId(), 
    							2, 
								(byte) getCommandClass().getKey(), 
								(byte) SWITCH_MULTILEVEL_GET };
    	result.setMessagePayload(newPayload);
    	return result;		
	}
	
	/**
	 * Gets a SerialMessage with the SWITCH_MULTILEVEL_SET command 
	 * @param the level to set. 0 is mapped to off, > 0 is mapped to on.
	 * @return the serial message
	 */
	public SerialMessage setLevelMessage(int level) {
		logger.debug("Creating new message for application command SWITCH_MULTILEVEL_SET for node {}", this.getNode().getNodeId());
		SerialMessage result = new SerialMessage(SerialMessageClass.SendData, SerialMessageType.Request);
		this.level = level;
    	byte[] newPayload = { 	(byte) this.getNode().getNodeId(), 
    							3, 
								(byte) getCommandClass().getKey(), 
								(byte) SWITCH_MULTILEVEL_SET,
								(byte) level
								};
    	result.setMessagePayload(newPayload);
    	return result;		
	}
	
	/**
	 * Gets a SerialMessage with the SWITCH_MULTILEVEL_SET command that
	 * increases the level by DIMMER_STEP_SIZE; 
	 * @return the serial message
	 */
	public SerialMessage increaseLevelMessage() {
		logger.debug("Creating new message for application command SWITCH_MULTILEVEL_SET for node {}", this.getNode().getNodeId());
		SerialMessage result = new SerialMessage(SerialMessageClass.SendData, SerialMessageType.Request);
		this.level += DIMMER_STEP_SIZE;
		
		if (this.level > 99)
			this.level = 99;
		
		logger.debug("Increasing level to {}", this.level);
		
    	byte[] newPayload = { 	(byte) this.getNode().getNodeId(), 
    							3, 
								(byte) getCommandClass().getKey(), 
								(byte) SWITCH_MULTILEVEL_SET,
								(byte) this.level 
								};
    	result.setMessagePayload(newPayload);
    	return result;		
	}
	
	/**
	 * Gets a SerialMessage with the SWITCH_MULTILEVEL_SET command that
	 * decreases the level by DIMMER_STEP_SIZE; 
	 * @return the serial message
	 */
	public SerialMessage decreaseLevelMessage() {
		logger.debug("Creating new message for application command SWITCH_MULTILEVEL_SET for node {}", this.getNode().getNodeId());
		SerialMessage result = new SerialMessage(SerialMessageClass.SendData, SerialMessageType.Request);
		
		if (this.level >= 99)
			this.level = ((((int)(99 / DIMMER_STEP_SIZE)) + 1) * DIMMER_STEP_SIZE) - DIMMER_STEP_SIZE; 
		else if (this.level > 0)
			this.level -= DIMMER_STEP_SIZE;
		else
			this.level = 0;
		
		logger.debug("Decreasing level to {}", this.level);
		
    	byte[] newPayload = { 	(byte) this.getNode().getNodeId(), 
    							3, 
								(byte) getCommandClass().getKey(), 
								(byte) SWITCH_MULTILEVEL_SET,
								(byte) this.level 
								};
    	result.setMessagePayload(newPayload);
    	return result;		
	}
}
