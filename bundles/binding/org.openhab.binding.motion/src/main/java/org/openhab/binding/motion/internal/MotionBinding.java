/**
 * Copyright (c) 2010-2014, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.motion.internal;

import java.io.File;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.openhab.binding.motion.MotionBindingConfig;
import org.openhab.binding.motion.MotionBindingProvider;
import org.openhab.core.binding.AbstractBinding;
import org.openhab.core.binding.BindingProvider;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.types.Command;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Binding which communicates with (one or many) motion detecting cameras. 
 * 
 * @author Ben Jones
 * @since 1.5.0
 */
public class MotionBinding extends AbstractBinding<MotionBindingProvider> implements ManagedService {
	
	private static final Logger logger = LoggerFactory.getLogger(MotionBinding.class);

	// regEx to validate a motion camera config
	private final Pattern CAMERA_CONFIG_PATTERN = Pattern.compile("^(.*?)\\.(url|other)$");
	
    // configured cameras - keyed by cameraId
	private final Map<String, Camera> camerasById = new ConcurrentHashMap<String, Camera>();
	
	/**
	 * Start the binding service.
	 */
	@Override
	public void activate() {
		logger.debug("Activating Motion binding");
		super.activate();
	}

	/**
	 * Shut down the binding service.
	 */
	@Override
	public void deactivate() {	
		logger.debug("Deactivating Motion binding");
		super.deactivate();
		stopCameras();
	}
    
	/**
	 * @{inheritDoc}
	 */	
	@Override
	public void bindingChanged(BindingProvider provider, String itemName) {
		if (provider instanceof MotionBindingProvider) {
			MotionBindingProvider motionProvider = (MotionBindingProvider) provider;
			registerBindingConfig(motionProvider.getBindingConfig(itemName));
		}		
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void allBindingsChanged(BindingProvider provider) {
		if (provider instanceof MotionBindingProvider) {
			MotionBindingProvider motionProvider = (MotionBindingProvider) provider;
			for (String itemName : motionProvider.getItemNames()) {
				registerBindingConfig(motionProvider.getBindingConfig(itemName));
			}
		}
	}
	
	/**
	 * @{inheritDoc}
	 */
	@Override
	public void internalReceiveCommand(String itemName, Command command) {
		logger.trace("internalReceiveCommand(itemname = {}, command = {})", itemName, command.toString());
		
		for (MotionBindingProvider provider : providers) {
			MotionBindingConfig bindingConfig = provider.getBindingConfig(itemName);
			
			String cameraId = bindingConfig.getCameraId();
			
			Camera camera = camerasById.get(cameraId);
			if (camera == null) {
				logger.warn("Item binding '{}' references camera id '{}' which has not been configured", itemName, cameraId);
				return;
			}
		
			try {
				switch (bindingConfig.getCommandType()) {
					case MOTION:
						logger.warn("The 'motion' command is readonly. Ignoring.");
						break;
					
					case ARMED:
						camera.setMotionDetectEnabled(command.equals(OnOffType.ON));
						break;
					
					case MASKED: 
						camera.setMaskEnabled(command.equals(OnOffType.ON));
						break;

					default:
						logger.warn("Unsupported command type '{}'", bindingConfig.getCommandType()); 
				}
			}
			catch (Exception e) {
				logger.warn("Error executing command type '" + bindingConfig.getCommandType() + "'", e);
			}	
		}
	}
	
	/**
	 * @{inheritDoc}
	 */
	public void updated(Dictionary<String, ?> properties) throws ConfigurationException {
		stopCameras();
        
		if (properties == null || properties.isEmpty()) {
			logger.warn("Empty or null configuration. Ignoring.");            	
			return;
		}
        
        Enumeration<String> keys = properties.keys();
        while (keys.hasMoreElements()) {
            
            String key = (String) keys.nextElement();
            
            // the config-key enumeration contains additional keys that we
            // don't want to process here ...
            if ("service.pid".equals(key))
                continue;
            
            String value = (String) properties.get(key);
            if (StringUtils.isEmpty(value))
            	continue;
            
            Matcher matcher = CAMERA_CONFIG_PATTERN.matcher(key);            
            if (matcher.matches()) {
            	
            	String cameraId = matcher.group(1);
            	String cameraConfig = matcher.group(2);
            	
            	if (!camerasById.containsKey(cameraId)) {
            		Camera camera = new Camera(cameraId, eventPublisher);
            		camerasById.put(cameraId, camera);
            	}

            	Camera camera = camerasById.get(cameraId);
            	
                if (cameraConfig.equals("url")) {
                	// TODO: validation of URL?
                    camera.setUrl( value );
                } else if (cameraConfig.equals("encoding")) {
                	// TODO: validation of encoding type?
                    camera.setEncoding(value);
                } else if (cameraConfig.equals("mask")) {
            		File mask = new File(value);
            		if (!mask.exists()) {
            			logger.warn("Mask file does not exist ({}). Ignoring.", value);
            			continue;
            		}
            		if (!mask.isFile()) {
            			logger.warn("Mask file is not a file ({}). Ignoring.", value);
            			continue;
            		}                			
            		camera.setMask(mask);
                } else if (cameraConfig.equals("encoding")) {
                	try {
                		camera.setSensitivity(Integer.parseInt(value));
                	} catch (NumberFormatException e) {
                		logger.warn("Invalid 'encoding' value specified ({}). Ignoring.", value);
                	}
                }

            } else {
    			logger.warn("Unexpected or unsupported configuration: " + key + ". Ignoring.");            	
            }
        }
        
        if (camerasById.size() == 0)
            logger.warn("No cameras configured - the motion binding will not do anything!");

        startCameras();
	}
	
	private void startCameras() {
		for (Camera camera : camerasById.values()) {
			camera.start();
		}		
	}
	
	private void stopCameras() {
		for (Camera camera : camerasById.values()) {
			camera.stop();
		}
		
		camerasById.clear();
	}
	
	private void registerBindingConfig(MotionBindingConfig bindingConfig) {	
		if (bindingConfig == null)
			return;
		
		String cameraId = bindingConfig.getCameraId();
		String itemName = bindingConfig.getItemName();

		Camera camera = camerasById.get(cameraId);
		if (camera == null) {
			logger.warn("Item binding '{}' references camera id '{}' which has not been configured", itemName, cameraId);
			return;
		}
		
		switch (bindingConfig.getCommandType()) {
			case MOTION:
				camera.registerItemName(itemName);
				break;
			
			default:
				logger.warn("Unsupported command type '{}'", bindingConfig.getCommandType()); 
		}
	}
}
