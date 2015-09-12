package com.morgan.design.properties.internal;

import java.nio.charset.Charset;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.NodeCache;
import org.apache.curator.framework.recipes.cache.NodeCacheListener;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent.Type;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class ZookeeperWatcher {
	protected static Logger log = LoggerFactory
			.getLogger(ZookeeperWatcher.class);

	private final String[] locations;
	private final EventPublisher eventPublisher;

	private CuratorFramework curatorFramework;
	final ExecutorService service;

	public ZookeeperWatcher(final String[] locations,
			final EventPublisher eventPublisher) {
		this.locations = locations;
		this.eventPublisher = eventPublisher;
		this.curatorFramework = ZkClientFacotry.getZkClient();
		this.service = Executors.newCachedThreadPool();
	}

	public void start() {
		Set<String> locationsSet = Sets.newHashSet(this.locations);
		Set<String> locationParentSet = Sets.newHashSet();
		for (String locationPath : locationsSet) {
			try {
				Stat stat = curatorFramework.checkExists()
						.forPath(locationPath);
				if (stat != null) {
					doNodeWatch(locationPath);
				} else {
					String parentPath = locationPath.substring(0,
							locationPath.lastIndexOf("/"));
					locationParentSet.add(parentPath);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		doPathWatch(locationParentSet);
	}

	private void doPathWatch(Set<String> pathSet) {
		for (String path : pathSet) {
			final PathChildrenCache cache = new PathChildrenCache(
					curatorFramework, path, true);
			try {
				cache.start();
				cache.getListenable().addListener(
						new PathChildrenCacheListener() {
							@Override
							public void childEvent(CuratorFramework client,
									PathChildrenCacheEvent event)
									throws Exception {
								if (event.getType() == Type.CHILD_ADDED) {
									final String path = event.getData()
											.getPath();
									try {
										eventPublisher
												.onZookeeperChanged(new String(
														event.getData()
																.getData(),
														Charset.defaultCharset()));
										doNodeWatch(path);
									} finally {
										cache.close();
									}
								}
							}
						});
			} catch (Exception e) {
				log.error("Unable to watch path [{}] Exception [{}]",
						new Object[] { path, e.getMessage() });
			}
		}
	}

	private void doNodeWatch(String path) throws Exception {
		final NodeCache cache = new NodeCache(curatorFramework, path);
		cache.getListenable().addListener(new NodeCacheListener() {
			@Override
			public void nodeChanged() throws Exception {
				byte[] data = cache.getCurrentData().getData();
				eventPublisher.onZookeeperChanged(new String(data, Charset
						.defaultCharset()));
				;
			}
		}, service);
		cache.start(true);

	}

	public static class ZkClientFacotry {

		private static Map<String, CuratorFramework> cacheConnection = Maps
				.newConcurrentMap();

		public static CuratorFramework getZkClient() {
			String zkConnection = System.getProperty("ZK_CONNECTION");
			if (cacheConnection.get(zkConnection) != null) {
				return cacheConnection.get(zkConnection);
			} else {
				CuratorFrameworkFactory.Builder builder = CuratorFrameworkFactory
						.builder();
				CuratorFramework client = builder.connectString(zkConnection)
						.sessionTimeoutMs(30000).connectionTimeoutMs(30000)
						.canBeReadOnly(true)
						.retryPolicy(new ExponentialBackoffRetry(1000, 3))
						.build();
				client.start();
				try {
					client.blockUntilConnected();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				return client;
			}
		}
	}

}
