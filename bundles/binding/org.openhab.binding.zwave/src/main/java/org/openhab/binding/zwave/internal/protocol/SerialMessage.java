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

import org.apache.commons.lang.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class represents a message which is used in serial API interface to communicate with usb Z-Wave stick
 * 
 * @author Victor Belov
 * @since 1.2.0
 */

public class SerialMessage {

	public static final byte MessageTypeRequest = 0x00;
	public static final byte MessageTypeResponse = 0x01;
	public boolean isValid = false;

	private static final Logger logger = LoggerFactory.getLogger(SerialMessage.class);
	private byte[] messagePayload;
	private byte messageLength = 0;
	private int messageType = 0;
	private int messageClass = 0;
	private int messageNode = 255; 

	public SerialMessage() {
		logger.debug("Creating empty message");
		messagePayload = new byte[] {};
	}
	
	public SerialMessage(byte mClass, byte mType) {
		logger.debug(String.format("Creating empty message of class = %02X, type = %02X", mClass, mType));
		messageClass = mClass;
		messageType = mType;
		messagePayload = new byte[] {};
		//messageNode = 255;
	}
	
	public SerialMessage(int nodeId, byte mClass, byte mType) {
		logger.debug(String.format("Creating empty message of class = %02X, type = %02X", mClass, mType));
		messageClass = mClass;
		messageType = mType;
		messagePayload = new byte[] {};
		messageNode = nodeId;
	}

	public SerialMessage(byte[] buffer) {
		logger.debug("Creating new SerialMessage from buffer = " + SerialInterface.bb2hex(buffer));
		messageLength = (byte)(buffer.length - 2); // buffer[1];
		byte messageCheckSumm = calculateChecksumm(buffer);
		byte messageCheckSummReceived = buffer[messageLength+1];
		logger.debug(String.format("Message checksum calculated = %02X, received = %02X", messageCheckSumm, messageCheckSummReceived));
		if (messageCheckSumm == messageCheckSummReceived) {
			logger.debug("Checksumm matched");
			isValid = true;
		} else {
			// TODO: Throw some exception here
			logger.debug("Checksumm error");
			isValid = false;
			return;
		}
		setMessageType(buffer[2]);
		setMessageClass(buffer[3]);
		messagePayload = ArrayUtils.subarray(buffer, 4, messageLength + 1);
		// Don't know if we need to do this b/c it defaults to 255
		//setMessageNode(255);
		logger.debug("Message payload = " + SerialInterface.bb2hex(messagePayload));
	}
	
	public SerialMessage(int nodeId, byte[] buffer) {
		logger.debug("Creating new SerialMessage from buffer = " + SerialInterface.bb2hex(buffer));
		messageLength = (byte)(buffer.length - 2); // buffer[1];
		byte messageCheckSumm = calculateChecksumm(buffer);
		byte messageCheckSummReceived = buffer[messageLength+1];
		logger.debug(String.format("Message checksum calculated = %02X, received = %02X", messageCheckSumm, messageCheckSummReceived));
		if (messageCheckSumm == messageCheckSummReceived) {
			logger.debug("Checksumm matched");
			isValid = true;
		} else {
			// TODO: Throw some exception here
			logger.debug("Checksumm error");
			isValid = false;
			return;
		}
		setMessageType(buffer[2]);
		setMessageClass(buffer[3]);
		messagePayload = ArrayUtils.subarray(buffer, 4, messageLength + 1);
		setMessageNode(nodeId);
		logger.debug("Message Node ID = " + getMessageNode());
		logger.debug("Message payload = " + SerialInterface.bb2hex(messagePayload));
	}

	public byte calculateChecksumm(byte[] buffer) {
		byte checkSumm = (byte)0xFF;
		for (int i=1; i<buffer.length-1; i++) {
			checkSumm = (byte) (checkSumm ^ buffer[i]);
		}
		logger.info(String.format("Calculated checksum = 0x%02X", checkSumm));
		return checkSumm;
	}

	public byte[] getMessageBuffer() {
		ByteArrayOutputStream resultByteBuffer = new ByteArrayOutputStream();
		byte result[];
		resultByteBuffer.write((byte)0x01);
		int messageLength = messagePayload.length + 3; // calculate and set length
		resultByteBuffer.write((byte) messageLength);
		resultByteBuffer.write((byte) messageType);
		resultByteBuffer.write((byte) messageClass);
		try {
			resultByteBuffer.write(messagePayload);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		resultByteBuffer.write((byte) 0x00);
		result = resultByteBuffer.toByteArray();
		result[4 + messagePayload.length] = 0x01;
		result[4 + messagePayload.length] = calculateChecksumm(result);
		logger.debug("Assembled message buffer = " + SerialInterface.bb2hex(result));
		return result;
	}

	public int getMessageType() {
		return messageType;
	}
	
	public byte[] getMessagePayload() {
		return messagePayload;
	}
	
	public void setMessagePayload(byte[] messagePayload) {
		this.messagePayload = messagePayload;
	}

	public void setMessageType(int messageType) {
		this.messageType = messageType;
	}

	public int getMessageClass() {
		return messageClass;
	}

	public void setMessageClass(int messageClass) {
		this.messageClass = messageClass;
	}

	/**
	 * @return the messageNode
	 */
	public int getMessageNode() {
		return messageNode;
	}

	/**
	 * @param messageNode the messageNode to set
	 */
	public void setMessageNode(int messageNode) {
		this.messageNode = messageNode;
	}
}
