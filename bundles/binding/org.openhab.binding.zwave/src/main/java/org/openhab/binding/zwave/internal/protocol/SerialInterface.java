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
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

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

	private static final byte[] zwave_nak = new byte[] { 0x15 };
	private static final byte[] zwave_ack = new byte[] { 0x06 };
	private static final byte[] zwave_can = new byte[] { 0x18 };

	private static int SOFCount = 0;
	private static int CANCount = 0;
	private static int NAKCount = 0;
	private static int ACKCount = 0;
	private static int OOFCount = 0;
	
	public static final byte MessageTypeRequest = 0x00;
	public static final byte MessageTypeResponse = 0x01;
	
	private static final byte SOF = 0x01;
	private static final byte ACK = 0x06;
	private static final byte NAK = 0x15;
	private static final byte CAN = 0x18; 
	
	private static final long WATCHDOG_TIMER_PERIOD = 10000; // 10 seconds watchdog timer

	private static final Logger logger = LoggerFactory.getLogger(SerialInterface.class);
	private SerialPort serialPort;
	private int maxBufferSize = 1024;
	private ArrayBlockingQueue<SerialMessage> outputQueue = new ArrayBlockingQueue<SerialMessage>(maxBufferSize, true);;
	
	private SerialInterfaceThread serialInterfaceThread;
	private ArrayList<SerialInterfaceEventListener> eventListeners = new ArrayList<SerialInterfaceEventListener>();
	
	private InputStream inputStream;
	private OutputStream outputStream;
	
	public int isWaitingResponseFromNode = 255;
	private Timer watchdog;

    /**
     * Constructor. Creates a new instance of the SerialInterface class.
     * @param serialPortName
     * @throws SerialInterfaceException
     */
    public SerialInterface(final String serialPortName) throws SerialInterfaceException {
		this.connect(serialPortName);
		this.watchdog = new Timer(true);
		this.watchdog.schedule(new TimerTask() {
			@Override
			public void run() {
				logger.debug("Watchdog: Checking SerialInterfaceThread");
				if (serialInterfaceThread != null && !serialInterfaceThread.isAlive())
				{
					logger.warn("SerialInterfaceThread not alive, respawning");
					disconnect();
					try {
						connect(serialPortName);
					} catch (SerialInterfaceException e) {
						logger.error("unable to restart SerialInterfacethread: {}", e.getLocalizedMessage());
					}
				}
			}
			
		}, WATCHDOG_TIMER_PERIOD, WATCHDOG_TIMER_PERIOD);

	}
    
    /**
     * Connects to a serial port and starts listening and sending on the port.
     * @param serialPortName
     * @throws SerialInterfaceException
     */
    public void connect(String serialPortName) throws SerialInterfaceException {
		logger.info("Initializing serial port " + serialPortName);
    	try {
			CommPortIdentifier portIdentifier = CommPortIdentifier.getPortIdentifier(serialPortName);
			CommPort commPort = portIdentifier.open("org.openhab.binding.zwave",2000);
			serialPort = (SerialPort) commPort;
			serialPort.setSerialPortParams(115200,SerialPort.DATABITS_8,SerialPort.STOPBITS_1,SerialPort.PARITY_NONE);
			this.inputStream = serialPort.getInputStream();
			this.outputStream = serialPort.getOutputStream();
			logger.info("Serial port is initialized");
			serialInterfaceThread = new SerialInterfaceThread(this);
			serialInterfaceThread.start();
		} catch (NoSuchPortException e) {
			logger.error(e.getLocalizedMessage());
			throw new SerialInterfaceException(e.getLocalizedMessage(), e);
		} catch (PortInUseException e) {
			logger.error(e.getLocalizedMessage());
			throw new SerialInterfaceException(e.getLocalizedMessage(), e);
		} catch (UnsupportedCommOperationException e) {
			logger.error(e.getLocalizedMessage());
			throw new SerialInterfaceException(e.getLocalizedMessage(), e);
		} catch (IOException e) {
			logger.error(e.getLocalizedMessage());
			throw new SerialInterfaceException(e.getLocalizedMessage(), e);
		}
    }
    
    /**
     * Disconnects connection to the serial port.
     */
    public void disconnect() {
    	if (serialInterfaceThread != null) {
    		serialInterfaceThread.interrupt();
    		serialInterfaceThread = null;
    	}
    	if (serialPort != null) {
    		serialPort.close();
    		serialPort = null;
    	}
		logger.info("Serial port is disconnected");
    }

    /**
     * Converts a byte array to a hexadecimal string representation    
     * @param bb
     * @return string
     */
    static public String bb2hex(byte[] bb) {
		String result = "";
		for (int i=0; i<bb.length; i++) {
			result = result + String.format("%02X ", bb[i]);
		}
		return result;
	}

    /**
     * Sends a simple request message
     * @param requestFunction
     */
    public void sendSimpleRequest(byte requestFunction) {
    	SerialMessage newMessage = new SerialMessage(requestFunction, MessageTypeRequest);
    	sendMessage(newMessage);
    }
    
    /**
     * Places a message on the queue to send over the serial interface.
     * @param message
     */
    public void sendMessage(SerialMessage message) {
    	try {
    		outputQueue.put(message);
    		logger.debug("Message placed on queue. Current Size = {}", outputQueue.size());
    	} catch (InterruptedException e) {
		}
    }
    
	public void addEventListener(SerialInterfaceEventListener serialInterfaceEventListener) {
		this.eventListeners.add(serialInterfaceEventListener);
	}
	
	public void removeEventListener(SerialInterfaceEventListener serialInterfaceEventListener) {
		this.eventListeners.remove(serialInterfaceEventListener);
	}

    private static class SerialInterfaceThread extends Thread {
    	private final Logger logger = LoggerFactory.getLogger(SerialInterfaceThread.class);
    	private SerialInterface serialInterface;
        
    	private static final byte[] zwave_nak = new byte[] { 0x15 };
    	private static final byte[] zwave_ack = new byte[] { 0x06 };
    	//private static final byte[] zwave_can = new byte[] { 0x18 };
    	private boolean isReceiving = false;
    	private boolean isWaitingResponse = false;
    	private byte [] readBuffer = new byte[400];

        public SerialInterfaceThread(SerialInterface serialInterface) {
        	this.serialInterface = serialInterface;
        }

        private void sendAck() {
        	logger.debug("Sending ACK");
        	try {
				this.serialInterface.outputStream.write(zwave_ack);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }

        private void sendNak() {
        	logger.debug("Sending NAK");
        	try {
				this.serialInterface.outputStream.write(zwave_nak);
			} catch (IOException e) {
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
    			logger.debug("Message is valid, sending ACK");
    			try {
    				this.serialInterface.outputStream.write(zwave_ack);
    				this.serialInterface.outputStream.flush();
    			} catch (IOException e) {
    				logger.error(e.getMessage());
    			}
    		} else {
    			logger.error("Message is not valid");
    			return;
    		}
    		
    		for (SerialInterfaceEventListener eventListener : this.serialInterface.eventListeners) {
    			try {
    				eventListener.SerialInterfaceIncomingMessage(serialMessage);
    			} catch (Exception ex) {
    			  logger.error("Got exception {} handling message", ex.getLocalizedMessage());
				}
			}
        }

        @Override
    	public void run() {
        	
        	try {
				this.serialInterface.outputStream.write(0x15);
	        	this.serialInterface.outputStream.flush();
				logger.debug("NAK sent");
			} catch (IOException e2) {
				// TODO Auto-generated catch block
				e2.printStackTrace();
			}
        	
    		byte nextByte = 0;
    		int availableBytes = -1;
    		ByteArrayOutputStream bb = new ByteArrayOutputStream();
    		while (!this.isInterrupted()) {
    			try {
					availableBytes = this.serialInterface.inputStream.available();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
    			if (availableBytes > 0) {
        			this.isReceiving = true;
					try {
						nextByte = (byte)this.serialInterface.inputStream.read();
					} catch (IOException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
    				// Some data arrived
    				if (nextByte == SOF) { // SOF
    					
    					if(this.isWaitingResponse){
    						logger.warn("Unsolicited message received while waiting for ACK.");
    					}
    					
    					bb.write((byte)0x01);
    					logger.debug("Rx SOF");
    					SOFCount++;
    					
    					// TODO: Implement Waiting for ACK 
    					// need to track CallbackID; CommandClassID; TargetNodeID; Reply
    					// also need to track number of attempts message has been sent
    					
    					try {
    						logger.debug("Reading message. In SOF.");
							
    						byte messageLength = (byte)this.serialInterface.inputStream.read();
							bb.write(messageLength);
							logger.debug("Message length will be {} bytes", messageLength);
							logger.debug("Available Bytes {}", availableBytes);
							
							// why do this? inputStream.read() will throw -1 when it reached the end?
							for (int i=0; i<messageLength-1; i++) {
								nextByte = (byte)this.serialInterface.inputStream.read();
								bb.write(nextByte);
							}
							
							byte messageChecksumm = (byte)this.serialInterface.inputStream.read();
							bb.write(messageChecksumm);
							logger.debug(String.format("Message read finished with checksumm = 0x%02X ", messageChecksumm));
							logger.debug("Message = " + SerialInterface.bb2hex(bb.toByteArray()));
							
							this.processIncomingMessage(bb.toByteArray());
							bb.reset();
						} catch (IOException e) {
							logger.error("Got IOException while running serial interface thread: {}", e.getLocalizedMessage());
						}
    				} else if (nextByte == ACK) { // ACK
    					logger.debug("Rx ACK");
    					ACKCount++;
    					this.isWaitingResponse = false;
    				} else if (nextByte == CAN) {
    					logger.debug("Rx CAN");  
    					CANCount++;
    					this.isWaitingResponse = false;
    				} else if (nextByte == NAK) {
    					logger.debug("Rx NAK");
    					NAKCount++;
    					this.isWaitingResponse = false;
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
    						byte[] buffer = nextMessage.getMessageBuffer();
    						logger.debug("Message = " + SerialInterface.bb2hex(buffer));
							this.serialInterface.outputStream.write(buffer);
							this.isWaitingResponse = true;
							this.serialInterface.isWaitingResponseFromNode = nextMessage.getMessageNode();
														// TODO: isWaitingResponse should be set to true; set to false in processing of message if ACK was rx.
						} catch (IOException e) {
							logger.error("Got IOException while running serial interface thread: {}", e.getLocalizedMessage());
						}
    				}
    			}
    			try {
					Thread.sleep(50);
				} catch (InterruptedException e) {
				}
    		}
    		logger.info("SerialInterfaceThread ended");
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
