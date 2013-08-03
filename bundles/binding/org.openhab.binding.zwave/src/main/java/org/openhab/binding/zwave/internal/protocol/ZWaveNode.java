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

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.openhab.binding.zwave.internal.commandclass.ZWaveCommandClass;
import org.openhab.binding.zwave.internal.commandclass.ZWaveMultiInstanceCommandClass;
import org.openhab.binding.zwave.internal.commandclass.ZWaveCommandClass.CommandClass;
import org.openhab.binding.zwave.internal.protocol.ZWaveDeviceClass.Basic;
import org.openhab.binding.zwave.internal.protocol.ZWaveDeviceClass.Generic;
import org.openhab.binding.zwave.internal.protocol.ZWaveDeviceClass.Specific;

/**
 * Z-Wave node class. Represents a node in the Z-Wave network.
 * @author Brian Crosby
 * @since 1.3.0
 */
public class ZWaveNode {

	private final ZWaveDeviceClass deviceClass;

	private int homeId;
	private int nodeId;
	private int version;
	
	private String name;
	private String location;
	
	private int manufacturer;
	private int deviceId;
	private int deviceType;
	
	private boolean listening;			// i.e. sleeping
	private boolean routing;
	
	private Map<CommandClass, ZWaveCommandClass> supportedCommandClasses = new HashMap<CommandClass, ZWaveCommandClass>();
	private Date lastUpdated; 
	private Date queryStageTimeStamp;
	private NodeStage nodeStage;

	
	// TODO: Implement ZWaveNodeValue for Nodes that store multiple values.
	
	/**
	 * Constructor. Creates a new instance of the ZWaveNode class.
	 * @param homeId the home ID to use.
	 * @param nodeId the node ID to use.
	 */
	public ZWaveNode(int homeId, int nodeId) {
		this.homeId = homeId;
		this.nodeId = nodeId;
		this.nodeStage = NodeStage.NODEBUILDINFO_EMPTYNODE;
		this.deviceClass = new ZWaveDeviceClass(Basic.NOT_KNOWN, Generic.NOT_KNOWN, Specific.NOT_USED);
	}

	/**
	 * Gets the node ID.
	 * @return
	 */
	public int getNodeId() {
		return nodeId;
	}

	/**
	 * Gets whether the node is listening.
	 * @return boolean indicating whether the node is listening or not.
	 */
	public boolean isListening() {
		return listening;
	}
	
	/**
	 * Sets whether the node is listening.
	 * @param listening
	 */
	public void setListening(boolean listening) {
		this.listening = listening;
	}

	/**
	 * Gets whether the node is sleeping or dead.
	 * @return
	 */
	public boolean isSleepingOrDead(){
		if(this.nodeStage == ZWaveNode.NodeStage.NODEBUILDINFO_DEAD)
			return true;
		else
			return false;
	}
	
	/**
	 * Gets the home ID
	 * @return the homeId
	 */
	public Integer getHomeId() {
		return homeId;
	}

	/**
	 * Gets the node name.
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * Sets the node name.
	 * @param name the name to set
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Gets the node location.
	 * @return the location
	 */
	public String getLocation() {
		return location;
	}

	/**
	 * Sets the node location.
	 * @param location the location to set
	 */
	public void setLocation(String location) {
		this.location = location;
	}

	/**
	 * Gets the manufacturer of the node.
	 * @return the manufacturer
	 */
	public int getManufacturer() {
		return manufacturer;
	}

	/**
	 * Sets the manufacturer of the node.
	 * @param tempMan the manufacturer to set
	 */
	public void setManufacturer(int tempMan) {
		this.manufacturer = tempMan;
	}

	/**
	 * Gets the device id of the node.
	 * @return the deviceId
	 */
	public int getDeviceId() {
		return deviceId;
	}

	/**
	 * Sets the device id of the node.
	 * @param tempDeviceId the device to set
	 */
	public void setDeviceId(int tempDeviceId) {
		this.deviceId = tempDeviceId;
	}

	/**
	 * Gets the device type of the node.
	 * @return the deviceType
	 */
	public int getDeviceType() {
		return deviceType;
	}

	/**
	 * Sets the device type of the node.
	 * @param tempDeviceType the deviceType to set
	 */
	public void setDeviceType(int tempDeviceType) {
		this.deviceType = tempDeviceType;
	}

	/**
	 * Get the date/time the node was last updated.
	 * @return the lastUpdated
	 */
	public Date getLastUpdated() {
		return lastUpdated;
	}

	/**
	 * Set the date/time the node was last updated.
	 * @param lastUpdated the lastUpdated to set
	 */
	public void setLastUpdated(Date lastUpdated) {
		this.lastUpdated = lastUpdated;
	}

