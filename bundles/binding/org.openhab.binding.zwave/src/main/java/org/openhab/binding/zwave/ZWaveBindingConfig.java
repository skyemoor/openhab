package org.openhab.binding.zwave;

import org.openhab.binding.zwave.internal.protocol.ZWaveNode;
import org.openhab.core.binding.BindingConfig;

/**
 * @author Victor Belov & Brian Crosby
 * @since 1.3.0
 */

public class ZWaveBindingConfig implements BindingConfig {
	
	public ZWaveBindingConfig(String nodeId, int endpoint, ZWaveReportCommands command ) {
		this.nodeId = nodeId;
		this.command = command;
		this.endpoint = endpoint;
	}
	
	private String nodeId;
	private int	endpoint;
	private ZWaveReportCommands command;
	
	public String getNodeId() {
		return nodeId;
	}
	
	public int getEndpoint() {
		return endpoint;
	}

	/**
	 * @return the 
	 */
	public ZWaveReportCommands getCommand() {
		return command;
	}
}