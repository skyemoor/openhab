package org.openhab.binding.zwave;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Brian Crosby
 * @since 1.3.0
 */

public class ZWaveCommandClass {
	
	private static final Logger logger = LoggerFactory.getLogger(ZWaveCommandClass.class);
	
	public enum Basic {
		NOTKNOWN(0, "Not Know", "N/A"),
		CONTROLLER(1, "Controller", "N/A"),
		STATIC_CONTROLLER(2, "Static Controller", "N/A"),
		SLAVE(3, "Slave", "N/A"),
		ROUTING_SLAVE(4, "Routing Slave", "N/A");
		
		/**
	     * A mapping between the integer code and its corresponding Basic CC to facilitate lookup by code.
	     */
	    private static Map<Integer, Basic> codeToBasicMapping;
		
		private int key;
		private String label;
		private String desc;
		
		private Basic (int key, String label, String desc){
			this.key = key;
			this.label = label;
			this.desc = desc;
		}

		 public static Basic getBasic(int i) {
		        if (codeToBasicMapping == null) {
		            initMapping();
		        }
		        Basic result = null;
		        for (Basic s : values()) {
		            result = codeToBasicMapping.get(i);
		        }
		        return result;
		 }
		 
		    private static void initMapping() {
		    	codeToBasicMapping = new HashMap<Integer, Basic>();
		        for (Basic s : values()) {
		        	codeToBasicMapping.put(s.key, s);
		        }
		    }
		
		/**
		 * @return the label
		 */
		public String getLabel() {
			return label;
		}
				
	}
	
	public enum Generic {
		NOTKNOWN(0, "Not Know", "N/A"),
		REMOTE_CONTROLLER(1, "Remote Controller", "N/A"),
		STATIC_CONTOLLER(2, "Static Controller", "N/A"),
		AV_CONTROL_POINT(3, "A/V Control Point", "N/A"),
		DISPLAY(4, "Display", "N/A"),
		THERMOSTAT(8, "Thermostat", "N/A"),
		WINDOW_COVERING(9, "Window Covering", "N/A"),
		REPEATER_SLAVE(15, "Repeater Slave", "N/A"),
		BINARY_SWITCH(16, "Binary Switch", "N/A"),
		MULTILEVEL_SWITCH(17, "Multi-Level Switch", "N/A"),
		REMOTE_SWITCH(18, "Remote Switch", "N/A"),
		TOGGLE_SWITCH(19, "Toggle Switch", "N/A"),
		Z_IP_GATEWAY(20, "Z/IP Gateway", "N/A"),
		Z_IP_NODE(21, "Z/IP Node", "N/A"),
		VENTILATION(22, "Ventilation", "N/A"),
		BINARY_SENSOR(32, "Binary Sensor", "N/A"),
		MULTILEVEL_SENSOR(33, "Multi-Level Sensor", "N/A"),
		PULSE_METER(48, "Pulse Meter", "N/A"),
		METER(49, "Meter", "N/A"),
		ENTRY_CONTROL(64, "Entry Control", "N/A"),
		SEMI_INTEROPERABLE(80, "Semi-Interoperable", "N/A"),
		NON_INTEROPERBALE(255, "Non-Interoperable", "N/A");
		
		/**
	     * A mapping between the integer code and its corresponding Generic CC to facilitate lookup by code.
	     */
	    private static Map<Integer, Generic> codeToGenericMapping;
		
		private int key;
		private String label;
		private String desc;
		
		private Generic (int key, String label, String desc) {
			this.key = key;
			this.label = label;
			this.desc = desc;
		}
		
		public static Generic getGeneric(int i) {
	        if (codeToGenericMapping == null) {
	            initMapping();
	        }
	        Generic result = null;
	        for (Generic s : values()) {
	            result = codeToGenericMapping.get(i);
	        }
	        return result;
	 }
	 
	    private static void initMapping() {
	    	codeToGenericMapping = new HashMap<Integer, Generic>();
	        for (Generic s : values()) {
	        	codeToGenericMapping.put(s.key, s);
	        }
	    }
	
	/**
	 * @return the label
	 */
	public String getLabel() {
		return label;
	}
		
	}
	
