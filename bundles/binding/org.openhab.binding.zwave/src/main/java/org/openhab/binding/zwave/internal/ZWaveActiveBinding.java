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
import org.openhab.binding.zwave.ZWaveBindingAction;
import org.openhab.binding.zwave.internal.protocol.SerialInterface;
import org.openhab.binding.zwave.internal.protocol.SerialInterfaceException;
import org.openhab.binding.zwave.internal.protocol.ZWaveController;
import org.openhab.binding.zwave.internal.protocol.ZWaveEvent;
import org.openhab.binding.zwave.internal.protocol.ZWaveEvent.ZWaveEventType;
import org.openhab.binding.zwave.internal.protocol.ZWaveEventListener;
import org.openhab.binding.zwave.internal.protocol.ZWaveNode;
import org.openhab.core.binding.AbstractActiveBinding;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ZWaveActiveBinding Class. Polls Z-Wave nodes frequently and
 * also handles events coming from the Z-Wave controller.
 * @author Victor Belov
 * @author Brian Crosby
 * @since 1.3.0
 */
public class ZWaveActiveBinding extends AbstractActiveBinding<ZWaveBindingProvider> implements ManagedService, ZWaveEventListener {

	private static final Logger logger = LoggerFactory.getLogger(ZWaveActiveBinding.class);
	private String port;
	private SerialInterface serialInterface;
	private ZWaveController zController;

	private boolean isProperlyConfigured = false;
	private boolean isZwaveNetworkReady = false;

