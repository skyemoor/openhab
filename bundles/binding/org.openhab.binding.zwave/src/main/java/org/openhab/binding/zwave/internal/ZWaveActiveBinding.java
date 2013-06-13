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
package org.openhab.binding.zwave.internal;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Dictionary;

import org.apache.commons.lang.StringUtils;
import org.openhab.binding.zwave.ZWaveBindingConfig;
import org.openhab.binding.zwave.ZWaveBindingProvider;
import org.openhab.binding.zwave.ZWaveCommandClass;
import org.openhab.binding.zwave.ZWaveReportCommands;
import org.openhab.binding.zwave.internal.protocol.SerialInterface;
import org.openhab.binding.zwave.internal.protocol.ZWaveController;
import org.openhab.binding.zwave.internal.protocol.ZWaveEvent;
import org.openhab.binding.zwave.internal.protocol.ZWaveEventListener;
import org.openhab.binding.zwave.internal.protocol.ZWaveNode;
import org.openhab.core.binding.AbstractActiveBinding;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implement this class if you are going create an actively polling service
 * like querying a Website/Device.
 * 
 * @author Victor Belov
 * @since 1.2.0
 */
public class ZWaveActiveBinding extends AbstractActiveBinding<ZWaveBindingProvider> implements ManagedService, ZWaveEventListener {

	private static final Logger logger = LoggerFactory.getLogger(ZWaveActiveBinding.class);

	private boolean isProperlyConfigured = false;

	/** the refresh interval which is used to poll values from the ZWave server (optional, defaults to 60000ms) */
	private long refreshInterval = 60000;
	private String port;
	private SerialInterface serialInterface;
	private ZWaveController zController;
	private boolean isZwaveNetworkReady = false;
	
	public ZWaveActiveBinding() {
	}

	public void activate() {
		logger.debug("activate()");
	}
	
	public void deactivate() {
		logger.debug("deactivate()");
	}

	/**
	 * @{inheritDoc}
	 */
	@Override
	protected long getRefreshInterval() {
		return refreshInterval;
	}

	/**
	 * @{inheritDoc}
	 */
	@Override
	protected String getName() {
		return "ZWave Refresh Service";
	}

	/**
	 * @{inheritDoc}
	 */
	@Override
	public boolean isProperlyConfigured() {
		return isProperlyConfigured;
	}

