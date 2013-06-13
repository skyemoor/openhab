package org.openhab.binding.zwave.internal.protocol;

public interface ZWaveEventListener {
	void ZWaveIncomingEvent(ZWaveEvent event);
}
