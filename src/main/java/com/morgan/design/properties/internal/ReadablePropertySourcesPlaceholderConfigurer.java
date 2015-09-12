package com.morgan.design.properties.internal;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;

import org.apache.curator.framework.CuratorFramework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.util.StringUtils;

import com.google.common.collect.Lists;
import com.morgan.design.properties.bean.PropertyModifiedEvent;
import com.morgan.design.properties.event.PropertyChangedEventNotifier;
import com.morgan.design.properties.internal.ZookeeperWatcher.ZkClientFacotry;
import com.morgan.design.properties.resolver.PropertyResolver;

/**
 * Specialisation of {@link PropertySourcesPlaceholderConfigurer} that can react
 * to changes in the resources specified. The watching process does not start by
 * default, initiation is triggered by calling
 * <code>ReadablePropertySourcesPlaceholderConfigurer.startWatching()</code>
 * 
 * @author James Morgan
 */
public class ReadablePropertySourcesPlaceholderConfigurer extends
		PropertySourcesPlaceholderConfigurer implements EventPublisher {

	protected static Logger log = LoggerFactory
			.getLogger(ReadablePropertySourcesPlaceholderConfigurer.class);

	private final PropertyChangedEventNotifier eventNotifier;
	private final PropertyResolver propertyResolver;

	private Properties properties;
	private Resource[] resourcesPath;
	private String[] zookeeperPath;

	@Autowired
	public ReadablePropertySourcesPlaceholderConfigurer(
			final PropertyChangedEventNotifier eventNotifier,
			final PropertyResolver propertyResolver) {
		this.eventNotifier = eventNotifier;
		this.propertyResolver = propertyResolver;
	}

	@Override
	protected void loadProperties(final Properties props) throws IOException {
		if (resourcesPath.length != 0) {
			super.loadProperties(props);
		}
		if (zookeeperPath.length != 0) {
			loadProperties0(props);
		}
		this.properties = props;
	}

	protected void loadProperties0(final Properties props) throws IOException {
		CuratorFramework curator = ZkClientFacotry.getZkClient();
		for (String str : zookeeperPath) {
			try {
				byte[] statbyte = curator.getData().forPath(str);
				String propstr = new String(statbyte, Charset.defaultCharset());
				props.load(new StringReader(propstr));
			} catch (Exception e) {
				throw new IOException(e);
			}
		}

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
	public void onZkNodeChange(String resource) {
		final Properties reloadedProperties = new Properties();
		try {
			reloadedProperties.load(new StringReader(resource));
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
