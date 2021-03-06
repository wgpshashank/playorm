package com.alvazan.orm.impl.bindings;

import java.util.Map;

import com.alvazan.orm.api.base.Bootstrap;
import com.alvazan.orm.api.base.DbTypeEnum;
import com.alvazan.orm.api.base.NoSqlEntityManagerFactory;
import com.alvazan.orm.api.z8spi.NoSqlRawSession;
import com.alvazan.orm.api.z8spi.conv.Converter;
import com.alvazan.orm.layer0.base.BaseEntityManagerFactoryImpl;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.netflix.astyanax.AstyanaxContext;
import com.netflix.astyanax.AstyanaxContext.Builder;
import com.netflix.astyanax.clock.MicrosecondsAsyncClock;
import com.netflix.astyanax.connectionpool.NodeDiscoveryType;
import com.netflix.astyanax.connectionpool.impl.ConnectionPoolConfigurationImpl;
import com.netflix.astyanax.connectionpool.impl.ConnectionPoolType;
import com.netflix.astyanax.connectionpool.impl.CountingConnectionPoolMonitor;
import com.netflix.astyanax.connectionpool.impl.FixedRetryBackoffStrategy;
import com.netflix.astyanax.connectionpool.impl.SimpleAuthenticationCredentials;
import com.netflix.astyanax.impl.AstyanaxConfigurationImpl;
import com.netflix.astyanax.model.ConsistencyLevel;

public class BootstrapImpl extends Bootstrap {

	@SuppressWarnings("rawtypes")
	@Override
	public NoSqlEntityManagerFactory createInstance(DbTypeEnum type, Map<String, Object> properties, Map<Class, Converter> converters, ClassLoader cl2) {
		ClassLoader previous = Thread.currentThread().getContextClassLoader();
		try {
			//Javassit uses the thread's context classloader so we need to set that for javassist then
			//we reset it back in case some other framework relies on it...
			ClassLoader playOrmCl = BootstrapImpl.class.getClassLoader();
			Thread.currentThread().setContextClassLoader(playOrmCl);
			return createInstanceImpl(type, properties, converters, cl2);
		} finally {
			Thread.currentThread().setContextClassLoader(previous);
		}
	}
	
	private NoSqlEntityManagerFactory createInstanceImpl(DbTypeEnum type, Map<String, Object> properties, Map<Class, Converter> converters, ClassLoader cl2) {
		Object spiImpl = properties.get(Bootstrap.SPI_IMPL);
		NoSqlRawSession temp = null;
		if(spiImpl != null && spiImpl instanceof NoSqlRawSession) {
			temp = (NoSqlRawSession) spiImpl;
		}
		
		Injector injector = Guice.createInjector(new ProductionBindings(type, temp));
		NoSqlEntityManagerFactory factory = injector.getInstance(NoSqlEntityManagerFactory.class);

		Named named = Names.named("logger");
		Key<NoSqlRawSession> key = Key.get(NoSqlRawSession.class, named);
		NoSqlRawSession inst = injector.getInstance(key);
		inst.start(properties);
		
		//why not just add setInjector() and setup() in NoSqlEntityManagerFactory
		BaseEntityManagerFactoryImpl impl = (BaseEntityManagerFactoryImpl)factory;
		impl.setInjector(injector);
		
		ClassLoader cl = cl2;
		if(cl == null)
			cl = BootstrapImpl.class.getClassLoader();
		//The expensive scan all entities occurs here...
		impl.setup(properties, converters, cl);
		
		return impl;
	}
	
