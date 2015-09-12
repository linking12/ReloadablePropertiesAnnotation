package com.vip.properties.internal;

import org.springframework.core.io.Resource;

public interface EventPublisher {

	void onResourceChanged(Resource resource);

	void onZookeeperChanged(String resource);
}