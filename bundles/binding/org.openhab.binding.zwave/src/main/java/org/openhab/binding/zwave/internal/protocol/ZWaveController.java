package org.openhab.binding.zwave.internal.protocol;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import org.apache.commons.lang.ArrayUtils;
import org.openhab.binding.zwave.ZWaveCommandClass;
import org.openhab.binding.zwave.ZWaveCommandClass.Basic;
import org.openhab.binding.zwave.ZWaveCommandClass.Generic;
import org.openhab.binding.zwave.ZWaveCommandClass.Specific;
import org.openhab.binding.zwave.internal.protocol.ZWaveNode.NodeStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is responsible for parsing the binding configuration.
 * 
 * @author Victor Belov
 * @author Brian Crosby
 * @since 1.3.0
 */

public class ZWaveController implements SerialInterfaceEventListener {

	public static final byte MessageSerialApiGetInitData = 0x02;
	public static final byte MessageSerialApiApplicationNodeInfo = 0x03;
	public static final byte MessageApplicationCommandHandler = 0x04;
	public static final byte MessageGetControllerCapabilities = 0x05;
	public static final byte MessageSerialApiSetTimeouts = 0x06;
	public static final byte MessageSerialApiGetCapabilities = 0x07;
	public static final byte MessageSerialApiSoftReset = 0x08;
	public static final byte MessageSendNodeInfo = 0x12;
	public static final byte MessageSendData = 0x13;
	public static final byte MessageGetVersion = 0x15;
	public static final byte MessageRfPowerLevelSet = 0x17;
	public static final byte MessageGetRandom = 0x1c;
	public static final byte MessageMemoryGetId = 0x20;
	public static final byte MessageMemoryGetByte = 0x21;
	public static final byte MessageReadMemory = 0x23;
	public static final byte MessageSetLearnNodeState = 0x40;    // Not implemented
	public static final byte MessageIdentifyNode = 0x41;    // Get protocol info (baud rate, listening, etc.) for a given node
	public static final byte MessageSetDefault =0x42;    // Reset controller and node info to default (original) values
	public static final byte MessageNewController = 0x43;    // Not implemented
	public static final byte MessageReplicationCommandComplete = 0x44;    // Replication send data complete
	public static final byte MessageReplicationSendData = 0x45;    // Replication send data
	public static final byte MessageAssignReturnRoute = 0x46;    // Assign a return route from the specified node to the controller
	public static final byte MessageDeleteReturnRoute = 0x47;    // Delete all return routes from the specified node
	public static final byte MessageRequestNodeNeighborUpdate = 0x48;    // Ask the specified node to update its neighbors (then read them from the controller)
	public static final byte MessageApplicationUpdate = 0x49;    // Get a list of supported (and controller) command classes
	public static final byte MessageAddNodeToNetwork = 0x4a;    // Control the addnode (or addcontroller) process...start, stop, etc.
	public static final byte MessageRemoveNodeFromNetwork = 0x4b;    // Control the removenode (or removecontroller) process...start, stop, etc.
	public static final byte MessageCreateNewPrimary = 0x4c;    // Control the createnewprimary process...start, stop, etc.
	public static final byte MessageControllerChange = 0x4d;    // Control the transferprimary process...start, stop, etc.
	public static final byte MessageSetLearnMode = 0x50;    // Put a controller into learn mode for replication/ receipt of configuration info
	public static final byte MessageAssignSucReturnRoute = 0x51;    // Assign a return route to the SUC
	public static final byte MessageEnableSuc = 0x52;    // Make a controller a Static Update Controller
	public static final byte MessageRequestNetworkUpdate = 0x53;    // Network update for a SUC(?)
	public static final byte MessageSetSucNodeID = 0x54;    // Identify a Static Update Controller node id
	public static final byte MessageDeleteSUCReturnRoute = 0x55;    // Remove return routes to the SUC
	public static final byte MessageGetSucNodeId = 0x56;    // Try to retrieve a Static Update Controller node id (zero if no SUC present)
	public static final byte MessageRequestNodeNeighborUpdateOptions = 0x5a;    // Allow options for request node neighbor update
	public static final byte MessageRequestNodeInfo = 0x60;    // Get info (supported command classes) for the specified node
	public static final byte MessageRemoveFailedNodeID = 0x61;    // Mark a specified node id as failed
	public static final byte MessageIsFailedNodeID = 0x62;    // Check to see if a specified node has failed
	public static final byte MessageReplaceFailedNode = 0x63;    // Remove a failed node from the controller's list (?)
	//public static final byte MessageGetRoutingInfo = 0x80;    // Get a specified node's neighbor information from the controller
	//public static final byte MessageSerialApiSlaveNodeInfo = 0xA0;    // Set application virtual slave node information
	//public static final byte MessageApplicationSlaveCommandHandler = 0xA1;    // Slave command handler
	//public static final byte MessageSendSlaveNodeInfo = 0xA2;    // Send a slave node information frame
	//public static final byte MessageSendSlaveData = 0xA3;    // Send data from slave
	//public static final byte MessageSetSlaveLearnMode = 0xA4;    // Enter slave learn mode
	//public static final byte MessageGetVirtualNodes = 0xA5;    // Return all virtual nodes
	//public static final byte MessageIsVirtualNode = 0xA6;    // Virtual node test
	//public static final byte MessageSetPromiscuousMode = 0xD0;    // Set controller into promiscuous mode to listen to all frames
	//public static final byte MessagePromiscuousApplicationCommandHandler = 0xD1;

	
	public static final byte CommandClassBasic = 0x20;
	public static final byte CommandClassSwitchBinary = 0x25;			// where did this come from?
	public static final byte CommandClassMultiLevelRemoteSwitch = 0x26;	// where did this come from?
	public static final byte CommandClassMeter = 0x32;					// where did this come from?
	
