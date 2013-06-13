package org.openhab.binding.zwave.internal.protocol;

import java.util.Date;
import java.util.HashSet;

import org.openhab.binding.zwave.ZWaveCommandClass;
import org.openhab.binding.zwave.ZWaveCommandClass.Basic;
import org.openhab.binding.zwave.ZWaveCommandClass.Generic;
import org.openhab.binding.zwave.ZWaveCommandClass.Specific;



/**
 * @author Brian Crosby
 * @since 1.3.0
 */

public class ZWaveNode {

	/* Possible Query stages. 
	 * Taken from openzwave
	QueryStage_ProtocolInfo,                             	< Retrieve protocol information 
    QueryStage_Probe,                                       < Ping device to see if alive 
    QueryStage_WakeUp,                                      < Start wake up process if a sleeping node 
    QueryStage_ManufacturerSpecific1,                       < Retrieve manufacturer name and product ids if ProtocolInfo lets us 
    QueryStage_NodeInfo,                                    < Retrieve info about supported, controlled command classes 
    QueryStage_ManufacturerSpecific2,                       < Retrieve manufacturer name and product ids 
    QueryStage_Versions,                                    < Retrieve version information 
    QueryStage_Instances,                                   < Retrieve information about multiple command class instances 
    QueryStage_Static,                                      < Retrieve static information (doesn't change) 
    QueryStage_Probe1,                                      < Ping a device upon starting with configuration 
    QueryStage_Associations,                                < Retrieve information about associations 
    QueryStage_Neighbors,                                   < Retrieve node neighbor list 
    QueryStage_Session,                                     < Retrieve session information (changes infrequently) 
    QueryStage_Dynamic,                                     < Retrieve dynamic information (changes frequently) 
    QueryStage_Configuration,                               < Retrieve configurable parameter information (only done on request) 
    QueryStage_Complete,                                    < Query process is completed for this node 
    QueryStage_None                                         < Query process hasn't started for this node 	
	*/
	
	private NodeStage nodeStage;
	
	public enum NodeStage {
		//TODO: add NodeStage Description to enum
		NODEBUILDINFO_EMPTYNODE(0, "Empty New Node"),
		NODEBUILDINFO_PROTOINFO(1, "Protocol Information"),
		NODEBUILDINFO_PING(2, "Ping Node"),
		NODEBUILDINFO_WAKEUP(3, "Wake Up"),
		NODEBUILDINFO_MANSPEC01(4, "Manufacture Name and Product Identification"),
		NODEBUILDINFO_DETAILS(5, "Node Information"),
		NODEBUILDINFO_MANSPEC02(6, "Manufacture Name and Product Identification"),
		NODEBUILDINFO_VERSION(7, "Node Version"),
		NODEBUILDINFO_INSTANCES(8, "Command Class Instances"),
		NODEBUILDINFO_STATIC(9, "Static Information"),
		NODEBUILDINFO_PROBE01(10, "Ping Node"),
		NODEBUILDINFO_ASSOCIATIONS(11, "Association Information"),
		NODEBUILDINFO_NEIGHBORS(12, "Node Neighbor Information"),
		NODEBUILDINFO_SESSION(13, "Infrequently Changed Information"),
		NODEBUILDINFO_DYNAMIC(14, "Frequently Changed Information"),
		NODEBUILDINFO_CONFIG(15, "Parameter Information"),
		NODEBUILDINFO_DONE(16, "Node Complete"),
		NODEBUILDINFO_INIT(17, "Node Not Started"),
		NODEBUILDINFO_DEAD(18, "Node Dead or Sleeping");
		
		private int stage;
		private String label;
		
		private NodeStage (int s, String l) {
			stage = s;
			label = l;
		}
		
		public int getStage() {
			return this.stage;
		}
		
		public String getLabel() {
			return this.label;
		}
	}
	
	/*
	public enum BasicClass {		
		CONTROLLER(1),
		STATIC_CONTROLLER(2),
		SLAVE(3),
		ROUTING_SLAVE(4);
		
		BasicClass(int id) {
			this.id = id;
		}
		
		private int id;
		
		public int getId() {
			return this.id;
		}
	} */
	
	private int homeId;
	private int nodeId;
	private String name;
	private String location;
	private short manufacturer;
	private short deviceId;
	private short deviceType;
	private boolean listening;			// i.e. sleeping
	private boolean routing;
	private HashSet<String> commandClasses;
	private int basicDeviceClass;
	private int genericDeviceClass;
	private int specificDeviceClass;
	private Boolean infoRx;
	private int version;
	
	private Date queryStageTimeStamp;
	
	public ZWaveCommandClass zCC;
	
	// TODO: Implement ZWaveNodeValue for Nodes that store multiple values.
	
