/**
 * openHAB, the open Home Automation Bus.
 * Copyright (C) 2010-2013, openHAB.org <admin@openhab.org>
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

package org.openhab.binding.zwave.internal.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class represents a message which is used in serial API 
 * interface to communicate with usb Z-Wave stick/
 * @author Victor Belov
 * @author Brian Crosby
 * @since 1.2.0
 */
public class SerialMessage {

	private static final Logger logger = LoggerFactory.getLogger(SerialMessage.class);

	private byte[] messagePayload;
	private int messageLength = 0;
	private SerialMessageType messageType;
	private SerialMessageClass messageClass;
	private int messageNode = 255; 

	public boolean isValid = false;

	/**
	 * Constructor. Creates a new instance of the SerialMessage class.
	 */
	public SerialMessage() {
		logger.debug("Creating empty message");
		messagePayload = new byte[] {};
	}
	
	/**
	 * Constructor. Creates a new instance of the SerialMessage class using the 
	 * specified message class and message type.
	 * @param messageClass the message class to use
	 * @param messageType the message type to use
	 */
	public SerialMessage(SerialMessageClass messageClass, SerialMessageType messageType) {
		this(255, messageClass, messageType);
	}
	
	/**
	 * Constructor. Creates a new instance of the SerialMessage class using the 
	 * specified message class and message type.
	 * @param nodeId the node the message is destined for
	 * @param messageClass the message class to use
	 * @param messageType the message type to use
	 */
	public SerialMessage(int nodeId, SerialMessageClass messageClass, SerialMessageType messageType) {
		logger.debug(String.format("Creating empty message of class = %s (0x%02X), type = %s (0x%02X)", 
				new Object[] { messageClass, messageClass.key, messageType, messageType.ordinal()}));
		this.messageClass = messageClass;
		this.messageType = messageType;
		this.messagePayload = new byte[] {};
		this.messageNode = nodeId;
	}

	/**
	 * Constructor. Creates a new instance of the SerialMessage class from a
	 * specified buffer.
	 * @param buffer the buffer to create the SerialMessage from.
	 */
	public SerialMessage(byte[] buffer) {
		this(255, buffer);
	}
	
	/**
	 * Constructor. Creates a new instance of the SerialMessage class from a
	 * specified buffer, and subsequently sets the node ID.
	 * @param buffer the buffer to create the SerialMessage from.
	 */
	public SerialMessage(int nodeId, byte[] buffer) {
		logger.debug("Creating new SerialMessage from buffer = " + SerialInterface.bb2hex(buffer));
		messageLength = buffer.length - 2; // buffer[1];
		byte messageCheckSumm = calculateChecksum(buffer);
		byte messageCheckSummReceived = buffer[messageLength+1];
		logger.debug(String.format("Message checksum calculated = 0x%02X, received = 0x%02X", messageCheckSumm, messageCheckSummReceived));
		if (messageCheckSumm == messageCheckSummReceived) {
			logger.debug("Checksum matched");
			isValid = true;
		} else {
			// TODO: Throw some exception here
			logger.debug("Checksum error");
			isValid = false;
			return;
		}
		this.messageType = buffer[2] == 0x00 ? SerialMessageType.Request : SerialMessageType.Response;;
		this.messageClass = SerialMessageClass.getMessageClass(buffer[3] & 0xFF);
		this.messagePayload = ArrayUtils.subarray(buffer, 4, messageLength + 1);
		this.messageNode = nodeId;
		logger.debug("Message Node ID = " + getMessageNode());
		logger.debug("Message payload = " + SerialInterface.bb2hex(messagePayload));
	}

	/**
	 * Calculates a checksum for the specified buffer.
	 * @param buffer the buffer to calculate.
	 * @return the checksum value.
	 */
	private static byte calculateChecksum(byte[] buffer) {
		byte checkSum = (byte)0xFF;
		for (int i=1; i<buffer.length-1; i++) {
			checkSum = (byte) (checkSum ^ buffer[i]);
		}
		logger.debug(String.format("Calculated checksum = 0x%02X", checkSum));
		return checkSum;
	}

