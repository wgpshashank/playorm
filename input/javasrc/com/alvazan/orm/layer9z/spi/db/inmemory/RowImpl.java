package com.alvazan.orm.layer9z.spi.db.inmemory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.SortedMap;
import java.util.TreeMap;

import com.alvazan.orm.api.spi3.meta.conv.ByteArray;
import com.alvazan.orm.api.spi9.db.Column;
import com.alvazan.orm.api.spi9.db.Row;

public class RowImpl implements Row {
	private byte[] key;
	private NavigableMap<ByteArray, Column> columns = new TreeMap<ByteArray, Column>();
	
	public RowImpl() {
	}

	public RowImpl(TreeMap<ByteArray, Column> map) {
		this.columns = map;
	}
	
	/* (non-Javadoc)
	 * @see com.alvazan.orm.layer3.spi.db.inmemory.RowTemp#getKey()
	 */
	@Override
	public byte[] getKey() {
		return key;
	}
	/* (non-Javadoc)
	 * @see com.alvazan.orm.layer3.spi.db.inmemory.RowTemp#setKey(byte[])
	 */
	@Override
	public void setKey(byte[] key) {
		this.key = key;
	}
	
	/* (non-Javadoc)
	 * @see com.alvazan.orm.layer3.spi.db.inmemory.RowTemp#getColumns()
	 */
	@Override
	public Collection<Column> getColumns() {
		return columns.values();
	}

	/* (non-Javadoc)
	 * @see com.alvazan.orm.layer3.spi.db.inmemory.RowTemp#getColumn(byte[])
	 */
	@Override
	public Column getColumn(byte[] key) {
		ByteArray bKey = new ByteArray(key);
		return columns.get(bKey);
	}
	/* (non-Javadoc)
	 * @see com.alvazan.orm.layer3.spi.db.inmemory.RowTemp#put(byte[], com.alvazan.orm.api.spi3.db.Column)
	 */
	@Override
	public void put(byte[] name, Column col) {
		ByteArray key = new ByteArray(name);
		columns.put(key, col);
	}
	/* (non-Javadoc)
	 * @see com.alvazan.orm.layer3.spi.db.inmemory.RowTemp#remove(byte[])
	 */
	@Override
	public void remove(byte[] name) {
		ByteArray key = new ByteArray(name);
		columns.remove(key);
	}
	
	/* (non-Javadoc)
	 * @see com.alvazan.orm.layer3.spi.db.inmemory.RowTemp#columnSlice(byte[], byte[])
	 */
	@Override
	public Collection<Column> columnSlice(byte[] from, byte[] to) {
		ByteArray fromArray = new ByteArray(from);
		ByteArray toArray = new ByteArray(to);
		SortedMap<ByteArray, Column> result = columns.subMap(fromArray, true, toArray, true);
		return result.values();
	}

	/* (non-Javadoc)
	 * @see com.alvazan.orm.layer3.spi.db.inmemory.RowTemp#columnByPrefix(byte[])
	 */
	@Override
	public Collection<Column> columnByPrefix(byte[] prefix) {
		List<Column> prefixed = new ArrayList<Column>();
		boolean started = false;
		for(Entry<ByteArray, Column> col : columns.entrySet()) {
			if(col.getKey().hasPrefix(prefix)) {
				started = true;
				prefixed.add(col.getValue());
			} else if(started)
				break; //since we hit the prefix and we are sorted, we can break.
		}
		return prefixed;
	}

	@Override
	public Iterable<Column> columnRangeScanAll() {
		throw new UnsupportedOperationException("not done yet");
	}
}