	/**
	 * Gets the node stage.
	 * @return the nodeStage
	 */
	public NodeStage getNodeStage() {
		return nodeStage;
	}

	/**
	 * Sets the node stage.
	 * @param nodeStage the nodeStage to set
	 */
	public void setNodeStage(NodeStage nodeStage) {
		
		this.nodeStage = nodeStage;
	}

	/**
	 * Gets the node version
	 * @return the version
	 */
	public int getVersion() {
		return version;
	}

	/**
	 * Sets the node version.
	 * @param version the version to set
	 */
	public void setVersion(int version) {
		this.version = version;
	}

	/**
	 * Gets whether the node is routing messages.
	 * @return the routing
	 */
	public boolean isRouting() {
		return routing;
	}

	/**
	 * Sets whether the node is routing messages.
	 * @param routing the routing to set
	 */
	public void setRouting(boolean routing) {
		this.routing = routing;
	}

	/**
	 * Gets the time stamp the node was last queried.
	 * @return the queryStageTimeStamp
	 */
	public Date getQueryStageTimeStamp() {
		return queryStageTimeStamp;
	}

	/**
	 * Sets the time stamp the node was last queried.
	 * @param queryStageTimeStamp the queryStageTimeStamp to set
	 */
	public void setQueryStageTimeStamp(Date queryStageTimeStamp) {
		this.queryStageTimeStamp = queryStageTimeStamp;
	}

	/**
	 * @return the deviceClass
	 */
	public ZWaveDeviceClass getDeviceClass() {
		return deviceClass;
	}

	/**
	 * Returns the Command classes this node implements.
	 * @return the command classes.
	 */
	public Collection<ZWaveCommandClass> getCommandClasses() {
		return supportedCommandClasses.values();
	}
	
	/**
	 * Returns a commandClass object this node implements.
	 * @param commandClass The command class to get.
	 * @return the command class.
	 * @throws IllegalArgumentException thrown when this node does not support this command class.
	 */
	public ZWaveCommandClass getCommandClass(CommandClass commandClass) throws IllegalArgumentException
	{
		if (!supportedCommandClasses.containsKey(commandClass))
			throw new IllegalArgumentException(String.format("Command class not supported by node %d", getNodeId()));
		
		return supportedCommandClasses.get(commandClass);
	}
	
	/**
	 * Adds a command class to the list of supported command classes by this node.
	 * Does nothing if command class is already added.
	 * @param commandClass the command class instance to add.
	 */
	public void addCommandClass(ZWaveCommandClass commandClass)
	{
		CommandClass key = commandClass.getCommandClass();
		
		if (!supportedCommandClasses.containsKey(key))
			supportedCommandClasses.put(key, commandClass);
	}
	
	/**
	 * Resolves a command class for this node. First endpoint is checked. 
	 * If endpoint == 1 or (endpoint != 1 and version of the multi instance 
	 * command == 1) then return a supported command class on the node itself. 
	 * If endpoint != 1 and version of the multi instance command == 2 then
	 * first try command classes of endpoints. If not found the return a  
	 * supported command class on the node itself.
	 * @param commandClass The command class to resolve.
	 * @param endpointId the endpoint / instance to resolve this command class for.
	 * @return the command class.
	 * @throws IllegalArgumentException thrown when this node does not support this command class.
	 */
	public ZWaveCommandClass resolveCommandClass(CommandClass commandClass, int endpointId) throws IllegalArgumentException
	{
		ZWaveMultiInstanceCommandClass multiInstanceCommandClass = (ZWaveMultiInstanceCommandClass)supportedCommandClasses.get(CommandClass.MULTI_INSTANCE);
		
		if (multiInstanceCommandClass != null && multiInstanceCommandClass.getVersion() == 2) {
			try {
				ZWaveEndpoint endpoint = multiInstanceCommandClass.getEndpoint(endpointId);
				ZWaveCommandClass result = endpoint.getCommandClass(commandClass);
				if (result != null)
					return result;
			} catch (IllegalArgumentException e) {
			}
		}
		
		return getCommandClass(commandClass);
	}
	

	/**
	 * Node Stage Enumeration. Represents the state the node
	 * is in.
	 * @author Brian Crosby
	 * @since 1.3.0
	 */
	public enum NodeStage {
		
		/* Possible Query stages. 
		 * Originally Sourced from openzwave, LinuxMCE, and other opensource documentation. Merged into OH and no longer the valid.
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
		
		/**
		 * Get the stage protocol number.
		 * @return number
		 */
		public int getStage() {
			return this.stage;
		}
		
		/**
		 * Get the stage label
		 * @return label
		 */
		public String getLabel() {
			return this.label;
		}
	}
}