	public enum Specific {
		NOTKNOWN(0, 0, Generic.NOTKNOWN, "Not Know", "N/A"),
		STATIC_PC_CONTROLLER(1, 1, Generic.STATIC_CONTOLLER, "Static PC Controller", "N/A"),
		BINARY_PWR_SWITCH(2, 1, Generic.BINARY_SWITCH, "Binary Power Switch", "N/A"),
		BINARY_SCN_SWITCH(3, 2, Generic.BINARY_SWITCH, "Binary Scene Switch", "N/A"),
		MULTI_POWER_SWITCH(4, 1, Generic.MULTILEVEL_SWITCH, "Multi-Level Power Switch", "N/A");
		
		/**
	     * A mapping between the integer code and its corresponding Generic CC to facilitate lookup by code.
	     */
	    private static Map<Integer, Specific> codeToSpecificMapping;
		
	    private int index;
		private int key;
		private String label;
		private String desc;
		private Generic gCC;
		
		private Specific (int index, int key, Generic gCC ,String label, String desc) {
			this.index = index;
			this.key = key;
			this.label = label;
			this.desc = desc;
			this.gCC = gCC;
		}
		
		public static Specific getSpecific(Generic gCC, int i) {
	        //if (codeToSpecificMapping == null) {
	        //    initMapping();
	        //}
	        
	        Specific result = null;
	        for (Specific s : values()) {
	            if((s.gCC == gCC) && (s.key == i)){
	            	return s;
	            }
	        }
	        return result;
		}
	 
	    private static void initMapping() {
	    	codeToSpecificMapping = new HashMap<Integer, Specific>();
	        for (Specific s : values()) {
	        	codeToSpecificMapping.put(s.key, s);
	        }
	    }
	
		/**
		 * @return the label
		 */
		public String getLabel() {
			return label;
		}
			
	}
	
	//public enum COMMAND_CLASS_ALARM	 0x71
	public enum COMMAND_CLASS_APPLICATION_STATUS {
		ID(0x22),
		BUSY(0x01),
		REJECTED_REQUEST(0x02);
		
		/**
		 * @param command
		 */
		private COMMAND_CLASS_APPLICATION_STATUS(int command) {
			this.command = command;
		}

		private int command;
		
		public int getCommand(){
			return this.command;
		}
		
	};
	//public enum COMMAND_CLASS_ASSOCIATION_COMMAND_CONFIGURATION	 0x9B	 
	//public enum COMMAND_CLASS_ASSOCIATION	 0x85	 
	//public enum COMMAND_CLASS_ASSOCIATION_V2	 0x85	 
	//public enum COMMAND_CLASS_AV_CONTENT_DIRECTORY_MD	 0x95	 
	//public enum COMMAND_CLASS_AV_CONTENT_SEARCH_MD	 0x97	 
	//public enum COMMAND_CLASS_AV_RENDERER_STATUS	 0x96	 
	//public enum COMMAND_CLASS_AV_TAGGING_MD	 0x99	 
	//public enum COMMAND_CLASS_BASIC_WINDOW_COVERING	 0x50	 
	public enum COMMAND_CLASS_BASIC{
		ID(0x20),
		SET(0x01),
		GET(0x02),
		REPORT(0x03);
		
		/**
		 * @param command
		 */
		private COMMAND_CLASS_BASIC(int command) {
			this.command = command;
		}

		private int command;
		
		public int getCommand(){
			return this.command;
		}
	}
	//public enum COMMAND_CLASS_BATTERY	 0x80	 
	//public enum COMMAND_CLASS_CHIMNEY_FAN	 0x2A	 
	//public enum COMMAND_CLASS_CLIMATE_CONTROL_SCHEDULE	 0x46	 
	//public enum COMMAND_CLASS_CLOCK	 0x81	 
	//public enum COMMAND_CLASS_COMPOSITE	 0x8D	 
	public enum COMMAND_CLASS_CONFIGURATION{
		ID(0x70),
		SET(0x04),
		GET(0x05),
		REPORT(0x06);

