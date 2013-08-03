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

import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.UnsupportedCommOperationException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.openhab.binding.zwave.internal.protocol.SerialMessage.SerialMessageClass;
import org.openhab.binding.zwave.internal.protocol.SerialMessage.SerialMessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class implements communications with a standard USB Z-Wave stick over serial protocol.
 * 
 * @author Victor Belov
 * @author Brian Crosby
 * @since 1.3.0
 */
public class SerialInterface {

	private static final int MAX_BUFFFER_SIZE = 1024;
	private static final long WATCHDOG_TIMER_PERIOD = 10000; // 10 seconds watchdog timer

	private static final Logger logger = LoggerFactory.getLogger(SerialInterface.class);

	private static int SOFCount = 0;
	private static int CANCount = 0;
	private static int NAKCount = 0;
	private static int ACKCount = 0;
	private static int OOFCount = 0;

	private final ArrayBlockingQueue<SerialMessage> sendQueue = new ArrayBlockingQueue<SerialMessage>(MAX_BUFFFER_SIZE, true);
	private final ArrayBlockingQueue<SerialMessage> receivedQueue = new ArrayBlockingQueue<SerialMessage>(MAX_BUFFFER_SIZE, true);
	
	private final ArrayList<SerialInterfaceEventListener> eventListeners = new ArrayList<SerialInterfaceEventListener>();

	private SerialPort serialPort;
	private SerialInterfaceThread serialInterfaceThread;
	private SerialEventThread serialEventThread;
	
