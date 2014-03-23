/**
 * Copyright (c) 2010-2014, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.motion.internal;

import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openhab.binding.motion.MotionBindingConfig;
import org.openhab.binding.motion.MotionBindingProvider;
import org.openhab.core.binding.AbstractBinding;
import org.openhab.core.binding.BindingProvider;
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
			registerItemConfig(motionProvider.getItemConfig(itemName));
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
				registerItemConfig(motionProvider.getItemConfig(itemName));
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
            if ("service.pid".equals(key)) {
                continue;
            }
            
            Matcher matcher = CAMERA_CONFIG_PATTERN.matcher(key);
            
            String value = (String) properties.get(key);
            
            if (matcher.matches()) {	
            	String cameraId = matcher.group(1);
            	if (!camerasById.containsKey(cameraId)) {
            		Camera camera = new Camera(cameraId, eventPublisher);
            		camerasById.put(cameraId, camera);
            	}

                String cameraConfig = matcher.group(2);
                if (cameraConfig.equals("url")) {
                    camerasById.get(cameraId).setUrl( value );
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
	
	private void registerItemConfig(MotionBindingConfig itemConfig) {	
		if (itemConfig == null)
			return;
		
		String itemName = itemConfig.getItemName();
		String cameraId = itemConfig.getCameraId();

		if (!camerasById.containsKey(cameraId)) {
			logger.warn("Item binding '{}' references camera id '{}' which has not been configured", itemName, cameraId);
		}
		
		Camera camera = camerasById.get(cameraId);
		camera.registerItemName(itemName);
	}		
}