		/**
		 * @param command
		 */
		private COMMAND_CLASS_CONFIGURATION(int command) {
			this.command = command;
		}

		private int command;
		
		public int getCommand(){
			return this.command;
		}
		
	};
	//public enum COMMAND_CLASS_CONTROLLER_REPLICATION	 0x21	 
	//public enum COMMAND_CLASS_DOOR_LOCK	 0x62	 
	//public enum COMMAND_CLASS_DOOR_LOCK_LOGGING	 0x4C	 
	//public enum COMMAND_CLASS_ENERGY_PRODUCTION	 0x90	 
	//public enum COMMAND_CLASS_FIRMWARE_UPDATE_MD	 0x7A	 
	//public enum COMMAND_CLASS_GEOGRAPHIC_LOCATION	 0x8C	 
	//public enum COMMAND_CLASS_GROUPING_NAME	 0x7B	 
	//public enum COMMAND_CLASS_HAIL	 0x82	 
	//public enum COMMAND_CLASS_INDICATOR	 0x87	 
	//public enum COMMAND_CLASS_IP_CONFIGURATION	 0x9A	 
	//public enum COMMAND_CLASS_LANGUAGE	 0x89	 
	//public enum COMMAND_CLASS_LOCK	 0x76	 
	//public enum COMMAND_CLASS_MANUFACTURER_PROPRIETARY {
	//	ID(0x91),
	//	SET(),
	//	GET(),
	//	REPORT();
	//};	 
	public enum COMMAND_CLASS_MANUFACTURER_SPECIFIC	 {
		ID(0x72),
		GET(0x04),
		REPORT(0x05);
		
		/**
		 * @param command
		 */
		private COMMAND_CLASS_MANUFACTURER_SPECIFIC(final int command) {
			this.command = command;
		}

		private final int command;
		
		public int getCommand(){
			return this.command;
		}
		
	};
	
	public enum COMMAND_CLASS_MARK	{
		ID(0xEF);
		
		/**
		 * @param command
		 */
		private COMMAND_CLASS_MARK(int command) {
			this.command = command;
		}

		private int command;
		
		public int getCommand(){
			return this.command;
		}
	}; 
	//public enum COMMAND_CLASS_METER_PULSE	 0x35	 
	//public enum COMMAND_CLASS_METER	 0x32	 
	//public enum COMMAND_CLASS_MTP_WINDOW_COVERING	 0x51	 
	//public enum COMMAND_CLASS_MULTI_CHANNEL_ASSOCIATION_V2	 0x8E	 
	//public enum COMMAND_CLASS_MULTI_CHANNEL_V2	 0x60	 
	//public enum COMMAND_CLASS_MULTI_CMD	 0x8F	 
	//public enum COMMAND_CLASS_MULTI_INSTANCE_ASSOCIATION	 0x8E
	
	public enum COMMAND_CLASS_MULTI_INSTANCE {
		ID(0x60),
		INSTANCE_GET(0x04),
		INSTANCE_REPORT(0x05),
		INSTANCE_ENCAP(0x06),
		ENDPOINT_GET(0x07),
		ENDPOINT_REPORT(0x08),
		CAPABILITY_GET(0x09),
		CAPABILITY_REPORT(0x0a),
		ENDPOINT_FIND(0x0b),
		ENDPOINT_FIND_REPORT(0x0c),
		ENDPOINT_ENCAP(0x0d);		
		
		/**
		 * @param command
		 */
		private COMMAND_CLASS_MULTI_INSTANCE(int command) {
			this.command = command;
		}

		private int command;
		
		public int getCommand(){
			return this.command;
		}
	}
	
	public enum COMMAND_CLASS_NO_OPERATION	{
		ID(0x00);
		
		/**
		 * @param command
		 */
		private COMMAND_CLASS_NO_OPERATION(int command) {
			this.command = command;
		}

		private int command;
		
		public int getCommand(){
			return this.command;
		}
	}; 
	//public enum COMMAND_CLASS_NODE_NAMING	 0x77	 
	//public enum COMMAND_CLASS_NON_INTEROPERABLE	 0xF0	 
	public enum COMMAND_CLASS_POWERLEVEL	{
		ID(0x73),
		SET(0x01),
		GET(0x02),
		REPORT(0x03),
		TEST_NODE_SET(0x04),
		TEST_NODE_GET(0x05),
		TEST_NODE_REPORT(0x06);
		