	private InputStream inputStream;
	private OutputStream outputStream;
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
				logger.debug("Watchdog: Checking Serial threads");
				if ((serialInterfaceThread != null && !serialInterfaceThread.isAlive()) ||
						(serialEventThread != null && !serialEventThread.isAlive()))
				{
					logger.warn("Threads not alive, respawning");
					disconnect();
					try {
						connect(serialPortName);
					} catch (SerialInterfaceException e) {
						logger.error("unable to restart Serial threads: {}", e.getLocalizedMessage());
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
			this.serialPort = (SerialPort) commPort;
			this.serialPort.setSerialPortParams(115200,SerialPort.DATABITS_8,SerialPort.STOPBITS_1,SerialPort.PARITY_NONE);
			this.inputStream = serialPort.getInputStream();
			this.outputStream = serialPort.getOutputStream();
			logger.info("Serial port is initialized");
			this.serialEventThread = new SerialEventThread(this);
			this.serialEventThread.start();
			this.serialInterfaceThread = new SerialInterfaceThread(this);
			this.serialInterfaceThread.start();
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
    	if (serialEventThread != null) {
    		serialEventThread.interrupt();
    		serialEventThread = null;
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
     * Sends a simple request message to the serial interface.
     * @param requestFunction
     */
    public void sendSimpleRequest(SerialMessageClass requestFunction) {
    	SerialMessage newMessage = new SerialMessage(requestFunction, SerialMessageType.Request);
    	sendMessage(newMessage);
    }
    
    /**
     * Places a message on the queue to send over the serial interface.
     * @param message
     */
    public void sendMessage(SerialMessage message) {
    	try {
    		sendQueue.put(message);
    		logger.debug("Message placed on queue. Current Size = {}", sendQueue.size());
    	} catch (InterruptedException e) {
		}
    }
    
    /**
     * Adds an event listener to the SerialInterface object
     * @param serialInterfaceEventListener the event listener.
     */
	public void addEventListener(SerialInterfaceEventListener serialInterfaceEventListener) {
		this.eventListeners.add(serialInterfaceEventListener);
	}

	/**
	 * Removes an event listener from the SerialInterface object.
	 * @param serialInterfaceEventListener
	 */
	public void removeEventListener(SerialInterfaceEventListener serialInterfaceEventListener) {
		this.eventListeners.remove(serialInterfaceEventListener);
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
	
	/**
	 * Serial event thread. Runs event signaling on
	 * separate thread to avoid blocking the serial thread.
	 * @author Jan-Willem Spuij
	 * @since 1.3.0
	 */
	private class SerialEventThread extends Thread {

		private static final int POLL_TIMEOUT = 1000;
		
		private final Logger logger = LoggerFactory.getLogger(SerialEventThread.class);
    	private SerialInterface serialInterface;

		/**
    	 * Constructor. Creates a new instance of the SerialEventThread
    	 * @param serialInterface
    	 */
    	public SerialEventThread(SerialInterface serialInterface) {
        	this.serialInterface = serialInterface;
        }
    	
    	/**
    	 * Run method. Runs the actual process of sending and 
    	 * receiving messages from the serial port.
    	 * {@inheritDoc}
    	 */
    	@Override
    	public void run() {
			try {
	    		while (!this.isInterrupted()) {
	    			SerialMessage serialMessage;
						serialMessage = this.serialInterface.receivedQueue.poll(POLL_TIMEOUT, TimeUnit.MILLISECONDS);
						
					if (serialMessage == null)
						continue;
	    		
	    			for (SerialInterfaceEventListener eventListener : this.serialInterface.eventListeners) {
	        			try {
	        				eventListener.SerialInterfaceIncomingMessage(serialMessage);
	        			} catch (Exception ex) {
	        			  logger.error("Got exception {} handling message", ex.getLocalizedMessage());
	        			  ex.printStackTrace();
	    				}
	    			}
	    		}
			} catch (InterruptedException e) {
			}
    		logger.info("SerialEventThread ended");
    	}
	}

	/**
	 * Serial Interface thread. Sends / receives serial messages on 
	 * the serial port.
	 * @author Victor Belov
	 * @author Brian Crosby
	 * @since 1.3.0
	 */
	private class SerialInterfaceThread extends Thread {
		private static final int SLEEP_INTERVAL = 50;
		private static final int SERIAL_TIMEOUT = 1500;
		private static final int SOF = 0x01;
		private static final int ACK = 0x06;
		private static final int NAK = 0x15;
		private static final int CAN = 0x18; 
		
    	private final Logger logger = LoggerFactory.getLogger(SerialInterfaceThread.class);
    	private SerialInterface serialInterface;
    	
    	/**
    	 * Constructor. Creates a new instance of the SerialInterfaceThread
    	 * @param serialInterface
    	 */
    	public SerialInterfaceThread(SerialInterface serialInterface) {
        	this.serialInterface = serialInterface;
        }
    	
    	/**
    	 * Processes incoming message and notifies event handlers.
    	 * @param buffer the buffer to process.
    	 * @throws InterruptedException on thread interruption.
    	 */
    	private void processIncomingMessage(byte[] buffer) throws InterruptedException {
    		SerialMessage serialMessage = new SerialMessage(buffer);
    		if (serialMessage.isValid) {
    			logger.debug("Message is valid, sending ACK");
    			sendResponse(ACK);
    		} else {
    			logger.error("Message is not valid, discarding");
    			return;
    		}
    		
    		receivedQueue.put(serialMessage);
        }
    	
    	/**
    	 * Sends 1 byte frame response.
    	 */
		private void sendResponse(int response) {
			try {
				this.serialInterface.outputStream.write(response);
				this.serialInterface.outputStream.flush();
			} catch (IOException e) {
				logger.error(e.getMessage());
			}
		}
		
    	/**
    	 * Run method. Runs the actual process of sending and 
    	 * receiving messages from the serial port.
    	 * {@inheritDoc}
    	 */
    	@Override
    	public void run() {
    		sendResponse(NAK);
    		logger.debug("NAK sent");
    		
    		SerialMessage currentMessage = null;
    		boolean isWaitingForResponse = false;
    		Calendar calendar = Calendar.getInstance();
    		long lastSentTime = calendar.getTimeInMillis();
    		
			try {
				while (!this.isInterrupted()) {
					while (this.serialInterface.inputStream.available() > 0)
					{
						int nextByte = this.serialInterface.inputStream.read();
						switch (nextByte) {
							case SOF:
								if(isWaitingForResponse) {
		    						logger.warn("Unsolicited message received while waiting for ACK.");
		    					}
								int messageLength = this.serialInterface.inputStream.read();
								byte[] buffer = new byte[messageLength + 2];
								buffer[0] = SOF;
								buffer[1] = (byte)messageLength;
								int read = 0;
								while (read < messageLength) {
									read += this.serialInterface.inputStream.read(buffer, read + 2, messageLength - read);
								}
								logger.debug("Reading message finished" );
								logger.debug("Message = " + SerialInterface.bb2hex(buffer));
								processIncomingMessage(buffer);
								SOFCount++;
								break;
							case ACK:
		    					logger.debug("Received ACK");
								currentMessage = null;
								isWaitingForResponse = false;
								ACKCount++;
								break;
							case NAK:
		    					logger.error("Message not acklowledged by controller (NAK), discarding");
		    					currentMessage = null;
								isWaitingForResponse = false;
								NAKCount++;
								break;
							case CAN:
		    					logger.error("Message cancelled by controller (CAN), resending");
								isWaitingForResponse = false;
								CANCount++;
								break;
							default:
								logger.warn(String.format("Out of Frame flow. Got 0x%02X. Sending NAK.", nextByte));
								isWaitingForResponse = false;
		    					sendResponse(NAK);
		    					OOFCount++; 
						}
					}
					
					if(isWaitingForResponse && (calendar.getTimeInMillis() > (lastSentTime + SERIAL_TIMEOUT))) {
						isWaitingForResponse = false;
    					logger.error("Message timed out waiting for controller to acknowledge, resending");
					}
					
					if (!isWaitingForResponse) {
						if (currentMessage == null && !this.serialInterface.sendQueue.isEmpty()) {
							currentMessage = this.serialInterface.sendQueue.poll();
	    					logger.debug("Getting next message from output queue");
						}
						
						if (currentMessage != null) {
    						byte[] buffer = currentMessage.getMessageBuffer();
    						logger.debug("Sending Message = " + SerialInterface.bb2hex(buffer));
							isWaitingForResponse = true;
							this.serialInterface.outputStream.write(buffer);
    						lastSentTime = calendar.getTimeInMillis();
						}
					}
					
					Thread.sleep(SLEEP_INTERVAL);
	    		}
			} catch (IOException e) {
				logger.error("Got IOException while running serial interface thread: {}", e.getLocalizedMessage());
			} catch (InterruptedException e) {
			}
			logger.info("SerialInterfaceThread ended");
    	}
	}
}
