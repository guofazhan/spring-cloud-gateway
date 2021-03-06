/*
 * Copyright 2013-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.gateway.support;

import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.handler.predicate.RoutePredicateFactory;

/**
 * name截取工具类
 * @author Spencer Gibb
 */
public class NameUtils {
	public static final String GENERATED_NAME_PREFIX = "_genkey_";

	public static String generateName(int i) {
		return GENERATED_NAME_PREFIX + i;
	}

	/**
	 * 根据谓语工厂类名截取谓语工厂的name属性
	 * xxxxxRoutePredicateFactory:name=xxxxx
	 * bbbbbRoutePredicateFactory:name=bbbbb
	 * @param clazz
	 * @return
	 */
	public static String normalizeRoutePredicateName(Class<? extends RoutePredicateFactory> clazz) {
		return removeGarbage(clazz.getSimpleName().replace(RoutePredicateFactory.class.getSimpleName(), ""));
	}

	/**
	 * 根据网关过滤器工厂截取工厂的name属性
	 * xxxxxGatewayFilterFactory:name=xxxxx
	 * bbbbbGatewayFilterFactory:name=bbbbb
	 * @param clazz
	 * @return
	 */
	public static String normalizeFilterFactoryName(Class<? extends GatewayFilterFactory> clazz) {
		return removeGarbage(clazz.getSimpleName().replace(GatewayFilterFactory.class.getSimpleName(), ""));
	}

	private static String removeGarbage(String s) {
		int garbageIdx = s.indexOf("$Mockito");
		if (garbageIdx > 0) {
			return s.substring(0, garbageIdx);
		}

		return s;
	}
}
