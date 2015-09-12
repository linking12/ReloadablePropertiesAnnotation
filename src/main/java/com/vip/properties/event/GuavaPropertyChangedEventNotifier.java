package com.vip.properties.event;

import com.google.common.eventbus.EventBus;
import com.vip.properties.bean.PropertyModifiedEvent;
import com.vip.properties.internal.ReloadablePropertyPostProcessor;

public class GuavaPropertyChangedEventNotifier implements
		PropertyChangedEventNotifier {

	private final EventBus eventBus;

	public GuavaPropertyChangedEventNotifier() {
		this.eventBus = new EventBus();
	}

	@Override
	public void post(final PropertyModifiedEvent propertyChangedEvent) {
		this.eventBus.post(propertyChangedEvent);
	}

	@Override
	public void unregister(
			final ReloadablePropertyPostProcessor ReloadablePropertyPostProcessor) {
		this.eventBus.unregister(ReloadablePropertyPostProcessor);
	}

	@Override
	public void register(
			final ReloadablePropertyPostProcessor ReloadablePropertyPostProcessor) {
		this.eventBus.register(ReloadablePropertyPostProcessor);
	}

}
