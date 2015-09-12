package com.vip.properties.resolver;

public interface PropertyResolver {

	/**
	 * @param property
	 *            The property to resolve by substitution, if required
	 * @return The result of the property resolution, or the property itself if
	 *         no substitution was required
	 */
	String resolveProperty(final Object property);

	/**
	 * Can be used to check whether a property requires further resolution
	 * 
	 * @param property
	 *            The property to resolve by substitution, if required
	 * @return true if the chosen {@link PropertyResolver} performs custom
	 *         resolution
	 */
	boolean requiresFurtherResoltuion(final Object property);

}