	public static final byte SwitchBinaryCmdSet = 0x01;					//TODO: not used, part of CC Class now
	public static final byte SwitchBinaryCmdGet = 0x02;
	public static final byte SwitchBinaryCmdReport = 0x03;

	private SerialInterface serialInterface;
	private static final Logger logger = LoggerFactory.getLogger(ZWaveController.class);
	//private ArrayList<ZWaveNode> zwaveNodes;
	private Map<Integer, ZWaveNode> zwaveNodes;
	
	//private int messageResponseNode = 0;
	private String ZWaveVersion = "Unknown";
	private int ZWaveLibraryType;
	private int homeId = 0;
	private int selfNodeId = 0;
	private String serialAPIVersion = "Unknown";
	private int manufactureId = 0;
	private int deviceType = 0; 
	private int deviceId = 0;
	private boolean connected = false;
	private ArrayList<ZWaveEventListener> zwaveEventListeners;
	private static final int QUERY_STAGE_TIMEOUT = 60000;

	public ZWaveController(SerialInterface serialInterface) {
		logger.info("Starting Z-Wave controller");
		this.zwaveNodes = new HashMap<Integer, ZWaveNode>();
		this.serialInterface = serialInterface;
		this.serialInterface.addEventListener(this);
		this.zwaveEventListeners = new ArrayList<ZWaveEventListener>();
	}

	public void initialize() {
		
		
		this.serialInterface.sendSimpleRequest(MessageGetVersion);
		this.serialInterface.sendSimpleRequest(MessageMemoryGetId);
		this.serialInterface.sendSimpleRequest(MessageGetControllerCapabilities);
		this.serialInterface.sendSimpleRequest(MessageSerialApiGetCapabilities); 
		//this.serialInterface.sendSimpleRequest(MessageSerialApiGetInitData); //moved to Handler SerialAPIGetCapabilities
		this.serialInterface.sendSimpleRequest(MessageGetSucNodeId);
		//this.setConnected(true);
//		this.startAddNodeToNetwork();
		// TODO: need to write a request status method to run at first startup...or enable in rule
	}
	
	public void checkForDeadOrSleepingNodes(){
		logger.debug("Checking for Dead or Sleeping Nodes.");
		for (Map.Entry<Integer, ZWaveNode> entry : zwaveNodes.entrySet()){
			logger.debug(String.format("Node %d has been in Stage %s since %s", entry.getKey(), entry.getValue().getNodeStage().getLabel(), entry.getValue().getQueryStageTimeStamp().toString()));
			if(entry.getValue().getNodeStage() != ZWaveNode.NodeStage.NODEBUILDINFO_DONE){
				logger.debug("Checking if 1 min has passed in current stage.");
				if(Calendar.getInstance().getTimeInMillis() > (entry.getValue().getQueryStageTimeStamp().getTime() + QUERY_STAGE_TIMEOUT)) {
					logger.debug(String.format("Node %d may be dead or sleeping, setting stage to DEAD.", entry.getKey()));
					entry.getValue().setNodeStage(ZWaveNode.NodeStage.NODEBUILDINFO_DEAD);
					checkNodesInitComplete();
				}
			}
				
		}
	}
	
	private void checkNodesInitComplete(){
		int completeCount = 0;
		//Map<Integer, ZWaveNode> map = new HashMap<Integer, ZWaveNode>();
		
		for (Map.Entry<Integer, ZWaveNode> entry : zwaveNodes.entrySet()){
			logger.debug(String.format("Node %d is in Stage %s", entry.getKey(), entry.getValue().getNodeStage().getLabel()));
			if((entry.getValue().getNodeStage() == ZWaveNode.NodeStage.NODEBUILDINFO_DONE) | (entry.getValue().getNodeStage() == ZWaveNode.NodeStage.NODEBUILDINFO_DEAD))
				completeCount++;
		}
		
		if(this.zwaveNodes.size() == completeCount){
			ZWaveEvent zEvent = new ZWaveEvent(ZWaveEvent.NETWORK_EVENT, 0, "INIT_DONE");
			this.notifyEventListeners(zEvent);
		}
		else {
			logger.debug("Nodes Init Not Complete Yet");
			logger.debug("Number of Nodes = " + this.zwaveNodes.size());
			logger.debug("Number of Nodes Basic Init Complete = " + completeCount);
		}
		
	}
	
	public void identifyNode(int nodeId) {
    	//messageResponseNode = nodeId;
		this.zwaveNodes.get(nodeId).setQueryStageTimeStamp(Calendar.getInstance().getTime());
		this.zwaveNodes.get(nodeId).setNodeStage(ZWaveNode.NodeStage.NODEBUILDINFO_PROTOINFO);
		SerialMessage newMessage = new SerialMessage(nodeId, MessageIdentifyNode, SerialInterface.MessageTypeRequest);
    	byte[] newPayload = { (byte) nodeId };
    	newMessage.setMessagePayload(newPayload);
    	this.serialInterface.sendMessage(newMessage);
	}

	public void requestNodeInfo(int nodeId) {
    	SerialMessage newMessage = new SerialMessage(MessageRequestNodeInfo, SerialInterface.MessageTypeRequest);
    	byte[] newPayload = { (byte) nodeId };
    	newMessage.setMessagePayload(newPayload);
    	this.serialInterface.sendMessage(newMessage);
	}
	
	/*
	public void startAddNodeToNetwork() {
    	SerialMessage newMessage = new SerialMessage(MessageAddNodeToNetwork, SerialInterface.MessageTypeRequest);
    	byte[] newPayload = { (byte) 0x01 };
    	newMessage.setMessagePayload(newPayload);
    	this.serialInterface.sendMessage(newMessage);		
	}
	
	public void stopAddNodeToNetwork() {
    	SerialMessage newMessage = new SerialMessage(MessageAddNodeToNetwork, SerialInterface.MessageTypeRequest);
    	byte[] newPayload = { (byte) 0x05 };
    	newMessage.setMessagePayload(newPayload);
    	this.serialInterface.sendMessage(newMessage);		
	}
	*/
	