	/* The refresh interval which is used to poll values from the ZWave server (optional, defaults to 10000ms). 
	 * The delay is a 10ms factor of refreshing non critical values.
	 */
	private long refreshInterval = 10000;
	private int refreshDelay = 6;
	private int refreshCount = 0;
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	protected long getRefreshInterval() {
		return refreshInterval;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected String getName() {
		return "ZWave Refresh Service";
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected boolean isProperlyConfigured() {
		return isProperlyConfigured;	
	}

	/**
	 * Working method that executes refreshing of the bound items. The method is executed
	 * at every refresh interval. The nodes are polled only every 6 refreshes.
	 */
	@Override
	protected void execute() {
		
		if(!isZwaveNetworkReady){
			logger.debug("Zwave Network isn't ready yet!");
			if (this.zController != null)
				this.zController.checkForDeadOrSleepingNodes();
			return;
		}
		
		refreshCount++;
		if(refreshCount > refreshDelay){
			logger.debug("Reseting Refresh Count to Zero");
			refreshCount = 0;
		}
		else{
			logger.debug(String.format("Refresh Count: %d", refreshCount));
		}
			
		// loop all binding providers for the Z-wave binding.
		for (ZWaveBindingProvider provider : providers) {
			// loop all bound items for this provider
			for (String itemName : provider.getItemNames()) {
				
				// default value for the binding.
				State value = UnDefType.UNDEF;
				int nodeId = provider.getZwaveBindingConfig(itemName).getNodeId();
				int endpoint = provider.getZwaveBindingConfig(itemName).getEndpoint();
				ZWaveBindingAction action = provider.getZwaveBindingConfig(itemName).getAction();
				
				ZWaveNode zNode = this.zController.getNode(nodeId);
				
				//TODO: implement a better means then polling to get values.
				//JWS: I don't think there is a better way, until we find out why some nodes just don't inform changes.
				switch (action) {
					case NONE: // just a plain node; no reporting.
						if (refreshCount == 0)
							this.zController.requestLevel(zNode.getNodeId(), endpoint);
						continue; // next item
					case REPORT_HOMEID:
						value = new StringType(String.format("0x%08X", zNode.getHomeId()));
						break;
					case REPORT_NODEID:
						value = new StringType(String.format("%d", zNode.getNodeId()));
						break;
					case REPORT_MANUFACTURER:
						value = new StringType(String.format("0x%04x", zNode.getManufacturer()));
						break;
					case REPORT_DEVICE_TYPE:
						value = new StringType(String.format("0x%04x", zNode.getDeviceType()));
						break;
					case REPORT_DEVICE_TYPE_ID:
						value = new StringType(String.format("0x%04x", zNode.getDeviceId()));
						break;
					case REPORT_BASIC:
						value = new StringType(String.format("0x%02x", zNode.getDeviceClass().getBasicDeviceClass().getKey()));
						break;
					case REPORT_BASIC_LABEL:
						value = new StringType(String.format("%s", zNode.getDeviceClass().getBasicDeviceClass().getLabel()));
						break;
					case REPORT_GENERIC:
						value = new StringType(String.format("0x%02x", zNode.getDeviceClass().getGenericDeviceClass().getKey()));
						break;
					case REPORT_GENERIC_LABEL:
						value = new StringType(String.format("%s", zNode.getDeviceClass().getGenericDeviceClass().getLabel()));
						break;
					case REPORT_SPECIFIC:
						value = new StringType(String.format("0x%02x", zNode.getDeviceClass().getSpecificDeviceClass().getKey()));
						break;
					case REPORT_SPECIFIC_LABEL:
						value = new StringType(String.format("%s", zNode.getDeviceClass().getSpecificDeviceClass().getLabel()));
						break;
					case REPORT_VERSION:
						value = new StringType(String.format("%s", zNode.getVersion()));
						break;
					case REPORT_ROUTING:
						value = new StringType(String.format("%s", (zNode.isRouting() == true) ? "True" : "False"));
						break;
					case REPORT_LISTENING:
						value = new StringType(String.format("%s", (zNode.isListening() == true) ? "True" : "False"));
						break;
					case REPORT_SLEEPING_DEAD:
						value = new StringType(String.format("%s", (zNode.isSleepingOrDead() == true) ? "True" : "False"));
						break;
					case REPORT_NAK:
						value = new StringType(String.format("%d", this.serialInterface.getNAKCount()));
						break;
					case REPORT_SOF:
						value = new StringType(String.format("%d", this.serialInterface.getSOFCount()));
						break;
					case REPORT_CAN:
						value = new StringType(String.format("%d", this.serialInterface.getCANCount()));
						break;
					case REPORT_ACK:
						value = new StringType(String.format("%d", this.serialInterface.getACKCount()));
						break;
					case REPORT_OOF:
						value = new StringType(String.format("%d", this.serialInterface.getOOFCount()));
						break;
					case REPORT_LASTUPDATE:
						SimpleDateFormat sdf = new SimpleDateFormat("MMM d yyyy HH:mm:ss");
						value = new StringType(sdf.format(zNode.getLastUpdated()).toString());
						break;
					case ZWAVE_JOIN:
						continue; // no refresh on join action item, next item
					default:
						logger.warn("ZWave Binding Action not supported! ZWave Binding Action = {}", action);
						continue; // next item
				}
				// post update on the bus
				eventPublisher.postUpdate(itemName, value);
			}
		}		
	}
	
	/**
	 * Handles a command update by sending the appropriate Z-Wave instructions
	 * to the controller.
	 * {@inheritDoc}
	 */
	@Override
	protected void internalReceiveCommand(String itemName, Command command) {
		// if we are not yet initialized, don't waste time and return
		if((isProperlyConfigured == false) | (isZwaveNetworkReady == false)) {
			logger.debug("internalReceiveCommand Called, But Not Properly Configure yet or Zwave Network Isn't Ready, returning.");
			return;
		}

		logger.debug("internalReceiveCommand(itemname = {}, Command = {})", itemName, command.toString());
		for (ZWaveBindingProvider provider : providers) {
			ZWaveBindingConfig bindingConfig = provider.getZwaveBindingConfig(itemName);
			
			int nodeId = bindingConfig.getNodeId();
			int endpoint = bindingConfig.getEndpoint();
			ZWaveBindingAction action = bindingConfig.getAction();
			
			logger.debug("BindingProvider = {}", provider.toString());
			logger.debug("Got nodeId = {}, endpoint = {}, action = {}", new Object[] { nodeId, endpoint, action });
			
			// TODO: Implement ZWaveNode NodeStage to ensure node information is complete
			// JWS: is this necessary? isZwaveNetworkReady already ensures a complete network.
			// I do see problems with dead or sleeping nodes though.
			switch (action) {
				case ZWAVE_JOIN:
					logger.debug("Special item - zwavejoin");
					if (command == OnOffType.ON) {
						//this.zController.startAddNodeToNetwork();
					} else if (command == OnOffType.OFF) {
						//this.zController.stopAddNodeToNetwork();
					}
					break;
				default:
					// TODO: check supported command class etc. 
					if (this.zController.isConnected()) {
						logger.debug("ZWaveController is connected");
						if (command == OnOffType.ON) {
							logger.debug("Sending ON");
							this.zController.sendLevel(nodeId, endpoint, 255);
						} else if (command == OnOffType.OFF) {
							logger.debug("Sending OFF");
							this.zController.sendLevel(nodeId, endpoint, 0);					
						} else if (command instanceof PercentType) {
							PercentType pt = (PercentType) command;
							int value = pt.intValue();
							if (value == 100) {
								value = 99;
							}
							logger.debug("Sending PercentType, value " + value);
							this.zController.sendLevel(nodeId, endpoint, value);
						} else {
							logger.warn("Unknown command >{}<", command.toString());
						}
					} else {
						logger.warn("ZWaveController is not connected");
					}
					break;
			}
		}
	}
	
	/**
	 * Activates the binding. Actually does nothing, because on activation
	 * OpenHAB always calls updated to indicate that the config is updated.
	 * Activation is done there.
	 */
	@Override
	public void activate() {
		
	}
	
	/**
	 * Deactivates the binding. The Controller is stopped and the serial interface
	 * is closed as well.
	 */
	@Override
	public void deactivate() {
		isZwaveNetworkReady = false;
		if (zController != null) {
			zController.removeEventListener(this);
			this.zController = null;
		}
		if (this.serialInterface != null)
		{
			this.serialInterface.disconnect();
			this.serialInterface = null;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void updated(Dictionary<String, ?> config) throws ConfigurationException {
		if (config != null) {
			
			// Check refresh interval configuration value
			String refreshIntervalString = (String) config.get("refresh");
			if (StringUtils.isNotBlank(refreshIntervalString)) {
				try {
					refreshInterval = Long.parseLong(refreshIntervalString);
				} catch (NumberFormatException ex) {
					isProperlyConfigured = false;
					throw new ConfigurationException("refresh", ex.getLocalizedMessage(), ex);
				}
			}
			
			// Check refresh delay configuration value.
			String refreshDelayString = (String) config.get("refreshDelay");
			if (StringUtils.isNotBlank(refreshDelayString)) {
				try {
					refreshDelay = Integer.parseInt(refreshDelayString);
				} catch (NumberFormatException ex) {
					isProperlyConfigured = false;
					throw new ConfigurationException("refresh", ex.getLocalizedMessage(), ex);
				}
			}
			
			// Check the serial port configuration value.
			// This value is mandatory.
			if (StringUtils.isNotBlank((String) config.get("port"))) {
				try {
					port = (String) config.get("port");
					logger.info("Update config, port = {}", port);
					isProperlyConfigured = true;
					this.deactivate();
					this.serialInterface = new SerialInterface(port);
					this.zController = new ZWaveController(serialInterface);
					zController.initialize();
					zController.addEventListener(this);
					return;
				} catch (SerialInterfaceException ex) {
					isProperlyConfigured = false;
					throw new ConfigurationException("port", ex.getLocalizedMessage(), ex);
				}
			}
		}
		isProperlyConfigured = false;
	}

	/**
	 * Returns the port value.
	 * @return
	 */
	public String getPort() {
		return port;
	}

	/**
	 * Event handler method for incoming Z-Wave events.
	 * @param event the incoming Z-Wave event.
	 */
	@Override
	public void ZWaveIncomingEvent(ZWaveEvent event) {
		
		// if we are not yet initialized, don't waste time and return
		if (!isProperlyConfigured)
			return;
		
		if (!isZwaveNetworkReady) {
			if (event.getEventType() == ZWaveEventType.NETWORK_EVENT && ((String)event.getEventValue()).equalsIgnoreCase("INIT_DONE")) {
				logger.debug("ZWaveIncomingEvent Called, Network Event, Init Done. Setting ZWave Network Ready.");
				isZwaveNetworkReady = true;
			} else {
				// TODO: Find some way to not let anything process until INit is done. i.e. notify OH
				logger.debug("Zwave Network Not Ready yet.");
			}			
			return;
		}
		
		logger.debug("ZwaveIncomingEvent");
		switch (event.getEventType()) {
			case BASIC_EVENT:
			case SWITCH_EVENT:
			case DIMMER_EVENT:
				logger.debug("Got a " + event.getEventType() + " event from Z-Wave network for nodeId = {}, state = {}, endpoint = {}", new Object[] { event.getNodeId(), event.getEventValue(), event.getEndpoint() } );
				for (ZWaveBindingProvider provider : providers) {
					logger.debug("Trying to find Item through {} provider", provider.toString());
					for (String itemName : provider.getItemNames()) {
						logger.debug("Looking in {}", itemName);
						ZWaveBindingConfig bindingConfig = provider.getZwaveBindingConfig(itemName);
						logger.debug("{} {} {}", new Object[] { bindingConfig.getNodeId(), bindingConfig.getAction(), bindingConfig.getEndpoint() });
						logger.debug("{}", String.valueOf(event.getNodeId()));
						if (bindingConfig.getNodeId() == event.getNodeId() && bindingConfig.getEndpoint() == event.getEndpoint()) {
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
								eventPublisher.postUpdate(itemName, new PercentType((Integer)event.getEventValue()));
							}
						}
					}
				}
				break;
			default:
				logger.warn("Unknown event type {}", event.getEventType());
				break;
		}
	}
}
