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
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;

import org.apache.commons.lang.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.UnsupportedCommOperationException;

/**
 * This class implements communications with a standard USB Z-Wave stick over serial protocol
 * 
 * @author Victor Belov
 * @author Brian Crosby
 * @since 1.3.0
 */

public class SerialInterface {

	public static final byte[] zwave_nak = new byte[] { 0x15 };
	public static final byte[] zwave_ack = new byte[] { 0x06 };
	public static final byte[] zwave_can = new byte[] { 0x18 };

	private static int SOFCount = 0;
	private static int CANCount = 0;
	private static int NAKCount = 0;
	private static int ACKCount = 0;
	private static int OOFCount = 0;
	private static int ACKWaiting = 0;
	
	public static final byte MessageTypeRequest = 0x00;
	public static final byte MessageTypeResponse = 0x01;
	public static final byte SOF = 0x01;
	public static final byte ACK = 0x06;
	public static final byte NAK = 0x15;
	public static final byte CAN = 0x18; 

	private static final Logger logger = LoggerFactory.getLogger(SerialInterface.class);
	private SerialPort serialPort;
    private InputStream inputStream;
    private OutputStream outputStream;
    protected int maxBufferSize = 1024;
    protected ArrayBlockingQueue<SerialMessage> inputQueue = new ArrayBlockingQueue<SerialMessage>(maxBufferSize, true);
    protected ArrayBlockingQueue<SerialMessage> outputQueue = new ArrayBlockingQueue<SerialMessage>(maxBufferSize, true);;
    protected ArrayBlockingQueue<SerialMessage> sentQueue = new ArrayBlockingQueue<SerialMessage>(maxBufferSize, true);;
    public boolean isWaitingResponse = false;
    public int isWaitingResponseFromNode = 255;
    private ArrayList<SerialInterfaceEventListener> eventListeners;

    public SerialInterface(String serialPortName) {
		logger.info("Initializing serial port " + serialPortName);
		this.eventListeners = new ArrayList<SerialInterfaceEventListener>();
		try {
			CommPortIdentifier portIdentifier = CommPortIdentifier.getPortIdentifier(serialPortName);
			CommPort commPort = portIdentifier.open("org.openhab.binding.zwave",2000);
			serialPort = (SerialPort) commPort;
			serialPort.setSerialPortParams(115200,SerialPort.DATABITS_8,SerialPort.STOPBITS_1,SerialPort.PARITY_NONE);
			inputStream = serialPort.getInputStream();
			outputStream = serialPort.getOutputStream();
			logger.info("Serial port is initialized");
			outputStream.write(0x15);
			outputStream.flush();
			logger.info("NAK sent");
			SerialInterfaceThread serialThread = new SerialInterfaceThread(this);
			serialThread.start();
		} catch (NoSuchPortException e) {
			logger.error(e.getMessage());
		} catch (PortInUseException e) {
			logger.error(e.getMessage());
		} catch (UnsupportedCommOperationException e) {
			logger.error(e.getMessage());
		} catch (IOException e) {
			logger.error(e.getMessage());
		}
	}

    public void sendSimpleRequest(byte requestFunction) {
    	SerialMessage newMessage = new SerialMessage(requestFunction, MessageTypeRequest);
    	sendMessage(newMessage);
    }
    
