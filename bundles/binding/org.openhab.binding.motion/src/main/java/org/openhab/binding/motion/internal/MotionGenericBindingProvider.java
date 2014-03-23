/**
 * Copyright (c) 2010-2014, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.motion.internal;

import org.apache.commons.lang.StringUtils;
import org.openhab.binding.motion.MotionBindingConfig;
import org.openhab.binding.motion.MotionBindingProvider;
import org.openhab.core.items.Item;
import org.openhab.core.library.items.SwitchItem;
import org.openhab.model.item.binding.AbstractGenericBindingProvider;
import org.openhab.model.item.binding.BindingConfigParseException;

/**
 * <p>This class can parse information from the generic binding format and 
 * provides Motion binding information from it. It registers as a 
 * {@link MotionBindingProvider} service as well.</p>
 * 
 * <p>Here are some examples for valid binding configuration strings:
 * 
 * <ul>
 * 	<li><code>{ motion="camera1:motion" }</code> - ON when motion detected, OFF when motion ceases</li>
 * </ul>
 * 
 * @author Ben Jones
 * @since 1.5.0
 */
public class MotionGenericBindingProvider extends AbstractGenericBindingProvider implements MotionBindingProvider {

	/**
	 * {@inheritDoc}
	 */
	public String getBindingType() {
		return "motion";
	}
	
	/**
	 * @{inheritDoc}
	 */
	public void validateItemType(Item item, String bindingConfig) throws BindingConfigParseException {
		if (!(item instanceof SwitchItem)) {
			throw new BindingConfigParseException("Item '" + item.getName()
					+ "' is of type '" + item.getClass().getSimpleName()
					+ "', only Switch items are allowed - please check your *.items configuration");
		}		
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void processBindingConfiguration(String context, Item item, String bindingConfig) throws BindingConfigParseException {
		super.processBindingConfiguration(context, item, bindingConfig);

        if (StringUtils.isEmpty(bindingConfig))
            throw new BindingConfigParseException("Null config for " + item.getName() + " - expecting a camera id");
		
		addBindingConfig(item, new MotionBindingConfig(item.getName(), bindingConfig));
	}
	
	/**
	 * {@inheritDoc}
	 */
	public MotionBindingConfig getItemConfig(String itemName) {
		return (MotionBindingConfig) this.bindingConfigs.get(itemName);
	}
}
