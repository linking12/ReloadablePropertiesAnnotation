package com.vip.util;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sun.misc.Unsafe;

public class UnsafeSupport {

	protected static Logger log = LoggerFactory.getLogger(UnsafeSupport.class);

	private static Unsafe unsafe;

	static {
		Field field;
		try {
			field = Unsafe.class.getDeclaredField("theUnsafe");
			field.setAccessible(true);
			unsafe = (Unsafe) field.get(null);
		} catch (Exception e) {
			log.error("Get Unsafe instance occur error", e);
		}
	}

	/**
	 * 计算一个对象的大小
	 * 
	 * @param o
	 * @return
	 */

	public static long sizeOf(Object o) {
		HashSet<Field> fields = new HashSet<Field>();
		Class c = o.getClass();
		while (c != Object.class) {
			for (Field f : c.getDeclaredFields()) {
				if ((f.getModifiers() & Modifier.STATIC) == 0) {
					fields.add(f);
				}
			}
			c = c.getSuperclass();
		}
		long maxSize = 0;
		for (Field f : fields) {
			long offset = unsafe.objectFieldOffset(f);
			if (offset > maxSize) {
				maxSize = offset;
			}
		}
		return ((maxSize / 8) + 1) * 8; // padding
	}
	
	
}
