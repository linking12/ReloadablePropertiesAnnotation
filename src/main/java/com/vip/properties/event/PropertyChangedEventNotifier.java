package com.vip.properties.event;

import com.vip.properties.bean.PropertyModifiedEvent;
import com.vip.properties.internal.ReloadablePropertyPostProcessor;

public interface PropertyChangedEventNotifier {

	void post(PropertyModifiedEvent propertyChangedEvent);

	void unregister(ReloadablePropertyPostProcessor reloadablePropertyProcessor);

	void register(ReloadablePropertyPostProcessor reloadablePropertyProcessor);

}