    public void sendMessage(SerialMessage message) {
    	try {
    		outputQueue.put(message);
    		logger.debug("Message placed on queue. Current Size = {}", outputQueue.size());
    	} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
    
    public void processIncomingMessage(byte[] buffer) {
		//int sofOffset = 0;
		//if (buffer[0] == 0x18) { // CAN received
			// TODO: if CAN rx we need to send CAN back
		//	return;
		//}
		
		// What is this used for?
		//for (int i=0; i<buffer.length; i++) {
		//	if (buffer[i] == 0x01) {
		//		sofOffset = i;
		//		break;
		//	}
		//}
		
		SerialMessage serialMessage = new SerialMessage(buffer);
		if (serialMessage.isValid) {
			logger.info("Message is valid, sending ACK");
			try {
				outputStream.write(zwave_ack);
				outputStream.flush();
			} catch (IOException e) {
				logger.error(e.getMessage());
			}
		} else {
			logger.info("Message is not valid");
			return;
		}
		for (SerialInterfaceEventListener eventListener : this.eventListeners) {
			eventListener.SerialInterfaceIncomingMessage(serialMessage);
		}
    }

	static public String bb2hex(byte[] bb) {
		String result = "";
		for (int i=0; i<bb.length; i++) {
			result = result + String.format("%02X ", bb[i]);
		}
		return result;
	}
	
	public void addEventListener(SerialInterfaceEventListener serialInterfaceEventListener) {
		this.eventListeners.add(serialInterfaceEventListener);
	}
	
	public void removeEventListener(SerialInterfaceEventListener serialInterfaceEventListener) {
		this.eventListeners.remove(serialInterfaceEventListener);
	}

    private static class SerialInterfaceThread extends Thread {
    	private final Logger logger = LoggerFactory.getLogger(SerialInterfaceThread.class);
        private InputStream inputStream;
        private OutputStream outputStream;
        private SerialInterface serialInterface;
    	private static final byte[] zwave_nak = new byte[] { 0x15 };
    	private static final byte[] zwave_ack = new byte[] { 0x06 };
    	private static final byte[] zwave_can = new byte[] { 0x18 };
    	private boolean isReceiving = false;
    	private boolean isWaitingResponse = false;
    	private byte [] readBuffer = new byte[400];

        public SerialInterfaceThread(SerialInterface serialInterface) {
        	this.serialInterface = serialInterface;
        	this.inputStream = serialInterface.inputStream;
        	this.outputStream = serialInterface.outputStream;
        }

        private void sendAck() {
        	logger.debug("Sending ACK");
        	try {
				this.outputStream.write(zwave_ack);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }

        private void sendNak() {
        	logger.debug("Sending NAK");
        	try {
				this.outputStream.write(zwave_nak);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }

    	public void run() {
    		byte nextByte = 0;
    		int nextRead = -1;
    		int availableBytes = -1;
    		ByteArrayOutputStream bb = new ByteArrayOutputStream();
    		while (!this.isInterrupted()) {
    			try {
					availableBytes = this.inputStream.available();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
    			if (availableBytes > 0) {
        			this.isReceiving = true;
					try {
						nextByte = (byte)this.inputStream.read();
					} catch (IOException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
    				// Some data arrived
    				if (nextByte == SOF) { // SOF
    					
    					if(this.isWaitingResponse){
    						logger.info("Unsolicited message received while waiting for ACK.");
    						ACKWaiting++;
    					}
    					
    					bb.write((byte)0x01);
    					logger.debug("Rx SOF");
    					SOFCount++;
    					
    					// TODO: Implement Waiting for ACK 
    					// need to track CallbackID; CommandClassID; TargetNodeID; Reply
    					// also need to track number of attempts message has been sent
    					
    					try {
    						logger.debug("Reading message. In SOF.");
							
    						byte messageLength = (byte)this.inputStream.read();
							bb.write(messageLength);
							logger.debug("Message length will be {} bytes", messageLength);
							logger.debug("Available Bytes {}", availableBytes);
							
							// why do this? inputStream.read() will throw -1 when it reached the end?
							for (int i=0; i<messageLength-1; i++) {
								nextByte = (byte)this.inputStream.read();
								bb.write(nextByte);
							}
							
							byte messageChecksumm = (byte)this.inputStream.read();
							bb.write(messageChecksumm);
							logger.debug(String.format("Message read finished with checksumm = 0x%02X ", messageChecksumm));
							logger.info("Message = " + this.serialInterface.bb2hex(bb.toByteArray()));
							
							// TODO: need to check checksum before sending ACK! Why are we sending it here and also sending it when we process and check the checksum? Need to do it in the processing not here.
							//sendAck();
							this.serialInterface.processIncomingMessage(bb.toByteArray());
							bb.reset();
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
    				} else if (nextByte == ACK) { // ACK
    					logger.debug("Rx ACK");
    					ACKCount++;
    					this.isWaitingResponse = false;
    				} else if (nextByte == CAN) {
    					logger.debug("Rx CAN");  
    					CANCount++;
    					// Send CAN Back
    					
    				} else if (nextByte == NAK) {
    					logger.debug("Rx NAK");
    					NAKCount++;
    					// Send NAK Back
    					
    				} else {
    					logger.warn(String.format("Out of Frame flow. Got 0x%02X. Sending NAK.", nextByte));
    					OOFCount++; 
    					sendNak();
    				}
        			this.isReceiving = false;
    			} else {
    				// Nothing to read so do the sending
    				if (!this.serialInterface.outputQueue.isEmpty() && !this.isWaitingResponse) {
    					logger.debug("Sending next message from output queue");
    					SerialMessage nextMessage = this.serialInterface.outputQueue.poll();
    					try {
							this.outputStream.write(nextMessage.getMessageBuffer());
							this.isWaitingResponse = true;
							this.serialInterface.isWaitingResponseFromNode = nextMessage.getMessageNode();
							// TODO: isWaitingResponse should be set to true; set to false in processing of message if ACK was rx.
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
    				}
    				// Commented out because it will write debug every 50 milliseconds
    				//else {
    				//	logger.debug("Nothing in queue to send or still waiting response!");
    				//}
    			}
    			try {
					Thread.sleep(50);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
    		}
    	}
    }

	/**
	 * @return the sOFCount
	 */
	public int getSOFCount() {
		return SOFCount;
	}

	/**
	 * @param sOFCount the sOFCount to set
	 */
	public void setSOFCount(int sOFCount) {
		SOFCount = sOFCount;
	}

	/**
	 * @return the cANCount
	 */
	public int getCANCount() {
		return CANCount;
	}

	/**
	 * @param cANCount the cANCount to set
	 */
	public void setCANCount(int cANCount) {
		CANCount = cANCount;
	}

	/**
	 * @return the nAKCount
	 */
	public int getNAKCount() {
		return NAKCount;
	}

	/**
	 * @param nAKCount the nAKCount to set
	 */
	public void setNAKCount(int nAKCount) {
		NAKCount = nAKCount;
	}

	/**
	 * @return the aCKCount
	 */
	public int getACKCount() {
		return ACKCount;
	}

	/**
	 * @param aCKCount the aCKCount to set
	 */
	public void setACKCount(int aCKCount) {
		ACKCount = aCKCount;
	}

	/**
	 * @return the oOFCount
	 */
	public int getOOFCount() {
		return OOFCount;
	}

	/**
	 * @param oOFCount the oOFCount to set
	 */
	public void setOOFCount(int oOFCount) {
		OOFCount = oOFCount;
	}

}