	/**
	 * Returns a string representation of this SerialMessage object.
	 * The string contains message class, message type and buffer contents.
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return String.format("Message: class = %s (0x%02X), type = %s (0x%02X), buffer = %s", 
				new Object[] { messageClass, messageClass.key, messageType, messageType.ordinal(),
				SerialInterface.bb2hex(this.getMessageBuffer()) });
	};
	
	/**
	 * Gets the SerialMessage as a byte array.
	 * @return the message
	 */
	public byte[] getMessageBuffer() {
		ByteArrayOutputStream resultByteBuffer = new ByteArrayOutputStream();
		byte[] result;
		resultByteBuffer.write((byte)0x01);
		int messageLength = messagePayload.length + 3; // calculate and set length
		resultByteBuffer.write((byte) messageLength);
		resultByteBuffer.write((byte) messageType.ordinal());
		resultByteBuffer.write((byte) messageClass.getKey());
		try {
			resultByteBuffer.write(messagePayload);
		} catch (IOException e) {
			
		}
		resultByteBuffer.write((byte) 0x00);
		result = resultByteBuffer.toByteArray();
		result[4 + messagePayload.length] = 0x01;
		result[4 + messagePayload.length] = calculateChecksum(result);
		logger.debug("Assembled message buffer = " + SerialInterface.bb2hex(result));
		return result;
	}
	
	/**
	 * Gets the message type (Request / Response).
	 * @return the message type
	 */
	public SerialMessageType getMessageType() {
		return messageType;
	}

	/**
	 * Gets the message class. This is the function it represents.
	 * @return
	 */
	public SerialMessageClass getMessageClass() {
		return messageClass;
	}

	/**
	 * Returns the Node Id for / from this message.
	 * @return the messageNode
	 */
	public int getMessageNode() {
		return messageNode;
	}

	/**
	 * Gets the message payload.
	 * @return the message payload
	 */
	public byte[] getMessagePayload() {
		return messagePayload;
	}
	
	/**
	 * Sets the message payload.
	 * @param messagePayload
	 */
	public void setMessagePayload(byte[] messagePayload) {
		this.messagePayload = messagePayload;
	}

	/**
	 * Serial message type enumeration. Indicates whether the message
	 * is a request or a response.
	 * @author Jan-Willem Spuij
	 * @since 1.3.0
	 */
	public enum SerialMessageType
	{
		Request,																			// 0x00
		Response																			// 0x01
	}
	
