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

import java.util.Calendar;

import org.openhab.binding.zwave.internal.protocol.SerialMessage;
import org.openhab.binding.zwave.internal.protocol.SerialMessage.SerialMessageClass;
import org.openhab.binding.zwave.internal.protocol.SerialMessage.SerialMessageType;
import org.openhab.binding.zwave.internal.protocol.ZWaveController;
import org.openhab.binding.zwave.internal.protocol.ZWaveEvent;
import org.openhab.binding.zwave.internal.protocol.ZWaveEvent.ZWaveEventType;
import org.openhab.binding.zwave.internal.protocol.ZWaveNode;
import org.openhab.core.types.EventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles the Basic command class. All devices will if possible support 
 * the Basic commands. This class contains a small number of very basic
 * commands that can be used to control the basic functionality of a device. 
 * The commands include the possibility to set a given level, get a given
 * level and report a level.
 * @author Jan-Willem Spuij
 * @since 1.3.0
 */
public class ZWaveBasicCommandClass extends ZWaveCommandClass {

	private static final Logger logger = LoggerFactory.getLogger(ZWaveBasicCommandClass.class);
	
	private static final int BASIC_SET = 0x01;
	private static final int BASIC_GET = 0x02;
	private static final int BASIC_REPORT = 0x03;
	
	/**
	 * Creates a new instance of the ZWaveBasicCommandClass class.
	 */
	public ZWaveBasicCommandClass(ZWaveNode node,
			ZWaveController controller) {
		super(node, controller);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public CommandClass getCommandClass() {
		return CommandClass.BASIC;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void handleApplicationCommandRequest(SerialMessage serialMessage,
			int offset) {

		logger.debug("Handle Message Basic Request");
		logger.debug(String.format("Received Basic Request for Node ID = %d", this.getNode().getNodeId()));
		int command = serialMessage.getMessagePayload()[offset] & 0xFF;
		switch (command) {
			case BASIC_SET:
			case BASIC_GET:
				logger.warn(String.format("Command 0x%02X not implemented.", command));
				return;
			case BASIC_REPORT:
				logger.debug("Process Basic Report");
				
				int value = serialMessage.getMessagePayload()[offset + 1] & 0xFF; 
				logger.debug(String.format("Basic report from nodeId = %d, value = 0x%02X", this.getNode().getNodeId(), value));
				String valueString = "";
				if (value == 0) {
					valueString = "OFF";
				} else if (value < 99) {
					valueString = String.valueOf(value);
				} else {
					valueString = "ON";
				}
				//TODO: Handle endpoint
				ZWaveEvent zEvent = new ZWaveEvent(ZWaveEventType.BASIC_EVENT, this.getNode().getNodeId(), 1, valueString);
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
	 * Gets a SerialMessage with the BASIC GET command 
	 * @return the serial message
	 */
	public SerialMessage getLevelMessage() {
		logger.debug("Creating new message for application command BASIC_GET for node {}", this.getNode().getNodeId());
		SerialMessage result = new SerialMessage(SerialMessageClass.SendData, SerialMessageType.Request);
    	byte[] newPayload = { 	(byte) this.getNode().getNodeId(), 
    							2, 
								(byte) getCommandClass().getKey(), 
								(byte) BASIC_GET };
    	result.setMessagePayload(newPayload);
    	return result;		
	}
	
	/**
	 * Gets a SerialMessage with the BASIC SET command 
	 * @return the serial message
	 */
	public SerialMessage setLevelMessage(int level) {
		logger.debug("Creating new message for application command BASIC_SET for node {}", this.getNode().getNodeId());
		SerialMessage result = new SerialMessage(SerialMessageClass.SendData, SerialMessageType.Request);
    	byte[] newPayload = { 	(byte) this.getNode().getNodeId(), 
    							3, 
								(byte) getCommandClass().getKey(), 
								(byte) BASIC_SET,
								(byte) level
								};
    	result.setMessagePayload(newPayload);
    	return result;		
	}

}