	public void requestLevel(int nodeId) {
    	SerialMessage newMessage = new SerialMessage((byte)0x13, SerialInterface.MessageTypeRequest);
    	// NodeId, 3 is command length, 0x20 is COMMAND_CLASS_BASIC, 2 is BASIC_GET, level, 5 is TRANSMIT_OPTION_ACK+TRANSMIT_OPTION_AUTO_ROUTE
    	byte[] newPayload = { (byte) nodeId, 3, 0x20, 2, 37 , 0};
    	newMessage.setMessagePayload(newPayload);
    	this.serialInterface.sendMessage(newMessage);		
	}
	
	public void sendLevel(int nodeId, int level) {
    	SerialMessage newMessage = new SerialMessage((byte)0x13, SerialInterface.MessageTypeRequest);
    	// NodeId, 3 is command length, 0x20 is COMMAND_CLASS_BASIC, 1 is BASIC_SET, level, 5 is TRANSMIT_OPTION_ACK+TRANSMIT_OPTION_AUTO_ROUTE
    	byte[] newPayload = { (byte) nodeId, 3, 0x20, 1, (byte)level, 5 , 0};
    	newMessage.setMessagePayload(newPayload);
    	this.serialInterface.sendMessage(newMessage);		
	}
	
	public void requestManufacturerSpecific(int nodeId) {
		this.zwaveNodes.get(nodeId).setQueryStageTimeStamp(Calendar.getInstance().getTime());
		this.zwaveNodes.get(nodeId).setNodeStage(ZWaveNode.NodeStage.NODEBUILDINFO_MANSPEC01);
    	SerialMessage newMessage = new SerialMessage(MessageSendData, SerialInterface.MessageTypeRequest);
    	// NodeId, command length, COMMAND_CLASS_, COMMAND, 5 is TRANSMIT_OPTION_ACK+TRANSMIT_OPTION_AUTO_ROUTE
    	byte[] newPayload = { (byte) nodeId, 2, (byte) ZWaveCommandClass.COMMAND_CLASS_MANUFACTURER_SPECIFIC.ID.getCommand(), (byte) ZWaveCommandClass.COMMAND_CLASS_MANUFACTURER_SPECIFIC.GET.getCommand() ,5 , 0};
    	newMessage.setMessagePayload(newPayload);
    	this.serialInterface.sendMessage(newMessage);		
	}
	