	/**
	 * Serial message class enumeration. Enumerates the different messages
	 * that can be exchanged with the controller.
	 * @author Jan-Willem Spuij
	 * @since 1.3.0
	 */
	public enum SerialMessageClass
	{
		SerialApiGetInitData(0x02,"SerialApiGetInitData"),									// Request initial information about devices in network
		SerialApiApplicationNodeInfo(0x03,"SerialApiApplicationNodeInfo"),					// Set controller node information
		ApplicationCommandHandler(0x04,"ApplicationCommandHandler"),						// Handle application command
		GetControllerCapabilities(0x05,"GetControllerCapabilities"),						// Request controller capabilities (primary role, SUC/SIS availability)
		SerialApiSetTimeouts(0x06,"SerialApiSetTimeouts"),									// Set Serial API timeouts
		SerialApiGetCapabilities(0x07,"SerialApiGetCapabilities"),							// Request Serial API capabilities
		SerialApiSoftReset(0x08,"SerialApiSoftReset"),										// Soft reset. Restarts Z-Wave chip
		SendNodeInfo(0x12,"SendNodeInfo"),													// Send Node Information Frame of the stick
		SendData(0x13,"SendData"),															// Send data.
		GetVersion(0x15,"GetVersion"),														// Request controller hardware version
		RfPowerLevelSet(0x17,"RfPowerLevelSet"),											// Set RF Power level
		GetRandom(0x1c,"GetRandom"),														// ???
		MemoryGetId(0x20,"MemoryGetId"),													// ???
		MemoryGetByte(0x21,"MemoryGetByte"),												// Get a byte of memory.
		ReadMemory(0x23,"ReadMemory"),														// Read memory.
		SetLearnNodeState(0x40,"SetLearnNodeState"),    									// ???
		IdentifyNode(0x41,"IdentifyNode"),    												// Get protocol info (baud rate, listening, etc.) for a given node
		SetDefault(0x42,"SetDefault"),    													// Reset controller and node info to default (original) values
		NewController(0x43,"NewController"),												// ???
		ReplicationCommandComplete(0x44,"ReplicationCommandComplete"),						// Replication send data complete
		ReplicationSendData(0x45,"ReplicationSendData"),									// Replication send data
		AssignReturnRoute(0x46,"AssignReturnRoute"),										// Assign a return route from the specified node to the controller
		DeleteReturnRoute(0x47,"DeleteReturnRoute"),										// Delete all return routes from the specified node
		RequestNodeNeighborUpdate(0x48,"RequestNodeNeighborUpdate"),						// Ask the specified node to update its neighbors (then read them from the controller)
		ApplicationUpdate(0x49,"ApplicationUpdate"),										// Get a list of supported (and controller) command classes
		AddNodeToNetwork(0x4a,"AddNodeToNetwork"),											// Control the addnode (or addcontroller) process...start, stop, etc.
		RemoveNodeFromNetwork(0x4b,"RemoveNodeFromNetwork"),								// Control the removenode (or removecontroller) process...start, stop, etc.
		CreateNewPrimary(0x4c,"CreateNewPrimary"),											// Control the createnewprimary process...start, stop, etc.
		ControllerChange(0x4d,"ControllerChange"),    										// Control the transferprimary process...start, stop, etc.
		SetLearnMode(0x50,"SetLearnMode"),													// Put a controller into learn mode for replication/ receipt of configuration info
		AssignSucReturnRoute(0x51,"AssignSucReturnRoute"),									// Assign a return route to the SUC
		EnableSuc(0x52,"EnableSuc"),														// Make a controller a Static Update Controller
		RequestNetworkUpdate(0x53,"RequestNetworkUpdate"),									// Network update for a SUC(?)
		SetSucNodeID(0x54,"SetSucNodeID"),													// Identify a Static Update Controller node id
		DeleteSUCReturnRoute(0x55,"DeleteSUCReturnRoute"),									// Remove return routes to the SUC
		GetSucNodeId(0x56,"GetSucNodeId"),													// Try to retrieve a Static Update Controller node id (zero if no SUC present)
		RequestNodeNeighborUpdateOptions(0x5a,"RequestNodeNeighborUpdateOptions"),   		// Allow options for request node neighbor update
		RequestNodeInfo(0x60,"RequestNodeInfo"),											// Get info (supported command classes) for the specified node
		RemoveFailedNodeID(0x61,"RemoveFailedNodeID"),										// Mark a specified node id as failed
		IsFailedNodeID(0x62,"IsFailedNodeID"),												// Check to see if a specified node has failed
		ReplaceFailedNode(0x63,"ReplaceFailedNode"),										// Remove a failed node from the controller's list (?)
		GetRoutingInfo(0x80,"GetRoutingInfo"),												// Get a specified node's neighbor information from the controller
		SerialApiSlaveNodeInfo(0xA0,"SerialApiSlaveNodeInfo"),								// Set application virtual slave node information
		ApplicationSlaveCommandHandler(0xA1,"ApplicationSlaveCommandHandler"),				// Slave command handler
		SendSlaveNodeInfo(0xA2,"ApplicationSlaveCommandHandler"),							// Send a slave node information frame
		SendSlaveData(0xA3,"SendSlaveData"),												// Send data from slave
		SetSlaveLearnMode(0xA4,"SetSlaveLearnMode"),										// Enter slave learn mode
		GetVirtualNodes(0xA5,"GetVirtualNodes"),											// Return all virtual nodes
		IsVirtualNode(0xA6,"IsVirtualNode"),												// Virtual node test
		SetPromiscuousMode(0xD0,"SetPromiscuousMode"),										// Set controller into promiscuous mode to listen to all frames
		PromiscuousApplicationCommandHandler(0xD1,"PromiscuousApplicationCommandHandler");
		
		/**
		 * A mapping between the integer code and its corresponding ZWaveMessage
		 * value to facilitate lookup by code.
		 */
		private static Map<Integer, SerialMessageClass> codeToMessageClassMapping;

		private int key;
		private String label;

		private SerialMessageClass(int key, String label) {
			this.key = key;
			this.label = label;
		}

		private static void initMapping() {
			codeToMessageClassMapping = new HashMap<Integer, SerialMessageClass>();
			for (SerialMessageClass s : values()) {
				codeToMessageClassMapping.put(s.key, s);
			}
		}

		/**
		 * Lookup function based on the generic device class code.
		 * @param i the code to lookup
		 * @return enumeration value of the generic device class.
		 */
		public static SerialMessageClass getMessageClass(int i) {
			if (codeToMessageClassMapping == null) {
				initMapping();
			}
			return codeToMessageClassMapping.get(i);
		}
		
		/**
		 * Returns the enumeration key.
		 * @return the key
		 */
		public int getKey() {
			return key;
		}

		/**
		 * Returns the enumeration label.
		 * @return the label
		 */
		public String getLabel() {
			return label;
		}
	}

}