	/**
	 * @{inheritDoc}
	 */
	@Override
	protected void execute() {
		
		if(!isZwaveNetworkReady){
			logger.debug("Zwave Network isn't ready yet!");
			this.zController.checkForDeadOrSleepingNodes();
			return;
		}
		
			State value = UnDefType.UNDEF;
			
			// the frequently executed code goes here ...
			for (ZWaveBindingProvider provider : providers) {
				for (String itemName : provider.getItemNames()) {
					
					String nodeIdString = provider.getZwaveData(itemName).getNodeId();
					ZWaveReportCommands rCommand = provider.getZwaveData(itemName).getCommand();
					//logger.debug("updating nodeIdString = {}, nodeCommand = {}", nodeIdString, rCommand.toString());
					try {
						
						//TODO: implement a better means then polling to get values
						//this.zController.requestLevel(Integer.parseInt(nodeIdString));
						
						// Binding specified a command if rCommand is anything but NONE
						if(rCommand != ZWaveReportCommands.NONE)
						{
													
							//logger.debug("From Zwave Active Binding Execute::item found that needs reporting, posting update.");
							ZWaveNode zNode = this.zController.getZwaveNodes().get(Integer.parseInt(nodeIdString));
							if (rCommand == ZWaveReportCommands.HOMEID)
								value = new StringType(String.format("0x%08X", zNode.getHomeId()));
							else if (rCommand == ZWaveReportCommands.NODEID)
								value = new StringType(String.format("%d", zNode.getNodeId()));
							else if (rCommand == ZWaveReportCommands.MANUFACTURER)
								value = new StringType(String.format("0x%04x", zNode.getManufacturer()));
							else if (rCommand == ZWaveReportCommands.DEVICE_TYPE)
								value = new StringType(String.format("0x%04x", zNode.getDeviceType()));
							else if (rCommand == ZWaveReportCommands.DEVICE_TYPE_ID)
								value = new StringType(String.format("0x%04x", zNode.getDeviceId()));
							else if (rCommand == ZWaveReportCommands.BASIC)
								value = new StringType(String.format("0x%02x", zNode.getBasicDeviceClass()));
							else if (rCommand == ZWaveReportCommands.BASIC_LABEL)
								value = new StringType(String.format("%s", zNode.zCC.getBasicCommandClass().getLabel()));
							else if (rCommand == ZWaveReportCommands.GENERIC)
								value = new StringType(String.format("0x%02x", zNode.getGenericDeviceClass()));
							else if (rCommand == ZWaveReportCommands.GENERIC_LABEL)
								value = new StringType(String.format("%s", zNode.zCC.getGenericCommandClass().getLabel()));
							else if (rCommand == ZWaveReportCommands.SPECIFIC)
								value = new StringType(String.format("0x%02x", zNode.getSpecificDeviceClass()));
							else if (rCommand == ZWaveReportCommands.SPECIFIC_LABEL)
								value = new StringType(String.format("%s", zNode.zCC.getSpecificCommandClass().getLabel()));
							else if (rCommand == ZWaveReportCommands.VERSION)
								value = new StringType(String.format("%s", zNode.getVersion()));
							else if (rCommand == ZWaveReportCommands.ROUTING)
								value = new StringType(String.format("%s", (zNode.isRouting() == true) ? "True" : "False"));
							else if (rCommand == ZWaveReportCommands.LISTENING)
								value = new StringType(String.format("%s", (zNode.isListening() == true) ? "True" : "False"));
							else if (rCommand == ZWaveReportCommands.SLEEPING_DEAD)
								value = new StringType(String.format("%s", (zNode.isSleepingOrDead() == true) ? "True" : "False"));
							else if (rCommand == ZWaveReportCommands.NAK)
								value = new StringType(String.format("%s", this.zController.getSerialInterfaceStats().get("NAK")));
							else if (rCommand == ZWaveReportCommands.SOF)
								value = new StringType(String.format("%s", this.zController.getSerialInterfaceStats().get("SOF")));
							else if (rCommand == ZWaveReportCommands.CAN)
								value = new StringType(String.format("%s", this.zController.getSerialInterfaceStats().get("CAN")));
							else if (rCommand == ZWaveReportCommands.ACK)
								value = new StringType(String.format("%s", this.zController.getSerialInterfaceStats().get("ACK")));
							else if (rCommand == ZWaveReportCommands.OOF)
								value = new StringType(String.format("%s", this.zController.getSerialInterfaceStats().get("OOF")));
							else if (rCommand == ZWaveReportCommands.LASTUPDATE){
								SimpleDateFormat sdf = new SimpleDateFormat("MMM d yyyy HH:mm:ss");
								value = new StringType(String.format("%s", sdf.format(zNode.getLastUpdated()).toString() ));
							}
							else
								logger.info("ZWave Binding Reporting Type not supported! ZWave Report Command = {}", rCommand);
							
							eventPublisher.postUpdate(itemName, value);
						}
					} catch (NumberFormatException e) {
						// nop
					}
				}
			}		
	
	}

	@Override
	protected void internalReceiveCommand(String itemName, Command command) {
		// if we are not yet initialized, don't waste time and return
		if((isProperlyConfigured == false) | (isZwaveNetworkReady == false)) {
			logger.debug("internalReceiveCommand Called, But Not Properly Configure yet or Zwave Network Isn't Ready, returning.");
			return;
		}

		logger.debug("internalReceiveCommand({}, {})", itemName, command.toString());
		for (ZWaveBindingProvider provider : providers) {
			logger.debug("BindingProvider = {}", provider.toString());
			logger.debug("Got nodeId = {}, command = {}", provider.getZwaveData(itemName).getNodeId(),
					provider.getZwaveData(itemName).getCommand());
			String nodeIdString = provider.getZwaveData(itemName).getNodeId();
			logger.debug("nodeIdString = {}", nodeIdString);
			
			// TODO: Implement ZWaveNode NodeStage to ensure node information is complete
			if (nodeIdString.equals("zwavejoin")) {
				logger.debug("Special item - zwavejoin");
				if (command == OnOffType.ON) {
					//this.zController.startAddNodeToNetwork();
				} else if (command == OnOffType.OFF) {
					//this.zController.stopAddNodeToNetwork();
				}
			} else {
				int nodeId = Integer.valueOf(nodeIdString);
				//ZWaveCommandClass commandClass = provider.getZwaveData(itemName).getCommandClass();
				if (this.zController.isConnected()) {
					logger.debug("ZWaveController is connected");
					if (command == OnOffType.ON) {
						logger.debug("Sending ON");
						this.zController.sendLevel(nodeId, 255);
					} else if (command == OnOffType.OFF) {
						logger.debug("Sending OFF");
						this.zController.sendLevel(nodeId, 0);					
					//} else if (command instanceof PercentType) {
					//	PercentType pt = (PercentType) command;
					//	logger.debug("Sending PercentType, value " + pt.intValue());
					//	this.zController.sendLevel(nodeId, pt.intValue());					
					} else {
						logger.warn("Unknown command >{}<", command.toString());
					}
				} else {
					logger.warn("ZWaveController is not connected");
				}
			}
		}
	}