	@Override
	protected void createBestCassandraConfig(Map<String, Object> properties,
			String clusterName, String keyspace2, String seeds2) {
		ConnectionPoolConfigurationImpl poolConfig = new ConnectionPoolConfigurationImpl("MyConnectionPool")
        .setMaxConnsPerHost(20)
        .setInitConnsPerHost(2)
        .setSeeds(seeds2)
        .setConnectTimeout(10000)
        .setRetryBackoffStrategy(new FixedRetryBackoffStrategy(5000, 30000));

		Object username = properties.get(Bootstrap.CASSANDRA_USERNAME);
		Object password = properties.get(Bootstrap.CASSANDRA_PASSWORD);
		if (username != null && password != null) {
			poolConfig.setAuthenticationCredentials(new SimpleAuthenticationCredentials(""+username, ""+password));
		}
		
		Object port = properties.get(Bootstrap.CASSANDRA_THRIFT_PORT);
		if(port != null) {
			int portVal = 9160;
			if(port instanceof Integer) 
				portVal = (Integer) port;
			else if(port instanceof String)
				portVal = Integer.valueOf((String) port);
			else
				throw new RuntimeException(Bootstrap.CASSANDRA_THRIFT_PORT+" key in map has a value of type="+port.getClass()+" but that must be a String or Integer");
			poolConfig.setPort(portVal);
		}
		
		Builder builder = new AstyanaxContext.Builder()
	    .forCluster(clusterName)
	    .forKeyspace(keyspace2)
	    .withAstyanaxConfiguration(new AstyanaxConfigurationImpl()      
	        .setDiscoveryType(NodeDiscoveryType.RING_DESCRIBE)
	        .setConnectionPoolType(ConnectionPoolType.TOKEN_AWARE)
		.setClock(new MicrosecondsAsyncClock())
	    )
	    .withConnectionPoolConfiguration(poolConfig)
	    .withConnectionPoolMonitor(new CountingConnectionPoolMonitor());

		String clStr = (String) properties.get(Bootstrap.CASSANDRA_DEFAULT_CONSISTENCY_LEVEL);
		if(clStr != null) {
			ConsistencyLevel cl = null;
			for(ConsistencyLevel l : ConsistencyLevel.values()) {
				if(l.toString().equals(clStr)) {
					cl = l;
					break;
				}
			}
			if(cl == null)
				throw new IllegalArgumentException("Consistency level must be one of the strings mathcin astyanax ConsistencyLevel.XXXX");

			AstyanaxConfigurationImpl config = new AstyanaxConfigurationImpl();
			config.setDefaultWriteConsistencyLevel(cl);
			config.setDefaultReadConsistencyLevel(cl);
			builder = builder.withAstyanaxConfiguration(config);
		} else if(seeds2.contains(",")) {
			//for a multi-node cluster, we want the test suite using quorum on writes and
			//reads so we have no issues...
			AstyanaxConfigurationImpl config = new AstyanaxConfigurationImpl();
			config.setDefaultWriteConsistencyLevel(ConsistencyLevel.CL_QUORUM);
			config.setDefaultReadConsistencyLevel(ConsistencyLevel.CL_QUORUM);
			builder = builder.withAstyanaxConfiguration(config);
		}
		properties.put(Bootstrap.CASSANDRA_BUILDER, builder);
	}

	@Override
	protected void createBestMongoDbConfig(Map<String, Object> properties,
			String clusterName, String keyspace2, String seeds2) {
		properties.put(Bootstrap.MONGODB_CLUSTERNAME, clusterName);
		properties.put(Bootstrap.MONGODB_KEYSPACE, keyspace2);
		properties.put(Bootstrap.MONGODB_SEEDS, seeds2);
	}

	@Override
	protected void createBestHBaseConfig(Map<String, Object> properties,
			String clusterName, String keyspace2, String seeds2) {
		properties.put(Bootstrap.HBASE_CLUSTERNAME, clusterName);
		properties.put(Bootstrap.HBASE_KEYSPACE, keyspace2);
		properties.put(Bootstrap.HBASE_SEEDS, seeds2);
	}

	@Override
	protected void createBestCqlConfig(Map<String, Object> properties,
			String clusterName, String keyspace2, String seeds2) {
		properties.put(Bootstrap.CQL_CLUSTERNAME, clusterName);
		properties.put(Bootstrap.CQL_KEYSPACE, keyspace2);
		properties.put(Bootstrap.CQL_SEEDS, seeds2);
	}
}
