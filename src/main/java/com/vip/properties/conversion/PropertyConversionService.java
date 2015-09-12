package com.vip.properties.conversion;

import java.lang.reflect.Field;

public interface PropertyConversionService {

	/**
	 * @param field
	 *            the destination filed to set the property on
	 * @param property
	 *            the property to be converted for the given field
	 * @return the potentially converted field
	 */
	Object convertPropertyForField(final Field field, final Object property);
}
