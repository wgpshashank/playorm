package com.alvazan.orm.impl.meta.data.collections;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alvazan.orm.api.spi5.NoSqlSession;
import com.alvazan.orm.api.spi9.db.KeyValue;
import com.alvazan.orm.api.spi9.db.Row;
import com.alvazan.orm.impl.meta.data.MetaAbstractClass;
import com.alvazan.orm.impl.meta.data.Tuple;

public final class MapProxyFetchAll<K, V> extends HashMap<K, V> implements CacheLoadCallback {

	private static final Logger log = LoggerFactory.getLogger(MapProxyFetchAll.class);
	private static final long serialVersionUID = 1L;
	private boolean cacheLoaded = false;
	private NoSqlSession session;
	private MetaAbstractClass<V> classMeta;
	private Field fieldForKey;
	private List<byte[]> keys;
	private Set<V> originals = new HashSet<V>();
	private boolean removeAll;
	private Object owner;
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static MapProxyFetchAll create(Object owner, NoSqlSession session, MetaAbstractClass classMeta,
			List<byte[]> keys, Field fieldForKey) {
		return new MapProxyFetchAll(owner, session, classMeta, keys, fieldForKey);
	}
	private MapProxyFetchAll(Object owner, NoSqlSession session, MetaAbstractClass<V> classMeta,
			List<byte[]> keys, Field fieldForKey) {
		this.session = session;
		this.classMeta = classMeta;
		this.keys = keys;
		this.fieldForKey = fieldForKey;
		this.owner = owner;
	}

	//Callback from one of the proxies to load the entire cache based
	//on a hit of getXXXXX (except for getId which doesn't need to go to database)
	@SuppressWarnings("unchecked")
	public void loadCacheIfNeeded() {
		if(cacheLoaded)
			return;

		Iterable<KeyValue<Row>> rows = session.findAll(classMeta.getColumnFamily(), keys);
		log.info("loading key list="+keys+" results="+rows);
		for(KeyValue<Row> kv : rows) {
			byte[] key = (byte[]) kv.getKey();
			Row row = kv.getValue();
			Tuple<V> tuple = classMeta.convertIdToProxy(row, key, session, null);
			if(row == null) {
				throw new IllegalStateException("This entity is corrupt(your entity='"+owner+"') and contains a" +
						" reference/FK to a row that does not exist in another table.  " +
						"It refers to another entity with pk="+tuple.getEntityId()+" which does not exist");
			}

			V proxy = tuple.getProxy();
			//inject the row into the proxy object here to load it's fields
			classMeta.fillInInstance(row, session, proxy);
			
			Object mapKey = getKeyField(proxy);
			super.put((K) mapKey,  proxy);
			originals.add(proxy);
		}
		cacheLoaded = true;
	}

	private Object getKeyField(V proxy) {
		try {
			return fieldForKey.get(proxy);
		} catch (IllegalArgumentException e) {
			throw new RuntimeException(e);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public int size() {
		if(cacheLoaded)
			return super.size();
		return keys.size();
	}

	@Override
	public boolean isEmpty() {
		if(cacheLoaded)
			return super.isEmpty();
		return keys.isEmpty();
	}

	@Override
	public V get(Object key) {
		loadCacheIfNeeded();
		return super.get(key);
	}

	@Override
	public boolean containsKey(Object key) {
		loadCacheIfNeeded();
		return super.containsKey(key);
	}

	@Override
	public V put(K key, V value) {
		loadCacheIfNeeded();
		return super.put(key, value);
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> m) {
		loadCacheIfNeeded();
		super.putAll(m);
	}

	@Override
	public V remove(Object key) {
		loadCacheIfNeeded();
		return super.remove(key);
	}

	@Override
	public void clear() {
		//no need to load from cache in this case, just clear both key list in
		//case they haven't loaded cache yet so when it loads it is super fast
		//and clear the hashtable in case they loaded already
		removeAll = true;
		super.clear();
	}

	@Override
	public boolean containsValue(Object value) {
		loadCacheIfNeeded();
		return super.containsValue(value);
	}

	@Override
	public Object clone() {
		loadCacheIfNeeded();
		return super.clone();
	}

	@Override
	public Set<K> keySet() {
		//Well, we don't know the keys since we have not loaded from cache as the
		//key could be any field and we only have ids in memory before cache is loaded
		//id could be a field and we could optimize later for that.
		loadCacheIfNeeded();
		return super.keySet();
	}

	@Override
	public Collection<V> values() {
		loadCacheIfNeeded();
		return super.values();
	}

	@Override
	public Set<java.util.Map.Entry<K, V>> entrySet() {
		loadCacheIfNeeded();
		return super.entrySet();
	}

	public Collection<V> getToBeRemoved() {
		List<V> removes = new ArrayList<V>();
		if(!removeAll && !cacheLoaded)
			return removes;
		
		//If remove all(clear method called) we still need to check in case they added some
		//back, but if !removeAll and !cacheLoaded, we know none were removed.
		Collection<V> current = values();
		for(V entity : originals) {
			if(!current.contains(entity))
				removes.add(entity);
		}
		return removes;
	}

	public Collection<V> getToBeAdded() {
		List<V> adds = new ArrayList<V>();
		if(!cacheLoaded)
			return adds;
			
		for(V entity : values()) {
			if(!originals.contains(entity))
				adds.add(entity);
		}
		return adds;
	}
	
}