	@Override
	public void SerialInterfaceIncomingMessage(SerialMessage incomingMessage) {
		
		boolean handleCallback = true;
		// buffer[0] SOF, CAN, NAK, ACK handled in SerialInterface Thread
		// TODO: validate handling of above
		
		logger.info("Incoming message to process");
		logger.info(SerialInterface.bb2hex(incomingMessage.getMessagePayload()));
		
		// TODO: implement handlers for each of the message types
		if (incomingMessage.getMessageType() == SerialInterface.MessageTypeResponse) {
			
			// We set the isWaitingResponse within the SerialInterface; this is a function of the Serial Protocol not Zwave.
			//this.serialInterface.isWaitingResponse = false;
			
			logger.info("Message type = RESPONSE");
			switch (incomingMessage.getMessageClass()) {
				case MessageGetVersion:
					int libraryType = incomingMessage.getMessagePayload()[12];
					String version = new String(ArrayUtils.subarray(incomingMessage.getMessagePayload(), 0, 11));
					logger.info(String.format("Got MessageGetVersion response. Version = %s, Library Type = 0x%02X", version, libraryType));
					this.setZWaveVersion(version);
					this.setZWaveLibraryType(libraryType);
					break;
				case MessageGetRandom:
					logger.info(String.format("Got MessageGetRandom response."));
					handleMessageGetRandomResponse(incomingMessage);
					break;
				case MessageMemoryGetId:
					int homeId = ((incomingMessage.getMessagePayload()[0] & 0xff) << 24) | ((incomingMessage.getMessagePayload()[1] & 0xff) << 16) | ((incomingMessage.getMessagePayload()[2] & 0xff) << 8) | (incomingMessage.getMessagePayload()[3] & 0xff);
					int selfNodeId = incomingMessage.getMessagePayload()[4];
					logger.info(String.format("Got MessageMemoryGetId response. Home id = 0x%08X, Node id = %d", homeId, selfNodeId));
					this.setHomeId(homeId);
					this.setSelfNodeId(selfNodeId);
					break;
				case MessageSerialApiGetInitData:
					logger.info(String.format("Got MessageSerialApiGetInitData response."));
					this.connected = true;
					if (incomingMessage.getMessagePayload()[2] == 29) {
						byte nodeId = 1;
						for (int i = 3;i < 3 + 29;i++) {
							for (int j=0;j<8;j++) {
								byte b1 = (byte) (incomingMessage.getMessagePayload()[i] & (byte)(int)Math.pow(2.0D, j));
								byte b2 = (byte)(int)Math.pow(2.0D, j);
		//						logger.info(String.format("%02X %02X", b1, b2));
								if (b1 == b2) {
									logger.info(String.format("Found node id = %d", nodeId));
									// Place nodes in the local ZWave Controller 
									this.zwaveNodes.put((int) nodeId, new ZWaveNode(this.homeId, nodeId));						
									// Queue up nodes for init
									this.identifyNode(nodeId);
								}
								nodeId = (byte)(nodeId + 1);
							}
						}
						
						logger.info("------------Number of Nodes Found Registered to ZWave Controller------------");
						logger.info(String.format("# Nodes = %d", this.zwaveNodes.size()));
						logger.info("----------------------------------------------------------------------------");
					}
					break;
				case MessageIdentifyNode:
					logger.info(String.format("Got MessageIdentifyNode response."));
					handleMessageGetNodeProtcolInfoResponse(incomingMessage);
					break;
				case MessageReplicationSendData:
					logger.info(String.format("Got MessageReplicationSendData response."));
					// TODO: Implement MessageReplicationSendData handler
					break;
				case MessageAssignReturnRoute:
					logger.info(String.format("Got MessaeAssignReturnRoute response."));
					handleMessageAssignReturnRouteResponse(incomingMessage);
					break;
				case MessageDeleteReturnRoute:
					logger.info(String.format("Got MessaeAssignReturnRoute response."));
					handleMessageDeleteReturnRouteResponse(incomingMessage);
					break;
				case MessageSerialApiGetCapabilities:
					logger.info(String.format("Got MessageSerialApiGetCapabilities response."));
					handleMessageSerialAPIGetCapabilitiesResponse(incomingMessage);
					break;
				case MessageSerialApiSoftReset:
					logger.info(String.format("Got MessageSerialApiSoftReset response."));
					handleMessageSerialAPISoftResetResponse(incomingMessage);
					break;
				case MessageGetSucNodeId:
					logger.info(String.format("Got MessageGetSucNodeId response. SUC Node Id = %d", incomingMessage.getMessagePayload()[0]));
					handleMessageGetSUCNodeIDResponse(incomingMessage);
					break;
				case MessageEnableSuc:
					logger.info(String.format("Got MessageEnableSUC response."));
					handleMessageEnableSUCResponse(incomingMessage);
					break;
				case MessageSetSucNodeID:
					logger.info(String.format("Got MessageSetSUCNodeID response."));
					handleMessageSetSUCNodeIDResponse(incomingMessage);
					break;
				case MessageSendData:
					logger.info(String.format("Got MessageSendData response."));
					handleMessageSendDataResponse(incomingMessage);
					break;
				case MessageGetControllerCapabilities:
					logger.info(String.format("Got MessageGetControllerCapabilities response."));
					handleMessageGetControllerCapabilitiesResponse(incomingMessage);
					break;
				case MessageRequestNetworkUpdate:
					logger.info(String.format("Got MessageRequestNetworkUpdate response."));
					// TODO: Implement MessageRequestNetworkUpdate Handler
					break;
				case MessageRequestNodeInfo:
					logger.info(String.format("Got MessageRequestNodeInfo response."));
					// TODO: Implement Handler for MessageRequestNodeInfo
					break;
				case MessageRemoveFailedNodeID:
					logger.info(String.format("Got MessageRemoveFailedNodeID response."));
					handleMessageRemoveFailedNodeIDResponse(incomingMessage);
					break;
				case MessageIsFailedNodeID:
					logger.info(String.format("Got MessageIsFailedNodeID response."));
					handleMessageIsFailedNodeIDResponse(incomingMessage);
					break;
				case MessageReplaceFailedNode:
					logger.info(String.format("Got MessageReplaceFailedNode response."));
					handleMessageReplaceFailedNodeResponse(incomingMessage);
					break;
				case MessageRfPowerLevelSet:
					logger.info(String.format("Got MessageRfPowerLevelSet response."));
					handleMessageRFPowerLevelSetResponse(incomingMessage);
					break;
				case MessageReadMemory:
					logger.info(String.format("Got MessageReadMemory response."));
					handleMessageReadMemoryResponse(incomingMessage);
					break;
				case MessageSerialApiSetTimeouts:
					logger.info(String.format("Got MessageSerialApiSetTimeouts response."));
					handleMessageSerialAPISetTimeoutsResponse(incomingMessage);
					break;
				case MessageMemoryGetByte:
					logger.info(String.format("Got MessageMemoryGetByte response."));
					handleMessageMemoryGetByteResponse(incomingMessage);
					break;
				default:
					logger.info(String.format("TODO: Implement processing of Response Message = 0x%02X", incomingMessage.getMessageClass()));
					break;
				}
		} else if (incomingMessage.getMessageType() == SerialInterface.MessageTypeRequest) {
			logger.info("Message type = REQUEST");
			switch (incomingMessage.getMessageClass()) {
				case MessageApplicationCommandHandler:
					byte commandClass = incomingMessage.getMessagePayload()[3];
					int sourceNodeId = incomingMessage.getMessagePayload()[1];
					
					// Update last time this node was updated
					this.zwaveNodes.get(sourceNodeId).setLastUpdated(Calendar.getInstance().getTime());
					logger.info(String.format("Got MessageApplicationCommandHandler for Source Node = %d and CommandClass = 0x%02X", sourceNodeId, commandClass));
					// TODO: update to handle all supported CommandClasses
					switch (commandClass) {
						case CommandClassSwitchBinary:
						case CommandClassMultiLevelRemoteSwitch:
						case CommandClassBasic:
							int eventType = commandClass == CommandClassSwitchBinary ? ZWaveEvent.SWITCH_EVENT : ZWaveEvent.DIMMER_EVENT;
							logger.info("Got CommandClassSwitchBinary or CommandClassMultiLevelRemoteSwitch");
							byte switchBinaryCmd = incomingMessage.getMessagePayload()[4];
							switch (switchBinaryCmd) {
								case SwitchBinaryCmdSet:
									logger.info("SwitchBinary set");
									break;
								case SwitchBinaryCmdGet:
									logger.info("SwitchBinary get");
									break;
								case SwitchBinaryCmdReport:
									byte switchValue = incomingMessage.getMessagePayload()[5];
									logger.info(String.format("SwitchBinary report from nodeId = %d, value = 0x%02X", sourceNodeId, switchValue));
									String switchValueString = "";
									if (switchValue == 0) {
										switchValueString = "OFF";
									// Not sure if this is working...
									//} else if (switchValue < 99) {
									//	switchValueString = String.valueOf(switchValue);
									} else {
										switchValueString = "ON";
									}
									ZWaveEvent zEvent = new ZWaveEvent(eventType, sourceNodeId, switchValueString);
									this.notifyEventListeners(zEvent);
									break;
								default:
									logger.info("Unknown SwitchBinary command");
									break;
								}
							break;
						case CommandClassMeter:
							logger.info("Got CommandClassMeter");
							break;
						case 0x72:
							logger.info("Got Message for Command Class Manuf. Specific");
							handleManufactureSpecificRequest(incomingMessage);
							break;
						default:
							logger.info(String.format("TODO: Implement processing of CommandClass = 0x%02X", commandClass));
							break;
						}
				case MessageSendData:
					logger.info(String.format("MessageSendData request."));
					handleMessageSendDataRequest(incomingMessage);
					break;
				case MessageReplicationCommandComplete:
					logger.info(String.format("MessageReplicationCommandComplete request."));
					// TODO: Implement ReplicationCommandComplete Handler
					break;
				case MessageSendNodeInfo:
					logger.info(String.format("MessageSendNodeInfo request."));
					handleMessageSendNodeInformationRequest(incomingMessage);
					break;
				case MessageRequestNodeNeighborUpdate:
				case MessageRequestNodeNeighborUpdateOptions:
					logger.info(String.format("MessageRequestNodeNeighborUpdate request."));
					handleMessageNodeNeighborUpdateRequest(incomingMessage);
					break;
				case MessageApplicationUpdate:
					logger.info(String.format("MessageApplicationUpdate request."));
					handleMessageApplicationUpdateRequest(incomingMessage);
					break;
				case MessageAddNodeToNetwork:
					logger.info(String.format("MessageAddNodeToNetwork request."));
					handleMessageAddNodeToNetworkRequest(incomingMessage);
					break;
				case MessageRemoveNodeFromNetwork:
					logger.info(String.format("MessageRemoveNodeFromNetwork request."));
					handleMessageRemoveNodeFromNetworkRequest(incomingMessage);
					break;
				case MessageCreateNewPrimary:
					logger.info(String.format("MessageCreateNewPrimary request."));
					handleMessageCreateNewPrimaryRequest(incomingMessage);
					break;
				case MessageControllerChange:
					logger.info(String.format("MessageControllerChange request."));
					handleMessageControllerChangeRequest(incomingMessage);
					break;
				case MessageSetLearnMode:
					logger.info(String.format("MessageSetLearnMode request."));
					handleMessageSetLearnModeRequest(incomingMessage);
					break;
				case MessageRequestNetworkUpdate:
					logger.info(String.format("MessageRequestNetworkUpdate request."));
					handleMessageNetworkUpdateRequest(incomingMessage);
					break;
				case MessageRemoveFailedNodeID:
					logger.info(String.format("MessageRemoveFailedNodeID request."));
					handleMessageRemoveFailedNodeRequest(incomingMessage);
					break;
				case MessageReplaceFailedNode:
					logger.info(String.format("MessageReplaceFailedNode request."));
					handleMessageReplaceFailedNodeRequest(incomingMessage);
					break;
				case MessageSetDefault:
					logger.info(String.format("MessageSetDefault request."));
					// TODO: Implement MessageSetDefault Handler
					break;
			default:
				logger.info(String.format("TODO: Implement processing of Request Message = 0x%02X", incomingMessage.getMessageClass()));
				break;
			}
		}
		else {
			logger.debug("Unsupoorted incomingMessageType: 0x%02X", incomingMessage.getMessageType());
		}
		
		if (handleCallback){
			
		}
		
	}


