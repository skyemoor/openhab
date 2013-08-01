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
package org.openhab.binding.zwave.internal.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

import org.apache.commons.lang.ArrayUtils;
import org.openhab.binding.zwave.internal.commandclass.ZWaveCommandClass;
import org.openhab.binding.zwave.internal.commandclass.ZWaveCommandClass.CommandClass;
import org.openhab.binding.zwave.internal.commandclass.ZWaveManufacturerSpecificCommandClass;
import org.openhab.binding.zwave.internal.protocol.SerialMessage.SerialMessageClass;
import org.openhab.binding.zwave.internal.protocol.SerialMessage.SerialMessageType;
import org.openhab.binding.zwave.internal.protocol.ZWaveDeviceClass.Basic;
import org.openhab.binding.zwave.internal.protocol.ZWaveDeviceClass.Generic;
import org.openhab.binding.zwave.internal.protocol.ZWaveDeviceClass.Specific;
import org.openhab.binding.zwave.internal.protocol.ZWaveEvent.ZWaveEventType;
import org.openhab.binding.zwave.internal.protocol.ZWaveNode.NodeStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ZWave controller class. Implements communication with the Z-Wave
 * controller stick using serial messages.
 * @author Victor Belov
 * @author Brian Crosby
 * @since 1.3.0
 */
public class ZWaveController implements SerialInterfaceEventListener {
	
	private static final Logger logger = LoggerFactory.getLogger(ZWaveController.class);
	
	private static final int QUERY_STAGE_TIMEOUT = 60000;
	private static final int ZWAVE_RESPONSE_TIMEOUT = 10000; // 10000 ms ZWAVE_RESPONSE TIMEOUT
	private static final int NODE_BYTES = 29; // 29 bytes = 232 bits, one for each supported node by Z-Wave;
	
	private static final int TRANSMIT_OPTION_ACK = 0x01;
	private static final int TRANSMIT_OPTION_LOW_POWER = 0x02;
	private static final int TRANSMIT_OPTION_AUTO_ROUTE = 0x04;
	private static final int TRANSMIT_OPTION_NO_ROUTE = 0x10;
	private static final int TRANSMIT_OPTION_EXPLORE = 0x20;

	private final SerialInterface serialInterface;
	private final Map<Integer, ZWaveNode> zwaveNodes = new HashMap<Integer, ZWaveNode>();
	private final ArrayList<ZWaveEventListener> zwaveEventListeners = new ArrayList<ZWaveEventListener>();

	private volatile SerialMessage lastSentMessage = null;
	private Semaphore functionWithoutNodeIdInReplySemaphore = new Semaphore(1);
	
	private String zWaveVersion = "Unknown";
	private String serialAPIVersion = "Unknown";
	private int homeId = 0;
	private int ownNodeId = 0;
	private int manufactureId = 0;
	private int deviceType = 0; 
	private int deviceId = 0;
	private int ZWaveLibraryType = 0;
	
	private boolean isConnected;
	
	/**
	 * Constructor. Creates a new instance of the Z-Wave controller class.
	 * @param serialInterface the serial interface to use for 
	 * communication with the Z-Wave controller stick.
	 */
	public ZWaveController(SerialInterface serialInterface) {
		logger.info("Starting Z-Wave controller");
		this.serialInterface = serialInterface;
		this.serialInterface.addEventListener(this);
	}

	/**
	 * Encapsulate a multichannel message for a specific endpoint.
	 * TODO: do this a better way.
	 * @param endpoint The endpoint to encapsulate for.
	 * @param message the message to encapsulate.
	 */
	private void encapsulate(int endpoint, SerialMessage message)
	{
		byte[] payload = message.getMessagePayload();
		byte[] newPayload = new byte[payload.length + 4];
		System.arraycopy(payload, 0, newPayload, 0, 2);
		System.arraycopy(payload, 0, newPayload, 4, payload.length);
		newPayload[1] += 4;
		newPayload[2] = 0x60;
		newPayload[3] = 0x0d;
		newPayload[4] = 0x01;
		newPayload[5] = (byte)(endpoint);
		
		message.setMessagePayload(newPayload);
	}
	
	/**
	 * Gets and resets the Last Sent Message. To be called
	 * from the response handler. Notifies waiting
	 * threads of the received response.
	 * @return the serial message that was sent.
	 */
	private SerialMessage getAndResetLastSentMessage()
	{
		functionWithoutNodeIdInReplySemaphore.release();
		SerialMessage result = lastSentMessage;
		lastSentMessage = null;
		return result;
	}
	
