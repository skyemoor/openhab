package org.openhab.binding.motion.internal;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import org.openhab.core.events.EventPublisher;
import org.openhab.core.library.types.OnOffType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Camera {
	
	private static final Logger logger = LoggerFactory.getLogger(Camera.class);

	private final String cameraId;
	private final EventPublisher eventPublisher;
	
	private final List<String> itemNames = 
			Collections.synchronizedList(new ArrayList<String>());
	
	private String url;
	
	private MonitorThread monitorThread;
	
	public Camera(String cameraId, EventPublisher eventPublisher) {
		this.cameraId = cameraId;
		this.eventPublisher = eventPublisher;
	}
	
	public String getCameraId() {
		return cameraId;
	}
	
	public String getUrl() {
		return url;
	}
	
	public void setUrl(String url) {
		this.url = url;
	}
	
	private List<String> getItemNames() {
		return new ArrayList<String>(itemNames);
	}
	
	public void registerItemName(String itemName) {
		if (!itemNames.contains(itemName))
			itemNames.add(itemName);
	}
	
	public void start() {
		monitorThread = new MonitorThread();
		monitorThread.start();
	}
	
	public void stop() {
		monitorThread.interrupt();
	}
	
	class MonitorThread extends Thread {
		
		public MonitorThread() {
			setName("Motion-Monitor-" + cameraId);
		}
	
		@Override
		public void run() {
			// TODO: create a connection to the camera stream
			// TODO: register a listener to receive motion events (handlers below)
			while (!isInterrupted()) {
				try {
					// CRIT: testing code - switch motion detection on/off every 5sec
					Thread.sleep(5000);
					motionDetected(new MotionEvent(this, true, 1, Calendar.getInstance(), cameraId, null, null));
					Thread.sleep(5000);
					motionCeased(new MotionEvent(this, false, 1, Calendar.getInstance(), cameraId, null, null));
				} catch (Exception e) {
					logger.error("Error monitoring camera feed", e);
				}
			}
		}
		
		public void motionDetected(MotionEvent motionEvent) {
			// TODO: handle Contact item types as well
			for (String itemName : getItemNames()) {
				eventPublisher.postUpdate(itemName, OnOffType.ON);
			}
		}

		public void motionCeased(MotionEvent motionEvent) {
			// TODO: handle Contact item types as well
			for (String itemName : getItemNames()) {
				eventPublisher.postUpdate(itemName, OnOffType.OFF);
			}
		}
	}
	
	// CRIT: copied from WS's raw code just to get compiling
	// CRIT: this can be deleted once we reference WS's JAR 
	class MotionEvent extends java.util.EventObject {
		private static final long serialVersionUID = 1L;
		
		private final boolean motionState;
		private  Calendar eventDateTime;
		private String cameraId;
		private long eventId;
		private String knownDetails;
		private String eventSubdirectory;
		
		//here's the constructor
	    public MotionEvent(Object source, 
	    					boolean motionState, 
	    					long eventId, 
	    					Calendar eventDateTime, 
	    					String cameraId, 
	    					String knownDetails,
	    					String eventSubdirectory) {
	        super(source);
	        this.motionState = motionState;
	        this.eventDateTime = eventDateTime;
	        this.cameraId = cameraId;
	        this.eventId = eventId;
	        this.eventSubdirectory = eventSubdirectory;
	        this.knownDetails = knownDetails;
	    }
	    
	    public Long getEventId() { return this.eventId; }
	    public boolean getMotionState () { return this.motionState; }
	    public Calendar getEventDateTime() { return this.eventDateTime; }
	    public String getCameraId() { return this.cameraId; }
	    public String getKnownDetails() { return this.knownDetails; }
	    public String getEventSubdirectory() { return this.eventSubdirectory; } 
	}
}