		/**
		 * @param command
		 */
		private COMMAND_CLASS_POWERLEVEL(int command) {
			this.command = command;
		}

		private int command;
		
		public int getCommand(){
			return this.command;
		}
	}; 
	public enum COMMAND_CLASS_PROPRIETARY	{
		ID(0x88),
		SET(0x01),
		GET(0x02),
		REPORT(0x03);
		
		/**
		 * @param command
		 */
		private COMMAND_CLASS_PROPRIETARY(int command) {
			this.command = command;
		}

		private int command;
		
		public int getCommand(){
			return this.command;
		}
	}; 
	//public enum COMMAND_CLASS_PROTECTION	 0x75	 
	//public enum COMMAND_CLASS_PROTECTION_V2	 0x75	 
	//public enum COMMAND_CLASS_REMOTE_ASSOCIATION_ACTIVATE	 0x7C	 
	//public enum COMMAND_CLASS_REMOTE_ASSOCIATION	 0x7D	 
	//public enum COMMAND_CLASS_SCENE_ACTIVATION	 0x2B	 
	//public enum COMMAND_CLASS_SCENE_ACTUATOR_CONF	 0x2C	 
	//public enum COMMAND_CLASS_SCENE_CONTROLLER_CONF	 0x2D	 
	//public enum COMMAND_CLASS_SCHEDULE_ENTRY_LOCK	 0x4E	 
	//public enum COMMAND_CLASS_SCREEN_ATTRIBUTES	 0x93	 
	//public enum COMMAND_CLASS_SCREEN_ATTRIBUTES_V2	 0x93	 
	//public enum COMMAND_CLASS_SCREEN_MD	 0x92	 
	//public enum COMMAND_CLASS_SCREEN_MD_V2	 0x92	 
	//public enum COMMAND_CLASS_SECURITY	 0x98	 
	//public enum COMMAND_CLASS_SENSOR_ALARM	 0x9C	 
	//public enum COMMAND_CLASS_SENSOR_BINARY	 0x30	 
	//public enum COMMAND_CLASS_SENSOR_CONFIGURATION	 0x9E	 
	//public enum COMMAND_CLASS_SENSOR_MULTILEVEL	 0x31	 
	//public enum COMMAND_CLASS_SENSOR_MULTILEVEL_V2	 0x31	 
	//public enum COMMAND_CLASS_SILENCE_ALARM	 0x9D	 
	//public enum COMMAND_CLASS_SIMPLE_AV_CONTROL	 0x94	 
	public enum COMMAND_CLASS_SWITCH_ALL	{
		ID(0x27),
		SET(0x01),
		GET(0x02),
		REPORT(0x03),
		ON(0x04),
		OFF(0x05);
		
		/**
		 * @param command
		 */
		private COMMAND_CLASS_SWITCH_ALL(int command) {
			this.command = command;
		}

		private int command;
		
		public int getCommand(){
			return this.command;
		}
	}; 
	public enum COMMAND_CLASS_SWITCH_BINARY	{
		ID(0x25),
		SET(0x01),
		GET(0x02),
		REPORT(0x03);
		
		/**
		 * @param command
		 */
		private COMMAND_CLASS_SWITCH_BINARY(int command) {
			this.command = command;
		}

		private int command;
		
		public int getCommand(){
			return this.command;
		}
	}; 
	public enum COMMAND_CLASS_SWITCH_MULTILEVEL	{
		ID(0x26),
		SET(0x01),
		GET(0x02),
		REPORT(0x03),
		START_LEVEL_CHANGE(0x04),
		STOP_LEVEL_CHANGE(0x05),
		SUPPORTED_GET(0x06),
		SUPPORTED_REPORT(0x07);
		
		/**
		 * @param command
		 */
		private COMMAND_CLASS_SWITCH_MULTILEVEL(int command) {
			this.command = command;
		}

		private int command;
		