	// Message Handlers//
	
    /**
	 * @return the serialAPIVersion
	 */
	public String getSerialAPIVersion() {
		return serialAPIVersion;
	}

	/**
	 * @param serialAPIVersion the serialAPIVersion to set
	 */
	public void setSerialAPIVersion(String serialAPIVersion) {
		this.serialAPIVersion = serialAPIVersion;
	}

	/**
	 * @return the manufactureId
	 */
	public int getManufactureId() {
		return manufactureId;
	}

	/**
	 * @param manufactureId the manufactureId to set
	 */
	public void setManufactureId(int manufactureId) {
		this.manufactureId = manufactureId;
	}

	/**
	 * @return the deviceType
	 */
	public int getDeviceType() {
		return deviceType;
	}

	/**
	 * @param deviceType the deviceType to set
	 */
	public void setDeviceType(int deviceType) {
		this.deviceType = deviceType;
	}

	/**
	 * @return the deviceId
	 */
	public int getDeviceId() {
		return deviceId;
	}

	/**
	 * @param deviceId the deviceId to set
	 */
	public void setDeviceId(int deviceId) {
		this.deviceId = deviceId;
	}

	private void handleMessageSerialAPIResetRequest(SerialMessage message) {
		logger.info("Handle Message Serial API Reset Request.");
	}
    
    private void handleMessageNetworkUpdateRequest(SerialMessage message) {
		logger.info("Handle Message Network Update Request.");
	}
    
    private void handleMessageRemoveFailedNodeRequest(SerialMessage message) {
		logger.info("Handle Message Remove Failed Node Request.");
	}
    
    private void handleMessageNetworkUpdateResponse(SerialMessage message) {
		logger.info("Handle Message Send Node Information Request.");
	}
	
	private void handleMessageSendNodeInformationRequest(SerialMessage message) {
		logger.info("Handle Message Send Node Information Request.");
	}
    
    private void handleMessageSerialAPIGetInitDataResponse(SerialMessage message){
		logger.info("Handle Message Serial API Get Init Data Response");
	}
	
	private void handleMessageGetControllerCapabilitiesResponse(SerialMessage message){
		logger.info("Handle Get Controller Capabilities Response");
		
		// Controller Capabilities = message.type
		logger.debug(String.format("Controller Capabilities response. Cap=%d", message.getMessageType()));
		
	}
	