	/**
	 * Sets the Last Sent Message for messages that expect a response
	 * but ZenSys did not add return information in the message to recognize
	 * the response. This sets the LastSentMessage and waits for it to become unset again by
	 * the receiving a response (or timeout) of the serial interface, effectively synchronizing access
	 * to the calling function.
	 * @param the message to set as the last sent message
	 */
	private void setLastSentMessage(SerialMessage serialMessage) throws SerialInterfaceException
	{
		try {
			functionWithoutNodeIdInReplySemaphore.acquire();
			lastSentMessage = serialMessage;
			return;
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
//		try {
//			if(functionWithoutNodeIdInReplySemaphore.tryAcquire(ZWAVE_RESPONSE_TIMEOUT, TimeUnit.MILLISECONDS))
//			{
//				lastSentMessage = serialMessage;
//				return;
//			}
//		} catch (InterruptedException e) {
//		}
		throw new SerialInterfaceException(String.format("Sending message failed: {}", serialMessage.toString()));
	}
	
	/**
	 * Handles an incoming request message.
	 * An incoming request message is a message initiated by a node or the controller.
	 * JWS: Messages that are not implemented yet are commented out.
	 * @param incomingMessage
	 */
	private void handleIncomingRequestMessage(SerialMessage incomingMessage) {
		logger.debug("Message type = REQUEST");
		switch (incomingMessage.getMessageClass()) {
			case ApplicationCommandHandler:
				handleApplicationCommandRequest(incomingMessage);
				break;
//			case SendData:
//				handleSendDataRequest(incomingMessage);
//				break;
//			case ReplicationCommandComplete:
//				handleReplicationCommandCompleteRequest(incomingMessage);
//				break;
//			case SendNodeInfo:
//				handleSendNodeInformationRequest(incomingMessage);
//				break;
//			case RequestNodeNeighborUpdate:
//				handleNodeNeighborUpdateRequest(incomingMessage);
//				break;
//			case RequestNodeNeighborUpdateOptions:
//				handleNodeNeighborUpdateRequestOptions(incomingMessage);
//				break;
			case ApplicationUpdate:
				handleApplicationUpdateRequest(incomingMessage);
				break;
//			case AddNodeToNetwork:
//				handleAddNodeToNetworkRequest(incomingMessage);
//				break;
//			case RemoveNodeFromNetwork:
//				handleRemoveNodeFromNetworkRequest(incomingMessage);
//				break;
//			case CreateNewPrimary:
//				handleCreateNewPrimaryRequest(incomingMessage);
//				break;
//			case ControllerChange:
//				handleControllerChangeRequest(incomingMessage);
//				break;
//			case SetLearnMode:
//				handleSetLearnModeRequest(incomingMessage);
//				break;
//			case RequestNetworkUpdate:
//				handleNetworkUpdateRequest(incomingMessage);
//				break;
//			case RemoveFailedNodeID:
//				handleRemoveFailedNodeRequest(incomingMessage);
//				break;
//			case ReplaceFailedNode:
//				handleReplaceFailedNodeRequest(incomingMessage);
//				break;
//			case SetDefault:
//				handleSetDefaultRequest(incomingMessage);
//				break;
		default:
			logger.warn(String.format("TODO: Implement processing of Request Message = %s (0x%02X)",
					incomingMessage.getMessageClass().getLabel(),
					incomingMessage.getMessageClass().getKey()));
			break;	
		}
	}
	
	/**
	 * Handles incoming Application Command Request.
	 * @param incomingMessage the request message to process.
	 */
	private void handleApplicationCommandRequest(SerialMessage incomingMessage) {
		logger.debug("Handle Message Application Command Request");
		int nodeId = incomingMessage.getMessagePayload()[1] & 0xFF;
		logger.debug("Application Command Request from Node " + nodeId);
		ZWaveNode node = getNode(nodeId);
		
		try {
			ZWaveCommandClass commandClass =  node.getCommandClass(
					CommandClass.getCommandClass(incomingMessage.getMessagePayload()[3] & 0xFF));
			
			// We got an unsupported command class, return.
			if (commandClass == null)
				return;
	
			logger.debug("Found Command Class {}, passing to handleApplicationCommandRequest" + commandClass.getCommandClass().getLabel());
			commandClass.handleApplicationCommandRequest(incomingMessage, 4);
		} catch (IllegalArgumentException e) {
			logger.error(e.getLocalizedMessage());
		}
	}
	
	/**
	 * Handles incoming Application Update Request.
	 * @param incomingMessage the request message to process.
	 * JWS: Update states that are not implemented yet are commented out.
	 *  
	 */
	private void handleApplicationUpdateRequest(SerialMessage incomingMessage) {
		logger.debug("Handle Message Application Update Request");
		
		int nodeId = incomingMessage.getMessagePayload()[1] & 0xFF;
		//TODO: Z-Wave devices with a remote control do not report this update. Figure out how to handle.
		logger.debug("Application Update Request from Node " + nodeId);
		
		UpdateState updateState = UpdateState.getUpdateState(incomingMessage.getMessagePayload()[0] & 0xFF);
		
		switch (updateState){
		case NODE_INFO_RECEIVED:
			logger.debug("Application update request, node information received.");			
			int length = incomingMessage.getMessagePayload()[2] & 0xFF;
			ZWaveNode node = getNode(nodeId);
			for (int i = 6; i < length + 3; i++) {
				int data = incomingMessage.getMessagePayload()[i] & 0xFF;
				if(data == 0xef )  {
					// TODO: Implement control command classes
					break;
				}
				logger.debug(String.format("Adding command class 0x%02X to the list of supported command classes.", data));
				ZWaveCommandClass commandClass = ZWaveCommandClass.getInstance(data, node, this);
				if (commandClass != null)
					node.addCommandClass(commandClass);
			}
			// try and get the manufacturerSpecific command class.
			ZWaveManufacturerSpecificCommandClass manufacturerSpecific = (ZWaveManufacturerSpecificCommandClass)node.getCommandClass(CommandClass.MANUFACTURER_SPECIFIC);
			this.zwaveNodes.get(nodeId).setQueryStageTimeStamp(Calendar.getInstance().getTime());

			// if this node implements the Manufacturer Specific command class, we use it to get manufacturer info.
			if (manufacturerSpecific != null) {
				this.zwaveNodes.get(nodeId).setNodeStage(ZWaveNode.NodeStage.NODEBUILDINFO_MANSPEC01);
				SerialMessage serialMessage = manufacturerSpecific.getManufacturerSpecificMessage();
				this.sendData(serialMessage);
			} else {
				this.zwaveNodes.get(nodeId).setNodeStage(ZWaveNode.NodeStage.NODEBUILDINFO_DONE); // nothing more to do for this node.
			}
			break;
//		case NODE_INFO_REQ_DONE:
//			logger.debug("Application update request, need to handle Node Info Request Done.");
//			break;
//		case NODE_INFO_REQ_FAILED:
//			logger.debug("Application update request, need to handle Node Info Request Failed.");
//			break;
//		case ROUTING_PENDING:
//			logger.debug("Application update request, need to handle Node Infor Routing Pending.");
//			break;
//		case DELETE_DONE:
//			logger.debug("Application update request, need to handle Node delete done.");
//		case NEW_ID_ASSIGNED:
//			logger.debug("Application update request, need to handle Node new id assigned.");
//		case SUC_ID:
//			logger.debug("Application update request, need to handle Suc node id update.");
		default:
			logger.warn("TODO: Implement Application Update Request Handling of %s (0x%02X)." + updateState.getLabel(), updateState.getKey());
		}
	}


	/**
	 * Handles an incoming response message.
	 * An incoming response message is a response, based one of our own requests.
	 * JWS: Messages that are not implemented yet are commented out.
	 * @param incomingMessage
	 */
	private void handleIncomingResponseMessage(SerialMessage incomingMessage) {
		logger.debug("Message type = RESPONSE");
		switch (incomingMessage.getMessageClass()) {
			case GetVersion:
				handleGetVersionResponse(incomingMessage);
				break;
//			case GetRandom:
//				handleMessageGetRandomResponse(incomingMessage);
//				break;
			case MemoryGetId:
				handleMemoryGetId(incomingMessage);
				break;
			case SerialApiGetInitData:
				handleSerialApiGetInitDataResponse(incomingMessage);
				break;
			case IdentifyNode:
				handleIdentifyNodeResponse(incomingMessage);
				break;
//			case ReplicationSendData:
//				handleReplicationSendData(incomingMessage);
//				break;
//			case MessageAssignReturnRoute:
//				handleAssignReturnRouteResponse(incomingMessage);
//				break;
//			case MessageDeleteReturnRoute:
//				handleDeleteReturnRouteResponse(incomingMessage);
//				break;
			case SerialApiGetCapabilities:
				handleSerialAPIGetCapabilitiesResponse(incomingMessage);
				break;
//			case SerialApiSoftReset:
//				handleSerialApiSoftResetResponse(incomingMessage);
//				break;
//			case GetSucNodeId:
//				handleGetSUCNodeIDResponse(incomingMessage);
//				break;
//			case EnableSuc:
//				handleEnableSUCResponse(incomingMessage);
//				break;
//			case SetSucNodeID:
//				handleSetSUCNodeIDResponse(incomingMessage);
//				break;
			case SendData:
				handleSendDataResponse(incomingMessage);
				break;
//			case GetControllerCapabilities:
//				handleGetControllerCapabilitiesResponse(incomingMessage);
//				break;
//			case RequestNetworkUpdate:
//				handleRequestNetworkUpdateResponse(incomingMessage);
//				break;
//			case RemoveFailedNodeID:
//				handleRemoveFailedNodeIDResponse(incomingMessage);
//				break;
//			case IsFailedNodeID:
//				handleIsFailedNodeIDResponse(incomingMessage);
//				break;
//			case ReplaceFailedNode:
//				handleReplaceFailedNodeResponse(incomingMessage);
//				break;
//			case RfPowerLevelSet:
//				handleRFPowerLevelSetResponse(incomingMessage);
//				break;
//			case ReadMemory:
//				handleReadMemoryResponse(incomingMessage);
//				break;
//			case SerialApiSetTimeouts:
//				handleSerialAPISetTimeoutsResponse(incomingMessage);
//				break;
//			case MemoryGetByte:
//				handleMemoryGetByteResponse(incomingMessage);
//				break;
			default:
				logger.warn(String.format("TODO: Implement processing of Response Message = %s (0x%02X)",
						incomingMessage.getMessageClass().getLabel(),
						incomingMessage.getMessageClass().getKey()));
				break;				
		}
	}

	/**
	 * Handles the response of the getVersion request.
	 * @param incomingMessage the response message to process.
	 */
	private void handleGetVersionResponse(SerialMessage incomingMessage) {
		this.ZWaveLibraryType = incomingMessage.getMessagePayload()[12] & 0xFF;
		this.zWaveVersion = new String(ArrayUtils.subarray(incomingMessage.getMessagePayload(), 0, 11));
		logger.debug(String.format("Got MessageGetVersion response. Version = %s, Library Type = 0x%02X", zWaveVersion, ZWaveLibraryType));
	}
	
	/**
	 * Handles the response of the SerialApiGetInitData request.
	 * @param incomingMessage the response message to process.
	 */
	private void handleSerialApiGetInitDataResponse(
			SerialMessage incomingMessage) {
		logger.debug(String.format("Got MessageSerialApiGetInitData response."));
		this.isConnected = true;
		int nodeBytes = incomingMessage.getMessagePayload()[2] & 0xFF;
		
		if (nodeBytes != NODE_BYTES) {
			logger.error("Invalid number of node bytes = {}", nodeBytes);
			return;
		}

		int nodeId = 1;
		int firstNodeId = -1;
		
		// loop bytes
		for (int i = 3;i < 3 + nodeBytes;i++) {
			int incomingByte = incomingMessage.getMessagePayload()[i] & 0xFF;
			// loop bits in byte
			for (int j=0;j<8;j++) {
				int b1 = incomingByte & (int)Math.pow(2.0D, j);
				int b2 = (int)Math.pow(2.0D, j);
				if (b1 == b2) {
					logger.info(String.format("Found node id = %d", nodeId));
					// Place nodes in the local ZWave Controller 
					this.zwaveNodes.put(nodeId, new ZWaveNode(this.homeId, nodeId));
					
					if (firstNodeId == -1)
						firstNodeId = nodeId;
					
				}
				nodeId++;
			}
		}
		
		logger.info("------------Number of Nodes Found Registered to ZWave Controller------------");
		logger.info(String.format("# Nodes = %d", this.zwaveNodes.size()));
		logger.info("----------------------------------------------------------------------------");
		
		try {
			// Ask controller for node identification of first node
			this.identifyNode(firstNodeId);
		} catch (SerialInterfaceException e) {
			logger.error(e.getLocalizedMessage());
		}
	}

	/**
	 * Handles the response of the MemoryGetId request.
	 * The MemoryGetId function gets the home and node id from the controller memory.
	 * @param incomingMessage the response message to process.
	 */
	private void handleMemoryGetId(SerialMessage incomingMessage) {
		this.homeId = ((incomingMessage.getMessagePayload()[0] & 0xff) << 24) | 
				((incomingMessage.getMessagePayload()[1] & 0xff) << 16) | 
				((incomingMessage.getMessagePayload()[2] & 0xff) << 8) | 
				(incomingMessage.getMessagePayload()[3] & 0xff);
		this.ownNodeId = incomingMessage.getMessagePayload()[4];
		logger.debug(String.format("Got MessageMemoryGetId response. Home id = 0x%08X, Controller Node id = %d", this.homeId, this.ownNodeId));
	}

	/**
	 * Handles the response of the IdentifyNode request.
	 * @param incomingMessage the response message to process.
	 */
	private void handleIdentifyNodeResponse(SerialMessage incomingMessage) {
		logger.debug("Handle Message Get Node ProtocolInfo Response");
		
		SerialMessage lastSentMessage = this.getAndResetLastSentMessage();
		int nodeId = lastSentMessage.getMessagePayload()[0] & 0xFF;
		logger.debug("Current Message Node = " + nodeId);
		
		boolean listening = (incomingMessage.getMessagePayload()[0] & 0x80)!=0 ? true : false;
		boolean routing = (incomingMessage.getMessagePayload()[0] & 0x40)!=0 ? true : false;
		int version = (incomingMessage.getMessagePayload()[0] & 0x07) + 1;
		logger.debug("Listening = " + listening);
		logger.debug("Routing = " + routing);
		logger.debug("Version = " + version);
		
		this.zwaveNodes.get(nodeId).setListening(listening);
		this.zwaveNodes.get(nodeId).setRouting(routing);
		this.zwaveNodes.get(nodeId).setVersion(version);

		Basic basic = Basic.getBasic(incomingMessage.getMessagePayload()[3] & 0xFF);
		Generic generic = Generic.getGeneric(incomingMessage.getMessagePayload()[4] & 0xFF);
		Specific specific = Specific.getSpecific(generic, incomingMessage.getMessagePayload()[5] & 0xFF);
		logger.debug(String.format("Basic = %s 0x%x", basic.getLabel(), basic.getKey()));
		logger.debug(String.format("Generic = %s 0x%x", generic.getLabel(), generic.getKey()));
		logger.debug(String.format("Specific = %s 0x%x", specific.getLabel(), specific.getKey()));
		
		ZWaveDeviceClass deviceClass = this.zwaveNodes.get(nodeId).getDeviceClass();
		deviceClass.setBasicDeviceClass(basic);
		deviceClass.setGenericDeviceClass(generic);
		deviceClass.setSpecificDeviceClass(specific);
		
		if(nodeId != this.ownNodeId)
		{
			this.requestNodeInfo(nodeId);
		}
		else {
			this.zwaveNodes.get(nodeId).setQueryStageTimeStamp(Calendar.getInstance().getTime());
			this.zwaveNodes.get(nodeId).setNodeStage(ZWaveNode.NodeStage.NODEBUILDINFO_DONE); // do this b/c we already got the ManSpec data from previous stage (assumes Node 01 is controller)
		}
			
	    this.zwaveNodes.get(nodeId).setLastUpdated(Calendar.getInstance().getTime());
	    
	    for (Map.Entry<Integer, ZWaveNode> entry : zwaveNodes.entrySet())
	    {
	    	NodeStage stage = entry.getValue().getNodeStage();
	    	logger.debug(String.format("Node %s NodeStage = %s 0x%02x", entry.getKey(), stage.getLabel(), stage.getStage()));
	    	
	    	if (stage != ZWaveNode.NodeStage.NODEBUILDINFO_PROTOINFO && stage != ZWaveNode.NodeStage.NODEBUILDINFO_EMPTYNODE)
	    		continue;
	    	
	    	try {
		    	// identify next node.
				this.identifyNode(entry.getKey());
			} catch (SerialInterfaceException e) {
				logger.error(e.getLocalizedMessage());
			}
	    	return;
	    }
	}
	
	/**
	 * Handles the response of the SerialAPIGetCapabilities request.
	 * @param incomingMessage the response message to process.
	 */
	private void handleSerialAPIGetCapabilitiesResponse(SerialMessage incomingMessage) {
		logger.debug("Handle Message Serial API Get Capabilities");

		this.serialAPIVersion = String.format("%d.%d", incomingMessage.getMessagePayload()[0] & 0xFF, incomingMessage.getMessagePayload()[1] & 0xFF);
		this.manufactureId = ((incomingMessage.getMessagePayload()[2] & 0xff) << 8) | (incomingMessage.getMessagePayload()[3] & 0xff);
		this.deviceType = ((incomingMessage.getMessagePayload()[4] & 0xff) << 8) | (incomingMessage.getMessagePayload()[5] & 0xff);
		this.deviceId = (((incomingMessage.getMessagePayload()[6] & 0xff) << 8) | (incomingMessage.getMessagePayload()[7] & 0xff));
		
		logger.debug(String.format("API Version = %s", this.getSerialAPIVersion()));
		logger.debug(String.format("Manufacture ID = 0x%x", this.getManufactureId()));
		logger.debug(String.format("Device Type = 0x%x", this.getDeviceType()));
		logger.debug(String.format("Device ID = 0x%x", this.getDeviceId()));
		
		// Ready to get information on Serial API		
		this.serialInterface.sendSimpleRequest(SerialMessageClass.SerialApiGetInitData);
		
		//JWS: I think this message is not necessary and meant for remote controllers in a different way, see http://razberry.z-wave.me/docs/zway.pdf
		
//		//TODO: Find out why this isn't returning any information
//		SerialMessage newMessage = new SerialMessage(MessageSerialApiApplicationNodeInfo, SerialInterface.MessageTypeRequest);
//    	// 0x01 is get only the node information for those listening, Generic, Specific, Length
//    	byte[] newPayload = { 0x01, 0x02, 0x01, 0x00};
//    	newMessage.setMessagePayload(newPayload);
//    	this.serialInterface.sendMessage(newMessage);
	}

	/**
	 * Handles the response of the SendData request.
	 * @param incomingMessage the response message to process.
	 */
	private void handleSendDataResponse(SerialMessage incomingMessage) {
		logger.debug("Handle Message Send Data Response");
		if(incomingMessage.getMessageBuffer()[2] != 0x00)
			logger.debug("Send Data successfully placed on stack.");
		else
			logger.error("Sent Data was not placed on stack due to error.");
		
		// TODO: Three times retry
	}
	
	/**
	 * Notify our own event listeners of a Z-Wave event.
	 * @param event the event to send.
	 */
	private void notifyEventListeners(ZWaveEvent event) {
		logger.debug("Notifying event listeners");
		for (ZWaveEventListener listener : this.zwaveEventListeners) {
			logger.debug("Notifying {}", listener.toString());
			listener.ZWaveIncomingEvent(event);
		}
	}
	
	/**
	 * Initializes communication with the Z-Wave controller stick.
	 */
	public void initialize() {
		this.serialInterface.sendSimpleRequest(SerialMessageClass.GetVersion);
		this.serialInterface.sendSimpleRequest(SerialMessageClass.MemoryGetId);
		this.serialInterface.sendSimpleRequest(SerialMessageClass.GetControllerCapabilities);
		this.serialInterface.sendSimpleRequest(SerialMessageClass.SerialApiGetCapabilities); 
		this.serialInterface.sendSimpleRequest(SerialMessageClass.GetSucNodeId);
		// TODO: need to write a request status method to run at first startup...or enable in rule
	}
	
	/**
	 * Send Identify Node message to the controller.
	 * WARNING: Calling this function again without handling the response will block this function!
	 * @param nodeId the nodeId of the node to identify
	 * @throws SerialInterfaceException when timing out or getting an invalid response.
	 */
	public void identifyNode(int nodeId) throws SerialInterfaceException {
		this.zwaveNodes.get(nodeId).setQueryStageTimeStamp(Calendar.getInstance().getTime());
		this.zwaveNodes.get(nodeId).setNodeStage(ZWaveNode.NodeStage.NODEBUILDINFO_PROTOINFO);
		SerialMessage newMessage = new SerialMessage(nodeId, SerialMessageClass.IdentifyNode, SerialMessageType.Request);
    	byte[] newPayload = { (byte) nodeId };
    	newMessage.setMessagePayload(newPayload);
    	this.serialInterface.sendMessage(newMessage);
    	this.setLastSentMessage(newMessage);
	}
	
	/**
	 * Send Request Node info message to the controller.
	 * @param nodeId the nodeId of the node to identify
	 * @throws SerialInterfaceException when timing out or getting an invalid response.
	 */
	public void requestNodeInfo(int nodeId) {
		this.zwaveNodes.get(nodeId).setQueryStageTimeStamp(Calendar.getInstance().getTime());
		this.zwaveNodes.get(nodeId).setNodeStage(ZWaveNode.NodeStage.NODEBUILDINFO_DETAILS);
		SerialMessage newMessage = new SerialMessage(nodeId, SerialMessageClass.RequestNodeInfo, SerialMessageType.Request);
    	byte[] newPayload = { (byte) nodeId };
    	newMessage.setMessagePayload(newPayload);
    	this.serialInterface.sendMessage(newMessage);
	}
	
	/**
	 * Checks for dead or sleeping nodes during Node initialization.
	 * JwS: merged checkInitComplete and checkForDeadOrSleepingNodes to prevent possibly looping nodes multiple times.
	 */
	public void checkForDeadOrSleepingNodes(){
		int completeCount = 0;
		
		if (zwaveNodes.isEmpty())
			return;
		
		logger.debug("Checking for Dead or Sleeping Nodes.");
		for (Map.Entry<Integer, ZWaveNode> entry : zwaveNodes.entrySet()){
			if (entry.getValue().getNodeStage() == ZWaveNode.NodeStage.NODEBUILDINFO_EMPTYNODE)
				continue;
			
			logger.debug(String.format("Node %d has been in Stage %s since %s", entry.getKey(), entry.getValue().getNodeStage().getLabel(), entry.getValue().getQueryStageTimeStamp().toString()));
			
			if(entry.getValue().getNodeStage() == ZWaveNode.NodeStage.NODEBUILDINFO_DONE) {
				completeCount++;
				continue;
			}
			
			logger.debug("Checking if 1 min has passed in current stage.");
			
			if(Calendar.getInstance().getTimeInMillis() < (entry.getValue().getQueryStageTimeStamp().getTime() + QUERY_STAGE_TIMEOUT))
				continue;
			
			logger.warn(String.format("Node %d may be dead or sleeping, setting stage to DEAD.", entry.getKey()));
			entry.getValue().setNodeStage(ZWaveNode.NodeStage.NODEBUILDINFO_DEAD);
			completeCount++;
		}
		
		if(this.zwaveNodes.size() == completeCount){
			ZWaveEvent zEvent = new ZWaveEvent(ZWaveEventType.NETWORK_EVENT, 1, 0, "INIT_DONE");
			this.notifyEventListeners(zEvent);
		}
	}
	
	/**
	 * Request level from switch / dimmer. 
	 * TODO: fix this using command classes.
	 * @param nodeId
	 * @param endpoint
	 */
	public void requestLevel(int nodeId, int endpoint) {
    	SerialMessage newMessage = new SerialMessage(SerialMessageClass.SendData, SerialMessageType.Request);
    	// NodeId, 3 is command length, 0x20 is COMMAND_CLASS_BASIC, 2 is BASIC_GET, level, 5 is TRANSMIT_OPTION_ACK+TRANSMIT_OPTION_AUTO_ROUTE
    	byte[] newPayload = { (byte) nodeId, 3, 0x20, 2, 37 , 0};
    	newMessage.setMessagePayload(newPayload);
    	
    	if (endpoint != 1)
    		this.encapsulate(endpoint, newMessage);
    	
    	this.serialInterface.sendMessage(newMessage);		
	}
	
	/**
	 * Transmits the SerialMessage to a single Z-Wave Node or all Z-Wave Nodes (broadcast).
	 * Sets the transmission options as well.
	 * TODO: handle completion function for transmit status of unacknowledged broadcasts.
	 * @param serialMessage the Serial message to send.
	 */
	public void sendData(SerialMessage serialMessage)
	{
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		
    	if (serialMessage.getMessageClass() != SerialMessageClass.SendData) {
    		logger.error(String.format("Invalid message class %s (0x%02X) for sendData", serialMessage.getMessageClass().getLabel(), serialMessage.getMessageClass().getKey()));
    		return;
    	}
    	if (serialMessage.getMessageType() != SerialMessageType.Request) {
    		logger.error("Only request messages can be sent");
    		return;
    	}
    	
    	try {
			outputStream.write(serialMessage.getMessagePayload());
			outputStream.write(TRANSMIT_OPTION_ACK | TRANSMIT_OPTION_AUTO_ROUTE);
			outputStream.write(0);
		} catch (IOException e) {
    		logger.error("Could not write to output stream");
    		return;
		}
    	serialMessage.setMessagePayload(outputStream.toByteArray());
    	this.serialInterface.sendMessage(serialMessage);
	}
	
	/**
	 * Send level from switch / dimmer. 
	 * TODO: fix this using command classes.
	 * @param nodeId
	 * @param endpoint
	 */
	public void sendLevel(int nodeId, int endpoint, int level) {
    	SerialMessage newMessage = new SerialMessage(SerialMessageClass.SendData, SerialMessageType.Request);
    	// NodeId, 3 is command length, 0x20 is COMMAND_CLASS_BASIC, 1 is BASIC_SET, level, 5 is TRANSMIT_OPTION_ACK+TRANSMIT_OPTION_AUTO_ROUTE
    	byte[] newPayload = { (byte) nodeId, 3, 0x20, 1, (byte)level, 5 , 0};
    	newMessage.setMessagePayload(newPayload);
    	
    	if (endpoint != 1)
    		this.encapsulate(endpoint, newMessage);
    	
    	this.serialInterface.sendMessage(newMessage);		
	}

	
	/**
	 * Handles incoming Serial Messages. Serial messages can either be messages
	 * that are a response to our own requests, or the stick asking us information.
	 * {@inheritDoc}
	 */
	@Override
	public void SerialInterfaceIncomingMessage(SerialMessage incomingMessage) {
		
		logger.debug("Incoming message to process");
		logger.debug(incomingMessage.toString());
		
		switch (incomingMessage.getMessageType()) {
			case Request:
				handleIncomingRequestMessage(incomingMessage);
				break;
			case Response:
				handleIncomingResponseMessage(incomingMessage);
				break;
			default:
				logger.warn("Unsupported incomingMessageType: 0x%02X", incomingMessage.getMessageType());
		}
	}

	/**
	 * Add a listener for Z-Wave events to this controller.
	 * @param eventListener the event listener to add.
	 */
	public void addEventListener(ZWaveEventListener eventListener) {
		this.zwaveEventListeners.add(eventListener);
	}

	/**
	 * Remove a listener for Z-Wave events to this controller.
	 * @param eventListener the event listener to remove.
	 */
	public void removeEventListener(ZWaveEventListener eventListener) {
		this.zwaveEventListeners.remove(eventListener);
	}
	
    /**
     * Gets the API Version of the controller.
	 * @return the serialAPIVersion
	 */
	public String getSerialAPIVersion() {
		return serialAPIVersion;
	}

	/**
	 * Gets the Manufacturer ID of the controller. 
	 * @return the manufactureId
	 */
	public int getManufactureId() {
		return manufactureId;
	}

	/**
	 * Gets the device type of the controller;
	 * @return the deviceType
	 */
	public int getDeviceType() {
		return deviceType;
	}

	/**
	 * Gets the device ID of the controller.
	 * @return the deviceId
	 */
	public int getDeviceId() {
		return deviceId;
	}

	/**
	 * Gets the node object using it's node ID as key.
	 * @param nodeId the Node ID of the node to get.
	 * @return node object
	 * @throws IllegalArgumentException thrown when the nodeId is not found.
	 */
	public ZWaveNode getNode(int nodeId) throws IllegalArgumentException{
		
		if (!this.zwaveNodes.containsKey(nodeId))
			throw new IllegalArgumentException(String.format("Node with nodeId {} not found.", nodeId)); 
		
		return this.zwaveNodes.get(nodeId);
	}
	
	/**
	 * Indicates a working connection to the
	 * Z-Wave controller stick.
	 * @return isConnected;
	 */
	public boolean isConnected() {
		return isConnected;
	}
	
	

	/**
	 * Update state enumeration. Indicates the type of application update state that was sent.
	 * @author Jan-Willem Spuij
	 * @ since 1.3.0
	 */
	private enum UpdateState {
		NODE_INFO_RECEIVED(0x84, "Node info received"),
		NODE_INFO_REQ_DONE(0x82, "Node info request done"),
		NODE_INFO_REQ_FAILED(0x81, "Node info request failed"),
		ROUTING_PENDING(0x80, "Routing pending"),
		NEW_ID_ASSIGNED(0x40, "New ID Assigned"),
		DELETE_DONE(0x20, "Delete done"),
		SUC_ID(0x10, "SUC ID");
		
		/**
		 * A mapping between the integer code and its corresponding update state
		 * class to facilitate lookup by code.
		 */
		private static Map<Integer, UpdateState> codeToUpdateStateMapping;

		private int key;
		private String label;

		private UpdateState(int key, String label) {
			this.key = key;
			this.label = label;
		}

		private static void initMapping() {
			codeToUpdateStateMapping = new HashMap<Integer, UpdateState>();
			for (UpdateState s : values()) {
				codeToUpdateStateMapping.put(s.key, s);
			}
		}

		/**
		 * Lookup function based on the update state code.
		 * @param i the code to lookup
		 * @return enumeration value of the update state.
		 * @exception IllegalArgumentException thrown when there is no update state with code i
		 */
		public static UpdateState getUpdateState(int i) throws IllegalArgumentException {
			if (codeToUpdateStateMapping == null) {
				initMapping();
			}
			
			if (!codeToUpdateStateMapping.containsKey(i))
				throw new IllegalArgumentException(String.format("Update State 0x%02x not found", i));
			
			return codeToUpdateStateMapping.get(i);
		}

		/**
		 * @return the key
		 */
		public int getKey() {
			return key;
		}

		/**
		 * @return the label
		 */
		public String getLabel() {
			return label;
		}
	}
	
}