	/**
	 * @return the infoRx
	 */
	public Boolean getInfoRx() {
		return infoRx;
	}

	/**
	 * @param infoRx the infoRx to set
	 */
	public void setInfoRx(Boolean infoRx) {
		this.infoRx = infoRx;
	}

	private Date lastUpdated; 
	
	public ZWaveNode(int homeId, int nodeId) {
		this.homeId = homeId;
		this.nodeId = nodeId;
		this.infoRx = false;
		this.nodeStage = NodeStage.NODEBUILDINFO_EMPTYNODE;
		this.zCC = new ZWaveCommandClass(Basic.NOTKNOWN, Generic.NOTKNOWN, Specific.NOTKNOWN);
	}

	public int getNodeId() {
		return nodeId;
	}
	public void setNodeId(int nodeId) {
		this.nodeId = nodeId;
	}

	public int getBasicDeviceClass() {
		return basicDeviceClass;
	}

	public void setBasicDeviceClass(int basicDeviceClass) {
		this.basicDeviceClass = basicDeviceClass;
	}

	public int getGenericDeviceClass() {
		return genericDeviceClass;
	}

	public void setGenericDeviceClass(int genericDeviceClass) {
		this.genericDeviceClass = genericDeviceClass;
	}

	public int getSpecificDeviceClass() {
		return specificDeviceClass;
	}

	public void setSpecificDeviceClass(int specificDeviceClass) {
		this.specificDeviceClass = specificDeviceClass;
	}

	public boolean isListening() {
		return listening;
	}
	
	public void setListening(boolean listening) {
		this.listening = listening;
	}

	public boolean isSleepingOrDead(){
		if(this.nodeStage == ZWaveNode.NodeStage.NODEBUILDINFO_DEAD)
			return true;
		else
			return false;
	}
	
	/**
	 * @return the homeId
	 */
	public Integer getHomeId() {
		return homeId;
	}

	/**
	 * @param homeId the homeId to set
	 */
	public void setHomeId(Integer homeId) {
		this.homeId = homeId;
	}

	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * @param name the name to set
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * @return the location
	 */
	public String getLocation() {
		return location;
	}

	/**
	 * @param location the location to set
	 */
	public void setLocation(String location) {
		this.location = location;
	}

	/**
	 * @return the manufacturer
	 */
	public Short getManufacturer() {
		return manufacturer;
	}

	/**
	 * @param manufacturer the manufacturer to set
	 */
	public void setManufacturer(Short manufacturer) {
		this.manufacturer = manufacturer;
	}

	/**
	 * @return the deviceId
	 */
	public Short getDeviceId() {
		return deviceId;
	}

	/**
	 * @param deviceId the device to set
	 */
	public void setDeviceId(Short deviceId) {
		this.deviceId = deviceId;
	}

	/**
	 * @return the deviceType
	 */
	public Short getDeviceType() {
		return deviceType;
	}

	/**
	 * @param deviceType the deviceType to set
	 */
	public void setDeviceType(Short deviceType) {
		this.deviceType = deviceType;
	}

	/**
	 * @return the commandClasses
	 */
	public HashSet<String> getCommandClasses() {
		return commandClasses;
	}

	/**
	 * @param commandClasses the commandClasses to set
	 */
	public void setCommandClasses(HashSet<String> commandClasses) {
		this.commandClasses = commandClasses;
	}

	/**
	 * @return the lastUpdated
	 */
	public Date getLastUpdated() {
		return lastUpdated;
	}

	/**
	 * @param lastUpdated the lastUpdated to set
	 */
	public void setLastUpdated(Date lastUpdated) {
		this.lastUpdated = lastUpdated;
	}

	/**
	 * @return the nodeStage
	 */
	public NodeStage getNodeStage() {
		return nodeStage;
	}

	/**
	 * @param nodeStage the nodeStage to set
	 */
	public void setNodeStage(NodeStage nodeStage) {
		
		this.nodeStage = nodeStage;
	}

	/**
	 * @return the version
	 */
	public int getVersion() {
		return version;
	}

	/**
	 * @param version the version to set
	 */
	public void setVersion(int version) {
		this.version = version;
	}

	/**
	 * @return the routing
	 */
	public boolean isRouting() {
		return routing;
	}

	/**
	 * @param routing the routing to set
	 */
	public void setRouting(boolean routing) {
		this.routing = routing;
	}

	/**
	 * @return the queryStageTimeStamp
	 */
	public Date getQueryStageTimeStamp() {
		return queryStageTimeStamp;
	}

	/**
	 * @param queryStageTimeStamp the queryStageTimeStamp to set
	 */
	public void setQueryStageTimeStamp(Date queryStageTimeStamp) {
		this.queryStageTimeStamp = queryStageTimeStamp;
	}

}