	private void handleMessageSerialAPIGetCapabilitiesResponse(SerialMessage message){
		logger.info("Handle Message Serial API Get Capabilities");
		
		this.setSerialAPIVersion(String.format("%d.%d", message.getMessagePayload()[0], message.getMessagePayload()[1]));
		this.setManufactureId(((message.getMessagePayload()[2] & 0xff) << 8) | (message.getMessagePayload()[3] & 0xff));
		this.setDeviceType(((message.getMessagePayload()[4] & 0xff) << 8) | (message.getMessagePayload()[5] & 0xff));
		this.setDeviceId(((message.getMessagePayload()[6] & 0xff) << 8) | (message.getMessagePayload()[7] & 0xff));
		
		//String serialAPIVersion = String.format("%d.%d", message.getMessagePayload()[0], message.getMessagePayload()[1]);
		//int manufactureId = ((message.getMessagePayload()[2] & 0xff) << 8) | (message.getMessagePayload()[3] & 0xff);	
		//int deviceType = ((message.getMessagePayload()[4] & 0xff) << 8) | (message.getMessagePayload()[5] & 0xff);	
		//int deviceId = ((message.getMessagePayload()[6] & 0xff) << 8) | (message.getMessagePayload()[7] & 0xff);	
		
		logger.info(String.format("API Version = %s", this.getSerialAPIVersion()));
		logger.info(String.format("Manufacture ID = 0x%x", this.getManufactureId()));
		logger.info(String.format("Device Type = 0x%x", this.getDeviceType()));
		logger.info(String.format("Device ID = 0x%x", this.getDeviceId()));
		
		// Ready to get information on Serial API		
		this.serialInterface.sendSimpleRequest(MessageSerialApiGetInitData);
		
		//TODO: Find out why this isn't returning any information
		SerialMessage newMessage = new SerialMessage(MessageSerialApiApplicationNodeInfo, SerialInterface.MessageTypeRequest);
    	// 0x01 is get only the node information for those listening, Generic, Specific, Length
    	byte[] newPayload = { 0x01, 0x02, 0x01, 0x00};
    	newMessage.setMessagePayload(newPayload);
    	this.serialInterface.sendMessage(newMessage);
		
	}
	
	private void handleMessageSerialAPISoftResetResponse(SerialMessage message){
		logger.info("Handle Message Serial API Soft Reset");
	}
	
	private void handleMessageSendDataResponse(SerialMessage message){
		logger.info("Handle Message Send Data Response");
		
		if(message.getMessageBuffer()[2] != 0x00)
			logger.info("Send Data successfully placed on stack.");
		else
			logger.info("Send Data was not placed on stack due to error.");
		
	}
	
	private void handleMessageSendDataRequest(SerialMessage message){
		logger.info("Handle Message Send Data Request");
	}
	
	private void handleManufactureSpecificRequest(SerialMessage message) {
		logger.info("Handle Message Manufacture Specific Request");
		
		int nodeId = message.getMessagePayload()[1];
		logger.debug(String.format("Recieved ManufactureSpcific Information for Node ID = %d", nodeId));
		
		if(message.getMessagePayload()[4] == ZWaveCommandClass.COMMAND_CLASS_MANUFACTURER_SPECIFIC.REPORT.getCommand()){
			logger.debug("Process Manufacturer Specific Report");
			
			short tempMan = (short) ((message.getMessagePayload()[5] << 8) | (message.getMessagePayload()[6] & 0xFF));
			short tempDeviceType = (short) ((message.getMessagePayload()[7] << 8) | (message.getMessagePayload()[8] & 0xFF));
			short tempDeviceId = (short) ((message.getMessagePayload()[9] << 8) | (message.getMessagePayload()[10] & 0xFF));
			
			this.zwaveNodes.get(nodeId).setManufacturer(tempMan);
			this.zwaveNodes.get(nodeId).setDeviceType(tempDeviceType);
			this.zwaveNodes.get(nodeId).setDeviceId(tempDeviceId);
			
			logger.debug(String.format("Node %d Manufacture ID = 0x%04x", nodeId, this.zwaveNodes.get(nodeId).getManufacturer()));
			logger.debug(String.format("Node %d Device Type = 0x%04x", nodeId, this.zwaveNodes.get(nodeId).getDeviceType()));
			logger.debug(String.format("Node %d Device ID = 0x%04x", nodeId, this.zwaveNodes.get(nodeId).getDeviceId()));
			
		}
		
		// TODO: Handle the rest of the zwave init stages
		this.zwaveNodes.get(nodeId).setQueryStageTimeStamp(Calendar.getInstance().getTime());
		this.zwaveNodes.get(nodeId).setNodeStage(ZWaveNode.NodeStage.NODEBUILDINFO_DONE);
		
		checkNodesInitComplete();
	}
	
	private void handleMessageGetVersionResponse(SerialMessage message){
		logger.info("Handle Message Get Version");
	}
	
	private void handleMessageGetRandomResponse(SerialMessage message){
		logger.info("Handle Message Get Random");
	}
	
	private void handleMessageMemoryGetIDResponse(SerialMessage message){
		logger.info("Handle Message Memory Get ID");
	}
	