		public int getCommand(){
			return this.command;
		}
	}; 
	//public enum COMMAND_CLASS_SWITCH_MULTILEVEL_V2	 0x26	 
	//public enum COMMAND_CLASS_SWITCH_TOGGLE_BINARY	{
	//	ID(0x28),
	//	SET(),
	//	GET(),
	//	REPORT();
	//};
	//public enum COMMAND_CLASS_SWITCH_TOGGLE_MULTILEVEL	{
	//	ID(0x29),
	//	SET(),
	//	GET(),
	//	REPORT();
	//}; 
	//public enum COMMAND_CLASS_THERMOSTAT_FAN_MODE	 0x44	 
	//public enum COMMAND_CLASS_THERMOSTAT_FAN_STATE	 0x45	 
	//public enum COMMAND_CLASS_THERMOSTAT_HEATING	 0x38	 
	//public enum COMMAND_CLASS_THERMOSTAT_MODE	 0x40	 
	//public enum COMMAND_CLASS_THERMOSTAT_OPERATING_STATE	 0x42	 
	//public enum COMMAND_CLASS_THERMOSTAT_SETBACK	 0x47	 
	//public enum COMMAND_CLASS_THERMOSTAT_SETPOINT	 0x43	 
	//public enum COMMAND_CLASS_TIME_PARAMETERS	 0x8B	 
	//public enum COMMAND_CLASS_TIME	 0x8A	 
	//public enum COMMAND_CLASS_USER_CODE	 0x63	 
	public enum COMMAND_CLASS_VERSION	{
		ID(0x86),
		GET(0x11),
		REPORT(0x12),
		CC_GET(0x13),
		CC_REPORT(0x14);
		
		/**
		 * @param command
		 */
		private COMMAND_CLASS_VERSION(int command) {
			this.command = command;
		}

		private int command;
		
		public int getCommand(){
			return this.command;
		}
	}; 
	//public enum COMMAND_CLASS_WAKE_UP	 0x84	 
	//public enum COMMAND_CLASS_WAKE_UP_V2	 0x84	 
	//public enum COMMAND_CLASS_ZIP_ADV_CLIENT	 0x34	 
	//public enum COMMAND_CLASS_ZIP_ADV_SERVER	 0x33	 
	//public enum COMMAND_CLASS_ZIP_ADV_SERVICES	 0x2F	 
	//public enum COMMAND_CLASS_ZIP_CLIENT	 0x2E	 
	//public enum COMMAND_CLASS_ZIP_SERVER	 0x24	 
	//public enum COMMAND_CLASS_ZIP_SERVICES	 0x23	 
	
	private Basic basicCommandClass;
	private Generic genericCommandClass;
	private Specific specificCommandClass;
	
	public ZWaveCommandClass(Basic bCC, Generic gCC, Specific sCC){
		logger.debug("Loading Zwave Command Class");
		
		this.basicCommandClass = bCC;
		this.genericCommandClass = gCC;
		this.specificCommandClass = sCC;
		
	}

	/**
	 * @return the basicCommandClass
	 */
	public Basic getBasicCommandClass() {
		return basicCommandClass;
	}

	/**
	 * @param basicCommandClass the basicCommandClass to set
	 */
	//public void setBasicCommandClass(Basic basicCommandClass) {
	//	this.basicCommandClass = basicCommandClass;
	//}
	
	public void setBasicCommandClass(Basic bCC) {
		this.basicCommandClass = bCC;
	}

	/**
	 * @return the genericCommandClass
	 */
	public Generic getGenericCommandClass() {
		return genericCommandClass;
	}

	/**
	 * @param genericCommandClass the genericCommandClass to set
	 */
	public void setGenericCommandClass(Generic genericCommandClass) {
		this.genericCommandClass = genericCommandClass;
	}
	
	/**
	 * @param specificCommandClass the genericCommandClass to set
	 */
	public void setSpecificCommandClass(Specific specificCommandClass) {
		this.specificCommandClass = specificCommandClass;
	}
	
	/**
	 * @return the genericCommandClass
	 */
	public Specific getSpecificCommandClass() {
		return specificCommandClass;
	}
	
}
