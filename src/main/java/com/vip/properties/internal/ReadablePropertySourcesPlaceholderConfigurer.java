package com.vip.properties.internal;

import java.io.IOException;
import java.io.StringReader;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;

import org.apache.curator.framework.CuratorFramework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.util.StringUtils;

import com.google.common.collect.Lists;
import com.vip.properties.bean.PropertyModifiedEvent;
import com.vip.properties.event.GuavaPropertyChangedEventNotifier;
import com.vip.properties.event.PropertyChangedEventNotifier;
import com.vip.properties.internal.ZookeeperWatcher.ZkClientFacotry;
import com.vip.properties.resolver.PropertyResolver;
import com.vip.properties.resolver.SubstitutingPropertyResolver;

public class ReadablePropertySourcesPlaceholderConfigurer extends
		PropertySourcesPlaceholderConfigurer implements EventPublisher {

	protected static Logger log = LoggerFactory
			.getLogger(ReadablePropertySourcesPlaceholderConfigurer.class);

	private final PropertyChangedEventNotifier eventNotifier;
	private final PropertyResolver propertyResolver;

	private String fileEncoding;

	protected boolean zkOverride = false;

	private Properties properties;
	private Resource[] resourcesPath;
	private String[] zookeeperPath;

	public ReadablePropertySourcesPlaceholderConfigurer() {
		this.eventNotifier = new GuavaPropertyChangedEventNotifier();
		this.propertyResolver = new SubstitutingPropertyResolver();
	}

	@Override
	protected void loadProperties(final Properties props) throws IOException {
		if (resourcesPath.length != 0) {
			loadPropertiesFromResource(props);
		}
		if (zookeeperPath.length != 0) {
			loadPropertiesFromZk(props);
		}
		this.properties = props;
	}

	@Override
	public void onResourceChanged(final Resource resource) {
		try {
			final Properties reloadedProperties = PropertiesLoaderUtils
					.loadProperties(resource);
			for (final String property : this.properties.stringPropertyNames()) {

				final String oldValue = this.properties.getProperty(property);
				final String newValue = reloadedProperties
						.getProperty(property);

				if (propertyExistsAndNotNull(property, newValue)
						&& propertyChange(oldValue, newValue)) {
					// Update locally stored copy of properties
					this.properties.setProperty(property, newValue);
					// Post change event to notify any potential listeners
					this.eventNotifier.post(new PropertyModifiedEvent(property,
							oldValue, newValue));
				}
			}
		} catch (final IOException e) {
			log.error("Failed to reload properties file once change", e);
		}
	}

	@Override
	public void onZookeeperChanged(byte[] resource) {
		final Properties reloadedProperties = new Properties();
		try {
			String result = new String(resource, this.fileEncoding);
			reloadedProperties.load(new StringReader(result));
			for (final String property : this.properties.stringPropertyNames()) {
				final String oldValue = this.properties.getProperty(property);
				final String newValue = reloadedProperties
						.getProperty(property);
				if (propertyExistsAndNotNull(property, newValue)
						&& propertyChange(oldValue, newValue)) {
					// Update locally stored copy of properties
					this.properties.setProperty(property, newValue);
					// Post change event to notify any potential listeners
					this.eventNotifier.post(new PropertyModifiedEvent(property,
							oldValue, newValue));
				}
			}
		} catch (IOException e) {
			log.error("Failed to reload properties file once change", e);
		}
	}

	protected void loadPropertiesFromResource(final Properties props)
			throws IOException {
		super.loadProperties(props);
	}

	protected void loadPropertiesFromZk(final Properties props)
			throws IOException {
		Properties result = new Properties();
		CuratorFramework curator = ZkClientFacotry.getZkClient();
		for (String str : zookeeperPath) {
			try {
				byte[] statbyte = curator.getData().forPath(str);
				String propstr = new String(statbyte, this.fileEncoding);
				result.load(new StringReader(propstr));
			} catch (Exception e) {
				throw new IOException(e);
			}
		}
		if (zkOverride) {
			props.putAll(result);
		} else {
			Iterator<Object> it = result.keySet().iterator();
			while (it.hasNext()) {
				Object key = it.next();
				if (props.get(key) == null) {
					props.put(key, result.get(key));
				}
			}
		}
	}

	@Override
	public void setFileEncoding(String encoding) {
		super.setFileEncoding(encoding);
		this.fileEncoding = encoding;
	}

	public void setZkOverride(boolean zkOverride) {
		this.zkOverride = zkOverride;
	}

	public void setLocations(final String[] locations) {
		List<Resource> resourcesPath = Lists.newArrayList();
		List<String> zookeeperPath = Lists.newArrayList();
		for (String str : locations) {
			if (str.startsWith("zookeeper")) {
				zookeeperPath.add(str);
			}
			if (str.startsWith("classpath")) {
				Resource resource = new ClassPathResource(str);
				resourcesPath.add(resource);
			}
			if (str.startsWith("file")) {
				String pathToUse = StringUtils.cleanPath(str);
				Resource resource = new FileSystemResource(pathToUse);
				resourcesPath.add(resource);
			}
		}
		Resource[] arrayResourcePath = (Resource[]) resourcesPath.toArray();
		String[] arrayZookeeperPath = (String[]) zookeeperPath.toArray();
		super.setLocations(arrayResourcePath);
		this.resourcesPath = arrayResourcePath;
		this.zookeeperPath = arrayZookeeperPath;
	}

	public Properties getProperties() {
		return this.properties;
	}

	public void startWatching() {
		if (null == this.eventNotifier) {
			throw new BeanInitializationException(
					"Event bus not setup, you should not be calling this method...!");
		}
		try {
			Executors.newSingleThreadExecutor().execute(
					new PropertiesWatcher(this.resourcesPath, this));
		} catch (final IOException e) {
			log.error("Unable to start properties file watcher", e);
		}
		ZookeeperWatcher zkWatcher = new ZookeeperWatcher(this.zookeeperPath,
				this);
		zkWatcher.start();

	}

	public Object resolveProperty(final Object property) {
		Object resolvedPropertyValue = this.properties
				.get(this.propertyResolver.resolveProperty(property));
		if (notStringpropertyToSubstitute(resolvedPropertyValue)) {
			return resolvedPropertyValue;
		}
		while (this.propertyResolver
				.requiresFurtherResoltuion(resolvedPropertyValue)) {
			resolvedPropertyValue = buildResolvedString(resolvedPropertyValue);
		}
		return resolvedPropertyValue;
	}

	private Object buildResolvedString(final Object resolvedPropertyValue) {
		final String resolvedValueStr = resolvedPropertyValue.toString();

		final int startingIndex = resolvedValueStr.indexOf("${");
		final int endingIndex = resolvedValueStr.indexOf("}", startingIndex) + 1;

		final String toResolve = resolvedValueStr.substring(startingIndex,
				endingIndex);
		final String resolved = resolveProperty(toResolve).toString();

		return new StringBuilder()
				.append(resolvedValueStr.substring(0, startingIndex))
				.append(resolved)
				.append(resolvedValueStr.substring(endingIndex)).toString();
	}

	private boolean notStringpropertyToSubstitute(
			final Object resolvedPropertyValue) {
		return !(resolvedPropertyValue instanceof String);
	}

	private boolean propertyChange(final String oldValue, final String newValue) {
		return null == oldValue || !oldValue.equals(newValue);
	}

	private boolean propertyExistsAndNotNull(final String property,
			final String newValue) {
		return this.properties.containsKey(property) && null != newValue;
	}

}