	private void handleMessageGetNodeProtcolInfoResponse(SerialMessage message){
		logger.info("Handle Message Get Node ProtocolInfo Response");
		
		int nodeId = this.serialInterface.isWaitingResponseFromNode;
		logger.info("Current Message Node = " + nodeId);
		
		this.zwaveNodes.get(nodeId).setHomeId(this.homeId);
		
		boolean listening = (message.getMessagePayload()[0] & 0x80)!=0 ? true : false;
		boolean routing = (message.getMessagePayload()[0] & 0x40)!=0 ? true : false;
		int version = (message.getMessagePayload()[0] & 0x07) + 1;
		logger.debug("Listening = " + listening);
		logger.debug("Routing = " + routing);
		logger.debug("Version = " + version);
		
		this.zwaveNodes.get(nodeId).setListening(listening);
		this.zwaveNodes.get(nodeId).setRouting(routing);
		this.zwaveNodes.get(nodeId).setVersion(version);
		
		int basic = message.getMessagePayload()[3];
		int generic = message.getMessagePayload()[4];
		int specific = message.getMessagePayload()[5];
		logger.debug(String.format("Basic = 0x%x", basic));
		logger.debug(String.format("Generic = 0x%x", generic));
		logger.debug(String.format("Specific = 0x%x", specific));
		
		this.zwaveNodes.get(nodeId).setBasicDeviceClass(basic);
		this.zwaveNodes.get(nodeId).zCC.setBasicCommandClass(Basic.getBasic(basic));
		this.zwaveNodes.get(nodeId).setGenericDeviceClass(generic);
		this.zwaveNodes.get(nodeId).zCC.setGenericCommandClass(Generic.getGeneric(generic));
		this.zwaveNodes.get(nodeId).setSpecificDeviceClass(specific);
		this.zwaveNodes.get(nodeId).zCC.setSpecificCommandClass(Specific.getSpecific(Generic.getGeneric(generic), specific));
		
		this.zwaveNodes.get(nodeId).setInfoRx(true);
		//this.zwaveNodes.get(nodeId).setNodeStage(ZWaveNode.NodeStage.NODEBUILDINFO_DONE);
		
		// This is done in the next stage for us
		//this.zwaveNodes.get(nodeId).setQueryStageTimeStamp(Calendar.getInstance().getTime());
		//this.zwaveNodes.get(nodeId).setNodeStage(ZWaveNode.NodeStage.NODEBUILDINFO_MANSPEC01);
		
		if(nodeId != 1)
			this.requestManufacturerSpecific(nodeId);
		else {
			this.zwaveNodes.get(nodeId).setQueryStageTimeStamp(Calendar.getInstance().getTime());
			this.zwaveNodes.get(nodeId).setNodeStage(ZWaveNode.NodeStage.NODEBUILDINFO_DONE); // do this b/c we already got the ManSpec data from previous stage (assumes Node 01 is controller)
		}
			
		//Calendar cal = Calendar.getInstance().getTime();
	    this.zwaveNodes.get(nodeId).setLastUpdated(Calendar.getInstance().getTime());
		
		// Check if we are done rx node info and can set binding to ready.
		checkNodesInitComplete();
		
	}

	
	//private void handleMessageReplicationSendData(SerialMessage message){
	//	logger.info("Handle Message Replication Send Data");
	//}
	
	//private void handleMessageReplicationCommandComplete(byte[] messagePayload){
	//	logger.info("Handle Message Replication Command Complete");
	//}
	
	private void handleMessageAssignReturnRouteResponse(SerialMessage message){
		logger.info("Handle Message Assign Return Route Response");
	}
	
	private void handleMessageAssignReturnRouteRequest(SerialMessage message){
		logger.info("Handle Message Assign Return Route Request");
	}
	
	private void handleMessageDeleteReturnRouteResponse(SerialMessage message){
		logger.info("Handle Message Delete Return Route Response");
	}
	
	private void handleMessageDeleteReturnRouteRequest(SerialMessage message){
		logger.info("Handle Message Delete Return Route Request");
	}
	
	private void handleMessageEnableSUCResponse(SerialMessage message){
		logger.info("Handle Message Enable SUC Response");
	}
	
	//private void handleMessageRequestNetworkUpdate(byte[] messagePayload, byte messageType){
	//	logger.info("Handle Message Request Network Update");
	//}
	
	private void handleMessageSetSUCNodeIDResponse(SerialMessage message){
		logger.info("Handle Message Set SUC Node ID Resposne");
	}
	
	private void handleMessageGetSUCNodeIDResponse(SerialMessage message){
		logger.info("Handle Message Get SUC Node ID Response");
	}
	
	//private void handleMessageRequestNodeInfo(byte[] messagePayload){
	//	logger.info("Handle Message Request Node Info");
	//}
	
	//private void handleMessageSendNodeInformation(byte[] messagePayload){
	//	logger.info("Handle Message Send Node Information");
	//}
	
	private void handleMessageNodeNeighborUpdateRequest(SerialMessage message){
		logger.info("Handle Message Serial API Get Init Data Request");
	}
	
	private void handleMessageApplicationUpdateRequest(SerialMessage message){
		logger.info("Handle Message Application Update Request");
		
		// Off message for picture box lights = 01 10 00 49 84 02 0A 04 10 01 25 27 73 70 86 72 77 BD
		// On message for picture box lights = 01 10 00 49 84 02 0A 04 10 01 25 27 73 70 86 72 77 BD
		// payload = 84 02 0A 04 10 01 25 27 73 70 86 72 77
		// from ozw; node = data[3]
		// from oh; node = payload[1]
		
		//TODO: Zwave devices with a remote control do not report this update. Figure out how to handle.
		
				
		logger.info("Application Update Request from Node {}" + message.getMessagePayload()[1]);
		
		switch (message.getMessagePayload()[0]){
		case (byte) 0x84:
			// Update Node Info; something changed
			logger.info("Application update request, updating node info.");
			requestLevel(message.getMessagePayload()[1]);
			break;
		case (byte) 0x82:
			logger.info("Application update request, need to handle Node Info Request Done.");
			break;
		case (byte) 0x81:
			logger.info("Application update request, need to handle Node Info Request Failed.");
			break;
		case (byte) 0x80:
			logger.info("Application update request, need to handle Node Infor Request Pending.");
			break;
		default:
			logger.debug("TODO: Implement Application Update Request Handleing of {}." + message.getMessagePayload()[0]);
		}
		
	}
	
	private void handleMessageAddNodeToNetworkRequest(SerialMessage message){
		logger.info("Handle Message Add Node To Network Request");
	}
	