	/**
	 * @{inheritDoc}
	 */
	@Override
	public void updated(Dictionary<String, ?> config) throws ConfigurationException {
		if (config != null) {
			String refreshIntervalString = (String) config.get("refresh");
			if (StringUtils.isNotBlank(refreshIntervalString)) {
				refreshInterval = Long.parseLong(refreshIntervalString);
			}
			if (StringUtils.isNotBlank((String) config.get("port"))) {
				port = (String) config.get("port");
				logger.info("Update config, port = {}", port);
				this.serialInterface = new SerialInterface(port);
				this.zController = new ZWaveController(serialInterface);
				zController.initialize();
				zController.addEventListener(this);
			}
			isProperlyConfigured = true;
		}
		
	}

	public String getPort() {
		return port;
	}

	public void setPort(String port) {
		this.port = port;
	}

	@Override
	public void ZWaveIncomingEvent(ZWaveEvent event) {
		
		// if we are not yet initialized, don't waste time and return
		if(isProperlyConfigured) {
			logger.debug("ZWaveIncomingEvent Called, checking if NETWORK EVENT.");
			if(event.getEventType() == ZWaveEvent.NETWORK_EVENT){
				logger.debug("ZWaveIncomingEvent Called, Network Event, checking if Init Done Event.");
				if(event.getEventValue().equalsIgnoreCase("INIT_DONE")){
					logger.debug("ZWaveIncomingEvent Called, Network Event, Init Done. Setting ZWave Network Ready.");
					//TODO Find some way to not let anything process until INit is done. i.e. notify OH
					isZwaveNetworkReady = true;
					
				}
				else {
					logger.debug("ZWaveIncomingEvent Called, But Zwave Network is not done Init, returning.");
					return;
				}
			}else {
				if(!isZwaveNetworkReady){
					logger.debug("Zwave Network Not Ready yet.");
					return;
				}
			}
		}
		
		State value = UnDefType.UNDEF;
		
		logger.debug("ZwaveIncomingEvent");
		switch (event.getEventType()) {
			case ZWaveEvent.SWITCH_EVENT:
			case ZWaveEvent.DIMMER_EVENT:
				logger.debug("Got a " + event.getEventType() + " event from Z-Wave network for nodeId = {}, state = {}", event.getNodeId(), event.getEventValue());
				for (ZWaveBindingProvider provider : providers) {
					logger.info("Trying to find Item through {} provider", provider.toString());
					for (String itemName : provider.getItemNames()) {
						logger.info("Looking in {}", itemName);
						ZWaveBindingConfig bindingConfig = provider.getZwaveData(itemName);
						logger.info("{} {}", bindingConfig.getNodeId(), bindingConfig.getCommand());
						logger.info("{}", String.valueOf(event.getNodeId()));
						try {
							if (bindingConfig.getNodeId() != "zwavejoin") {
								if (Integer.valueOf(bindingConfig.getNodeId()) == event.getNodeId()) {
									logger.debug("NodeId match");
									//if (bindingConfig.getCommandClass() == ZWaveCommandClass.SWITCH || bindingConfig.getCommandClass() == ZWaveCommandClass.DIMMER) {
										//logger.debug("CommandClass match");
										logger.debug("Will send an update to {}", itemName);
										if (event.getEventValue().equals("ON")) {
											eventPublisher.postUpdate(itemName, OnOffType.ON);
										} else if (event.getEventValue().equals("OFF")) {
											eventPublisher.postUpdate(itemName, OnOffType.OFF);
										} else {
											// dimmer value
											logger.debug("Zwave Event Value not of type of ON or OFF.");
											//eventPublisher.postUpdate(itemName, new PercentType(event.getEventValue()));
										}
								}
							}
						} catch (NumberFormatException e) {
							logger.error("The nodeId from binding config is not integer: '" + bindingConfig.getNodeId() + "'");
						}
					}
				}
				break;
			default:
				break;
		}
				
	}

}
