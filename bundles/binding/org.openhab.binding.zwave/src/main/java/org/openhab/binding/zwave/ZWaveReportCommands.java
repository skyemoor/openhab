package org.openhab.binding.zwave;

import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.types.Type;

/**
 * @author Brian Crosby
 * @since 1.3.0
 */

public enum ZWaveReportCommands {
	
	/** Default. */
	NONE {
		{
			command = "NOP";
			typeClass = DecimalType.class;
		}
	},
	
	/** Default. */
	HOMEID {
		{
			command = "HOMEID";
			typeClass = DecimalType.class;
		}
	},
	/** Default. */
	NODEID {
		{
			command = "NODEID";
			typeClass = DecimalType.class;
		}
	},
	/** Default. */
	LISTENING {
		{
			command = "LISTENING";
			typeClass = OnOffType.class;
		}
	},
	
	/** Default. */
	//TODO: Make a sleeping AND a dead reporting type. Need more information from Node befure this is possible.
	SLEEPING_DEAD {
		{
			command = "SLEEPING_DEAD";
			typeClass = OnOffType.class;
		}
	},
	
	/** Default. */
	ROUTING {
		{
			command = "ROUTING";
			typeClass = OnOffType.class;
		}
	},
	/** Default. */
	VERSION {
		{
			command = "VERSION";
			typeClass = DecimalType.class;
		}
	},
	/** BASIC. */
	BASIC {
		{
			command = "BASIC";
			typeClass = DecimalType.class;
		}
	},
	
	BASIC_LABEL {
		{
			command = "BASIC_LABEL";
			typeClass = DecimalType.class;
		}
	},
	
	/** GENERIC. */
	GENERIC {
		{
			command = "GENERIC";
			typeClass = DecimalType.class;
		}
	},
	
	GENERIC_LABEL {
		{
			command = "GENERIC_LABEL";
			typeClass = DecimalType.class;
		}
	},
	
	/** Default. */
	SPECIFIC {
		{
			command = "SPECIFIC";
			typeClass = DecimalType.class;
		}
	},
	
	/** Default. */
	SPECIFIC_LABEL {
		{
			command = "SPECIFIC";
			typeClass = DecimalType.class;
		}
	},
	
	/** Default. */
	MANUFACTURER {
		{
			command = "MANUFACTURER";
			typeClass = DecimalType.class;
		}
	},
	
	/** Default. */
	DEVICE_TYPE_ID {
		{
			command = "DEVICE_TYPE_ID";
			typeClass = DecimalType.class;
		}
	},
	
	/** Default. */
	DEVICE_TYPE {
		{
			command = "DEVICE_TYPE";
			typeClass = DecimalType.class;
		}
	},
	
	/** Default. */
	LASTUPDATE {
		{
			command = "LASTUPDATE";
			typeClass = StringType.class;
		}
	},

	/** Default. */
	SOF {
		{
			command = "SOF";
			typeClass = StringType.class;
		}
	},
	
	/** Default. */
	CAN {
		{
			command = "CAN";
			typeClass = StringType.class;
		}
	},
	
	/** Default. */
	NAK {
		{
			command = "NAK";
			typeClass = StringType.class;
		}
	},
	
	/** Default. */
	OOF {
		{
			command = "OOF";
			typeClass = StringType.class;
		}
	},
	
	/** Default. */
	ACK {
		{
			command = "ACK";
			typeClass = StringType.class;
		}
	};
	
	String command;
	Class<? extends Type> typeClass;
	
	/**
	 * @return the command
	 */
	public String getZWaveCommand() {
		return command;
	}
	/**
	 * @param command the command to set
	 */
	public void setZWaveCommand(String command) {
		this.command = command;
	}
	/**
	 * @return the typeClass
	 */
	public Class<? extends Type> getTypeClass() {
		return typeClass;
	}
	/**
	 * @param typeClass the typeClass to set
	 */
	public void setTypeClass(Class<? extends Type> typeClass) {
		this.typeClass = typeClass;
	}
	
	
	
}