	private void handleMessageRemoveNodeFromNetworkRequest(SerialMessage message){
		logger.info("Handle Message Remove Node From Network Request");
	}
	
	private void handleMessageCreateNewPrimaryRequest(SerialMessage message){
		logger.info("Handle Message Create new Primary Request");
	}
	
	private void handleMessageControllerChangeRequest(SerialMessage message){
		logger.info("Handle Message Controller Change");
	}
	
	private void handleMessageSetLearnModeRequest(SerialMessage message){
		logger.info("Handle Message Set Learn Mode Request");
	}
	
	private void handleMessageRemoveFailedNodeIDResponse(SerialMessage messaged){
		logger.info("Handle Message Remove Failed Node ID");
	}
	
	private void handleMessageIsFailedNodeIDResponse(SerialMessage message){
		logger.info("Handle Message Is Failed Node ID");
	}
	
	private void handleMessageReplaceFailedNodeResponse(SerialMessage message){
		logger.info("Handle Message Replace Failed Node Response");
	}
	
	private void handleMessageReplaceFailedNodeRequest(SerialMessage message){
		logger.info("Handle Message Replace Failed Node Request");
	}
	
	private void handleMessageGetRoutingInfoResponse(SerialMessage message){
		logger.info("Handle Message Get Routing Info");
	}
	
	private void handleMessageRFPowerLevelSetResponse(SerialMessage message){
		logger.info("Handle Message RF Power Level Set Response");
	}
	
	private void handleMessageReadMemoryResponse(SerialMessage message){
		logger.info("Handle Message Read Memory Response");
	}
	
	private void handleMessageSerialAPISetTimeoutsResponse(SerialMessage message){
		logger.info("Handle Message Serial API Set Timeouts Response");
	}
	
	private void handleMessageMemoryGetByteResponse(SerialMessage message){
		logger.info("Handle Message Memory Get Byte Response");
	}
	
	private void handleMessageGetVirtualNodesResponse(SerialMessage message){
		logger.info("Handle Message Get Virtual Nodes Response");
	}
	
	private void handleMessageSetSlaveLearnModeResponse(SerialMessage message){
		logger.info("Handle Message Set Slave Learn Mode Response");
	}
	
	private void handleMessageSetSlaveLearnModeRequest(SerialMessage message){
		logger.info("Handle Message Set Slave Learn Mode Request");
	}
	
	private void handleMessageSendSlaveNodeInfoRequest(SerialMessage message){
		logger.info("Handle Message Send Slave Node Info Request");
	}
	
	private void handleMessageApplicationCommandRequest(SerialMessage message){
		logger.info("Handle Message Application Command Request");
	}
	
	private void handleMessageApplicationSlaveCommandRequest(SerialMessage message){
		logger.info("Handle Message Application Slave Command Request");
	}
	
	private void handleMessagePromiscuousAppplicationCommandRequest(SerialMessage message){
		logger.info("Handle Message Promiscuous Application Command Request");
	}
	
	//private void handleMessageSetDefault(byte[] messagePayload){
	//	logger.info("Handle Message Set Default");
	//}
	
	//private void handleMessageDefault(byte[] messagePayload){
	//	logger.info("Handle Message Default");
	//}
	
	
	private void notifyEventListeners(ZWaveEvent event) {
		logger.debug("Notifying event listeners");
		for (ZWaveEventListener listener : this.zwaveEventListeners) {
			logger.debug("Notifying {}", listener.toString());
			listener.ZWaveIncomingEvent(event);
		}
	}

	public String getZWaveVersion() {
		return ZWaveVersion;
	}

	public void setZWaveVersion(String zWaveVersion) {
		ZWaveVersion = zWaveVersion;
	}

	public int getHomeId() {
		return homeId;
	}

	public void setHomeId(int homeId) {
		this.homeId = homeId;
	}

	public int getSelfNodeId() {
		return selfNodeId;
	}

	public void setSelfNodeId(int selfNodeId) {
		this.selfNodeId = selfNodeId;
	}

	public int getZWaveLibraryType() {
		return ZWaveLibraryType;
	}

	public void setZWaveLibraryType(int zWaveLibraryType) {
		ZWaveLibraryType = zWaveLibraryType;
	}

	public boolean isConnected() {
		return connected;
	}

	public void setConnected(boolean connected) {
		this.connected = connected;
	}	
	
	/**
	 * @return the zwaveNodes
	 */
	public Map<Integer, ZWaveNode> getZwaveNodes() {
		return zwaveNodes;
	}
	
	public void addEventListener(ZWaveEventListener eventListener) {
		this.zwaveEventListeners.add(eventListener);
	}

	public void removeEventListener(ZWaveEventListener eventListener) {
		this.zwaveEventListeners.remove(eventListener);
	}
	
	public Map<String, Integer> getSerialInterfaceStats() {
		Map<String, Integer> statMap = new HashMap<String, Integer>();
		
		logger.debug("Get Stats for SOF = {}" + this.serialInterface.getSOFCount());
		logger.debug("Get Stats for CAN = {}" + this.serialInterface.getCANCount());
		logger.debug("Get Stats for NAK = {}" + this.serialInterface.getNAKCount());
		logger.debug("Get Stats for ACK = {}" + this.serialInterface.getACKCount());
		logger.debug("Get Stats for OOF = {}" + this.serialInterface.getOOFCount());
		
		statMap.put("SOF", this.serialInterface.getSOFCount());
		statMap.put("CAN", this.serialInterface.getCANCount());
		statMap.put("NAK", this.serialInterface.getNAKCount());
		statMap.put("ACK", this.serialInterface.getACKCount());
		statMap.put("OOF", this.serialInterface.getOOFCount());
		//statMap.put("ACKW", this.serialInterface.getACKCount());
		
		return statMap;		
	}
	
}
